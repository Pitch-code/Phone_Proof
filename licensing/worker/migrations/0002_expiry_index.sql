-- An index for forgetting.
--
-- The nightly sweep asks for rows by expiry alone: `expires_at <= now`. The existing index leads with
-- `code`, so it cannot serve that range scan — without this, every sweep reads the whole table, and the
-- cost of deleting old rows would grow with the number of rows that were never deleted.

CREATE INDEX IF NOT EXISTS activations_by_expiry
  ON activations (expires_at);
