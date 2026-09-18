import type { TransactionHistoryItem, TransactionRow, UserRow } from "./types";

export async function getUserByTokenHash(db: D1Database, tokenHash: string): Promise<UserRow | null> {
  const row = await db.prepare("SELECT * FROM users WHERE token_hash = ?").bind(tokenHash).first<UserRow>();
  return row ?? null;
}

export async function createUser(
  db: D1Database,
  params: { userId: string; tokenHash: string; initialCredits: number; deviceHash?: string | null }
): Promise<void> {
  const now = new Date().toISOString();
  await db
    .prepare(
      "INSERT INTO users (user_id, token_hash, credits, device_hash, last_request_at, created_at, updated_at) VALUES (?, ?, ?, ?, NULL, ?, ?)"
    )
    .bind(params.userId, params.tokenHash, params.initialCredits, params.deviceHash ?? null, now, now)
    .run();
}

/** برای محدودیت ساده ضد سوءاستفاده: حداقل فاصله بین دو درخواست پیاپی یک کاربر.
 * برای diagnose و همچنین payments/intent و payments/verify مشترک استفاده
 * می‌شود -- یک کاربر واقعی هرگز این سه را در کمتر از ۱.۵ ثانیه پشت‌سرهم
 * نمی‌زند، پس اشتراک ستون last_request_at مشکلی ایجاد نمی‌کند. */
export async function touchRateLimit(db: D1Database, userId: string, minGapMs: number): Promise<boolean> {
  const now = new Date();
  const cutoff = new Date(now.getTime() - minGapMs).toISOString();
  // شرط و به‌روزرسانی در یک statement هستند؛ بنابراین دو درخواست هم‌زمان
  // نمی‌توانند هر دو از rate limit عبور کنند.
  const result = await db
    .prepare(
      "UPDATE users SET last_request_at = ? WHERE user_id = ? AND (last_request_at IS NULL OR last_request_at <= ?)"
    )
    .bind(now.toISOString(), userId, cutoff)
    .run();
  return (result.meta.changes ?? 0) > 0;
}

/** rate-limit سبک روی خود POST /api/register، بر مبنای هش دستگاه (نه IP --
 * Cloudflare IP واقعی کاربران موبایل معمولاً پشت CGNAT مشترک است و rate-limit
 * بر مبنای IP باعث مسدودشدن کاربران بی‌گناه می‌شود).
 * برمی‌گرداند:
 *  - "first"        دستگاه قبلاً دیده نشده -- مجاز به گرفتن اعتبار رایگان
 *  - "repeat"        دستگاه قبلاً اعتبار رایگان گرفته -- بدون اعتبار رایگان جدید
 *  - "rate_limited"  دستگاه خیلی زود دوباره درخواست register داده
 */
export async function claimDeviceRegistration(
  db: D1Database,
  deviceHash: string,
  userId: string,
  minGapMs: number
): Promise<"first" | "repeat" | "rate_limited"> {
  const now = new Date().toISOString();
  try {
    await db
      .prepare(
        "INSERT INTO device_registrations (device_hash, first_user_id, last_register_at, register_count) VALUES (?, ?, ?, 1)"
      )
      .bind(deviceHash, userId, now)
      .run();
    return "first";
  } catch {
    // نقض UNIQUE(device_hash) -- این دستگاه قبلاً دیده شده.
    const cutoff = new Date(Date.now() - minGapMs).toISOString();
    const result = await db
      .prepare(
        "UPDATE device_registrations SET last_register_at = ?, register_count = register_count + 1 WHERE device_hash = ? AND last_register_at <= ?"
      )
      .bind(now, deviceHash, cutoff)
      .run();
    return (result.meta.changes ?? 0) > 0 ? "repeat" : "rate_limited";
  }
}

