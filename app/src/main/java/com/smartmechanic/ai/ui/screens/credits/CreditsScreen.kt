package com.smartmechanic.ai.ui.screens.credits

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartmechanic.ai.billing.MyketPurchaseManager
import com.smartmechanic.ai.config.AIConfig

@Composable
fun CreditsScreen(viewModel: CreditsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val activity = LocalContext.current as? Activity

    // IabHelper باید طول عمر خودش را با Activity هماهنگ نگه دارد؛ با ترک این صفحه آزاد می‌شود.
    val purchaseManager = remember(activity) {
        activity?.let { if (AIConfig.isMyketPurchaseConfigured()) MyketPurchaseManager(it, AIConfig.MYKET_IAB_PUBLIC_KEY) else null }
    }
    DisposableEffect(purchaseManager) {
        onDispose { purchaseManager?.dispose() }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("اعتبار و خرید") }, navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        Column { Text("${item.credits} اعتبار"); Text(item.productId, style = MaterialTheme.typography.bodySmall) }
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

            if (purchaseManager == null) {
                Text(
                    "خرید مایکت پس از قرار دادن کلید RSA عمومی برنامه (از پنل توسعه‌دهندگان مایکت، در MYKET_IAB_PUBLIC_KEY) فعال می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) { Text("به‌روزرسانی موجودی") }
        }
    }
}
