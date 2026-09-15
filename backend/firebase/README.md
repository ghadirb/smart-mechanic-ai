# سرویس اعتبار و تشخیص امن

این سرویس جایگزین Google Apps Script برای نسخهٔ تجاری است. کلید AvalAI، دفتر اعتبار و تایید پرداخت همگی فقط در سرور نگهداری می‌شوند.

## چرا این سرویس لازم است؟

`APP_SECRET` که در APK قرار می‌گیرد قابل استخراج است؛ بنابراین برای کنترل موجودی یا جلوگیری از مصرف غیرمجاز قابل اتکا نیست. این سرویس با Firebase Authentication کاربر را شناسایی می‌کند و هر کسر اعتبار را در تراکنش Firestore ثبت می‌کند.

## راه‌اندازی اولیه

1. در Firebase Console یک پروژهٔ production بسازید و Firestore و **Anonymous Authentication** را فعال کنید. پروژهٔ فعلی این مخزن `smart-mechanic-ai-153d3` است.
2. Firebase CLI را نصب و سپس در پوشهٔ `backend/firebase` اجرا کنید:

   ```bash
   npm install
   npx firebase login
   npx firebase use --add
   npx firebase functions:secrets:set AVALAI_API_KEY
   npx firebase functions:secrets:set AVALAI_MODEL
   npm run deploy
   ```

3. مقدار `AVALAI_API_KEY` و نام مدل را فقط به‌عنوان Firebase Secret ثبت کنید؛ آن‌ها را در Android، GitHub یا Apps Script قرار ندهید.
4. پس از deploy، URL تابع `api` را برای اتصال مرحلهٔ بعدی Android نگه دارید.

> Cloud Functions برای deployment تولیدی به طرح Blaze و در نتیجه اتصال حساب پرداخت نیاز دارد. قبل از فعال‌سازی، Budget Alert و سقف هزینه برای Cloud Run تنظیم کنید.

## اتصال اپ اندروید به این بک‌اند

اپ اندروید از این ترتیب اولویت برای انتخاب سرویس هوش مصنوعی استفاده می‌کند
(رجوع کنید به `SmartMechanicApp.kt`): **Firebase (این بک‌اند) > Apps Script قدیمی > تماس مستقیم Gemini**.
برای فعال‌سازی مسیر Firebase:

1. `npm run deploy` را در همین پوشه اجرا کنید. خروجی دستور یک URL شبیه
   `https://api-xxxxxxxxxx-ew.a.run.app` (یا آدرس Cloud Run منطقه `europe-west1`) چاپ می‌کند.
2. همان URL را به‌عنوان `FIREBASE_FUNCTION_BASE_URL` در `local.properties` (برای بیلد لوکال)
   یا به‌عنوان GitHub Actions secret + متغیر محیطی هم‌نام (برای بیلد CI) قرار دهید — دقیقاً
   مثل `GEMINI_API_KEY` و `PROXY_URL`.
3. تا وقتی `FIREBASE_FUNCTION_BASE_URL` خالی باشد، اپ خودکار به مسیر بعدی (Apps Script یا Gemini
   مستقیم) بازمی‌گردد؛ نیازی به تغییر کد نیست.

## مدل اعتبار پیشنهادی اولیه

| خدمت | اعتبار |
|---|---:|
| متن | 1 |
| عکس | 3 |
| صوت | 5 |
| ویدیو | 12 |

این اعداد در `functions/src/creditPolicy.ts` متمرکز هستند. پیش از فروش، هزینهٔ واقعی هر درخواست را برای چند هفته ثبت کنید و نرخ‌ها را با حاشیهٔ امن تغییر دهید.

## پرداخت

محصول‌های پیشنهادی مصرفی: `credit_20`، `credit_50` و `credit_150`.

کلاینت هرگز اجازهٔ افزایش موجودی ندارد. پس از خرید، رسید باید به endpoint تایید پرداخت ارسال شود؛ سرور با API مایکت یا بازار آن را تایید می‌کند و سپس در تراکنش Firestore اعتبار اضافه می‌شود. adapterهای هر بازار در مرحلهٔ اتصال حساب توسعه‌دهنده اضافه می‌شوند، زیرا نیازمند شناسهٔ محصول و دسترسی‌های واقعی فروشگاه هستند.

## وضعیت این مرحله

- API امن تشخیص با کسر اتمیک اعتبار: آماده
- دفتر اعتبار و idempotency برای جلوگیری از دوبار کسر شدن: آماده
- اتصال Android به Firebase: آماده (`FirebaseAIService.kt`) — فقط منتظر `FIREBASE_FUNCTION_BASE_URL` بعد از دیپلوی شماست
- تایید خرید مایکت و بازار: نیازمند ایجاد حساب‌ها و محصول‌های واقعی
