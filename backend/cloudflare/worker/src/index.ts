import { callAvalAi } from "./avalai";
import { sha256Hex, bearerToken, generateToken, generateUserId } from "./auth";
import { corsPreflightResponse, json, withCors } from "./cors";
import { CREDIT_COST, CREDIT_PACKAGES, INITIAL_FREE_CREDITS, creditsForProduct } from "./creditPolicy";
import {
  claimDeviceRegistration,
  completeTransaction,
  createUser,
  debitCredits,
  deleteExpiredPaymentIntents,
  deleteTransaction,
  getTransactionByRequestId,
  getTransactionHistory,
  getUserByTokenHash,
  refundCredits,
  reserveTransaction,
  touchRateLimit,
  createPaymentIntent,
  getPaymentIntent,
  getGrantedStorePurchase,
  grantStorePurchase,
} from "./db";
import { validatePurchaseBody, verifyMyketPurchase } from "./payments";
import type { Env } from "./types";
import { toDiagnoseRequestBody, validateDiagnoseBody } from "./validation";

// حداقل فاصله بین دو درخواست پیاپی یک کاربر روی diagnose/payments/intent/verify
// -- یک محافظت ساده و رایگان ضد سوءاستفاده؛ برای محافظت جدی‌تر، از Cloudflare
// Rate Limiting Rules هم می‌توان در کنار این استفاده کرد (رجوع کنید به README).
const MIN_REQUEST_GAP_MS = 1500;

// Register must remain available after an app reinstall or an interrupted
// first launch. Duplicate free credits are already prevented independently by
// device_registrations, so throttling this endpoint can only strand a valid
// installation without a session. Edge-level rate limiting can still protect
// against abusive traffic without blocking normal recovery.
const MIN_REGISTER_GAP_MS = 0;

// یک تراکنش «رزرو» (response_raw = NULL) که از این مقدار قدیمی‌تر باشد یعنی
// به‌احتمال زیاد Worker قبلی قبل از رسیدن به completeTransaction/refund از کار
// افتاده (خیلی نادر). این عدد آگاهانه بزرگ‌تر از timeout داخلی AvalAI
// (۵۵ ثانیه در avalai.ts) انتخاب شده تا یک درخواست واقعاً درحال‌پردازش هرگز
// به‌اشتباه stale تشخیص داده نشود.
const STALE_RESERVATION_MS = 90_000;

async function handleRegister(env: Env, deviceId: string | null): Promise<Response> {
  const userId = generateUserId();
  const token = generateToken();
  const tokenHash = await sha256Hex(token);

  let initialCredits = INITIAL_FREE_CREDITS;
  let deviceHash: string | null = null;

  if (typeof deviceId === "string" && deviceId.length >= 4 && deviceId.length <= 256) {
    deviceHash = await sha256Hex(deviceId);
    const claim = await claimDeviceRegistration(env.DB, deviceHash, userId, MIN_REGISTER_GAP_MS);
    if (claim === "rate_limited") return json(429, { error: "RATE_LIMITED" });
    // "repeat" یعنی این دستگاه قبلاً یک‌بار اعتبار رایگان نصب را گرفته --
    // نصب مجدد اپ نباید دوباره ۵ اعتبار رایگان بدهد. کاربر همچنان یک session
    // معتبر می‌گیرد، فقط credits اولیه‌اش صفر است.
    if (claim === "repeat") initialCredits = 0;
  }
  // اگر deviceId ارسال نشده (نسخه‌های قدیمی‌تر اپ)، رفتار قبلی حفظ می‌شود:
  // همیشه ۵ اعتبار رایگان -- عمداً backward-compatible، نه یک خطا.

  await createUser(env.DB, { userId, tokenHash, initialCredits, deviceHash });
  // توکن فقط همین یک‌بار در پاسخ برمی‌گردد؛ کلاینت باید آن را محلی ذخیره کند
  // (رجوع کنید به BackendSession.kt سمت اندروید) -- سرور فقط هش آن را نگه می‌دارد.
  return json(200, { userId, token, credits: initialCredits });
}

async function handleCredits(userCredits: number): Promise<Response> {
  return json(200, { balance: userCredits, packages: CREDIT_PACKAGES, costs: CREDIT_COST });
}

