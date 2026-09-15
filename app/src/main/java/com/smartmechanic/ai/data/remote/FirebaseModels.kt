package com.smartmechanic.ai.data.remote

import com.google.gson.annotations.SerializedName

/**
 * قرارداد JSON بین اپ اندروید و Cloud Function اعتباری Firebase
 * (رجوع کنید به backend/firebase/functions/src/index.ts).
 *
 * برخلاف ProxyRequest (اپس‌اسکریپت قدیمی)، اینجا appSecret وجود ندارد؛
 * هویت کاربر فقط از طریق هدر Authorization (ID Token فایربیس) تایید می‌شود.
 */
data class FirebaseDiagnoseRequest(
    @SerializedName("action") val action: String, // "text" | "image" | "audio" | "video"
    @SerializedName("prompt") val prompt: String,
    @SerializedName("imageBase64") val imageBase64: String? = null,
    @SerializedName("imageMimeType") val imageMimeType: String? = null,
    @SerializedName("audioBase64") val audioBase64: String? = null,
    @SerializedName("audioMimeType") val audioMimeType: String? = null,
    @SerializedName("videoBase64") val videoBase64: String? = null,
    @SerializedName("videoMimeType") val videoMimeType: String? = null
)

data class FirebaseDiagnoseResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("raw") val raw: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("creditsCharged") val creditsCharged: Int? = null
)

data class FirebaseCreditsResponse(
    @SerializedName("balance") val balance: Int = 0,
    @SerializedName("packages") val packages: List<FirebaseCreditPackage> = emptyList(),
    @SerializedName("costs") val costs: Map<String, Int> = emptyMap()
)

data class FirebaseCreditPackage(
    @SerializedName("productId") val productId: String,
    @SerializedName("credits") val credits: Int
)
