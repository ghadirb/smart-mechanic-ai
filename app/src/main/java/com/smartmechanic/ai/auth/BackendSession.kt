package com.smartmechanic.ai.auth

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.smartmechanic.ai.config.AIConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * هویت این نصب برای بک‌اند اعتباری Cloudflare: به‌جای یک user_id ساده که کلاینت
 * انتخاب می‌کند (که تغییر آن روی دستگاه کاربر می‌توانست موجودی کاربر دیگری را
 * در دسترس بگذارد)، این کلاس یک‌بار POST /api/register می‌زند، توکن تصادفی
 * صادرشده توسط Worker را در SharedPreferences محلی ذخیره می‌کند، و همان توکن
 * را برای همه درخواست‌های بعدی از حافظه یا دیسک برمی‌گرداند.
 */
class BackendSession(private val context: Context) {

    private data class RegisterResponse(
        @SerializedName("userId") val userId: String,
        @SerializedName("token") val token: String,
        @SerializedName("credits") val credits: Int
    )

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val mutex = Mutex()
    private val gson = Gson()
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AIConfig.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** برمی‌گرداند: توکن معتبر برای هدر Authorization، یا null اگر ثبت‌نام هم شکست بخورد. */
    suspend fun currentToken(): String? = mutex.withLock {
        val cached = prefs.getString(KEY_TOKEN, null)
        if (!cached.isNullOrBlank()) return@withLock cached
        register()
    }

    /** فقط برای نمایش موجودی محلی سریع؛ همیشه با GET /api/credits دوباره تایید شود. */
    fun cachedUserId(): String? = prefs.getString(KEY_USER_ID, null)

    private suspend fun register(): String? = withContext(Dispatchers.IO) {
        if (!AIConfig.isBackendConfigured()) return@withContext null
        try {
            val request = Request.Builder()
                .url(AIConfig.BACKEND_URL.trimEnd('/') + "/api/register")
                .post(ByteArray(0).toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) return@withContext null
                val parsed = gson.fromJson(bodyString, RegisterResponse::class.java)
                prefs.edit()
                    .putString(KEY_USER_ID, parsed.userId)
                    .putString(KEY_TOKEN, parsed.token)
                    .apply()
                parsed.token
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "backend_session"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_TOKEN = "token"
    }
}
