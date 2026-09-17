package com.smartmechanic.ai.data.remote

import com.google.gson.annotations.SerializedName

/**
 * قرارداد JSON بین اپ اندروید و Cloudflare Worker
 * (رجوع کنید به backend/cloudflare/worker/src/index.ts).
 * هویت با هدر Authorization (توکن صادرشده توسط POST /api/register، نه appSecret
 * و نه Firebase ID Token) تایید می‌شود -- رجوع کنید به BackendSession.kt.
 */
data class CloudflareDiagnoseRequest(
    @SerializedName("action") val action: String, // "text" | "image" | "audio" | "video"
    @SerializedName("prompt") val prompt: String,
    @SerializedName("imageBase64") val imageBase64: String? = null,
    @SerializedName("imageMimeType") val imageMimeType: String? = null,
    @SerializedName("audioBase64") val audioBase64: String? = null,
    @SerializedName("audioMimeType") val audioMimeType: String? = null,
    @SerializedName("videoBase64") val videoBase64: String? = null,
    @SerializedName("videoMimeType") val videoMimeType: String? = null
)

data class CloudflareDiagnoseResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("raw") val raw: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("creditsCharged") val creditsCharged: Int? = null
)

data class CreditPackageDto(
    @SerializedName("productId") val productId: String,
    @SerializedName("credits") val credits: Int
)

data class CreditsResponse(
    @SerializedName("balance") val balance: Int,
    @SerializedName("packages") val packages: List<CreditPackageDto> = emptyList(),
    @SerializedName("costs") val costs: Map<String, Int> = emptyMap()
)

/** پاسخ POST /api/payments/intent -- پیش از فراخوانی SDK مایکت گرفته می‌شود. */
data class PaymentIntentResponse(
    @SerializedName("productId") val productId: String,
    @SerializedName("developerPayload") val developerPayload: String,
    @SerializedName("expiresInSeconds") val expiresInSeconds: Int = 0
)

/** پاسخ POST /api/payments/verify -- تایید نهایی server-to-server خرید مایکت. */
data class PaymentVerifyResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("duplicate") val duplicate: Boolean = false,
    @SerializedName("creditsGranted") val creditsGranted: Int? = null,
    @SerializedName("error") val error: String? = null
)
