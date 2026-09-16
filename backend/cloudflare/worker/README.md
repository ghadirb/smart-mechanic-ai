# بک‌اند اعتباری Cloudflare Workers + D1

بک‌اند اصلی و رایگان «مکانیک هوشمند» -- جایگزین Firebase Cloud Functions (که به
Blaze Plan و Google Cloud Billing نیاز داشت). این Worker روی **Cloudflare Free
Plan** اجرا می‌شود؛ تنها هزینه‌ی واقعی، مصرف خودِ AvalAI است (مستقل از این
بک‌اند).

```
Android App  ---HTTPS--->  Cloudflare Worker  --->  Cloudflare D1 (اعتبار)
                                  |
                                  +---------------> AvalAI API
```

## چرا این معماری؟

- **بدون کارت اعتباری / Billing گوگل:** Cloudflare Workers Free نیازی به کارت
  ندارد (برخلاف Firebase Blaze).
- **بدون Service Account JSON یا Workload Identity Federation:** فقط یک API
  Token ساده کافی است.
- **اعتبار هر کاربر همچنان سمت سرور کنترل می‌شود:** کلاینت هرگز نمی‌تواند
  موجودی خودش را دستکاری کند.

## امنیت هویت کاربر

کلاینت **user_id انتخاب نمی‌کند**. هنگام اولین اجرا، اپ یک‌بار `POST /api/register`
می‌زند؛ Worker یک `user_id` و یک **توکن تصادفی ۲۵۶ بیتی** تولید می‌کند، فقط هش
SHA-256 توکن را در D1 ذخیره می‌کند، و توکن خام را فقط همان یک بار به کلاینت
برمی‌گرداند. اپ توکن را در SharedPreferences محلی نگه می‌دارد (`BackendSession.kt`)
و در هر درخواست بعدی به‌صورت `Authorization: Bearer <token>` می‌فرستد. تغییر
`user_id` توسط کاربر بی‌فایده است چون هویت واقعی از روی توکن (نه فیلدی که
کلاینت در بدنه‌ی JSON می‌فرستد) تعیین می‌شود.

> این یک سیستم احراز هویت کامل (مثل OAuth) نیست، اما معماری طوری طراحی شده که
> بعداً بتوان بدون تغییر اساسی، یک لایه احراز هویت واقعی (مثلاً ورود با شماره
> موبایل) روی همین جدول `users` اضافه کرد.

## Endpointها

| Method | مسیر | نیاز به توکن | توضیح |
|---|---|---|---|
| POST | `/api/register` | خیر | ثبت‌نام نصب جدید؛ ۵ اعتبار رایگان می‌دهد |
| GET | `/api/credits` | بله | موجودی فعلی + جدول هزینه‌ها + بسته‌های خرید |
| POST | `/api/diagnose` | بله | تشخیص هوش مصنوعی؛ نیاز به هدر `X-Idempotency-Key` (حداقل ۱۶ کاراکتر) |
| POST | `/api/payments/verify` | بله | جای‌نگه‌دار -- فعلاً `501 NOT_IMPLEMENTED` |

بدنه‌ی `POST /api/diagnose` دقیقاً همان شکل `FirebaseDiagnoseRequest` قبلی است:
`action` ("text"/"image"/"audio"/"video")، `prompt`، و بسته به نوع،
`imageBase64`/`imageMimeType`، `audioBase64`/`audioMimeType` یا
`videoBase64`/`videoMimeType`. پاسخ موفق: `{ success, raw, creditsCharged }`.

## سیاست اعتبار

در `src/creditPolicy.ts` متمرکز شده: متن=۱، عکس=۳، صوت=۵، ویدیو=۱۲. بسته‌های
خرید پیشنهادی: `credit_20`, `credit_50`, `credit_150` (فعلاً فقط تعریف شده‌اند؛
اتصال واقعی به بازار/مایکت هنوز پیاده نشده -- رجوع کنید به بخش «محدودیت‌ها»).

## راه‌اندازی Cloudflare

### ۱. ساخت توکن صحیح و D1

در Cloudflare Dashboard یک **API Token** بسازید (نه R2 S3 access key) که برای
همین account دسترسی‌های `Workers Scripts:Edit` و `D1:Edit` داشته باشد. سپس در
ترمینال، در پوشهٔ همین Worker:

```bash
npm ci
npx wrangler login
npx wrangler d1 create smart-mechanic-ai-db
```

خروجی دستور آخر شامل `database_id` است. آن را در `wrangler.toml`، داخل binding
`DB` جایگزین کنید. شناسهٔ account را نیز می‌توانید با `npx wrangler whoami` ببینید.