export async function getTransactionByRequestId(
  db: D1Database,
  userId: string,
  requestId: string
): Promise<TransactionRow | null> {
  const row = await db
    .prepare("SELECT * FROM credit_transactions WHERE user_id = ? AND request_id = ?")
    .bind(userId, requestId)
    .first<TransactionRow>();
  return row ?? null;
}

/** رزرو اتمیک: به لطف UNIQUE(user_id, request_id)، دو درخواست هم‌زمان با همان
 * کلید idempotency فقط یکی‌شان موفق به درج می‌شود -- این همان مکانیزم
 * idempotency است.
 *
 * قرارداد ledger: amount مثبت یعنی اعتبار مصرف‌شده (type='debit')، amount
 * منفی یعنی اعتبار اضافه‌شده (type='credit'، مثلاً خرید مایکت در
 * grantStorePurchase). جمع amount یک کاربر همیشه برابر «کل اعتباری که تابه‌حال
 * مصرف کرده منهای کل اعتباری که خریده» است.
 *
 * یک ردیف «رزرو» (response_raw = NULL) یعنی در حال پردازش است. اگر Worker قبل
 * از تکمیل یا refund از کار بیفتد (خیلی نادر)، این ردیف برای همیشه معلق
 * می‌ماند؛ handleDiagnose در index.ts چنین ردیف‌هایی را اگر از حد معینی
 * (STALE_RESERVATION_MS) قدیمی‌تر باشند خودش refund و حذف می‌کند تا کلاینت
 * بتواند با همان request_id دوباره تلاش کند -- به همین دلیل ستون status
 * جداگانه لازم نبود. */
export async function reserveTransaction(
  db: D1Database,
  params: { id: string; userId: string; amount: number; requestId: string; description: string }
): Promise<boolean> {
  try {
    await db
      .prepare(
        "INSERT INTO credit_transactions (id, user_id, amount, type, request_id, description, response_raw, created_at) VALUES (?, ?, ?, 'debit', ?, ?, NULL, ?)"
      )
      .bind(params.id, params.userId, params.amount, params.requestId, params.description, new Date().toISOString())
      .run();
    return true;
  } catch {
    return false; // نقض UNIQUE(user_id, request_id) -- یعنی این درخواست تکراری است
  }
}

export async function deleteTransaction(db: D1Database, id: string): Promise<void> {
  await db.prepare("DELETE FROM credit_transactions WHERE id = ?").bind(id).run();
}

export async function completeTransaction(db: D1Database, id: string, responseRaw: string): Promise<void> {
  await db.prepare("UPDATE credit_transactions SET response_raw = ? WHERE id = ?").bind(responseRaw, id).run();
}

/** کسر اتمیک اعتبار: شرط credits >= amount مستقیم در همان UPDATE بررسی
 * می‌شود، پس دو درخواست هم‌زمان هرگز نمی‌توانند موجودی را منفی کنند --
 * نیازی به تراکنش صریح BEGIN/COMMIT نیست چون این یک عبارت SQL واحد است. */
export async function debitCredits(db: D1Database, userId: string, amount: number): Promise<boolean> {
  const now = new Date().toISOString();
  const result = await db
    .prepare("UPDATE users SET credits = credits - ?, updated_at = ? WHERE user_id = ? AND credits >= ?")
    .bind(amount, now, userId, amount)
    .run();
  return (result.meta.changes ?? 0) > 0;
}

export async function refundCredits(db: D1Database, userId: string, amount: number): Promise<void> {
  const now = new Date().toISOString();
  await db.prepare("UPDATE users SET credits = credits + ?, updated_at = ? WHERE user_id = ?").bind(amount, now, userId).run();
}

/** تاریخچهٔ اعتبار برای GET /api/credits/history -- فقط ستون‌های امن برای
 * نمایش به کاربر (response_raw هرگز برنمی‌گردد). صفحه‌بندی بر مبنای created_at
 * (cursor = آخرین created_at صفحهٔ قبل) چون شناسهٔ ردیف UUID است، نه شمارشی. */