async function handleCreditsHistory(env: Env, userId: string, url: URL): Promise<Response> {
  const rawLimit = Number(url.searchParams.get("limit") ?? "20");
  const limit = Number.isFinite(rawLimit) ? Math.min(Math.max(Math.trunc(rawLimit), 1), 50) : 20;
  const before = url.searchParams.get("before");

  const items = await getTransactionHistory(env.DB, userId, limit, before);
  const nextCursor = items.length === limit ? items[items.length - 1].created_at : null;
  return json(200, {
    items: items.map((item) => ({
      id: item.id,
      type: item.type,
      amount: item.amount,
      description: item.description,
      createdAt: item.created_at,
      requestId: item.request_id,
    })),
    nextCursor,
  });
}

async function handlePaymentIntent(env: Env, userId: string, body: unknown): Promise<Response> {
  const productId = (body as Record<string, unknown> | null)?.productId;
  if (typeof productId !== "string" || creditsForProduct(productId) == null) return json(400, { error: "INVALID_PRODUCT" });
  // پاکسازی opportunistic: به‌جای Cron جداگانه، هر بار این کاربر intent جدید
  // می‌سازد، intentهای منقضی‌شدهٔ خودش هم پاک می‌شوند.
  await deleteExpiredPaymentIntents(env.DB, userId);
  const developerPayload = crypto.randomUUID() + crypto.randomUUID();
  await createPaymentIntent(env.DB, userId, productId, developerPayload);
  return json(200, { productId, developerPayload, expiresInSeconds: 900 });
}

async function handlePaymentVerify(env: Env, userId: string, body: unknown): Promise<Response> {
  const purchase = validatePurchaseBody(body);
  if (!purchase) return json(400, { error: "INVALID_PURCHASE" });
  const intent = await getPaymentIntent(env.DB, purchase.developerPayload);
  if (!intent || intent.user_id !== userId || intent.expires_at < new Date().toISOString()) return json(400, { error: "INVALID_PURCHASE_INTENT" });
  const alreadyGranted = await getGrantedStorePurchase(env.DB, purchase.tokenId);
  if (alreadyGranted) {
    // اگر همان کاربر دوباره verify همان خرید را فرستاد (مثلاً بعد از قطع
    // اینترنت که اولین verify موفق ولی پاسخش گم شد)، موفق تلقی می‌شود --
    // بدون اعتبار اضافه. اگر متعلق به کاربر دیگری بود، خطای امنیتی صریح.
    return alreadyGranted.user_id === userId
      ? json(200, { success: true, duplicate: true, creditsGranted: alreadyGranted.credits_granted })
      : json(409, { error: "PURCHASE_ALREADY_CLAIMED" });
  }
  try {
    const verified = await verifyMyketPurchase(env, intent.product_id, purchase.tokenId);
    if (verified.developerPayload !== purchase.developerPayload) return json(400, { error: "PAYLOAD_MISMATCH" });
    const credits = creditsForProduct(intent.product_id)!;
    const granted = await grantStorePurchase(env.DB, { userId, productId: intent.product_id, purchaseToken: purchase.tokenId, credits, purchaseTime: verified.purchaseTime });
    if (!granted) {
      const existing = await getGrantedStorePurchase(env.DB, purchase.tokenId);
      return existing?.user_id === userId
        ? json(200, { success: true, duplicate: true, creditsGranted: existing.credits_granted })
        : json(409, { error: "PURCHASE_ALREADY_CLAIMED" });
    }
    return json(200, { success: true, creditsGranted: credits });
  } catch (error) {
    const code = error instanceof Error ? error.message : "MYKET_VERIFICATION_FAILED";
    return json(502, { error: code });
  }
}

