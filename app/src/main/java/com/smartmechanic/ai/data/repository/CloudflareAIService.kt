package com.smartmechanic.ai.data.repository

import android.content.Context
import com.google.gson.Gson
import com.smartmechanic.ai.auth.BackendSession
import com.smartmechanic.ai.config.AIConfig
import com.smartmechanic.ai.data.model.Car
import com.smartmechanic.ai.data.model.DiagnosisResult
import com.smartmechanic.ai.data.remote.CloudflareDiagnoseRequest
import com.smartmechanic.ai.data.remote.CloudflareDiagnoseResponse
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
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * پیاده‌سازی AIService که به بک‌اند اعتباری Cloudflare Worker وصل می‌شود --
 * جایگزین اصلی FirebaseAIService (که legacy باقی مانده، رجوع کنید به آن فایل).
 * تفاوت اصلی با Firebase: هویت کاربر با توکنی که خودِ همین Worker صادر می‌کند
 * تایید می‌شود (BackendSession)، نه با Firebase Anonymous Auth.
 */
class CloudflareAIService(
    private val context: Context,
    private val gson: Gson = Gson()
) : AIService {

    private val session by lazy { BackendSession(context) }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun analyzeText(car: Car?, userDescription: String): AppResult<DiagnosisResult> {
        val prompt = PromptBuilder.buildTextPrompt(car, userDescription)
        return callBackend(CloudflareDiagnoseRequest(action = "text", prompt = prompt))
    }

    override suspend fun analyzeImage(car: Car?, userNote: String?, imageFile: File): AppResult<DiagnosisResult> {
        if (!FileUtils.isSupportedImage(imageFile)) return AppResult.Error(AppError.UnsupportedFormat(imageFile.extension))
        FileUtils.validateImage(imageFile)?.let { return AppResult.Error(it) }

        val prompt = PromptBuilder.buildMediaPrompt(car, userNote, "عکس")
        return callBackend(
            CloudflareDiagnoseRequest(
                action = "image",
                prompt = prompt,
                imageBase64 = FileUtils.encodeToBase64(imageFile),
                imageMimeType = FileUtils.mimeTypeForImage(imageFile)
            )
        )
    }

    override suspend fun analyzeAudio(car: Car?, userNote: String?, audioFile: File): AppResult<DiagnosisResult> {
        if (!FileUtils.isSupportedAudio(audioFile)) return AppResult.Error(AppError.UnsupportedFormat(audioFile.extension))
        FileUtils.validateAudio(audioFile)?.let { return AppResult.Error(it) }

        val prompt = PromptBuilder.buildMediaPrompt(car, userNote, "فایل صوتی موتور (پیاده‌سازی‌شده به متن)")
        return callBackend(
            CloudflareDiagnoseRequest(
                action = "audio",
                prompt = prompt,
                audioBase64 = FileUtils.encodeToBase64(audioFile),
                audioMimeType = FileUtils.mimeTypeForAudio(audioFile)
            )
        )
    }

    override suspend fun analyzeVideo(car: Car?, userNote: String?, videoFile: File): AppResult<DiagnosisResult> {
        if (!FileUtils.isSupportedVideo(videoFile)) return AppResult.Error(AppError.UnsupportedFormat(videoFile.extension))
        FileUtils.validateVideo(videoFile)?.let { return AppResult.Error(it) }

        val prompt = PromptBuilder.buildMediaPrompt(car, userNote, "ویدئوی کوتاه خودرو")
        return callBackend(
            CloudflareDiagnoseRequest(
                action = "video",
                prompt = prompt,
                videoBase64 = FileUtils.encodeToBase64(videoFile),
                videoMimeType = FileUtils.mimeTypeForVideo(videoFile)
            )
        )
    }

    private suspend fun callBackend(request: CloudflareDiagnoseRequest): AppResult<DiagnosisResult> {
        if (!AIConfig.isBackendConfigured()) return AppResult.Error(AppError.MissingApiKey)
        if (!NetworkMonitor.isOnline(context)) return AppResult.Error(AppError.NoInternet)

        val token = session.currentToken()
            ?: return AppResult.Error(AppError.ApiError(code = 401, apiMessage = "REGISTER_FAILED"))

        val bodyJson = gson.toJson(request)
        // کلید idempotency باید برای «همان تلاش منطقی» ثابت بماند، نه برای هر
        // HTTP attempt. اگر پاسخ Worker به دلیل قطع اینترنت/timeout به اپ
        // نرسد ولی اعتبار قبلاً کسر و AI اجرا شده باشد، و کاربر دوباره دکمهٔ
        // «ارسال برای تحلیل» را با همان فایل/متن/خودرو بزند، باید همان کلید
        // تولید شود تا Worker آن را idempotent replay کند، نه یک کسر جدید.
        // هش SHA-256 بدنهٔ دقیق درخواست همین را تضمین می‌کند بدون نیاز به نگه‌داشتن
        // state اضافه بین تلاش‌ها؛ اگر کاربر واقعاً چیزی را عوض کند (فایل/متن/خودرو)،
        // بدنه فرق می‌کند و کلید هم به‌طور طبیعی جدید می‌شود.
        val idempotencyKey = stableIdempotencyKey(bodyJson)
        val httpRequest = Request.Builder()
            .url(AIConfig.BACKEND_URL.trimEnd('/') + "/api/diagnose")
            .header("Authorization", "Bearer $token")
            .header("X-Idempotency-Key", idempotencyKey)
            .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return try {
            withContext(Dispatchers.IO) {
                withTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS * 1000) {
                    client.newCall(httpRequest).execute().use { response ->
                        val bodyString = response.body?.string()
                        val parsed = bodyString?.let {
                            try {
                                gson.fromJson(it, CloudflareDiagnoseResponse::class.java)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        when {
                            response.code == 402 -> return@withTimeout AppResult.Error(AppError.InsufficientCredits)
                            response.code == 429 -> return@withTimeout AppResult.Error(
                                AppError.ApiError(code = 429, apiMessage = "RATE_LIMITED")
                            )
                            !response.isSuccessful -> return@withTimeout AppResult.Error(
                                AppError.ApiError(code = response.code, apiMessage = parsed?.error ?: bodyString)
                            )
                            parsed == null || !parsed.success || parsed.raw.isNullOrBlank() -> return@withTimeout AppResult.Error(
                                AppError.ApiError(code = null, apiMessage = parsed?.error)
                            )
                            else -> DiagnosisJsonParser.parse(parsed.raw, gson)
                        }
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

    private fun stableIdempotencyKey(bodyJson: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bodyJson.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
