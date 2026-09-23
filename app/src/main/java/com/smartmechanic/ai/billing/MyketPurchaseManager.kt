package com.smartmechanic.ai.billing

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
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
class MyketPurchaseManager(private val activity: Activity, private val publicKey: String) {

    private enum class SetupState { IDLE, SETTING_UP, READY }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var helper = IabHelper(activity, publicKey)
    private var state = SetupState.IDLE
    private var generation = 0
    private var disposed = false
    private var queryInFlight = false
    private val setupWaiters = mutableListOf<(ok: Boolean, message: String) -> Unit>()
    private val timeoutRunnable = Runnable { onSetupTimeout() }

    /**
     * ریشه‌ی باگ «IAB helper is already set up»: IabHelper.startSetup همان لحظه‌ی
     * اولین فراخوانی iabConnection را مقداردهی می‌کند و هر فراخوانی دوم (چه هنوز
     * در حال اتصال باشد، چه اتصال قبلی شکست خورده باشد) بلافاصله با خطا برمی‌گردد.
     * قبلاً queryPrices و launchPurchase هر دو مستقیم startSetup را صدا می‌زدند و
     * خرید همیشه با همین خطا رد می‌شد. حالا فقط یک setup هم‌زمان انجام می‌شود،
     * بقیه‌ی درخواست‌ها در صف می‌مانند، و بعد از شکست یک IabHelper تازه ساخته
     * می‌شود تا دوباره بشود تلاش کرد.
     */
    private fun ensureSetup(onReady: (ok: Boolean, message: String) -> Unit) {
        if (disposed) {
            onReady(false, "PURCHASE_MANAGER_DISPOSED")
            return
        }
        when (state) {
            SetupState.READY -> onReady(true, "")
            SetupState.SETTING_UP -> setupWaiters.add(onReady)
            SetupState.IDLE -> {
                setupWaiters.add(onReady)
                state = SetupState.SETTING_UP
                val myGeneration = ++generation
                mainHandler.postDelayed(timeoutRunnable, SETUP_TIMEOUT_MS)
                try {
                    helper.startSetup { result ->
                        mainHandler.post {
                            if (disposed || myGeneration != generation) return@post
                            mainHandler.removeCallbacks(timeoutRunnable)
                            Log.i(TAG, "setup finished: success=${result.isSuccess} message=${result.message}")
                            val myketMissing = result.message.contains("Not Installed", ignoreCase = true)
                            if (result.isSuccess && !myketMissing) {
                                state = SetupState.READY
                                flushWaiters(true, "")
                            } else {
                                resetHelper()
                                flushWaiters(false, result.message)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "startSetup threw", t)
                    mainHandler.removeCallbacks(timeoutRunnable)
                    resetHelper()
                    flushWaiters(false, t.message ?: "SETUP_EXCEPTION")
                }
            }
        }
    }

    private fun onSetupTimeout() {
        if (disposed || state != SetupState.SETTING_UP) return
        Log.w(TAG, "setup timed out")
        resetHelper()
        flushWaiters(false, "SETUP_TIMEOUT")
    }

    /** بعد از شکست، IabHelper قابل استفاده‌ی مجدد نیست؛ یکی تازه می‌سازیم. */
    private fun resetHelper() {
        generation++ // callbackهای دیررسیده‌ی helper قدیمی نادیده گرفته می‌شوند
        runCatching { helper.dispose() }
        helper = IabHelper(activity, publicKey)
        state = SetupState.IDLE
    }

    private fun flushWaiters(ok: Boolean, message: String) {
        val waiters = setupWaiters.toList()
        setupWaiters.clear()
        waiters.forEach { it(ok, message) }
    }

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
        ensureSetup { ok, message ->
            if (!ok) {
                onResult(false, null, message)
                return@ensureSetup
            }
            val listener = IabHelper.OnIabPurchaseFinishedListener { result, purchase ->
                val token = purchase?.token
                Log.i(TAG, "purchase finished: success=${result.isSuccess} message=${result.message}")
                if (result.isSuccess && !token.isNullOrBlank()) {
                    onResult(true, token, result.message)
                } else {
                    onResult(false, null, result.message)
                }
            }
            try {
                helper.launchPurchaseFlow(activity, productId, listener, developerPayload)
            } catch (t: Throwable) {
                Log.w(TAG, "launchPurchaseFlow threw", t)
                onResult(false, null, t.message ?: "LAUNCH_EXCEPTION")
            }
        }
    }

    /**
     * قیمت واقعی هر SKU را از خود مایکت می‌گیرد (نه یک عدد hard-code شده در اپ). اگر SDK به هر دلیلی قیمت را برنگرداند (مایکت نصب نیست، خطای شبکه)،
     * نقشهٔ خالی برمی‌گردد و UI باید قیمت جعلی/تخمینی نشان ندهد -- فقط نام و
     * تعداد اعتبار بسته را نشان بدهد.
     */
    fun queryPrices(productIds: List<String>, onResult: (Map<String, String>) -> Unit) {
        if (disposed || queryInFlight) {
            // درخواست قیمت هم‌زمان دیگری در جریان است؛ نتیجه‌ی همان به UI می‌رسد.
            if (disposed) onResult(emptyMap())
            return
        }
        queryInFlight = true
        ensureSetup { ok, _ ->
            if (!ok) {
                queryInFlight = false
                onResult(emptyMap())
                return@ensureSetup
            }
            try {
                helper.queryInventoryAsync(true, productIds) { result, inventory ->
                    queryInFlight = false
                    if (disposed || !result.isSuccess || inventory == null) {
                        onResult(emptyMap())
                    } else {
                        onResult(productIds.mapNotNull { sku -> inventory.getSkuDetails(sku)?.price?.let { sku to it } }.toMap())
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "queryInventoryAsync threw", t)
                queryInFlight = false
                onResult(emptyMap())
            }
        }
    }

    fun dispose() {
        disposed = true
        mainHandler.removeCallbacks(timeoutRunnable)
        setupWaiters.clear()
        runCatching { helper.dispose() }
    }

    private companion object {
        const val TAG = "MyketPurchase"
        const val SETUP_TIMEOUT_MS = 15_000L
    }
}
