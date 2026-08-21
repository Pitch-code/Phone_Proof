import { strict as assert } from 'node:assert';
import test from 'node:test';

import worker from '../src/index.js';

/**
 * These tests exist because of one wrong answer.
 *
 * The first version checked the method before the path, so `GET /` replied "use POST" — telling whoever
 * typed the URL that their URL was fine. The distinction between "no such endpoint" and "wrong method" is
 * the entire diagnostic value of these two status codes, and getting it backwards costs somebody an hour.
 */
/** From licensing/code-test-vectors.txt — a code whose checksum is genuinely right. */
const VALID_CODE = 'PP-N6WE-DKZE';

const call = (method, path, body) =>
  worker.fetch(
    new Request(`https://licence.test${path}`, {
      method,
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    }),
    {},
  );

test('health says ok', async () => {
  const response = await call('GET', '/health');
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true });
});

test('health answers HEAD, because uptime monitors use it', async () => {
  const response = await call('HEAD', '/health');
  assert.equal(response.status, 200);
});

test('an unknown path is a missing route, not a wrong method', async () => {
  for (const path of ['/', '/issu', '/redeem/', '/admin']) {
    const response = await call('GET', path);
    assert.equal(response.status, 404, path);
    assert.equal((await response.json()).reason, 'route', path);
  }
});

test('a real path with the wrong method is a wrong method, and says which is right', async () => {
  const response = await call('GET', '/issue');
  assert.equal(response.status, 405);
  assert.equal(response.headers.get('allow'), 'POST');
  assert.equal((await response.json()).reason, 'method');
});

test('POSTing to health is refused rather than quietly accepted', async () => {
  const response = await call('POST', '/health');
  assert.equal(response.status, 405);
  assert.equal(response.headers.get('allow'), 'GET, HEAD');
});

test('a POST with no JSON body is rejected before anything is looked up', async () => {
  // env is `{}` here — no DB binding at all. Reaching a database would throw, so a clean 400 is also
  // proof that malformed input is turned away before it can cost a query.
  for (const path of ['/issue', '/redeem']) {
    const response = await worker.fetch(new Request(`https://licence.test${path}`, { method: 'POST' }), {});
    assert.equal(response.status, 400, path);
    assert.equal((await response.json()).reason, 'body', path);
  }
});

test('a redeem with a malformed code never reaches the database', async () => {
  const response = await call('POST', '/redeem', {
    code: 'PP-XXXX-XXXX',
    deviceHash: '0123456789abcdef0123',
  });
  assert.equal(response.status, 400);
  assert.equal((await response.json()).reason, 'malformed');
});

test('an unexpected failure is a 500 that gives nothing away', async () => {
  const exploding = {
    DB: {
      prepare() {
        throw new Error('sensitive internal detail');
      },
    },
    PLAY_LICENSE_PUBLIC_KEY: '',
  };
  const response = await worker.fetch(
    new Request('https://licence.test/redeem', {
      method: 'POST',
      // Well-formed enough to get past the offline checks and reach the lookup.
      body: JSON.stringify({ code: VALID_CODE, deviceHash: '0123456789abcdef0123' }),
    }),
    exploding,
  );
  assert.equal(response.status, 500);
  const payload = await response.json();
  assert.equal(payload.reason, 'server');
  assert.doesNotMatch(payload.message, /sensitive/);
});
