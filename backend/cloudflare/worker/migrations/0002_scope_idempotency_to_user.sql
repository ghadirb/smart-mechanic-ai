-- نسخهٔ اولیه request_id را در سطح کل سیستم UNIQUE کرده بود. یک کلید
-- idempotency فقط باید برای همان دارندهٔ توکن قابل تکرار باشد؛ در غیر این صورت
-- یک کاربر می‌توانست با حدس/استفاده از همان کلید، پاسخ کاربر دیگر را ببیند.
PRAGMA foreign_keys = OFF;

ALTER TABLE credit_transactions RENAME TO credit_transactions_old;

CREATE TABLE credit_transactions (
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

INSERT INTO credit_transactions (id, user_id, amount, type, request_id, description, response_raw, created_at)
SELECT id, user_id, amount, type, request_id, description, response_raw, created_at
FROM credit_transactions_old;

DROP TABLE credit_transactions_old;
CREATE INDEX IF NOT EXISTS idx_credit_transactions_user_id
  ON credit_transactions(user_id);

PRAGMA foreign_keys = ON;
