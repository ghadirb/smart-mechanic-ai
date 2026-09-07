package com.smartmechanic.ai.data.remote

import com.google.gson.annotations.SerializedName

/**
 * قرارداد ساده JSON بین اپ اندروید و Web App گوگل اپس‌اسکریپت
 * (رجوع کنید به backend/apps-script/Code.gs).
 */
data class ProxyRequest(
    @SerializedName("action") val action: String, // "text" | "image" | "audio"
    @SerializedName("prompt") val prompt: String,
    @SerializedName("imageBase64") val imageBase64: String? = null,
    @SerializedName("imageMimeType") val imageMimeType: String? = null,
    @SerializedName("audioBase64") val audioBase64: String? = null,
    @SerializedName("audioMimeType") val audioMimeType: String? = null,
    @SerializedName("appSecret") val appSecret: String? = null
)

data class ProxyResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("raw") val raw: String? = null,
    @SerializedName("error") val error: String? = null
)
