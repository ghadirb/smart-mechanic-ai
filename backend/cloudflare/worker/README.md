# بک‌اند اعتباری Cloudflare Workers + D1

بک‌اند اصلی و رایگان «مکانیک هوشمند» -- جایگزین Firebase Cloud Functions (که به
Blaze Plan و Google Cloud Billing نیاز داشت). این Worker روی **Cloudflare Free
Plan** اجرا می‌شود؛ تنها هزینه‌ی واقعی، مصرف خودِ AvalAI است (مستقل از این
بک‌اند).

```
Android App  ---HTTPS--->  Cloudflare Worker  --->  Cloudflare D1 (اعتبار)
                                  |                          ^
                                  |                          |
                                  +---------------> AvalAI API
                                  |
                                  +---------------> Myket (verify خرید)
```

## چرا این معماری؟

- **بدون کارت اعتباری / Billing گوگل:** Cloudflare Workers Free نیازی به کارت
  ندارد (برخلاف Firebase Blaze).
- **بدون Service Account JSON یا Workload Identity Federation:** فقط یک API
  Token ساده کافی است.
- **اعتبار هر کاربر همچنان سمت سرور کنترل می‌شود:** کلاینت هرگز نمی‌تواند
  موجودی، اعتبار خرید یا اعتبار اعطاشده را دستکاری کند -- همه چیز از Bearer
  Token و جدول‌های D1 استخراج می‌شود، نه از بدنهٔ درخواست کلاینت.

## امنیت هویت کاربر

کلاینت **user_id انتخاب نمی‌کند**. هنگام اولین اجرا، اپ یک‌بار `POST /api/register`
می‌زند؛ Worker یک `user_id` و یک **توکن تصادفی ۲۵۶ بیتی** تولید می‌کند، فقط هش
SHA-256 توکن را در D1 ذخیره می‌کند، و توکن خام را فقط همان یک بار به کلاینت
برمی‌گرداند. اپ توکن را در (Encrypted)SharedPreferences محلی نگه می‌دارد
(`BackendSession.kt`) و در هر درخواست بعدی به‌صورت `Authorization: Bearer <token>`
می‌فرستد. تغییر `user_id` توسط کاربر بی‌فایده است چون هویت واقعی از روی توکن
(نه فیلدی که کلاینت در بدنه‌ی JSON می‌فرستد) تعیین می‌شود.

> این یک سیستم احراز هویت کامل (مثل OAuth) نیست، اما معماری طوری طراحی شده که
> بعداً بتوان بدون تغییر اساسی، یک لایه احراز هویت واقعی (مثلاً ورود با شماره
> موبایل) روی همین جدول `users` اضافه کرد.

### محدودیت شناخته‌شده: توکن گم‌شده = اعتبار گم‌شده (TODO)

چون هیچ حساب واقعی (شماره موبایل/ایمیل) پشت این توکن نیست، اگر کاربر توکن
محلی را از دست بدهد (پاک‌کردن data اپ، تعویض گوشی بدون انتقال دستی) هیچ راه
بازیابی موجودی وجود ندارد -- باید دوباره `register` کند و از صفر (یا صفر
اعتبار رایگان، چون `device_hash` او را قبلاً دیده -- پایین‌تر را ببینید) شروع
کند. اندروید هرگز به‌صورت خودکار بعد از خطای `401` دوباره register نمی‌زند
(چون این کار یک کاربر جدید و بالقوه اعتبار رایگان جدید می‌سازد)؛ این کار را
فقط با اقدام صریح کاربر (دکمهٔ «بازنشانی نشست») انجام می‌دهد. راه‌حل واقعی این
محدودیت، افزودن یک حساب کاربری واقعی است که عمداً در این مرحله از محصول اضافه
نشده (رجوع کنید به بخش «مدل Credit-based» پایین).

## جلوگیری از سوءاستفاده از اعتبار رایگان نصب

`POST /api/register` می‌تواند اختیاراً `{ "deviceId": "..." }` بگیرد (اندروید
مقدار `Settings.Secure.ANDROID_ID` را می‌فرستد -- نیازی به پرمیشن ندارد و به
شمارهٔ تلفن/لاگین نیازی نیست). Worker فقط **هش SHA-256** این مقدار را در جدول
`device_registrations` نگه می‌دارد، نه مقدار خام را:

