-- users: one row per installation (identified by a bearer token, not a
-- client-supplied user_id -- see src/auth.ts).
CREATE TABLE IF NOT EXISTS users (
  user_id TEXT PRIMARY KEY,
  token_hash TEXT NOT NULL UNIQUE,
  credits INTEGER NOT NULL DEFAULT 0,
  last_request_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

-- credit_transactions: one row per successfully completed AI request.
-- (user_id, request_id) is UNIQUE so a duplicate client retry (same
-- X-Idempotency-Key) can be detected and replayed only for its own user
-- instead of double-charging (see src/db.ts).
-- Failed/refunded attempts are deleted rather than kept, so the same
-- request_id can be retried later.
CREATE TABLE IF NOT EXISTS credit_transactions (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  amount INTEGER NOT NULL,
  type TEXT NOT NULL DEFAULT 'debit',
  request_id TEXT NOT NULL,
  description TEXT,
  response_raw TEXT,
  created_at TEXT NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(user_id),
  UNIQUE (user_id, request_id)
);

CREATE INDEX IF NOT EXISTS idx_credit_transactions_user_id
  ON credit_transactions(user_id);
