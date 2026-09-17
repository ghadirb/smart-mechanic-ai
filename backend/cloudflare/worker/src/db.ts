import type { TransactionRow, UserRow } from "./types";

export async function getUserByTokenHash(db: D1Database, tokenHash: string): Promise<UserRow | null> {
  const row = await db.prepare("SELECT * FROM users WHERE token_hash = ?").bind(tokenHash).first<UserRow>();
  return row ?? null;
}

export async function createUser(
  db: D1Database,
  params: { userId: string; tokenHash: string; initialCredits: number }
): Promise<void> {
  const now = new Date().toISOString();
  await db
    .prepare(
      "INSERT INTO users (user_id, token_hash, credits, last_request_at, created_at, updated_at) VALUES (?, ?, ?, NULL, ?, ?)"
    )
    .bind(params.userId, params.tokenHash, params.initialCredits, now, now)
    .run();
}

/** برای محدودیت ساده ضد سوءاستفاده: حداقل فاصله بین دو درخواست پیاپی یک کاربر. */
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

/** رزرو اتمیک: به لطف UNIQUE(request_id)، دو درخواست هم‌زمان با همان کلید
 * idempotency فقط یکی‌شان موفق به درج می‌شود -- این همان مکانیزم idempotency است. */
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
    return false; // نقض UNIQUE(request_id) -- یعنی این درخواست تکراری است
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

export async function createPaymentIntent(db: D1Database, userId: string, productId: string, payload: string): Promise<void> {
  const now = new Date();
  await db.prepare("INSERT INTO payment_intents (id, user_id, product_id, developer_payload, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)")
    .bind(crypto.randomUUID(), userId, productId, payload, new Date(now.getTime() + 15 * 60 * 1000).toISOString(), now.toISOString()).run();
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