- اولین باری که یک `device_hash` دیده می‌شود → کاربر جدید ۵ اعتبار رایگان می‌گیرد.
- اگر همان `device_hash` دوباره `register` بزند (پاک/نصب مجدد اپ) → یک کاربر
  و توکن جدید می‌گیرد (برای اینکه اپ کار کند)، ولی **بدون اعتبار رایگان جدید**.
- اگر خیلی زود (کمتر از ۳۰ ثانیه) پشت‌سرهم `register` بیاید → `429 RATE_LIMITED`.

این یک محدودیت سخت و ضدجعل کامل نیست (کسی که `ANDROID_ID` را دستی reset کند
دوباره واجد شرایط می‌شود)، اما بدون شماره تلفن یا لاگین، حداکثر محافظتی است که
منطقی است -- دقیقاً طبق درخواست: بدون احراز هویت پیچیده، بدون کاهش بی‌دلیل
حریم خصوصی.

## Endpointها

| Method | مسیر | نیاز به توکن | توضیح |
|---|---|---|---|
| POST | `/api/register` | خیر | ثبت‌نام نصب جدید؛ `{ deviceId? }` اختیاری؛ ۵ اعتبار رایگان (یا ۰ اگر device قبلاً دیده شده) |
| GET | `/api/credits` | بله | موجودی فعلی + جدول هزینه‌ها + بسته‌های خرید |
| GET | `/api/credits/history` | بله | تاریخچهٔ تراکنش‌های همین کاربر، صفحه‌بندی‌شده (`?limit=&before=`) |
| POST | `/api/diagnose` | بله | تشخیص هوش مصنوعی؛ نیاز به هدر `X-Idempotency-Key` (حداقل ۱۶ کاراکتر) |
| POST | `/api/payments/intent` | بله | `{ productId }` → `{ developerPayload }` یک‌بارمصرف پیش از باز کردن SDK مایکت |
| POST | `/api/payments/verify` | بله | `{ tokenId, developerPayload }` → تایید server-to-server با مایکت و افزایش واقعی اعتبار |

بدنه‌ی `POST /api/diagnose`: `action` ("text"/"image"/"audio"/"video")، `prompt`،
و بسته به نوع، `imageBase64`/`imageMimeType`، `audioBase64`/`audioMimeType` یا
`videoBase64`/`videoMimeType`. پاسخ موفق: `{ success, raw, creditsCharged }`.

### Idempotency و بازیابی از timeout

کلاینت برای هر «تلاش منطقی» (نه هر HTTP attempt) یک `X-Idempotency-Key` ثابت
می‌فرستد -- در Android این کلید، هش SHA-256 بدنهٔ دقیق درخواست است
(`CloudflareAIService.kt`)، پس یک retry از همان فایل/متن/خودرو همیشه همان کلید
را تولید می‌کند، بدون نیاز به نگه‌داشتن state اضافه بین تلاش‌ها؛ اگر کاربر
فایل یا متن را عوض کند، کلید هم به‌طور طبیعی عوض می‌شود.

سه حالت ممکن وقتی همان کلید دوباره می‌رسد:

1. تراکنش قبلی کامل شده (`response_raw` دارد) → همان نتیجه دوباره برگردانده
   می‌شود، **بدون کسر مجدد اعتبار**.
2. تراکنش هنوز «رزرو» است (`response_raw = NULL`) ولی کمتر از ۹۰ ثانیه از
   ایجادش گذشته → به‌احتمال زیاد واقعاً در حال پردازش است → `409 REQUEST_IN_PROGRESS`
   (کلاینت باید کمی صبر کند و دوباره با همان کلید تلاش کند، نه کلید جدید بسازد).
3. تراکنش «رزرو» و از ۹۰ ثانیه قدیمی‌تر است → یعنی Worker قبلی به‌احتمال زیاد
   بدون رسیدن به `catch` از کار افتاده (نادر) → اعتبار خودکار برگردانده و رزرو
   حذف می‌شود، و همان تلاش با همان کلید از نو شروع می‌شود.

با این طراحی نیازی به ستون `status` جداگانه (reserved/completed/refunded) نبود؛
`response_raw IS NULL` + سن رکورد همان اطلاعات را می‌دهد.

