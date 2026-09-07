package com.smartmechanic.ai.util

/** Wrapper استاندارد برای همه نتایج عملیات async (شبکه/فایل/دیتابیس). */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val error: AppError) : AppResult<Nothing>()
    data object Loading : AppResult<Nothing>()
}

/**
 * انواع خطای قابل‌پیش‌بینی برنامه. هر کدام به یک پیام فارسی مشخص در UI نگاشته می‌شود
 * (رجوع کنید به ErrorMessageMapper) تا برنامه هرگز Crash نکند و کاربر همیشه
 * پیام قابل‌فهم دریافت کند.
 */
sealed class AppError : Exception() {
    data object NoInternet : AppError()
    data object Timeout : AppError()
    data object MissingApiKey : AppError()
    data class FileTooLarge(val maxSizeMb: Int) : AppError()
    data class UnsupportedFormat(val format: String) : AppError()
    data class InvalidAiResponse(val raw: String? = null) : AppError()
    data class ApiError(val code: Int?, val message: String?) : AppError()
    data class Unknown(val cause: Throwable) : AppError()
}
