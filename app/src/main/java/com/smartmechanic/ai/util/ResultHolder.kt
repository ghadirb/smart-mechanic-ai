package com.smartmechanic.ai.util

import com.smartmechanic.ai.data.model.DiagnosisResult

/**
 * نگهدارنده موقت آخرین نتیجه تشخیص برای انتقال بین صفحه تحلیل و صفحه نتیجه،
 * بدون نیاز به Serialize کردن مدل پیچیده در آرگومان‌های Navigation.
 * این شیء فقط برای طول عمر یک نمایش استفاده می‌شود و در دیتابیس ذخیره نمی‌شود؛
 * ذخیره‌سازی دائمی سوابق از طریق DiagnosisRepository انجام می‌شود.
 */
object ResultHolder {
    var latestResult: DiagnosisResult? = null
        private set

    fun set(result: DiagnosisResult) {
        latestResult = result
    }

    fun clear() {
        latestResult = null
    }
}