## سیاست اعتبار

در `src/creditPolicy.ts` متمرکز شده: متن=۱، عکس=۳، صوت=۵، ویدیو=۱۲. این نسبت‌ها
تقریبی‌اند، نه دقیقاً متناسب با هزینهٔ واقعی AvalAI -- رجوع کنید به
[`TECHNICAL_NOTES.md`](./TECHNICAL_NOTES.md) برای روش اندازه‌گیری هزینهٔ واقعی
و تعداد فراخوانی AI به ازای هر تشخیص.

بسته‌های خرید: `credit_20` (۲۰ اعتبار)، `credit_50` (۵۰ اعتبار)، `credit_150`
(۱۵۰ اعتبار). **قیمت ریالی این محصولات فقط در پنل مایکت تعریف می‌شود، هرگز در
این Worker hard-code نمی‌شود** -- Worker فقط `productId → credits` را می‌داند.

## جریان خرید Myket

```
Android:  POST /api/payments/intent {productId}
              → Worker یک developerPayload یک‌بارمصرف می‌سازد و در D1 ذخیره می‌کند
Android:  IabHelper.launchPurchaseFlow(productId, developerPayload)
              → کاربر در اپ مایکت پرداخت می‌کند
Android:  purchaseToken را از Myket SDK می‌گیرد
Android:  POST /api/payments/verify {tokenId: purchaseToken, developerPayload}
              → Worker با X-Access-Token خودش (Secret، هرگز در APK) از
                developer.myket.ir تایید server-to-server می‌گیرد
              → اگر معتبر و developerPayload تطبیق داشت: credits را در D1
                افزایش می‌دهد و purchase_token را UNIQUE ثبت می‌کند
```

نکات امنیتی کلیدی که همیشه باید برقرار بمانند:
- `userId` فقط از Bearer Token استخراج می‌شود، هرگز از بدنهٔ client.
- `productId`ای که Worker استفاده می‌کند از خود `payment_intents` می‌آید (همان
  چیزی که هنگام `intent` ذخیره شده)، نه چیزی که کلاینت در `verify` دوباره بفرستد.
- `credits` همیشه از `CREDIT_PACKAGES` سرور محاسبه می‌شود؛ هیچ `creditsGranted`
  از کلاینت پذیرفته نمی‌شود.
- `store_purchases.purchase_token UNIQUE` از اعتبار دوباره برای یک خرید تکراری
  (حتی اگر verify چند بار صدا زده شود) جلوگیری می‌کند.
