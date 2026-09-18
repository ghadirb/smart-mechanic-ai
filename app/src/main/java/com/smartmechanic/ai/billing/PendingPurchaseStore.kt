package com.smartmechanic.ai.billing

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class PendingPurchase(
    val productId: String,
    val purchaseToken: String,
    val developerPayload: String,
    val createdAtMillis: Long
)

/**
 * اگر SDK مایکت خرید را موفق تلقی کند ولی POST /api/payments/verify (به‌دلیل
 * قطع اینترنت یا بسته‌شدن اپ درست بعد از خرید) هرگز به Worker نرسد، این
 * purchaseToken/developerPayload اینجا نگه داشته می‌شوند تا در باز شدن بعدی
 * صفحه‌ی اعتبار یا resume شدن اپ، verify دوباره تلاش شود -- بدون این، پول
 * کاربر پرداخت شده ولی اعتباری اضافه نمی‌شد و راهی برای جبران آن نبود.
 *
 * اندروید هرگز خودش اعتبار اضافه نمی‌کند؛ این کلاس فقط یک صف تک‌آیتمی برای
 * تلاش مجدد صدا زدن همان endpoint امن سرور است.
 */
class PendingPurchaseStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "pending_purchases",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(purchase: PendingPurchase) {
        prefs.edit()
            .putString(KEY_PRODUCT, purchase.productId)
            .putString(KEY_TOKEN, purchase.purchaseToken)
            .putString(KEY_PAYLOAD, purchase.developerPayload)
            .putLong(KEY_CREATED, purchase.createdAtMillis)
            .apply()
    }

    fun load(): PendingPurchase? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val payload = prefs.getString(KEY_PAYLOAD, null) ?: return null
        val productId = prefs.getString(KEY_PRODUCT, null) ?: return null
        return PendingPurchase(productId, token, payload, prefs.getLong(KEY_CREATED, 0L))
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_PRODUCT = "product_id"
        private const val KEY_TOKEN = "purchase_token"
        private const val KEY_PAYLOAD = "developer_payload"
        private const val KEY_CREATED = "created_at"
    }
}
