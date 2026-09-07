package com.smartmechanic.ai.data.model

import com.google.gson.annotations.SerializedName

/**
 * ساختار استاندارد خروجی تحلیل هوش مصنوعی؛ صرف‌نظر از نوع ورودی
 * (متن/عکس/صدا/ویدئو) همیشه به این مدل تبدیل می‌شود.
 */
data class DiagnosisResult(
    @SerializedName("summary") val summary: String,
    @SerializedName("possibleCauses") val possibleCauses: List<PossibleCause>,
    @SerializedName("urgency") val urgency: UrgencyLevel,
    @SerializedName("recommendations") val recommendations: List<String>,
    @SerializedName("safetyWarning") val safetyWarning: String? = null,
    @SerializedName("followUpQuestions") val followUpQuestions: List<String> = emptyList(),
    @SerializedName("mechanicNeeded") val mechanicNeeded: Boolean = false,
    @SerializedName("lowQualityInputNote") val lowQualityInputNote: String? = null
)

data class PossibleCause(
    @SerializedName("title") val title: String,
    @SerializedName("likelihood") val likelihood: Likelihood
)

enum class Likelihood { LOW, MEDIUM, HIGH }

enum class UrgencyLevel {
    @SerializedName("NORMAL") NORMAL,
    @SerializedName("NEEDS_CHECK") NEEDS_CHECK,
    @SerializedName("SOON") SOON,
    @SerializedName("SERIOUS") SERIOUS,
    @SerializedName("DANGER") DANGER
}

/** موارد ایمنیِ همیشه‌بحرانی که باید هشدار واضح نمایش دهند، صرف‌نظر از پاسخ مدل. */
object CriticalSafetyKeywords {
    val KEYWORDS = listOf(
        "ترمز", "فرمان", "داغ شدن شدید", "دود شدید", "آتش‌سوزی",
        "نشت شدید سوخت", "نشت روغن شدید", "خرابی احتمالی چرخ", "چراغ هشدار"
    )

    fun containsCriticalTerm(text: String): Boolean =
        KEYWORDS.any { text.contains(it) }
}
