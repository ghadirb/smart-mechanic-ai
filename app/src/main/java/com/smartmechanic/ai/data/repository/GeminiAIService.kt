package com.smartmechanic.ai.data.repository

import android.content.Context
import com.google.gson.Gson
import com.smartmechanic.ai.config.AIConfig
import com.smartmechanic.ai.config.SystemPrompt
import com.smartmechanic.ai.data.model.Car
import com.smartmechanic.ai.data.model.DiagnosisResult
import com.smartmechanic.ai.data.remote.GeminiApiService
import com.smartmechanic.ai.data.remote.GeminiContent
import com.smartmechanic.ai.data.remote.GeminiInlineData
import com.smartmechanic.ai.data.remote.GeminiPart
import com.smartmechanic.ai.data.remote.GeminiRequest
import com.smartmechanic.ai.data.remote.GenerationConfig
import com.smartmechanic.ai.util.AppError
import com.smartmechanic.ai.util.AppResult
import com.smartmechanic.ai.util.FileUtils
import com.smartmechanic.ai.util.NetworkMonitor
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException

/**
 * پیاده‌سازی AIService با استفاده از Google Gemini API.
 * تعویض به یک ارائه‌دهنده دیگر در آینده فقط نیازمند یک کلاس جایگزین برای همین Interface است.
 */
class GeminiAIService(
    private val context: Context,
    private val api: GeminiApiService = com.smartmechanic.ai.data.remote.RetrofitClient.geminiApiService,
    private val gson: Gson = Gson()
) : AIService {

    override suspend fun analyzeText(car: Car?, userDescription: String): AppResult<DiagnosisResult> {
        val prompt = PromptBuilder.buildTextPrompt(car, userDescription)
        return callModel(model = AIConfig.LIGHT_MODEL_NAME, textPrompt = prompt, mediaParts = emptyList())
    }

    override suspend fun analyzeImage(car: Car?, userNote: String?, imageFile: File): AppResult<DiagnosisResult> {
        if (!FileUtils.isSupportedImage(imageFile)) {
            return AppResult.Error(AppError.UnsupportedFormat(imageFile.extension))
        }
        FileUtils.validateImage(imageFile)?.let { return AppResult.Error(it) }

        val prompt = PromptBuilder.buildMediaPrompt(car, userNote, "عکس")
        val part = GeminiPart(
            inlineData = GeminiInlineData(
                mimeType = FileUtils.mimeTypeForImage(imageFile),
                data = FileUtils.encodeToBase64(imageFile)
            )
        )
        return callModel(model = AIConfig.HEAVY_MODEL_NAME, textPrompt = prompt, mediaParts = listOf(part))
    }

    override suspend fun analyzeAudio(car: Car?, userNote: String?, audioFile: File): AppResult<DiagnosisResult> {
        if (!FileUtils.isSupportedAudio(audioFile)) {
            return AppResult.Error(AppError.UnsupportedFormat(audioFile.extension))
        }
        FileUtils.validateAudio(audioFile)?.let { return AppResult.Error(it) }

        val prompt = PromptBuilder.buildMediaPrompt(car, userNote, "فایل صوتی موتور")
        val part = GeminiPart(
            inlineData = GeminiInlineData(
                mimeType = FileUtils.mimeTypeForAudio(audioFile),
                data = FileUtils.encodeToBase64(audioFile)
            )
        )
        return callModel(model = AIConfig.HEAVY_MODEL_NAME, textPrompt = prompt, mediaParts = listOf(part))
    }

    override suspend fun analyzeVideo(car: Car?, userNote: String?, videoFile: File): AppResult<DiagnosisResult> {
        if (!FileUtils.isSupportedVideo(videoFile)) {
            return AppResult.Error(AppError.UnsupportedFormat(videoFile.extension))
        }
        FileUtils.validateVideo(videoFile)?.let { return AppResult.Error(it) }

        val prompt = PromptBuilder.buildMediaPrompt(car, userNote, "ویدئوی کوتاه خودرو")
        val part = GeminiPart(
            inlineData = GeminiInlineData(
                mimeType = FileUtils.mimeTypeForVideo(videoFile),
                data = FileUtils.encodeToBase64(videoFile)
            )
        )
        return callModel(model = AIConfig.HEAVY_MODEL_NAME, textPrompt = prompt, mediaParts = listOf(part))
    }

    // -------------------------------------------------------------------
    // منطق مشترک فراخوانی مدل + تبدیل پاسخ به DiagnosisResult + مدیریت خطا
    // -------------------------------------------------------------------
    private suspend fun callModel(
        model: String,
        textPrompt: String,
        mediaParts: List<GeminiPart>
    ): AppResult<DiagnosisResult> {
        if (!AIConfig.isApiKeyConfigured()) {
            return AppResult.Error(AppError.MissingApiKey)
        }
        if (!NetworkMonitor.isOnline(context)) {
            return AppResult.Error(AppError.NoInternet)
        }

        val parts = mutableListOf(GeminiPart(text = textPrompt))
        parts.addAll(mediaParts)

        val request = GeminiRequest(
            contents = listOf(GeminiContent(role = "user", parts = parts)),
            generationConfig = GenerationConfig(),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = SystemPrompt.DIAGNOSIS_SYSTEM_PROMPT)))
        )

        return try {
            withContext(Dispatchers.IO) {
                withTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS * 1000) {
                    val response = api.generateContent(model = model, apiKey = AIConfig.API_KEY, request = request)

                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        return@withTimeout AppResult.Error(
                            AppError.ApiError(code = response.code(), message = errorBody)
                        )
                    }

                    val body = response.body()
                    if (body?.promptFeedback?.blockReason != null) {
                        return@withTimeout AppResult.Error(AppError.InvalidAiResponse("blocked"))
                    }

                    val rawText = body?.candidates
                        ?.firstOrNull()
                        ?.content
                        ?.parts
                        ?.firstOrNull { it.text != null }
                        ?.text

                    if (rawText.isNullOrBlank()) {
                        return@withTimeout AppResult.Error(AppError.InvalidAiResponse())
                    }

                    DiagnosisJsonParser.parse(rawText, gson)
                }
            }
        } catch (e: TimeoutCancellationException) {
            AppResult.Error(AppError.Timeout)
        } catch (e: IOException) {
            AppResult.Error(AppError.NoInternet)
        } catch (e: Exception) {
            AppResult.Error(AppError.Unknown(e))
        }
    }
}
