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
