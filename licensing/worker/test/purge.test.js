import { strict as assert } from 'node:assert';
import test from 'node:test';

import { purgeExpired } from '../src/purge.js';

/**
 * A stand-in for D1, just faithful enough to answer the only statement the sweep issues.
 *
 * It does not parse SQL. It asserts the shape of what it is given — batched by rowid, bound in the order
 * `(now, limit)` — and then does what that statement would do to an array of rows. If the statement is
 * rewritten into something with different semantics, this stops matching and the test says so.
 */
function fakeDb(rows) {
  const state = { rows: [...rows], statements: 0 };

  return {
    state,
    prepare(sql) {
      assert.match(sql, /DELETE FROM activations/);
      assert.match(sql, /rowid IN \(SELECT rowid FROM activations WHERE expires_at <= \? LIMIT \?\)/);
      return {
        bind(now, limit) {
          assert.equal(typeof now, 'number');
          assert.equal(typeof limit, 'number');
          return {
            async run() {
              state.statements += 1;
              const doomed = state.rows.filter((row) => row.expires_at <= now).slice(0, limit);
              state.rows = state.rows.filter((row) => !doomed.includes(row));
              return { meta: { changes: doomed.length } };
            },
          };
        },
      };
    },
  };
}

const row = (expiresAt) => ({ expires_at: expiresAt });

test('deletes the expired rows and leaves the live ones', async () => {
  const now = 1_000;
  const db = fakeDb([row(500), row(999), row(1_000), row(1_001), row(90_000)]);

  const result = await purgeExpired(db, now, { batchSize: 10 });

  // 1_000 goes: a pass that expires exactly now has expired.
  assert.equal(result.deleted, 3);
  assert.equal(result.complete, true);
  assert.deepEqual(
    db.state.rows.map((r) => r.expires_at),
    [1_001, 90_000],
  );
});

test('an empty table costs one statement and reports nothing deleted', async () => {
  const db = fakeDb([]);

  const result = await purgeExpired(db, Date.now(), { batchSize: 10 });

  assert.deepEqual(result, { deleted: 0, complete: true });
  assert.equal(db.state.statements, 1);
});

test('keeps going in batches until a short one proves the table is clean', async () => {
  const db = fakeDb(Array.from({ length: 25 }, () => row(1)));

  const result = await purgeExpired(db, 1_000, { batchSize: 10, maxBatches: 20 });

  assert.deepEqual(result, { deleted: 25, complete: true });
  // Three full-ish batches: 10, 10, 5 — the last one short, which is what ends the loop.
  assert.equal(db.state.statements, 3);
  assert.equal(db.state.rows.length, 0);
});

test('a table that fills faster than the cap stops rather than running forever', async () => {
  // Exactly batchSize * maxBatches expired rows: every batch is full, so nothing ever proves the table
  // is clean. The sweep must stop at the cap and admit it, not keep issuing statements.
  const db = fakeDb(Array.from({ length: 20 }, () => row(1)));

  const result = await purgeExpired(db, 1_000, { batchSize: 5, maxBatches: 4 });

  assert.deepEqual(result, { deleted: 20, complete: false });
  assert.equal(db.state.statements, 4);
});

test('never touches a row whose pass is still live', async () => {
  const now = Date.now();
  const db = fakeDb([row(now + 1), row(now + 60_000)]);

  const result = await purgeExpired(db, now, { batchSize: 100 });

  assert.equal(result.deleted, 0);
  assert.equal(db.state.rows.length, 2);
});