- اگر پرداخت در Myket موفق شود ولی `verify` هرگز به Worker نرسد (قطع اینترنت،
  بسته‌شدن اپ)، اندروید `purchaseToken`/`developerPayload` را محلی نگه می‌دارد
  و در باز شدن بعدی صفحهٔ اعتبار دوباره `verify` را تلاش می‌کند
  (`CreditsViewModel` -- «pending purchase recovery»).

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
npx wrangler secret put MYKET_ACCESS_TOKEN
npx wrangler deploy
```

- `AVALAI_MODEL`: نام مدلی که حساب AvalAI شما واقعاً فعال کرده.
- `MYKET_ACCESS_TOKEN`: مقدار X-Access-Token که از پنل توسعه‌دهندگان مایکت
  گرفته‌اید (برای verify سمت سرور؛ کاملاً جدا از کلید RSA عمومی که سمت اندروید
  در `MYKET_IAB_PUBLIC_KEY` می‌رود).

هیچ‌کدام از این سه مقدار را در `wrangler.toml`، `local.properties` یا GitHub
قرار ندهید -- فقط `wrangler secret put`.

اگر از Cloudflare Git integration (dash.cloudflare.com → Worker → Settings →
Build) به‌جای/در کنار GitHub Actions استفاده می‌کنید، حتماً **Root directory**
را روی `backend/cloudflare/worker` بگذارید (نه `/`)، وگرنه `wrangler.toml`
پیدا نمی‌شود.

### ۳. اتصال Android

URL چاپ‌شده پس از deploy را در فایل محلیِ نادیده‌گرفته‌شده قرار دهید:

```properties
AI_BACKEND_BASE_URL=https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev
MYKET_IAB_PUBLIC_KEY=MIG... (کلید RSA عمومی از پنل مایکت، برای فعال‌شدن دکمهٔ خرید)
```

یا همان نام متغیرها را در محیط CI build Android تعریف کنید. URL Worker راز
نیست؛ کلید AvalAI، `MYKET_ACCESS_TOKEN` و bearer tokenهای کاربران هرگز نباید
در APK یا Git باشند.

## GitHub Actions

workflow [`deploy-cloudflare-worker.yml`](../../../.github/workflows/deploy-cloudflare-worker.yml)
پس از هر push در این پوشه migrationها را اعمال و Worker را deploy می‌کند. در
GitHub → Settings → Secrets and variables → Actions فقط این دو **repository secret**
را ایجاد کنید:

| نام | مقدار |
|---|---|
| `CLOUDFLARE_API_TOKEN` | همان API Token محدود با Workers Scripts:Edit و D1:Edit |
| `CLOUDFLARE_ACCOUNT_ID` | account id Cloudflare |

`AVALAI_API_KEY`، `AVALAI_MODEL` و `MYKET_ACCESS_TOKEN` عمداً GitHub Secret
نیستند و فقط با `wrangler secret put` روی Worker ثبت می‌شوند.

## تست end-to-end بعد از deploy

```bash
curl -X POST "$AI_BACKEND_BASE_URL/api/register" -H "Content-Type: application/json" --data '{}'
curl -H "Authorization: Bearer <token>" "$AI_BACKEND_BASE_URL/api/credits"
curl -H "Authorization: Bearer <token>" "$AI_BACKEND_BASE_URL/api/credits/history?limit=5"
curl -X POST "$AI_BACKEND_BASE_URL/api/diagnose" \
  -H "Authorization: Bearer <token>" \
  -H "X-Idempotency-Key: 4e5cda8d-1f8c-4ef4-b7c3-123456789abc" \
  -H "Content-Type: application/json" \
  --data '{"action":"text","prompt":"موتور هنگام استارت صدای تق تق دارد"}'
```

همان درخواست سوم را با همان کلید تکرار کنید: پاسخ cache شده برمی‌گردد و فقط یک
اعتبار کسر می‌شود. با اعتبار ناکافی پاسخ `402`، برای token نامعتبر `401`، و برای
درخواست‌های خیلی پشت‌سرهم `429` است.

## schema D1

- `users(user_id, token_hash, credits, device_hash, last_request_at, created_at, updated_at)`
- `credit_transactions(id, user_id, amount, type, request_id, description, response_raw, created_at)`
  — قرارداد: `amount` مثبت = اعتبار مصرف‌شده (`type='debit'`)، `amount` منفی =
  اعتبار اضافه‌شده (`type='credit'`، خرید مایکت). `UNIQUE(user_id, request_id)`
  idempotency تشخیص را فقط در محدودهٔ همان کاربر نگه می‌دارد.
- `payment_intents(id, user_id, product_id, developer_payload UNIQUE, expires_at, created_at)`
- `store_purchases(id, user_id, store, product_id, purchase_token UNIQUE, credits_granted, purchase_time, verified_at)`
- `device_registrations(device_hash, first_user_id, last_register_at, register_count)`
  — فقط برای ضدسوءاستفادهٔ اعتبار رایگان؛ حاوی هیچ دادهٔ شخصی خامی نیست.

## محدودیت‌ها و عملیات آینده

- اندازه‌های اجرایی نسخهٔ اول: تصویر 10MB، صوت 8MB، ویدیو 25MB؛ پیش از رسیدن به
  سقف Cloudflare اعتبارسنجی می‌شوند.
- Worker Free سقف 100,000 درخواست روزانه دارد. D1 Free نیز سهمیهٔ روزانه دارد؛
  پس قبل از عرضهٔ پرمقیاس، مصرف را در Dashboard پایش کنید. مستندات رسمی:
  [Workers limits](https://developers.cloudflare.com/workers/platform/limits/) و
  [D1 pricing](https://developers.cloudflare.com/d1/platform/pricing/).
- بازیابی توکن گم‌شده حل نشده (بالا را ببینید) -- نیازمند یک لایهٔ حساب کاربری
  واقعی است که عمداً هنوز اضافه نشده.
- `device_hash` یک ضدسوءاستفادهٔ بهترین‌تلاش است، نه ضدجعل کامل.
