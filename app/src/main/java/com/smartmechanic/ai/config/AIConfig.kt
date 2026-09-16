package com.smartmechanic.ai.config

import com.smartmechanic.ai.BuildConfig

/**
 * تمام تنظیمات قابل‌تغییر مربوط به مدل هوش مصنوعی در یک محل مرکزی.
 * برای تغییر مدل در آینده (مثلاً از gemini-1.5-flash به نسخه جدیدتر یا مدل قوی‌تر)
 * فقط کافی است این فایل یا مقدار BuildConfig ویرایش شود؛ هیچ تغییری در معماری لازم نیست.
 */
object AIConfig {

    /** نام مدل پیش‌فرض برای تحلیل‌های سبک (متن، سوالات ساده) — فقط در مسیر Gemini مستقیم استفاده می‌شود. */
    val LIGHT_MODEL_NAME: String = BuildConfig.GEMINI_MODEL_NAME

    /** مدل قوی‌تر برای تحلیل‌های چندوجهی (تصویر/صدا/ویدئو) — فقط در مسیر Gemini مستقیم. */
    const val HEAVY_MODEL_NAME: String = "gemini-3.1-pro-preview"

    /** آدرس پایه API. در صورت استفاده از Backend واسط به جای تماس مستقیم، این مقدار عوض می‌شود. */
    val BASE_URL: String = BuildConfig.BACKEND_BASE_URL

    /** کلید API — هرگز مستقیماً در سورس یا لاگ چاپ نشود. */
    val API_KEY: String = BuildConfig.GEMINI_API_KEY

    fun isApiKeyConfigured(): Boolean = API_KEY.isNotBlank()

    /**
     * آدرس Web App گوگل اپس‌اسکریپت که به‌عنوان پروکسی امن جلوی gapgpt.app قرار می‌گیرد.
     * اگر این مقدار خالی باشد، برنامه مستقیماً (و با API_KEY گوگل Gemini) صحبت می‌کند؛
     * اگر مقداردهی شود، همه درخواست‌ها از طریق این Backend عبور می‌کنند و کلید gapgpt
     * هرگز داخل اپ اندروید قرار نمی‌گیرد (رجوع کنید به backend/apps-script/Code.gs).
     */
    val PROXY_URL: String = BuildConfig.PROXY_URL
    val APP_SECRET: String = BuildConfig.APP_SECRET

    fun isProxyConfigured(): Boolean = PROXY_URL.isNotBlank()

    /**
     * آدرس Cloudflare Worker بک‌اند اعتباری (رجوع کنید به backend/cloudflare/worker).
     * این بک‌اند بالاترین اولویت را دارد: روی Cloudflare Free اجرا می‌شود (بدون نیاز
     * به Google Cloud Billing)، هویت هر نصب را با یک توکن اختصاصی که خودش صادر می‌کند
     * تایید می‌کند، و مصرف اعتبار را به‌صورت اتمیک در Cloudflare D1 کنترل می‌کند.
     */
    val BACKEND_URL: String = BuildConfig.AI_BACKEND_BASE_URL

    fun isBackendConfigured(): Boolean = BACKEND_URL.isNotBlank()

    /**
     * آدرس Cloud Function بک‌اند قدیمی Firebase (رجوع کنید به backend/firebase) --
     * legacy، دیگر در مسیر اصلی استفاده نمی‌شود اما کد و امکان بازگشت به آن حفظ شده.
     */
    val FUNCTION_BASE_URL: String = BuildConfig.FIREBASE_FUNCTION_BASE_URL

    fun isFirebaseBackendConfigured(): Boolean = FUNCTION_BASE_URL.isNotBlank()

    // محدودیت‌های مصرف برای کنترل هزینه
    const val MAX_AUDIO_DURATION_SECONDS = 30
    const val MAX_VIDEO_DURATION_SECONDS = 60
    const val MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024       // 10MB
    const val MAX_AUDIO_SIZE_BYTES = 8L * 1024 * 1024        // 8MB — هماهنگ با حد تست‌شده پروکسی
    const val MAX_VIDEO_SIZE_BYTES = 25L * 1024 * 1024       // 25MB — Base64 آن حدود ۳۳MB، زیر سقف Apps Script
    const val MAX_IMAGE_DIMENSION_PX = 1280

    const val REQUEST_TIMEOUT_SECONDS = 60L
}
