package com.smartmechanic.ai.ui.screens.credits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartmechanic.ai.billing.PendingPurchase
import com.smartmechanic.ai.billing.PendingPurchaseStore
import com.smartmechanic.ai.data.remote.CreditHistoryItem
import com.smartmechanic.ai.data.remote.CreditPackageDto
import com.smartmechanic.ai.data.repository.CreditsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreditsUiState(
    val loading: Boolean = true,
    val balance: Int = 0,
    val packages: List<CreditPackageDto> = emptyList(),
    /** قیمت واقعی هر productId که از خود SDK مایکت گرفته شده -- هیچ‌وقت hard-code یا تخمینی نیست.
     * اگر یک productId اینجا نباشد، یعنی قیمت هنوز/اصلاً در دسترس نیست و UI نباید عددی نشان دهد. */
    val storePrices: Map<String, String> = emptyMap(),
    val error: String? = null,
    /** productId در حال پردازش خرید، یا null اگر خریدی در جریان نیست. */
    val purchasingProductId: String? = null,
    val purchaseMessage: String? = null,
    val history: List<CreditHistoryItem>? = null,
    val historyLoading: Boolean = false
)

class CreditsViewModel(
    private val repository: CreditsRepository,
    private val pendingPurchases: PendingPurchaseStore
) : ViewModel() {
    private val _state = MutableStateFlow(CreditsUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
        recoverPendingPurchase()
    }

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        repository.load().onSuccess { _state.value = _state.value.copy(loading = false, balance = it.balance, packages = it.packages) }
            .onFailure { error -> _state.value = _state.value.copy(loading = false, error = repository.userFriendlyLoadError(error)) }
    }

    fun applyStorePrices(prices: Map<String, String>) {
        if (prices.isEmpty()) return
        _state.value = _state.value.copy(storePrices = _state.value.storePrices + prices)
    }

    fun loadHistory() = viewModelScope.launch {
        _state.value = _state.value.copy(historyLoading = true)
        repository.loadHistory().onSuccess { _state.value = _state.value.copy(historyLoading = false, history = it.items) }
            .onFailure { _state.value = _state.value.copy(historyLoading = false, error = "دریافت تاریخچه ممکن نشد.") }
    }

    /**
     * اگر خرید قبلی در Myket موفق شده ولی verify هرگز به Worker نرسیده بود
     * (قطع اینترنت/بسته‌شدن اپ)، اینجا (روی هر بار باز شدن این ViewModel --
     * یعنی باز شدن صفحه یا resume شدن اپ روی همین صفحه) دوباره تلاش می‌شود.
     */
    fun recoverPendingPurchase() {
        val pending = pendingPurchases.load() ?: return
        if (_state.value.purchasingProductId != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(purchasingProductId = pending.productId)
            val verify = repository.verifyPurchase(pending.purchaseToken, pending.developerPayload).getOrNull()
            if (verify?.success == true) {
                pendingPurchases.clear()
                _state.value = _state.value.copy(
                    purchasingProductId = null,
                    purchaseMessage = if (verify.duplicate) "این خرید قبلاً ثبت شده بود." else "یک خرید ناتمام قبلی تایید و اعتبارش اضافه شد."
                )
                refresh()
            } else {
                // شاید هنوز اینترنت در دسترس نیست یا Worker موقتاً پاسخ نداد؛
                // pending را نگه می‌داریم تا دفعه‌ی بعد دوباره امتحان شود، نه
                // اینکه آن را گم کنیم.
                _state.value = _state.value.copy(purchasingProductId = null)
            }
        }
    }

    /**
     * گام ۱: developerPayload را از Worker می‌گیرد، سپس [launchFlow] را صدا می‌زند تا
     * لایه UI (که به Activity دسترسی دارد) SDK مایکت را با همان payload اجرا کند.
     * [launchFlow] باید بعد از پایان خرید مایکت، callback را با (success, purchaseToken, message) صدا بزند.
     */
    fun purchase(
        productId: String,
        launchFlow: (developerPayload: String, onPurchaseResult: (Boolean, String?, String) -> Unit) -> Unit
    ) {
        if (_state.value.purchasingProductId != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(purchasingProductId = productId, error = null, purchaseMessage = null)

            val intent = repository.createPaymentIntent(productId).getOrNull()
            if (intent == null) {
                _state.value = _state.value.copy(purchasingProductId = null, error = "شروع خرید ممکن نشد. دوباره تلاش کنید.")
                return@launch
            }

            launchFlow(intent.developerPayload) { success, purchaseToken, message ->
                if (success && purchaseToken != null) {
                    // قبل از verify ذخیره می‌شود: اگر همین‌جا اینترنت قطع شود یا
                    // اپ بسته شود، این خرید برای همیشه گم نمی‌شود.
                    pendingPurchases.save(PendingPurchase(productId, purchaseToken, intent.developerPayload, System.currentTimeMillis()))
                }
                viewModelScope.launch { onPurchaseFlowResult(success, purchaseToken, message, intent.developerPayload) }
            }
        }
    }

    private suspend fun onPurchaseFlowResult(success: Boolean, purchaseToken: String?, message: String, developerPayload: String) {
        if (!success || purchaseToken == null) {
            val friendly = when {
                message.contains("Not Installed", ignoreCase = true) ||
                    message.contains("BILLING_UNAVAILABLE", ignoreCase = true) ->
                    "اپلیکیشن مایکت روی این دستگاه نصب نیست یا در دسترس نیست."
                message.contains("User Canceled", ignoreCase = true) ||
                    message.contains("USER_CANCELLED", ignoreCase = true) -> null // کاربر خودش لغو کرده، نیازی به پیام خطا نیست
                else -> "خرید انجام نشد. دوباره تلاش کنید."
            }
            _state.value = _state.value.copy(purchasingProductId = null, error = friendly)
            return
        }

        val verify = repository.verifyPurchase(purchaseToken, developerPayload).getOrNull()
        if (verify?.success == true) {
            pendingPurchases.clear()
            _state.value = _state.value.copy(
                purchasingProductId = null,
                purchaseMessage = if (verify.duplicate) "این خرید قبلاً ثبت شده بود." else "اعتبار با موفقیت اضافه شد."
            )
            refresh()
        } else {
            // pending را عمداً پاک نمی‌کنیم -- recoverPendingPurchase دفعه‌ی بعد
            // که این صفحه باز شود دوباره تلاش می‌کند. پول کاربر گرفته شده؛ حذف
            // pending یعنی گم‌شدن دائمی این تلاش برای verify.
            _state.value = _state.value.copy(
                purchasingProductId = null,
                error = "پرداخت انجام شد ولی تایید آن هنوز کامل نشد. کمی صبر کنید و «به‌روزرسانی موجودی» را بزنید؛ به‌صورت خودکار دوباره تلاش می‌شود."
            )
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, purchaseMessage = null)
    }

    companion object {
        fun Factory(repository: CreditsRepository, pendingPurchases: PendingPurchaseStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) = CreditsViewModel(repository, pendingPurchases) as T
        }
    }
}
