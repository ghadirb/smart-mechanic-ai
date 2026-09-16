# 🔧 مکانیک هوشمند AI (Smart Mechanic AI)

اپلیکیشن اندروید برای تشخیص هوشمند مشکلات خودرو با استفاده از هوش مصنوعی چندوجهی (Google Gemini).
کاربر می‌تواند مشکل خودرو را با **متن**، **عکس**، **صدای موتور** یا **ویدئو** توصیف کند و تحلیلی
ساختاریافته و قابل‌فهم به **زبان فارسی** دریافت کند.

> ⚠️ این برنامه جایگزین تشخیص مکانیکی حرفه‌ای نیست. نتایج آن صرفاً یک ارزیابی کمکی مبتنی بر
> هوش مصنوعی هستند. برای مشکلات جدی یا ایمنی (ترمز، فرمان، سوخت، برق فشار بالا و ...) حتماً
> به یک تعمیرکار متخصص مراجعه کنید.

---

## 📱 تکنولوژی‌ها

| بخش | فناوری |
|---|---|
| زبان | Kotlin |
| UI | Jetpack Compose (Material 3، کاملاً RTL/فارسی) |
| معماری | MVVM + لایه Repository (قابل ارتقا به Clean Architecture) |
| Async | Kotlin Coroutines / Flow |
| شبکه | Retrofit + OkHttp |
| دیتابیس محلی | Room |
| بک‌اند اصلی | Cloudflare Workers + D1 (بدون Google Cloud Billing) |
| مدل AI | AvalAI / Gemini (کلید فقط در Worker، چندوجهی: متن/عکس/صدا/ویدئو) |
| حداقل نسخه اندروید | API 26 (Android 8.0) |

---

## 🏗️ معماری پروژه

```
app/src/main/java/com/smartmechanic/ai/
├── config/           # AIConfig (نام مدل، محدودیت‌ها)، SystemPrompt (قابل ویرایش)
├── data/
│   ├── model/        # مدل‌های دامنه: Car, DiagnosisResult, InputType
│   ├── remote/       # DTOهای Gemini + Retrofit service + RetrofitClient
│   ├── local/         # Room: Entities, DAOs, AppDatabase
│   └── repository/    # AIService (interface) + GeminiAIService (impl)
│                       # CarRepository, DiagnosisRepository, PromptBuilder
├── ui/
│   ├── theme/         # رنگ، تایپوگرافی فارسی
│   ├── navigation/    # مسیرهای Navigation Compose
│   ├── components/    # کارت‌های نتیجه، هشدار ایمنی، Loading/Error/Consent
│   └── screens/       # home, cars, diagnosis/{text,image,audio,video}, result, history
├── util/              # FileUtils, ErrorMessageMapper, NetworkMonitor,
│                       # MediaFileFactory, AudioRecorderHelper, ResultHolder
├── SmartMechanicApp.kt   # Application + Service Locator ساده
└── MainActivity.kt       # NavHost اصلی
```

**چرا این ساختار؟**
لایه `AIService` یک Interface است؛ `GeminiAIService` تنها پیاده‌سازی فعلی آن است.
برای تعویض ارائه‌دهنده مدل (مثلاً به یک مدل دیگر یا یک Backend واسط)، فقط کافی است یک
پیاده‌سازی جدید از `AIService` نوشته و در `SmartMechanicApp.onCreate()` جایگزین شود؛ هیچ‌کدام
از ViewModel ها یا صفحات UI نیاز به تغییر ندارند.

نام مدل در `AIConfig` (بر پایه `BuildConfig.GEMINI_MODEL_NAME`) قابل تغییر بدون نیاز به تغییر معماری است.

---

## 🔐 پیکربندی و امنیت (مهم!)

**API Key هرگز نباید در سورس‌کد، APK نهایی یا مخزن Git قرار گیرد.**

روش تزریق کلید (در `app/build.gradle.kts` پیاده‌سازی شده):

1. **اولویت اول – متغیر محیطی** (مناسب برای CI/CD):
   ```bash
   export GEMINI_API_KEY="your_key_here"
   ```
2. **اولویت دوم – فایل `local.properties`** (مناسب برای توسعه لوکال، در `.gitignore` است):
   ```properties
   GEMINI_API_KEY=your_gemini_api_key_here
   ```
   یک نمونه در `local.properties.example` قرار دارد؛ آن را کپی و مقداردهی کنید.

مقدار کلید در زمان build از طریق `buildConfigField` به `BuildConfig.GEMINI_API_KEY` تزریق می‌شود
و در **زمان اجرا در حافظه** خوانده می‌شود، نه به‌صورت رشته ثابت در بایت‌کد قابل‌مشاهده به سادگی.

