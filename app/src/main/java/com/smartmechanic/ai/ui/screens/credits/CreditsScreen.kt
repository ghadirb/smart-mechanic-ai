package com.smartmechanic.ai.ui.screens.credits

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.smartmechanic.ai.billing.MyketPurchaseManager
import com.smartmechanic.ai.config.AIConfig
import com.smartmechanic.ai.data.remote.CreditHistoryItem

/** نام‌های نمایشی برای کاربر عادی -- productId داخلی (credit_20 و غیره) هرگز
 * در UI عادی دیده نمی‌شود، فقط در لاگ/debug. */
private fun friendlyPackageName(productId: String): String = when (productId) {
    "credit_20" -> "بسته ۲۰ اعتبار"
    "credit_50" -> "بسته ۵۰ اعتبار"
    "credit_150" -> "بسته ۱۵۰ اعتبار"
    else -> productId
}

@Composable
fun CreditsScreen(viewModel: CreditsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val activity = LocalContext.current as? Activity
    var showHistory by remember { mutableStateOf(false) }

    // IabHelper باید طول عمر خودش را با Activity هماهنگ نگه دارد؛ با ترک این صفحه آزاد می‌شود.
    val purchaseManager = remember(activity) {
        activity?.let { if (AIConfig.isMyketPurchaseConfigured()) MyketPurchaseManager(it, AIConfig.MYKET_IAB_PUBLIC_KEY) else null }
    }
    DisposableEffect(purchaseManager) {
        onDispose { purchaseManager?.dispose() }
    }

    // قیمت واقعی بسته‌ها را از خود مایکت می‌گیریم -- هرگز عدد ساختگی نشان نمی‌دهیم.
    LaunchedEffect(purchaseManager, state.packages) {
        val manager = purchaseManager ?: return@LaunchedEffect
        val productIds = state.packages.map { it.productId }
        if (productIds.isEmpty()) return@LaunchedEffect
        manager.queryPrices(productIds) { prices -> viewModel.applyStorePrices(prices) }
    }

    // اگر خرید قبلی ناتمام مانده بود (verify نرسیده)، هم موقع باز شدن صفحه
    // (init در ViewModel) و هم موقع resume شدن اپ روی همین صفحه دوباره تلاش می‌شود.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.recoverPendingPurchase()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("اعتبار و خرید") }, navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("اعتبار قابل استفاده", style = MaterialTheme.typography.titleMedium)
                    Text(if (state.loading) "در حال به‌روزرسانی…" else "${state.balance} اعتبار", style = MaterialTheme.typography.displaySmall)
                    Text("هزینه‌ها: متن ۱ · عکس ۳ · صوت ۵ · ویدئو ۱۲", style = MaterialTheme.typography.bodySmall)
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.purchaseMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Text("افزایش اعتبار", style = MaterialTheme.typography.titleMedium)
            state.packages.forEach { item ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(friendlyPackageName(item.productId))
                            // فقط اگر SDK مایکت قیمت واقعی را برگردانده باشد نشان داده می‌شود؛
                            // هیچ قیمت تخمینی/hard-code شده‌ای اینجا نیست.
                            state.storePrices[item.productId]?.let { price ->
                                Text(price, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Button(
                            onClick = {
                                val manager = purchaseManager ?: return@Button
                                viewModel.purchase(item.productId) { developerPayload, onPurchaseResult ->
                                    manager.launchPurchase(item.productId, developerPayload, onPurchaseResult)
                                }
                            },
                            enabled = purchaseManager != null && state.purchasingProductId == null
                        ) {
                            if (state.purchasingProductId == item.productId) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("خرید از مایکت")
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.weight(1f)) { Text("به‌روزرسانی موجودی") }
                OutlinedButton(
                    onClick = { showHistory = true; viewModel.loadHistory() },
                    modifier = Modifier.weight(1f)
                ) { Text("تاریخچه") }
            }
        }
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("بستن") } },
            title = { Text("تاریخچه اعتبار") },
            text = {
                when {
                    state.historyLoading -> Text("در حال بارگذاری…")
                    state.history.isNullOrEmpty() -> Text("هنوز تراکنشی ثبت نشده.")
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(state.history!!) { item -> HistoryRow(item) }
                    }
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(item: CreditHistoryItem) {
    Column(Modifier.padding(vertical = 8.dp)) {
        val label = when (item.type) {
            "debit" -> "مصرف: ${item.amount} اعتبار"
            "credit" -> "افزایش: ${-item.amount} اعتبار"
            else -> "${item.type}: ${item.amount}"
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
        item.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(item.createdAt, style = MaterialTheme.typography.labelSmall)
    }
}
