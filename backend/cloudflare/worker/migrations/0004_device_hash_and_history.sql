-- افزودن شناسه دستگاه (فقط هش SHA-256، هرگز مقدار خام) برای جلوگیری از
-- دریافت مکرر اعتبار رایگان نصب با پاک/نصب دوباره‌ی برنامه -- بدون نیاز به
-- شماره تلفن یا هر لاگین دیگری (رجوع کنید به README، بخش امنیت).
ALTER TABLE users ADD COLUMN device_hash TEXT;
CREATE INDEX IF NOT EXISTS idx_users_device_hash ON users(device_hash);

-- یک ردیف به ازای هر دستگاه: اولین باری که این هش دیده شده و آخرین باری که
-- دوباره سراغ /api/register آمده (هم برای تشخیص «قبلاً اعتبار رایگان گرفته»
-- و هم برای rate-limit سبک روی خود register استفاده می‌شود).
CREATE TABLE IF NOT EXISTS device_registrations (
  device_hash TEXT PRIMARY KEY,
  first_user_id TEXT NOT NULL,
  last_register_at TEXT NOT NULL,
  register_count INTEGER NOT NULL DEFAULT 1
);

-- برای پاکسازی opportunistic payment_intents منقضی (بدون Cron) و برای
-- صفحه‌بندی GET /api/credits/history.
CREATE INDEX IF NOT EXISTS idx_payment_intents_user_expires ON payment_intents(user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_credit_transactions_user_created ON credit_transactions(user_id, created_at);