async function handleDiagnose(env: Env, userId: string, requestId: string, rawBody: any): Promise<Response> {
  const cost = CREDIT_COST[rawBody.action as keyof typeof CREDIT_COST];
  const txnId = crypto.randomUUID();
  const description = `diagnose:${rawBody.action}`;

  let reserved = await reserveTransaction(env.DB, { id: txnId, userId, amount: cost, requestId, description });

  if (!reserved) {
    const existing = await getTransactionByRequestId(env.DB, userId, requestId);

    if (existing?.response_raw) {
      // پخش دوباره‌ی نتیجه‌ی قبلاً موفق -- بدون کسر مجدد اعتبار (idempotency واقعی).
      return json(200, { success: true, raw: existing.response_raw, creditsCharged: existing.amount });
    }

    if (existing && Date.now() - new Date(existing.created_at).getTime() > STALE_RESERVATION_MS) {
      // رزرو قدیمی و ناتمام -- به‌احتمال زیاد Worker قبلی از کار افتاده، نه
      // اینکه هنوز در حال پردازش باشد. اعتبار را برمی‌گردانیم و اجازه می‌دهیم
      // همین درخواست (با همان request_id) از نو تلاش کند؛ نه اعتبار برای همیشه
      // سوخته می‌شود و نه امکان double charge پیش می‌آید.
      await refundCredits(env.DB, userId, existing.amount);
      await deleteTransaction(env.DB, existing.id);
      reserved = await reserveTransaction(env.DB, { id: txnId, userId, amount: cost, requestId, description });
    }

    if (!reserved) {
      // یا واقعاً همین الان یک تلاش دیگر با همین کلید در حال پردازش است (کمتر
      // از STALE_RESERVATION_MS)، یا یک race نادر رخ داد. کلاینت باید کمی بعد
      // با همان X-Idempotency-Key دوباره تلاش کند، نه اینکه کلید جدید بسازد.
      return json(409, { error: "REQUEST_IN_PROGRESS" });
    }
  }

  const debited = await debitCredits(env.DB, userId, cost);
  if (!debited) {
    await deleteTransaction(env.DB, txnId);
    return json(402, { error: "INSUFFICIENT_CREDITS" });
  }

  try {
    const raw = await callAvalAi(env, toDiagnoseRequestBody(rawBody));
    await completeTransaction(env.DB, txnId, raw);
    return json(200, { success: true, raw, creditsCharged: cost });
  } catch (err) {
    // خطای ارائه‌دهنده هوش مصنوعی -- اعتبار برگردانده می‌شود و رزرو حذف
    // می‌شود تا همان request_id بعداً بتواند دوباره تلاش کند.
    await refundCredits(env.DB, userId, cost);
    await deleteTransaction(env.DB, txnId);
    const message = err instanceof Error ? err.message : "AI_PROVIDER_ERROR";
    console.error("AvalAI call failed", message); // فقط پیام خطا لاگ می‌شود، نه کلید یا داده حساس
    return json(502, { error: message });
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") return corsPreflightResponse();
    const url = new URL(request.url);

    try {
      if (request.method === "POST" && url.pathname === "/api/register") {
        const body = await request.json().catch(() => null);
        const deviceId =
          body && typeof body === "object" && typeof (body as Record<string, unknown>).deviceId === "string"
            ? (body as Record<string, unknown>).deviceId as string
            : null;
        return withCors(await handleRegister(env, deviceId));
      }

      const token = bearerToken(request.headers.get("Authorization"));
      if (!token) return withCors(json(401, { error: "UNAUTHENTICATED" }));
      const user = await getUserByTokenHash(env.DB, await sha256Hex(token));
      if (!user) return withCors(json(401, { error: "UNAUTHENTICATED" }));

      if (request.method === "GET" && url.pathname === "/api/credits") {
        return withCors(await handleCredits(user.credits));
      }

      if (request.method === "GET" && url.pathname === "/api/credits/history") {
        return withCors(await handleCreditsHistory(env, user.user_id, url));
      }

      if (request.method === "POST" && url.pathname === "/api/payments/intent") {
        if (!(await touchRateLimit(env.DB, user.user_id, MIN_REQUEST_GAP_MS))) {
          return withCors(json(429, { error: "RATE_LIMITED" }));
        }
        const body = await request.json().catch(() => null);
        return withCors(await handlePaymentIntent(env, user.user_id, body));
      }

      if (request.method === "POST" && url.pathname === "/api/payments/verify") {
        if (!(await touchRateLimit(env.DB, user.user_id, MIN_REQUEST_GAP_MS))) {
          return withCors(json(429, { error: "RATE_LIMITED" }));
        }
        const body = await request.json().catch(() => null);
        return withCors(await handlePaymentVerify(env, user.user_id, body));
      }

      if (request.method === "POST" && url.pathname === "/api/diagnose") {
        const requestId = request.headers.get("X-Idempotency-Key");
        if (!requestId || requestId.length < 16) {
          return withCors(json(400, { error: "MISSING_IDEMPOTENCY_KEY" }));
        }
        if (!(await touchRateLimit(env.DB, user.user_id, MIN_REQUEST_GAP_MS))) {
          return withCors(json(429, { error: "RATE_LIMITED" }));
        }

        let body: unknown;
        try {
          body = await request.json();
        } catch {
          return withCors(json(400, { error: "INVALID_JSON" }));
        }
        const validationError = validateDiagnoseBody(body);
        if (validationError) return withCors(json(400, { error: validationError }));

        return withCors(await handleDiagnose(env, user.user_id, requestId, body));
      }

      return withCors(json(404, { error: "NOT_FOUND" }));
    } catch (err) {
      console.error("Unhandled worker error", err instanceof Error ? err.message : "unknown");
      return withCors(json(500, { error: "INTERNAL_ERROR" }));
    }
  },
};
