package com.smartmechanic.ai.config

import com.smartmechanic.ai.BuildConfig

/**
 * تمام تنظیمات قابل‌تغییر مربوط به مدل هوش مصنوعی در یک محل مرکزی.
 * برای تغییر مدل در آینده (مثلاً از gemini-1.5-flash به نسخه جدیدتر یا مدل قوی‌تر)
 * فقط کافی است این فایل یا مقدار BuildConfig ویرایش شود؛ هیچ تغییری در معماری لازم نیست.
 */
object AIConfig {

    /** نام مدل پیش‌فرض برای تحلیل‌های سبک (متن، سوالات ساده) */
    val LIGHT_MODEL_NAME: String = BuildConfig.GEMINI_MODEL_NAME

    /** مدل قوی‌تر برای تحلیل‌های پیچیده‌تر (تصویر/صدا/ویدئو یا موارد نامشخص) */
    const val HEAVY_MODEL_NAME: String = "gemini-1.5-pro"

    /** آدرس پایه API. در صورت استفاده از Backend واسط به جای تماس مستقیم، این مقدار عوض می‌شود. */
    val BASE_URL: String = BuildConfig.BACKEND_BASE_URL

    /** کلید API — هرگز مستقیماً در سورس یا لاگ چاپ نشود. */
    val API_KEY: String = BuildConfig.GEMINI_API_KEY

    fun isApiKeyConfigured(): Boolean = API_KEY.isNotBlank()

    // محدودیت‌های مصرف برای کنترل هزینه
    const val MAX_AUDIO_DURATION_SECONDS = 30
    const val MAX_VIDEO_DURATION_SECONDS = 60
    const val MAX_IMAGE_SIZE_BYTES = 8L * 1024 * 1024        // 8MB
    const val MAX_AUDIO_SIZE_BYTES = 15L * 1024 * 1024       // 15MB
    const val MAX_VIDEO_SIZE_BYTES = 50L * 1024 * 1024       // 50MB (بعد از فشرده‌سازی)
    const val MAX_IMAGE_DIMENSION_PX = 1280

    const val REQUEST_TIMEOUT_SECONDS = 60L
}
