package com.smartmechanic.ai.auth

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
 * انتخاب می‌کند، این کلاس یک‌بار POST /api/register می‌زند، توکن تصادفی
 * صادرشده توسط Worker را رمزنگاری‌شده (Android Keystore، از طریق
 * EncryptedSharedPreferences) روی دیسک نگه می‌دارد، و همان توکن را برای همه‌ی
 * درخواست‌های بعدی برمی‌گرداند.
 *
 * توجه امنیتی مهم: اگر Worker به یک درخواست ۴۰۱ برگرداند، این کلاس هرگز
 * خودش را خودکار پاک/register نمی‌کند -- register خودکار یعنی یک کاربر و ۵
 * اعتبار رایگان جدید، دقیقاً همان سوءاستفاده‌ای که باید جلویش گرفته شود.
 * پاک‌کردن نشست فقط با [clearSessionForManualReset] و فقط با اقدام صریح
 * کاربر (مثلاً دکمه‌ی «بازنشانی نشست» در UI) انجام می‌شود.
 */
class BackendSession(private val context: Context) {

    private data class RegisterResponse(
        @SerializedName("userId") val userId: String,
        @SerializedName("token") val token: String,
        @SerializedName("credits") val credits: Int
    )

    // نسخه‌ی قدیمی (SharedPreferences ساده و رمزنگاری‌نشده) فقط برای مهاجرت
    // یک‌باره‌ی نصب‌هایی که قبل از افزودن EncryptedSharedPreferences آپدیت
    // کرده‌اند نگه داشته می‌شود؛ بعد از مهاجرت پاک می‌شود.
    private val legacyPrefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encrypted = EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        migrateLegacyTokenIfNeeded(encrypted)
        encrypted
    }

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

    /** فقط با اقدام صریح کاربر صدا زده شود -- بالای فایل را ببینید. */
    fun clearSessionForManualReset() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER_ID).apply()
    }

    /** شناسه‌ی نسبتاً پایدار دستگاه، فقط برای اینکه Worker بتواند اعتبار رایگان
     * نصب مجدد را تشخیص دهد (device_hash در Worker -- هرگز مقدار خام ذخیره
     * نمی‌شود، فقط هش SHA-256 آن). نیازی به هیچ پرمیشنی ندارد. */
    private fun deviceId(): String? = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() && it != KNOWN_BROKEN_ANDROID_ID }
    } catch (e: Exception) {
        null
    }

    private suspend fun register(): String? = withContext(Dispatchers.IO) {
        if (!AIConfig.isBackendConfigured()) return@withContext null
        try {
            val payload = gson.toJson(mapOf("deviceId" to deviceId()))
            val request = Request.Builder()
                .url(AIConfig.BACKEND_URL.trimEnd('/') + "/api/register")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
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

    private fun migrateLegacyTokenIfNeeded(target: SharedPreferences) {
        if (target.contains(KEY_TOKEN)) return
        val legacyToken = legacyPrefs.getString(KEY_TOKEN, null) ?: return
        val legacyUserId = legacyPrefs.getString(KEY_USER_ID, null)
        val editor = target.edit().putString(KEY_TOKEN, legacyToken)
        if (legacyUserId != null) editor.putString(KEY_USER_ID, legacyUserId)
        editor.apply()
        legacyPrefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "backend_session"
        private const val ENCRYPTED_PREFS_NAME = "backend_session_encrypted"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_TOKEN = "token"

        // ANDROID_ID معیوب معروف که روی برخی دستگاه‌های خیلی قدیمی/سفارشی بین
        // میلیون‌ها دستگاه مشترک است؛ استفاده از آن به‌عنوان device_hash باعث
        // می‌شد آن دستگاه‌ها همه یک device_hash مشترک بگیرند.
        private const val KNOWN_BROKEN_ANDROID_ID = "9774d56d682e549c"
    }
}
