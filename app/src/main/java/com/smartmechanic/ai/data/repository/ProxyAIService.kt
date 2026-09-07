package com.smartmechanic.ai.data.repository

import android.content.Context
import com.google.gson.Gson
import com.smartmechanic.ai.config.AIConfig
import com.smartmechanic.ai.data.model.Car
import com.smartmechanic.ai.data.model.DiagnosisResult
import com.smartmechanic.ai.data.remote.ProxyRequest
import com.smartmechanic.ai.data.remote.ProxyResponse
import com.smartmechanic.ai.util.AppError
import com.smartmechanic.ai.util.AppResult
import com.smartmechanic.ai.util.FileUtils
import com.smartmechanic.ai.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * پیاده‌سازی جایگزین AIService که به‌جای تماس مستقیم با Gemini، از یک Web App گوگل
 * اپس‌اسکریپت (پروکسی رایگان) استفاده می‌کند؛ کلید واقعی gapgpt.app هرگز داخل این اپ
 * قرار نمی‌گیرد و فقط سمت سرور (در Script Properties) نگه‌داری می‌شود.
 *
 * محدودیت شناخته‌شده: طبق مستندات فعلی gapgpt.app، فقط چت متنی (با پشتیبانی احتمالی
 * تصویر) و تبدیل گفتار به متن (Whisper) مستند شده‌اند؛ تحلیل ویدئو در این پیاده‌سازی
 * پشتیبانی نمی‌شود (AppError.UnsupportedFormat برگردانده می‌شود).
 */
class ProxyAIService(
    private val context: Context,
    private val gson: Gson = Gson()
) : AIService {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun analyzeText(car: Car?, userDescription: String): AppResult<DiagnosisResult> {
        val prompt = PromptBuilder.buildTextPrompt(car, userDescription)
        return callProxy(ProxyRequest(action = "text", prompt = prompt, appSecret = secretOrNull()))
    }

    override suspend fun analyzeImage(car: Car?, userNote: String?, imageFile: File): AppResult<DiagnosisResult> {
        if (!FileUtils.isSupportedImage(imageFile)) return AppResult.Error(AppError.UnsupportedFormat(imageFile.extension))
        FileUtils.validateImage(imageFile)?.let { return AppResult.Error(it) }

        val prompt = PromptBuilder.buildMediaPrompt(car, userNote, "عکس")
        return callProxy(
            ProxyRequest(
                action = "image",
                prompt = prompt,
                imageBase64 = FileUtils.encodeToBase64(imageFile),
                imageMimeType = FileUtils.mimeTypeForImage(imageFile),
                appSecret = secretOrNull()
            )
        )
    }

    override suspend fun analyzeAudio(car: Car?, userNote: String?, audioFile: File): AppResult<DiagnosisResult> {
        if (!FileUtils.isSupportedAudio(audioFile)) return AppResult.Error(AppError.UnsupportedFormat(audioFile.extension))
        FileUtils.validateAudio(audioFile)?.let { return AppResult.Error(it) }

        val prompt = PromptBuilder.buildMediaPrompt(car, userNote, "فایل صوتی موتور (پیاده‌سازی‌شده به متن)")
        return callProxy(
            ProxyRequest(
                action = "audio",
                prompt = prompt,
                audioBase64 = FileUtils.encodeToBase64(audioFile),
                audioMimeType = FileUtils.mimeTypeForAudio(audioFile),
                appSecret = secretOrNull()
            )
        )
    }

    override suspend fun analyzeVideo(car: Car?, userNote: String?, videoFile: File): AppResult<DiagnosisResult> {
        // gapgpt.app طبق مستندات فعلی، endpoint اختصاصی برای تحلیل ویدئو ندارد.
        return AppResult.Error(AppError.UnsupportedFormat("video (پشتیبانی‌نشده در پیکربندی فعلی پروکسی)"))
    }

    private fun secretOrNull(): String? = AIConfig.APP_SECRET.ifBlank { null }

    private suspend fun callProxy(request: ProxyRequest): AppResult<DiagnosisResult> {
        if (!AIConfig.isProxyConfigured()) {
            return AppResult.Error(AppError.MissingApiKey)
        }
        if (!NetworkMonitor.isOnline(context)) {
            return AppResult.Error(AppError.NoInternet)
        }

        val bodyJson = gson.toJson(request)
        val httpRequest = Request.Builder()
            .url(AIConfig.PROXY_URL)
            .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return try {
            withContext(Dispatchers.IO) {
                withTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS * 1000) {
                    client.newCall(httpRequest).execute().use { response ->
                        val bodyString = response.body?.string()
                        if (!response.isSuccessful || bodyString.isNullOrBlank()) {
                            return@withTimeout AppResult.Error(AppError.ApiError(code = response.code, apiMessage = bodyString))
                        }

                        val proxyResponse = try {
                            gson.fromJson(bodyString, ProxyResponse::class.java)
                        } catch (e: Exception) {
                            null
                        }

                        if (proxyResponse == null || !proxyResponse.success || proxyResponse.raw.isNullOrBlank()) {
                            return@withTimeout AppResult.Error(AppError.ApiError(code = null, apiMessage = proxyResponse?.error))
                        }

                        DiagnosisJsonParser.parse(proxyResponse.raw, gson)
                    }
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
