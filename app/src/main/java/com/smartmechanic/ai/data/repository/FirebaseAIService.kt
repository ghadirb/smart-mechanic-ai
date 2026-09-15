package com.smartmechanic.ai.data.repository

import android.content.Context
import com.google.gson.Gson
import com.smartmechanic.ai.auth.FirebaseSession
import com.smartmechanic.ai.config.AIConfig
import com.smartmechanic.ai.data.model.Car
import com.smartmechanic.ai.data.model.DiagnosisResult
import com.smartmechanic.ai.data.remote.FirebaseDiagnoseRequest
import com.smartmechanic.ai.data.remote.FirebaseDiagnoseResponse
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
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * پیاده‌سازی AIService که به Cloud Function اعتباری Firebase وصل می‌شود
 * (رجوع کنید به backend/firebase). این تنها پیاده‌سازی است که:
 *  - هویت کاربر را با Firebase Anonymous Auth تایید می‌کند (نه appSecret ساده)،
 *  - اعتبار (credit) هر کاربر را سمت سرور کنترل می‌کند تا مصرف غیرمجاز ممکن نباشد،
 *  - و در صورت خطای ارائه‌دهنده هوش مصنوعی، اعتبار کسرشده را خودکار برمی‌گرداند
 *    (رجوع کنید به منطق transaction در functions/src/index.ts).
 */
class FirebaseAIService(
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
        return callFunction(FirebaseDiagnoseRequest(action = "text", prompt = prompt))
    }

    override suspend fun analyzeImage(car: Car?, userNote: String?, imageFile: File): AppResult<DiagnosisResult> {
        if (!FileUtils.isSupportedImage(imageFile)) return AppResult.Error(AppError.UnsupportedFormat(imageFile.extension))
        FileUtils.validateImage(imageFile)?.let { return AppResult.Error(it) }

        val prompt = PromptBuilder.buildMediaPrompt(car, userNote, "عکس")
        return callFunction(
            FirebaseDiagnoseRequest(
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
        return callFunction(
            FirebaseDiagnoseRequest(
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
        return callFunction(
            FirebaseDiagnoseRequest(
                action = "video",
                prompt = prompt,
                videoBase64 = FileUtils.encodeToBase64(videoFile),
                videoMimeType = FileUtils.mimeTypeForVideo(videoFile)
            )
        )
    }

    private suspend fun callFunction(request: FirebaseDiagnoseRequest): AppResult<DiagnosisResult> {
        if (!AIConfig.isFirebaseBackendConfigured()) {
            return AppResult.Error(AppError.MissingApiKey)
        }
        if (!NetworkMonitor.isOnline(context)) {
            return AppResult.Error(AppError.NoInternet)
        }

        val idToken = FirebaseSession.currentIdToken()
            ?: return AppResult.Error(AppError.ApiError(code = 401, apiMessage = "UNAUTHENTICATED"))

        val bodyJson = gson.toJson(request)
        val httpRequest = Request.Builder()
            .url(AIConfig.FUNCTION_BASE_URL.trimEnd('/') + "/diagnose")
            .header("Authorization", "Bearer $idToken")
            // هر تلاش مجدد (retry) شبکه یک کلید یکتای جدید می‌گیرد؛ سرور از این کلید
            // برای جلوگیری از کسر دوباره‌ی اعتبار در صورت تکرار همان درخواست استفاده می‌کند.
            .header("X-Idempotency-Key", UUID.randomUUID().toString())
            .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return try {
            withContext(Dispatchers.IO) {
                withTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS * 1000) {
                    client.newCall(httpRequest).execute().use { response ->
                        val bodyString = response.body?.string()
                        val parsed = bodyString?.let {
                            try {
                                gson.fromJson(it, FirebaseDiagnoseResponse::class.java)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        when {
                            response.code == 402 -> return@withTimeout AppResult.Error(AppError.InsufficientCredits)
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
}
