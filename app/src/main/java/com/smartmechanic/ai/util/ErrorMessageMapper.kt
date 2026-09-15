package com.smartmechanic.ai.util

/**
 * تبدیل خطاهای داخلی به پیام فارسیِ قابل‌نمایش به کاربر.
 * هدف: برنامه هرگز نباید Stacktrace خام یا پیام انگلیسی فنی به کاربر نشان دهد.
 */
object ErrorMessageMapper {

    fun toPersianMessage(error: AppError): String = when (error) {
        is AppError.NoInternet ->
            "ارتباط با سرویس هوش مصنوعی برقرار نشد. اتصال اینترنت را بررسی کنید و دوباره تلاش کنید."
        is AppError.Timeout ->
            "زمان پاسخ‌گویی سرویس هوش مصنوعی به پایان رسید. لطفاً دوباره تلاش کنید."
        is AppError.MissingApiKey ->
            "کلید سرویس هوش مصنوعی تنظیم نشده است. لطفاً با پشتیبانی تماس بگیرید."
        is AppError.InsufficientCredits ->
            "اعتبار حساب شما کافی نیست. لطفاً از بخش خرید اعتبار، بسته‌ی جدیدی تهیه کنید."
        is AppError.FileTooLarge ->
            "حجم فایل بیش از حد مجاز (حدود ${error.maxSizeMb} مگابایت) است. لطفاً فایل کوچک‌تری انتخاب کنید."
        is AppError.UnsupportedFormat ->
            "فرمت فایل (${error.format}) پشتیبانی نمی‌شود."
        is AppError.InvalidAiResponse ->
            "پاسخ دریافتی از هوش مصنوعی نامعتبر یا ناقص بود. لطفاً دوباره تلاش کنید."
        is AppError.ApiError ->
            "خطایی در ارتباط با سرویس هوش مصنوعی رخ داد" +
                (error.code?.let { " (کد $it)" } ?: "") + ". لطفاً بعداً دوباره تلاش کنید."
        is AppError.Unknown ->
            "خطای غیرمنتظره‌ای رخ داد. لطفاً دوباره تلاش کنید."
    }
}