> 🔒 در نسخهٔ تولیدی این پروژه کلید AvalAI **اصلاً در کلاینت قرار نمی‌گیرد**: Worker
> کلادفلر آن را به‌صورت Secret نگه می‌دارد و Android فقط با `AI_BACKEND_BASE_URL` به Worker
> وصل می‌شود. کلید Gemini فقط fallback توسعه‌ای است، نه پیش‌نیاز مسیر اصلی.

### گرفتن Gemini API Key
از [Google AI Studio](https://aistudio.google.com/app/apikey) یک کلید رایگان بسازید.

---

## 💳 روش اصلی نسخه تجاری: Cloudflare Workers + D1

مسیر اصلی در `SmartMechanicApp.kt` این ترتیب را دارد:
**Cloudflare > Firebase (legacy) > Apps Script > Gemini مستقیم**. Cloudflare مصرف هر نصب را
سمت سرور کنترل می‌کند و به Firebase Blaze، Google Cloud Billing، Service Account یا WIF نیاز ندارد.

### چطور کار می‌کند؟
```
اپ اندروید  →  HTTPS + Bearer token  →  Cloudflare Worker  →  AvalAI / Gemini
                                         │
                                         └── Cloudflare D1 (اعتبار و دفتر تراکنش)
```
- Worker برای هر نصب یک توکن تصادفی ۲۵۶ بیتی صادر می‌کند؛ فقط هش آن در D1 ذخیره می‌شود.
- هر درخواست پیش از تماس با AI به‌شکل اتمیک از D1 کسر می‌شود و در خطای AvalAI بازگردانده می‌شود.
- idempotency به‌ازای هر کاربر مانع کسر تکراری اعتبار است؛ Android هرگز نمی‌تواند اعتبار اضافه کند.
- کلید واقعی AvalAI فقط Cloudflare Worker Secret است، نه در کد، APK یا GitHub.

### هزینه هر عملیات (قابل تغییر در `backend/cloudflare/worker/src/creditPolicy.ts`)
| نوع تشخیص | اعتبار مصرفی |
|---|---:|
| متن | 1 |
| عکس | 3 |
| صوت | 5 |
| ویدیو | 12 |

### مراحل راه‌اندازی
راهنمای دقیق ساخت D1، اجرای migration، تنظیم Secretها، deploy و GitHub Actions در
[`backend/cloudflare/worker/README.md`](backend/cloudflare/worker/README.md) آمده است. پس از
deploy، URL Worker را فقط در `local.properties` یا متغیر CI با نام
`AI_BACKEND_BASE_URL` بگذارید — نیازی به تغییر کد نیست.

> 🛒 **بسته‌های خرید (`credit_20`/`credit_50`/`credit_150`) و تایید پرداخت از طریق Cafe Bazaar/Myket**
> هنوز نیازمند اتصال حساب توسعه‌دهنده و محصولات واقعی هر فروشگاه هستند. endpoint
> `POST /api/payments/verify` عمداً تا آن زمان `501` برمی‌گرداند؛ افزایش مستقیم اعتبار ممکن نیست.

Firebase فقط به‌عنوان مسیر legacy در [`backend/firebase`](backend/firebase) نگه‌داری شده و دیگر
workflow خودکار deploy ندارد.

---

## 🌐 روش جایگزین: پروکسی رایگان با Google Apps Script + AvalAI

اگر نمی‌خواهید حتی کلید Gemini هم داخل اپلیکیشن اندروید (BuildConfig) قرار بگیرد، یک پیاده‌سازی
جایگزین آماده است: `ProxyAIService`. این معماری از همان روش اثبات‌شده و تست‌شده در پروژه خواهر
این اپ ([DriveMate-AI](https://github.com/ghadirb/DriveMate-AI)) استفاده می‌کند: به‌جای gapgpt،
از **[AvalAI](https://avalai.ir)** استفاده می‌شود — چون AvalAI همان endpoint بومی
`generateContent` گوگل را Proxy می‌کند (نه فقط یک لایه سازگار با OpenAI)، **تحلیل ویدئو هم کامل
کار می‌کند**، نه فقط متن/عکس/صدا.

### چطور کار می‌کند؟
```
اپ اندروید  →  Web App گوگل اپس‌اسکریپت (رایگان)  →  api.avalai.ir  →  Gemini
             (کلید AvalAI فقط اینجا نگه‌داری می‌شود)
```
کلید AvalAI هرگز در APK یا کد کلاینت قرار نمی‌گیرد؛ فقط در «Script Properties» گوگل
اپس‌اسکریپت (که کاملاً سمت سرور و خارج از دسترس کاربر نهایی است) ذخیره می‌شود.

### مراحل راه‌اندازی
1. یک کلید از [avalai.ir](https://avalai.ir) بگیرید.
2. به [script.google.com](https://script.google.com) بروید → **New project**.
3. محتوای فایل [`backend/apps-script/Code.gs`](backend/apps-script/Code.gs) این مخزن را در
   ویرایشگر پیست کنید.
4. از منوی چرخ‌دنده (⚙️ Project Settings) → **Script Properties** → **Add script property**:
   - نام: `AVALAI_API_KEY` — مقدار: کلید AvalAI شما
   - نام: `CAR_DIAGNOSIS_MODEL` — مقدار: `gemini-3.1-pro-preview` (یا هر مدلی که حساب AvalAI شما فعال دارد؛ تعویض مدل فقط با تغییر همین مقدار و Deploy مجدد انجام می‌شود — نیازی به نسخه جدید اپ نیست)
   - (اختیاری، پیش‌فرض `https://api.avalai.ir` است) نام: `AVALAI_BASE_URL`
   - (اختیاری ولی توصیه‌شده) نام: `APP_SECRET` — یک رشته تصادفی دلخواه، تا فقط اپ خودتان
     بتواند از این Web App استفاده کند.
5. **Deploy → New deployment → نوع: Web app**
   - Execute as: **Me**
   - Who has access: **Anyone**
   - روی **Deploy** بزنید و در صورت نیاز مجوزهای گوگل را تأیید کنید.
6. آدرس Web app (چیزی شبیه `https://script.google.com/macros/s/AKfycb.../exec`) را کپی کنید.
7. در `local.properties` مقداردهی کنید:
   ```properties
   PROXY_URL=https://script.google.com/macros/s/AKfycb.../exec
   APP_SECRET=همان رشته‌ای که در مرحله ۴ گذاشتید (اگر گذاشتید)
   ```
   با مقداردهی `PROXY_URL`، برنامه به‌طور خودکار (`SmartMechanicApp.kt`) به‌جای `GeminiAIService`
   از `ProxyAIService` استفاده می‌کند — دیگر نیازی به `GEMINI_API_KEY` نیست.

> **اگر قبلاً نسخه مبتنی بر gapgpt را دیپلوی کرده بودید:** کافیست محتوای Code.gs را با نسخه
> بالا جایگزین کنید، Script Property را از `GAPGPT_API_KEY` به `AVALAI_API_KEY` (با کلید AvalAI)
> تغییر دهید، `CAR_DIAGNOSIS_MODEL` را اضافه کنید، سپس از **Deploy → Manage deployments** نسخه
> موجود را **Edit** و دوباره **Deploy** کنید. آدرس Web App (`PROXY_URL`) همان قبلی می‌ماند و
> نیازی به تغییر آن در `local.properties` نیست.

### چه چیزی از این طریق پشتیبانی می‌شود؟
| قابلیت | از طریق Apps Script/AvalAI | توضیح |
|---|---|---|
| تشخیص متنی | ✅ | endpoint بومی `generateContent` گوگل |
| تحلیل عکس | ✅ | همان `generateContent` با `inlineData` — دقیقاً مثل تماس مستقیم با Gemini |
| تحلیل ویدئو | ✅ | همان `generateContent` با `inlineData` — هم تصویر و هم صدای ویدئو بررسی می‌شود |
| تحلیل صدا | ✅ | طبق تجربه تست‌شده، از مسیر جداگانه `chat/completions` با فرمت `input_audio` (سازگار با OpenAI) عبور می‌کند که قابل‌اطمینان‌تر است |

### هزینه
Google Apps Script برای این حجم استفاده (Web App ساده) کاملاً **رایگان** است (در محدوده
سهمیه روزانه رایگان گوگل). هزینه واقعی فقط مربوط به مصرف کلید AvalAI شماست.

---

## ▶️ اجرای پروژه

1. پروژه را در Android Studio (Hedgehog یا جدیدتر) باز کنید.
2. فایل `local.properties.example` را کپی کرده و به نام `local.properties` ذخیره کنید؛ مسیر SDK و `GEMINI_API_KEY` را تنظیم کنید.
3. Gradle Sync را اجرا کنید.
4. روی یک دستگاه/شبیه‌ساز با API 26+ اجرا (`Run`) کنید.

---

## 🤖 بیلد خودکار با GitHub Actions

یک workflow آماده در `.github/workflows/android-build.yml` قرار دارد که در هر `push`/`pull request`
به شاخه `main` (و همچنین به‌صورت دستی از تب Actions) اجرا می‌شود و:

1. JDK 17 و Android SDK را نصب می‌کند.
2. تست‌های واحد (`./gradlew test`) را اجرا می‌کند.
3. یک APK نسخه Debug می‌سازد (`./gradlew assembleDebug`).
4. APK و گزارش تست‌ها را به‌عنوان Artifact قابل‌دانلود در همان اجرای Actions آپلود می‌کند.

### تنظیم لازم قبل از اولین اجرا

برای build Android، در صورت استفاده از fallback مستقیم Gemini، کلید را به‌عنوان یک **GitHub Secret** تعریف کنید:

1. به مخزن در گیت‌هاب بروید → **Settings** → **Secrets and variables** → **Actions**
2. روی **New repository secret** بزنید.
3. نام: `GEMINI_API_KEY` — مقدار: کلید Gemini خودتان.

برای deploy Worker نیز `CLOUDFLARE_API_TOKEN` و `CLOUDFLARE_ACCOUNT_ID` را در همان بخش Secrets
قرار دهید. `AVALAI_API_KEY` و `AVALAI_MODEL` را **در GitHub نگذارید**؛ آن‌ها فقط Cloudflare Worker
Secrets هستند. جزئیات در راهنمای Worker آمده است.

بعد از آن، هر بار که به `main` پوش کنید (یا از تب **Actions** دکمه **Run workflow** را بزنید)،
بیلد به‌صورت خودکار اجرا می‌شود و می‌توانید APK را از بخش **Artifacts** همان اجرا دانلود کنید.

---

## ✅ قابلیت‌های پیاده‌سازی‌شده در این نسخه (MVP)

| # | قابلیت | وضعیت |
|---|---|---|
| 1 | ثبت و مدیریت چند خودرو (Room) | ✅ |
| 2 | تشخیص مشکل با **متن** + مشخصات خودرو | ✅ |
| 3 | تحلیل **عکس** (دوربین/گالری) | ✅ |
| 4 | تحلیل **صدای موتور** (ضبط تا ۳۰ ثانیه + هشدار ایمنی قبل از ضبط) | ✅ |
| 5 | تحلیل **ویدئو** (دوربین/گالری، محدودیت ۶۰ ثانیه و حجم) | ✅ |
| 6 | نمایش نتیجه ساختاریافته (خلاصه، موارد محتمل، فوریت، اقدام پیشنهادی، نیاز به مکانیک، سؤالات تکمیلی) | ✅ |
| 7 | هشدار ایمنی ثابت + بدون درصد ساختگی | ✅ |
| 8 | سوابق تشخیص + حذف سوابق | ✅ |
| 9 | مدیریت کامل خطا (بدون Crash): قطع اینترنت، Timeout، فایل بزرگ، فرمت نامعتبر، پاسخ ناقص مدل، نبود API Key | ✅ |
| 10 | رضایت صریح کاربر قبل از ارسال فایل به سرویس AI | ✅ |
| 11 | System Prompt جدا و قابل ویرایش | ✅ |
| 12 | تست‌های واحد (پارس JSON، مدیریت خطا، اعتبارسنجی فایل) | ✅ |

## ❌ قابلیت‌هایی که در این نسخه پیاده‌سازی نشده‌اند (طبق محدودیت MVP)

طبق سند طراحی، این موارد **عمداً** به نسخه‌های بعدی موکول شده‌اند تا نسخه اول پایدار بماند:

- Live Camera / راهنمایی تصویری زنده (V4)
- OBD2 Bluetooth، DTC، RPM، دمای آب، ولتاژ باتری زنده (V3)
- فشرده‌سازی واقعی ویدئو قبل از ارسال (فعلاً فقط اعتبارسنجی مدت/حجم انجام می‌شود؛ در صورت
  عبور از حد مجاز از کاربر خواسته می‌شود ویدئوی کوتاه‌تر/کم‌حجم‌تر انتخاب کند). افزودن
  فشرده‌سازی واقعی (مثلاً با Media3 Transformer) یک بهبود آماده برای نسخه بعدی است.
- مقایسه دو صدای موتور (V2)
- دیتابیس اختصاصی مشکلات رایج خودروهای ایرانی و تخمین هزینه تعمیر (V5)
- Cache نتایج برای درخواست‌های تکراری (پیشنهاد شده در بخش ۱۵ سند؛ در این نسخه پیاده نشده)

## 🔑 API Keyهای مورد نیاز

- **AvalAI API Key** برای مسیر اصلی — فقط Cloudflare Worker Secret است.
- **Gemini API Key** فقط در صورت فعال‌کردن fallback مستقیم لازم است.

## 💰 برآورد تقریبی هزینه

هزینه Gemini بر اساس تعداد توکن (متن) و حجم رسانه (عکس/صدا/ویدئو) محاسبه می‌شود و بسته به مدل
انتخابی (`gemini-1.5-flash` ارزان‌تر، `gemini-1.5-pro` گران‌تر ولی دقیق‌تر) متفاوت است. برای
قیمت دقیق و به‌روز به [صفحه قیمت‌گذاری Gemini API](https://ai.google.dev/pricing) مراجعه کنید.
راهکارهای کاهش هزینه در `AIConfig` پیاده‌سازی شده‌اند: محدودیت مدت صدا/ویدئو، محدودیت حجم فایل،
و امکان انتخاب مدل سبک‌تر برای تحلیل متن ساده در برابر مدل قوی‌تر برای رسانه.

## 🚀 نقشه راه نسخه‌های بعدی

- **V2:** مقایسه دو صدای موتور (قبل/بعد از تعمیر) — نیازمند ذخیره اختیاری فایل صدا (که معماری فعلی از طریق `mediaFilePath` در `DiagnosisEntity` از آن پشتیبانی می‌کند) و یک صفحه مقایسه جدید.
- **V3 (OBD2):** افزودن یک ماژول `data/obd2` با اتصال Bluetooth (مثلاً از طریق کتابخانه‌های موجود ELM327)، خواندن DTC/RPM/دما/ولتاژ به‌صورت `Flow<LiveData>` و ترکیب آن با `PromptBuilder` موجود.
- **V4 (Live Camera):** افزودن CameraX + یک ViewModel جدید که فریم‌ها را به‌صورت دوره‌ای به Gemini ارسال کرده و راهنمایی گام‌به‌گام را در قالب یک Overlay نمایش دهد؛ `AIService` باید یک متد `analyzeLiveFrame()` جدید بگیرد.
- **V5:** افزودن یک جدول Room جدید برای دیتابیس مشکلات رایج ایرانی و تخمین هزینه (قابل تغذیه از یک فایل JSON محلی یا API خارجی).

## 🧪 تست

تست‌های واحد در `app/src/test/` شامل:
- پارس پاسخ JSON مدل (موفق، بسته‌بندی‌شده در Markdown، نامعتبر، summary خالی)
- نگاشت خطاها به پیام فارسی برای تمام حالات (`NoInternet`, `Timeout`, `MissingApiKey`, `FileTooLarge`, `UnsupportedFormat`, `ApiError`, `Unknown`)
- اعتبارسنجی حجم فایل و تشخیص فرمت‌های پشتیبانی‌شده
- تشخیص کلمات کلیدی بحرانی ایمنی

برای اجرای تست‌ها:
```bash
./gradlew test
```

سناریوهای دستی توصیه‌شده برای تست روی دستگاه واقعی (بخش ۱۹ سند طراحی): تصویر واضح موتور،
تصویر چراغ Check Engine، صدای موتور سالم/غیرعادی، ویدئوی سالم/دارای لرزش، فایل خراب، فایل
بیش‌ازحد بزرگ، قطع اینترنت، خطای API، پاسخ ناقص مدل — در همه این حالات برنامه نباید Crash کند
(مدیریت خطا در `GeminiAIService` و `ErrorMessageMapper` این موارد را پوشش می‌دهد).

## 🔒 حریم خصوصی

- قبل از ارسال هر فایل به سرویس هوش مصنوعی، رضایت صریح کاربر گرفته می‌شود (`PrivacyConsentDialog`).
- فایل‌های صوتی/تصویری/ویدئویی به‌طور پیش‌فرض پس از تحلیل حذف می‌شوند مگر کاربر صراحتاً بخواهد نگه داشته شوند.
- سوابق تشخیص هر زمان قابل حذف توسط کاربر هستند.
- هیچ اطلاعات شخصی غیرضروری جمع‌آوری نمی‌شود.

## ⚠️ ایمنی

خروجی AI هرگز خرابی را قطعی اعلام نمی‌کند (این قانون در `SystemPrompt` تصریح شده است) و برای
موارد پرخطر (ترمز، فرمان، دود شدید، آتش‌سوزی، نشتی شدید و ...) همیشه یک هشدار واضح نمایش داده
می‌شود که کاربر را به توقف خودرو و مراجعه به متخصص راهنمایی می‌کند.

---

## 📄 لایسنس

این پروژه به‌عنوان یک MVP نمایشی/آموزشی ساخته شده است.
