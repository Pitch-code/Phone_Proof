-- Inspection passes: what the licence server remembers, and deliberately nothing more.
--
-- Two tables. `packs` is what somebody bought; `activations` is where their passes went.

CREATE TABLE IF NOT EXISTS packs (
  -- Canonical body, eight characters, no prefix and no hyphens. What `normalise` returns.
  code            TEXT PRIMARY KEY,

  -- Google's purchase token, UNIQUE so one receipt can only ever mint one code. Without this a buyer
  -- could replay a single purchase into an unlimited supply of packs, which is the cheapest possible
  -- attack on this design.
  purchase_token  TEXT NOT NULL UNIQUE,

  product_id      TEXT NOT NULL,
  passes_total    INTEGER NOT NULL,
  passes_used     INTEGER NOT NULL DEFAULT 0,
  created_at      INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS activations (
  code          TEXT NOT NULL REFERENCES packs(code),

  -- A hash of the device id SALTED WITH THE CODE, computed on the phone. Never a raw device id.
  --
  -- The server needs exactly one thing from it: to recognise "this same phone again" so that reopening
  -- the app inside the 24-hour window does not cost a second pass. Salting with the code means the same
  -- phone under a different code hashes differently, so this table cannot be used to work out which
  -- phones a person inspected, or to link one phone across two buyers.
  --
  -- That limit is deliberate. The phones being identified belong to sellers who never agreed to anything.
  device_hash   TEXT NOT NULL,

  activated_at  INTEGER NOT NULL,
  expires_at    INTEGER NOT NULL
);

-- The lookup on the redeem path: "is there a live pass for this code on this phone?"
CREATE INDEX IF NOT EXISTS activations_by_code_device
  ON activations (code, device_hash, expires_at);
