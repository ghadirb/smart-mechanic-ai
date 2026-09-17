package com.smartmechanic.ai.ui.screens.credits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartmechanic.ai.data.remote.CreditPackageDto
import com.smartmechanic.ai.data.repository.CreditsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreditsUiState(
    val loading: Boolean = true,
    val balance: Int = 0,
    val packages: List<CreditPackageDto> = emptyList(),
    val error: String? = null,
    /** productId در حال پردازش خرید، یا null اگر خریدی در جریان نیست. */
    val purchasingProductId: String? = null,
    val purchaseMessage: String? = null
)

class CreditsViewModel(private val repository: CreditsRepository) : ViewModel() {
    private val _state = MutableStateFlow(CreditsUiState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        repository.load().onSuccess { _state.value = _state.value.copy(loading = false, balance = it.balance, packages = it.packages) }
            .onFailure { _state.value = _state.value.copy(loading = false, error = "دریافت موجودی ممکن نشد. اتصال اینترنت را بررسی کنید.") }
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
            _state.value = _state.value.copy(
                purchasingProductId = null,
                purchaseMessage = if (verify.duplicate) "این خرید قبلاً ثبت شده بود." else "اعتبار با موفقیت اضافه شد."
            )
            refresh()
        } else {
            _state.value = _state.value.copy(
                purchasingProductId = null,
                error = "پرداخت انجام شد ولی تایید آن ناموفق بود. لطفاً «به‌روزرسانی موجودی» را بزنید یا با پشتیبانی تماس بگیرید."
            )
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, purchaseMessage = null)
    }

    companion object {
        fun Factory(repository: CreditsRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) = CreditsViewModel(repository) as T
        }
    }
}
