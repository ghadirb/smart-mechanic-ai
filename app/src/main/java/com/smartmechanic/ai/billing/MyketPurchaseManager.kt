package com.smartmechanic.ai.billing

import android.app.Activity
import ir.myket.billingclient.IabHelper

/**
 * لایه‌ی نازک روی IabHelper مایکت (کتابخانه‌ی رسمی، رجوع کنید به
 * app/build.gradle.kts: com.github.myketstore:myket-billing-client).
 *
 * این کلاس هرگز اعتبار را افزایش نمی‌دهد -- فقط توکن خرید امضاشده توسط
 * مایکت را برمی‌گرداند. افزایش واقعی اعتبار فقط بعد از این اتفاق می‌افتد که
 * CreditsRepository.verifyPurchase همین توکن را برای تایید server-to-server
 * به Cloudflare Worker (backend/cloudflare/worker/src/payments.ts) بفرستد.
 *
 * باید در Activity ساخته شود (نه ViewModel) چون launchPurchaseFlow خود SDK
 * یک Activity زنده لازم دارد؛ CreditsScreen آن را با remember/DisposableEffect
 * نگه می‌دارد و در dispose() آزاد می‌کند.
 */
class MyketPurchaseManager(private val activity: Activity, publicKey: String) {

    private val helper = IabHelper(activity, publicKey)
    private var setupDone = false
    private var disposed = false

    /**
     * @param productId شناسه محصول مایکت (مثلاً credit_20)
     * @param developerPayload مقدار یک‌بارمصرفی که از POST /api/payments/intent گرفته شده
     * @param onResult (success, purchaseToken, message) -- در صورت success=true، purchaseToken
     *   باید بلافاصله برای CreditsRepository.verifyPurchase فرستاده شود.
     */
    fun launchPurchase(
        productId: String,
        developerPayload: String,
        onResult: (success: Boolean, purchaseToken: String?, message: String) -> Unit
    ) {
        if (disposed) {
            onResult(false, null, "PURCHASE_MANAGER_DISPOSED")
            return
        }

        fun launch() {
            val listener = IabHelper.OnIabPurchaseFinishedListener { result, purchase ->
                val token = purchase?.token
                if (result.isSuccess && !token.isNullOrBlank()) {
                    onResult(true, token, result.message)
                } else {
                    onResult(false, null, result.message)
                }
            }
            helper.launchPurchaseFlow(activity, productId, listener, developerPayload)
        }

        if (setupDone) {
            launch()
            return
        }

        helper.startSetup { result ->
            if (disposed) return@startSetup
            if (result.isSuccess) {
                setupDone = true
                launch()
            } else {
                onResult(false, null, result.message)
            }
        }
    }

    /**
     * قیمت واقعی هر SKU را از خود مایکت می‌گیرد (نه یک عدد hard-code شده در اپ). اگر SDK به هر دلیلی قیمت را برنگرداند (مایکت نصب نیست، خطای شبکه)،
     * نقشهٔ خالی برمی‌گردد و UI باید قیمت جعلی/تخمینی نشان ندهد -- فقط نام و
     * تعداد اعتبار بسته را نشان بدهد.
     */
    fun queryPrices(productIds: List<String>, onResult: (Map<String, String>) -> Unit) {
        if (disposed) {
            onResult(emptyMap())
            return
        }

        fun query() {
            helper.queryInventoryAsync(true, productIds) { result, inventory ->
                if (!result.isSuccess || inventory == null) {
                    onResult(emptyMap())
                } else {
                    onResult(productIds.mapNotNull { sku -> inventory.getSkuDetails(sku)?.price?.let { sku to it } }.toMap())
                }
            }
        }

        if (setupDone) {
            query()
            return
        }
        helper.startSetup { result ->
            if (disposed) return@startSetup
            if (result.isSuccess) {
                setupDone = true
                query()
            } else {
                onResult(emptyMap())
            }
        }
    }

    fun dispose() {
        disposed = true
        runCatching { helper.dispose() }
    }
}