### ۲. schema و Secretها

```bash
npx wrangler d1 migrations apply smart-mechanic-ai-db --remote
npx wrangler secret put AVALAI_API_KEY
npx wrangler secret put AVALAI_MODEL
npx wrangler deploy
```

برای `AVALAI_MODEL` نام مدلی را وارد کنید که حساب AvalAI شما واقعاً فعال کرده
است. هیچ‌کدام از دو مقدار را در `wrangler.toml`، `local.properties` یا GitHub
قرار ندهید. Secret اول کلید AvalAI و Secret دوم نام مدل قابل‌تغییر سرور است.

### ۳. اتصال Android

URL چاپ‌شده پس از deploy را در فایل محلیِ نادیده‌گرفته‌شده قرار دهید:

```properties
AI_BACKEND_BASE_URL=https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev
```

یا همان نام متغیر را در محیط CI build Android تعریف کنید. URL Worker راز نیست؛
کلید AvalAI و bearer tokenهای کاربران هرگز نباید در APK یا Git باشند.

## GitHub Actions

workflow [`deploy-cloudflare-worker.yml`](../../../.github/workflows/deploy-cloudflare-worker.yml)
پس از هر push در این پوشه migrationها را اعمال و Worker را deploy می‌کند. در
GitHub → Settings → Secrets and variables → Actions فقط این دو **repository secret**
را ایجاد کنید:

| نام | مقدار |
|---|---|
| `CLOUDFLARE_API_TOKEN` | همان API Token محدود با Workers Scripts:Edit و D1:Edit |
| `CLOUDFLARE_ACCOUNT_ID` | account id Cloudflare |

`AVALAI_API_KEY` و `AVALAI_MODEL` عمداً GitHub Secret نیستند و فقط با
`wrangler secret put` روی Worker ثبت می‌شوند. تا وقتی دو secret GitHub بالا
تعریف نشده‌اند، deploy workflow با خطای authentication/migration متوقف می‌شود.

## تست end-to-end بعد از deploy

ابتدا یک نصب آزمایشی بسازید و `token` بازگشتی را فقط در shell همان تست نگه دارید:

```bash
curl -X POST "$AI_BACKEND_BASE_URL/api/register"
curl -H "Authorization: Bearer <token>" "$AI_BACKEND_BASE_URL/api/credits"
curl -X POST "$AI_BACKEND_BASE_URL/api/diagnose" \
  -H "Authorization: Bearer <token>" \
  -H "X-Idempotency-Key: 4e5cda8d-1f8c-4ef4-b7c3-123456789abc" \
  -H "Content-Type: application/json" \
  --data '{"action":"text","prompt":"موتور هنگام استارت صدای تق تق دارد"}'
```

همان درخواست سوم را با همان کلید تکرار کنید: پاسخ cache شده برمی‌گردد و فقط یک
اعتبار کسر می‌شود. با اعتبار ناکافی پاسخ `402` و برای token نامعتبر `401` است.

## schema D1

- `users(user_id, token_hash, credits, last_request_at, created_at, updated_at)`
- `credit_transactions(id, user_id, amount, type, request_id, description, response_raw, created_at)`

قید `UNIQUE(user_id, request_id)` idempotency را فقط در محدودهٔ همان کاربر نگه
می‌دارد. migration `0002` نسخهٔ اولیهٔ سراسری را به این طراحی امن ارتقا می‌دهد.

## محدودیت‌ها و عملیات آینده

- اندازه‌های اجرایی نسخهٔ اول: تصویر 10MB، صوت 8MB، ویدیو 25MB؛ پیش از رسیدن به
  سقف Cloudflare اعتبارسنجی می‌شوند. این برای ارسال base64 و سقف عملی زمان/حافظهٔ
  Worker محافظه‌کارانه است، نه دور زدن محدودیت سرویس.
- Worker Free سقف 100,000 درخواست روزانه دارد. D1 Free نیز سهمیهٔ روزانه دارد؛
  پس قبل از عرضهٔ پرمقیاس، مصرف را در Dashboard پایش کنید. مستندات رسمی:
  [Workers limits](https://developers.cloudflare.com/workers/platform/limits/) و
  [D1 pricing](https://developers.cloudflare.com/d1/platform/pricing/).
- endpoint خرید (`POST /api/payments/verify`) تا اتصال رسمی و server-side به
  رسید Myket/Bazaar عمداً `501` برمی‌گرداند. هیچ endpointی برای افزایش مستقیم
  credit توسط Android وجود ندارد.
