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
