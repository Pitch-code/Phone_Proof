/**
 * Pass codes, the server half.
 *
 * The app reads codes; this issues them. Both must agree exactly, because the failure mode is a code
 * somebody paid for that the app calls invalid while they stand in front of a seller. So the algorithm
 * is duplicated deliberately and both halves are tested against one committed file,
 * `licensing/code-test-vectors.txt`.
 *
 * Kept dependency-free on purpose. It means `node --test` can check it against the vectors with nothing
 * installed, which is what allows the arithmetic to be verified before any of it reaches a real Worker.
 *
 * The Kotlin twin is core/preferences/.../passes/PassCode.kt. Read that file's comments before changing
 * anything here; in particular the weights are odd for a reason that took measuring.
 */

/** Crockford Base32: digits and letters minus I, L, O and U. Order defines each character's value. */
export const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

export const PREFIX = 'PP';
export const BODY_LENGTH = 8;

/**
 * The check character for a 7-character payload.
 *
 * Weights are 1, 3, 5, … — all odd, which is what makes every single-character error detectable: the
 * alphabet has 32 characters, so the modulus is a power of two, and odd numbers are invertible there.
 * With weights 2, 3, 4, … twelve single-character errors slip through. The cost is that a pair of
 * characters exactly 16 apart survives being swapped; both cannot be caught at once, because the
 * difference of two odd weights is always even.
 */
export function checkCharacter(payload) {
  let sum = 0;
  for (let i = 0; i < payload.length; i++) {
    const value = ALPHABET.indexOf(payload[i]);
    if (value < 0) throw new Error(`'${payload[i]}' is not in the pass-code alphabet`);
    sum += value * (2 * i + 1);
  }
  return ALPHABET[sum % ALPHABET.length];
}

/**
 * Canonical form, or null if what was typed cannot be one.
 *
 * Forgiving in the ways Crockford specifies, because someone is copying this off a screenshot by eye:
 * case is ignored, separators are dropped, `O` reads as `0` and `I`/`L` as `1`.
 */
export function normalise(typed) {
  if (typeof typed !== 'string') return null;

  let cleaned = '';
  for (const character of typed) {
    const upper = character.toUpperCase();
    if (upper === '-' || upper === '_' || /\s/.test(upper)) continue;
    if (upper === 'O') cleaned += '0';
    else if (upper === 'I' || upper === 'L') cleaned += '1';
    else cleaned += upper;
  }

  const body = cleaned.startsWith(PREFIX) ? cleaned.slice(PREFIX.length) : cleaned;
  if (body.length !== BODY_LENGTH) return null;
  for (const character of body) {
    if (!ALPHABET.includes(character)) return null;
  }
  return body;
}

/** Whether this is a code this system could have issued. Says nothing about whether it exists. */
export function isWellFormed(typed) {
  const body = normalise(typed);
  if (body === null) return false;
  return body[BODY_LENGTH - 1] === checkCharacter(body.slice(0, BODY_LENGTH - 1));
}

/** `PP-XXXX-XXXX`, for showing to a person. */
export function format(code) {
  const body = normalise(code);
  if (body === null) return null;
  return `${PREFIX}-${body.slice(0, 4)}-${body.slice(4)}`;
}

/**
 * A fresh code. **Server only** — the app deliberately has no equivalent.
 *
 * Never starts with the prefix characters. `normalise` strips a leading `PP` exactly once, so a body
 * beginning `PP` is genuinely ambiguous: typed with the prefix it reads correctly, typed without it
 * loses two characters and fails. Rather than complicate the reader, the issuer simply never mints one.
 * Costs 1/1024 of the keyspace and removes a class of "my code does not work" that would be very hard
 * to diagnose from a support message.
 */
export function generate(randomBytes) {
  for (;;) {
    const payload = Array.from(randomBytes(7), (byte) => ALPHABET[byte % ALPHABET.length]).join('');
    if (payload.startsWith(PREFIX)) continue;
    return payload + checkCharacter(payload);
  }
}