export async function getTransactionHistory(
  db: D1Database,
  userId: string,
  limit: number,
  before: string | null
): Promise<TransactionHistoryItem[]> {
  const query = before
    ? db
        .prepare(
          "SELECT id, type, amount, description, created_at, request_id FROM credit_transactions WHERE user_id = ? AND created_at < ? ORDER BY created_at DESC LIMIT ?"
        )
        .bind(userId, before, limit)
    : db
        .prepare(
          "SELECT id, type, amount, description, created_at, request_id FROM credit_transactions WHERE user_id = ? ORDER BY created_at DESC LIMIT ?"
        )
        .bind(userId, limit);
  const result = await query.all<{
    id: string;
    type: string;
    amount: number;
    description: string | null;
    created_at: string;
    request_id: string;
  }>();
  return (result.results ?? []).map((row) => ({
    id: row.id,
    type: row.type,
    amount: row.amount,
    description: row.description,
    created_at: row.created_at,
    request_id: row.request_id,
  }));
}

export async function createPaymentIntent(db: D1Database, userId: string, productId: string, payload: string): Promise<void> {
  const now = new Date();
  await db.prepare("INSERT INTO payment_intents (id, user_id, product_id, developer_payload, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)")
    .bind(crypto.randomUUID(), userId, productId, payload, new Date(now.getTime() + 15 * 60 * 1000).toISOString(), now.toISOString()).run();
}

/** پاکسازی opportunistic: چون Worker بدون Cron روی Free plan هزینهٔ اضافه
 * ندارد، به‌جای یک job زمان‌بندی‌شدهٔ جداگانه، هر بار که کاربری intent جدید
 * می‌سازد، intentهای منقضی‌شدهٔ خودش هم حذف می‌شوند (idx_payment_intents_user_expires
 * این را ارزان می‌کند). */
export async function deleteExpiredPaymentIntents(db: D1Database, userId: string): Promise<void> {
  await db
    .prepare("DELETE FROM payment_intents WHERE user_id = ? AND expires_at < ?")
    .bind(userId, new Date().toISOString())
    .run();
}

export async function getPaymentIntent(db: D1Database, payload: string): Promise<{ user_id: string; product_id: string; expires_at: string } | null> {
  return await db.prepare("SELECT user_id, product_id, expires_at FROM payment_intents WHERE developer_payload = ?")
    .bind(payload).first<{ user_id: string; product_id: string; expires_at: string }>();
}

export async function grantStorePurchase(db: D1Database, params: { userId: string; productId: string; purchaseToken: string; credits: number; purchaseTime: number }): Promise<boolean> {
  const now = new Date().toISOString();
  try {
    await db.batch([
      db.prepare("INSERT INTO store_purchases (id, user_id, store, product_id, purchase_token, credits_granted, purchase_time, verified_at) VALUES (?, ?, 'myket', ?, ?, ?, ?, ?)")
        .bind(crypto.randomUUID(), params.userId, params.productId, params.purchaseToken, params.credits, params.purchaseTime, now),
      db.prepare("UPDATE users SET credits = credits + ?, updated_at = ? WHERE user_id = ?").bind(params.credits, now, params.userId),
      db.prepare("INSERT INTO credit_transactions (id, user_id, amount, type, request_id, description, response_raw, created_at) VALUES (?, ?, ?, 'credit', ?, ?, NULL, ?)")
        .bind(crypto.randomUUID(), params.userId, -params.credits, `myket:${params.purchaseToken}`, `myket:${params.productId}`, now),
    ]);
    return true;
  } catch { return false; }
}

export async function getGrantedStorePurchase(db: D1Database, purchaseToken: string): Promise<{ user_id: string; credits_granted: number } | null> {
  return await db.prepare("SELECT user_id, credits_granted FROM store_purchases WHERE purchase_token = ?")
    .bind(purchaseToken).first<{ user_id: string; credits_granted: number }>();
}
