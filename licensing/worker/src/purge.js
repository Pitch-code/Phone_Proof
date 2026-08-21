/**
 * Forgetting expired activations.
 *
 * An `activations` row exists to answer one question: "does this phone already have a live pass under this
 * code?" — so that a buyer who closes the app by accident is not charged a second inspection. The moment
 * `expires_at` passes, that question can only ever be answered no, and the row's device hash stops being
 * a record of anything useful and becomes a record of a phone that belonged to a stranger.
 *
 * So it goes. This is not housekeeping for the sake of database size — the rows are tiny and D1 would not
 * notice them. It is so the honest answer to "how long do you keep this?" is "until the pass expires",
 * which is a sentence that can be written on the Data safety form and then actually be true.
 *
 * Nothing depends on these rows afterwards. The count of passes spent lives on `packs.passes_used`, which
 * is never derived from this table, so deleting here cannot give anyone their inspections back.
 */

/**
 * Rows per statement. Small enough that a single DELETE stays well inside a Worker's time budget even on
 * a cold, contended database; large enough that a normal day's sweep is one statement.
 */
export const PURGE_BATCH_SIZE = 500;

/**
 * A hard stop on the loop. If a sweep ever finds more than `BATCH * MAX` expired rows, something has gone
 * wrong that deserves attention — and the sweep runs again tomorrow, so falling behind is self-correcting.
 * Better a bounded job that reports being incomplete than an unbounded one that is killed mid-way.
 */
export const PURGE_MAX_BATCHES = 20;

/**
 * Delete every activation that expired at or before `now`, in bounded batches.
 *
 * Batched via `rowid IN (SELECT … LIMIT ?)` rather than `DELETE … LIMIT ?`, because the latter needs
 * SQLite compiled with SQLITE_ENABLE_UPDATE_DELETE_LIMIT and D1 is not guaranteed to be.
 *
 * @param db a D1 binding
 * @param now epoch millis; rows with `expires_at <= now` are gone
 * @returns `{ deleted, complete }` — `complete: false` means the cap was hit and rows remain
 */
export async function purgeExpired(db, now, options = {}) {
  const batchSize = options.batchSize ?? PURGE_BATCH_SIZE;
  const maxBatches = options.maxBatches ?? PURGE_MAX_BATCHES;

  let deleted = 0;

  for (let batch = 0; batch < maxBatches; batch += 1) {
    const result = await db
      .prepare(
        `DELETE FROM activations
          WHERE rowid IN (SELECT rowid FROM activations WHERE expires_at <= ? LIMIT ?)`,
      )
      .bind(now, batchSize)
      .run();

    const changes = result?.meta?.changes ?? 0;
    deleted += changes;

    // A short batch means the table had fewer than we asked for, so there is nothing left to find.
    if (changes < batchSize) {
      return { deleted, complete: true };
    }
  }

  return { deleted, complete: false };
}
