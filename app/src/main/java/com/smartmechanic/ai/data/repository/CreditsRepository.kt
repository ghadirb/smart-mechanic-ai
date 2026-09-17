package com.smartmechanic.ai.data.repository

import android.content.Context
import com.google.gson.Gson
import com.smartmechanic.ai.auth.BackendSession
import com.smartmechanic.ai.config.AIConfig
import com.smartmechanic.ai.data.remote.CreditsResponse
import com.smartmechanic.ai.data.remote.PaymentIntentResponse
import com.smartmechanic.ai.data.remote.PaymentVerifyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class CreditsRepository(private val context: Context) {
    private val session = BackendSession(context)
    private val gson = Gson()
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    suspend fun load(): Result<CreditsResponse> = withContext(Dispatchers.IO) {
        if (!AIConfig.isBackendConfigured()) return@withContext Result.failure(IllegalStateException("BACKEND_NOT_CONFIGURED"))
        val token = session.currentToken() ?: return@withContext Result.failure(IllegalStateException("REGISTER_FAILED"))
        runCatching {
            val request = Request.Builder().url(AIConfig.BACKEND_URL.trimEnd('/') + "/api/credits")
                .header("Authorization", "Bearer $token").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP_${response.code}")
                gson.fromJson(response.body?.string(), CreditsResponse::class.java)
            }
        }
    }

    /** گام ۱ خرید: یک developerPayload یک‌بارمصرف از Worker می‌گیرد که بعداً همراه
     * توکن خرید مایکت برای تایید server-to-server برگردانده می‌شود. */
    suspend fun createPaymentIntent(productId: String): Result<PaymentIntentResponse> = withContext(Dispatchers.IO) {
        if (!AIConfig.isBackendConfigured()) return@withContext Result.failure(IllegalStateException("BACKEND_NOT_CONFIGURED"))
        val token = session.currentToken() ?: return@withContext Result.failure(IllegalStateException("REGISTER_FAILED"))
        runCatching {
            val body = gson.toJson(mapOf("productId" to productId))
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(AIConfig.BACKEND_URL.trimEnd('/') + "/api/payments/intent")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string()
                if (!response.isSuccessful || raw.isNullOrBlank()) error("HTTP_${response.code}")
                gson.fromJson(raw, PaymentIntentResponse::class.java)
            }
        }
    }

    /** گام ۲ خرید: بعد از موفقیت SDK مایکت، توکن خرید را برای تایید واقعی
     * و افزایش اعتبار به Worker می‌فرستد (کلاینت هرگز خودش credits را زیاد نمی‌کند). */
    suspend fun verifyPurchase(tokenId: String, developerPayload: String): Result<PaymentVerifyResponse> = withContext(Dispatchers.IO) {
        if (!AIConfig.isBackendConfigured()) return@withContext Result.failure(IllegalStateException("BACKEND_NOT_CONFIGURED"))
        val token = session.currentToken() ?: return@withContext Result.failure(IllegalStateException("REGISTER_FAILED"))
        runCatching {
            val body = gson.toJson(mapOf("tokenId" to tokenId, "developerPayload" to developerPayload))
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(AIConfig.BACKEND_URL.trimEnd('/') + "/api/payments/verify")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string()
                val parsed = raw?.let { runCatching { gson.fromJson(it, PaymentVerifyResponse::class.java) }.getOrNull() }
                parsed ?: error("HTTP_${response.code}")
            }
        }
    }
}
