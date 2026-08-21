/**
 * The licence server.
 *
 * Exists for one reason, recorded fully in `.kiro/steering/inspection-passes.md`: a Google Play purchase
 * belongs to a Google account, and this app runs on the phone being inspected — which is the *seller's*
 * phone, signed in to the seller's account. So a buyer's purchase can never be seen where the app actually
 * runs. A code carries the entitlement across; this server is the only thing that can count how many times
 * it has been used, because two phones that have never met cannot agree on a number.
 *
 * ## What it does not do
 *
 * It stores no personal data. No account, no phone number, no email, no raw device identifier. What a
 * buyer gives it is a code they were issued; what a phone gives it is a hash of its own id salted with
 * that code, which is enough to recognise the same phone twice under one code and useless for anything
 * else. There is nothing here to leak that would embarrass anyone.
 *
 * ## Trust model, stated plainly
 *
 * `/issue` verifies Google's own signature over the purchase, using the app's public licensing key. That
 * proves the receipt is genuine and was issued for this app. It does **not** prove the purchase is still
 * valid — a refund afterwards is invisible here, and detecting that would need the Play Developer API and
 * a service account. The exposure is one pack of passes per refund, capped and small, and the decision to
 * accept it is written down rather than implied.
 *
 * Replay is closed off separately: `purchase_token` is UNIQUE, so one receipt mints exactly one pack
 * however many times it is presented.
 */

import { format, isWellFormed, normalise } from './passcode.js';
import { generate } from './passcode.js';
import { purgeExpired } from './purge.js';

/** A pass lasts a day. Must match InspectionPass.DURATION_MILLIS in the app. */
const PASS_DURATION_MS = 24 * 60 * 60 * 1000;

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8' },
  });

/**
 * Errors carry a machine-readable `reason` as well as a sentence.
 *
 * The app has to tell a buyer something specific while a seller waits — "this code has no inspections
 * left" and "we cannot reach the licence server" call for completely different behaviour on screen, and a
 * bare 400 would leave the app guessing.
 */
const fail = (reason, message, status, headers = {}) =>
  new Response(JSON.stringify({ ok: false, reason, message }), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', ...headers },
  });

/**
 * The whole surface of this server, in one table.
 *
 * Written as data because the alternative was a chain of `if`s where the method was checked before the
 * path — which answered `GET /` with "use POST" instead of "no such endpoint", a small lie that would send
 * anyone debugging a typo'd URL looking in the wrong place entirely. A table cannot get that order wrong.
 *
 * HEAD is allowed on /health because uptime monitors use it, and a monitor that reports 405 on the one
 * endpoint whose job is to say "I am fine" is worse than no monitor.
 */
const ROUTES = {
  '/health': { GET: () => json({ ok: true }), HEAD: () => json({ ok: true }) },
  '/issue': { POST: issue },
  '/redeem': { POST: redeem },
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    const methods = ROUTES[url.pathname];
    if (!methods) {
      return fail('route', 'No such endpoint.', 404);
    }

    const handler = methods[request.method];
    if (!handler) {
      const allowed = Object.keys(methods).join(', ');
      // The Allow header is what the status code means; sending 405 without it is a half-answer.
      return fail('method', `Use ${allowed} on this endpoint.`, 405, { allow: allowed });
    }

    try {
      return await handler(request, env);
    } catch (error) {
      // Logged rather than returned. An internal message helps an attacker and means nothing to a buyer.
      console.error('unhandled', { path: url.pathname, error: String(error) });
      return fail('server', 'Something went wrong at our end. Please try again.', 500);
    }
  },

  /**
   * The nightly forgetting. See src/purge.js for why it matters more than it looks.
   *
   * Failures are left to throw: a cron run that fails is visible in the Cloudflare dashboard, and a swept
   * error here would mean quietly keeping device hashes forever while the Data safety form said otherwise.
   */
  async scheduled(event, env) {
    const { deleted, complete } = await purgeExpired(env.DB, Date.now());
    console.log('purged expired activations', { deleted, complete });
    if (!complete) {
      console.warn('purge hit its batch cap; more rows remain and tomorrow will take them');
    }
  },
};

/**
 * Turn a verified Play purchase into a code.
 *
 * Idempotent by construction: the same purchase token always returns the same code, because a buyer whose
 * connection dropped after paying must be able to ask again rather than lose their money.
 */
async function issue(request, env) {
  const body = await request.json().catch(() => null);
  if (!body) return fail('body', 'Expected JSON.', 400);

  const { purchaseJson, signature } = body;
  if (typeof purchaseJson !== 'string' || typeof signature !== 'string') {
    return fail('body', 'purchaseJson and signature are required.', 400);
  }

  if (!(await verifyPlaySignature(purchaseJson, signature, env.PLAY_LICENSE_PUBLIC_KEY))) {
    // Deliberately the same answer whether the signature is malformed, wrong, or for another app. A
    // caller learning *which* is a caller being helped to forge one.
    return fail('signature', 'That purchase could not be verified.', 403);
  }

  const purchase = JSON.parse(purchaseJson);
  const purchaseToken = purchase.purchaseToken;
  const productId = purchase.productId;
  if (!purchaseToken || !productId) {
    return fail('purchase', 'That purchase is missing its token or product.', 400);
  }

  // Asked before inserting, so a repeated call returns the original code rather than a duplicate-key
  // error. The UNIQUE constraint is still there as the thing that actually guarantees it under a race.
  const existing = await env.DB.prepare('SELECT code FROM packs WHERE purchase_token = ?')
    .bind(purchaseToken)
    .first();
  if (existing) {
    return json({ ok: true, code: format(existing.code), reissued: true });
  }

  const passes = Number(env.PASSES_PER_PACK ?? 5);
  const code = generate((n) => crypto.getRandomValues(new Uint8Array(n)));

  try {
    await env.DB.prepare(
      `INSERT INTO packs (code, purchase_token, product_id, passes_total, passes_used, created_at)
       VALUES (?, ?, ?, ?, 0, ?)`,
    )
      .bind(code, purchaseToken, productId, passes, Date.now())
      .run();
  } catch (error) {
    // Two calls arriving together: one inserted, this one lost. Whoever won, the buyer wants that code.
    const raced = await env.DB.prepare('SELECT code FROM packs WHERE purchase_token = ?')
      .bind(purchaseToken)
      .first();
    if (raced) return json({ ok: true, code: format(raced.code), reissued: true });
    throw error;
  }

  console.log('issued', { productId, passes });
  return json({ ok: true, code: format(code), passes, reissued: false });
}

