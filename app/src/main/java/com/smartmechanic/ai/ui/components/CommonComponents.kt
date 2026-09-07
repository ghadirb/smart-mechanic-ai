package com.smartmechanic.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SafetyWarningBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFFEBEE), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFC62828))
        Text(text = message, color = Color(0xFFC62828), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun FullScreenLoading(label: String = "در حال دریافت تحلیل هوش مصنوعی...") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(label)
        }
    }
}

@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit, onRetry: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("خطا") },
        text = { Text(message) },
        confirmButton = {
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("تلاش دوباره") }
            } else {
                TextButton(onClick = onDismiss) { Text("باشه") }
            }
        },
        dismissButton = {
            if (onRetry != null) TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )
}

@Composable
fun PrivacyConsentDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ارسال به سرویس هوش مصنوعی") },
        text = { Text("این فایل برای تحلیل به سرویس هوش مصنوعی (Gemini) ارسال می‌شود. آیا موافقید؟") },
        confirmButton = { TextButton(onClick = onAccept) { Text("موافقم") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
