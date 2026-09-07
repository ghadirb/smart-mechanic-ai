package com.smartmechanic.ai.data.remote

import com.google.gson.annotations.SerializedName

/**
 * مدل‌های درخواست/پاسخ سازگار با Gemini generateContent API.
 * https://ai.google.dev/api/generate-content
 *
 * این لایه کاملاً مجزا از بقیه برنامه است؛ در صورت تغییر ارائه‌دهنده مدل AI
 * فقط همین فایل و GeminiApiService/GeminiAIService باید تغییر کنند.
 */

data class GeminiRequest(
    @SerializedName("contents") val contents: List<GeminiContent>,
    @SerializedName("generationConfig") val generationConfig: GenerationConfig? = null,
    @SerializedName("systemInstruction") val systemInstruction: GeminiContent? = null
)

data class GeminiContent(
    @SerializedName("role") val role: String? = null,
    @SerializedName("parts") val parts: List<GeminiPart>
)

data class GeminiPart(
    @SerializedName("text") val text: String? = null,
    @SerializedName("inlineData") val inlineData: GeminiInlineData? = null
)

data class GeminiInlineData(
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("data") val data: String // Base64
)

data class GenerationConfig(
    @SerializedName("temperature") val temperature: Double = 0.4,
    @SerializedName("responseMimeType") val responseMimeType: String = "application/json"
)

data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<GeminiCandidate>? = null,
    @SerializedName("promptFeedback") val promptFeedback: PromptFeedback? = null,
    @SerializedName("error") val error: GeminiApiError? = null
)

data class GeminiCandidate(
    @SerializedName("content") val content: GeminiContent?,
    @SerializedName("finishReason") val finishReason: String? = null
)

data class PromptFeedback(
    @SerializedName("blockReason") val blockReason: String? = null
)

data class GeminiApiError(
    @SerializedName("code") val code: Int?,
    @SerializedName("message") val message: String?,
    @SerializedName("status") val status: String?
)