/**
 * Spend one pass on this phone, or hand back the one already running on it.
 *
 * The second half is the rule that stops the model feeling like a trap: reopening the app on the same
 * phone inside the window must not cost a second inspection. A buyer who closed it by accident would
 * otherwise be charged for a slip.
 */
async function redeem(request, env) {
  const body = await request.json().catch(() => null);
  if (!body) return fail('body', 'Expected JSON.', 400);

  const { code, deviceHash } = body;
  if (typeof code !== 'string' || typeof deviceHash !== 'string' || deviceHash.length < 16) {
    return fail('body', 'code and deviceHash are required.', 400);
  }

  // Checked here as well as on the phone. The app rejects typos offline so the buyer gets an instant
  // answer; this repeats it because a server may never trust that a client did its job.
  if (!isWellFormed(code)) {
    return fail('malformed', 'That is not a valid code. Check it and try again.', 400);
  }

  const body8 = normalise(code);
  const pack = await env.DB.prepare(
    'SELECT code, passes_total, passes_used FROM packs WHERE code = ?',
  )
    .bind(body8)
    .first();

  if (!pack) {
    return fail('unknown', 'We have no record of that code.', 404);
  }

  const now = Date.now();

  // Already unlocked on this phone? Return the same expiry and charge nothing.
  const live = await env.DB.prepare(
    'SELECT expires_at FROM activations WHERE code = ? AND device_hash = ? AND expires_at > ? ORDER BY expires_at DESC LIMIT 1',
  )
    .bind(body8, deviceHash, now)
    .first();
  if (live) {
    return json({
      ok: true,
      expiresAtEpochMs: live.expires_at,
      passesLeft: pack.passes_total - pack.passes_used,
      alreadyActive: true,
    });
  }

  if (pack.passes_used >= pack.passes_total) {
    return fail(
      'exhausted',
      'That code has no inspections left. Every test that does not need a pass still works.',
      409,
    );
  }

  const expiresAt = now + PASS_DURATION_MS;

  // Conditional UPDATE rather than read-then-write: the WHERE clause is what makes two simultaneous
  // redeems unable to spend the same last pass twice.
  const spent = await env.DB.prepare(
    'UPDATE packs SET passes_used = passes_used + 1 WHERE code = ? AND passes_used < passes_total',
  )
    .bind(body8)
    .run();

  if (!spent.meta || spent.meta.changes !== 1) {
    return fail('exhausted', 'That code has no inspections left.', 409);
  }

  await env.DB.prepare(
    'INSERT INTO activations (code, device_hash, activated_at, expires_at) VALUES (?, ?, ?, ?)',
  )
    .bind(body8, deviceHash, now, expiresAt)
    .run();

  console.log('redeemed', { passesLeft: pack.passes_total - pack.passes_used - 1 });
  return json({
    ok: true,
    expiresAtEpochMs: expiresAt,
    passesLeft: pack.passes_total - pack.passes_used - 1,
    alreadyActive: false,
  });
}

/**
 * Verify Google's signature over a purchase, using the app's Base64 licensing public key.
 *
 * Play signs the purchase JSON with **SHA-1 and RSA PKCS#1 v1.5**. SHA-1 is not a choice made here and
 * not one that can be changed: it is what Google signs with, so it is what has to be verified. It is also
 * not load-bearing in the way a password hash would be — the signature only has to prove the bytes came
 * from Google, and a forgery would need Google's private key.
 *
 * The key from the Play Console is a base64 X.509 SubjectPublicKeyInfo, which is exactly what
 * `importKey('spki', …)` wants.
 */
async function verifyPlaySignature(purchaseJson, signatureBase64, publicKeyBase64) {
  if (!publicKeyBase64) {
    console.error('PLAY_LICENSE_PUBLIC_KEY is not configured');
    return false;
  }

  try {
    const key = await crypto.subtle.importKey(
      'spki',
      base64ToBytes(publicKeyBase64),
      { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-1' },
      false,
      ['verify'],
    );
    return await crypto.subtle.verify(
      'RSASSA-PKCS1-v1_5',
      key,
      base64ToBytes(signatureBase64),
      new TextEncoder().encode(purchaseJson),
    );
  } catch (error) {
    // A malformed signature or key throws rather than returning false. Either way the answer is no.
    console.error('signature check failed', String(error));
    return false;
  }
}

function base64ToBytes(value) {
  const binary = atob(value.replace(/\s/g, ''));
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}
