package com.smartmechanic.ai.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartmechanic.ai.R
import com.smartmechanic.ai.ui.navigation.Routes

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "مکانیک هوشمند AI",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResourceSafe(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                HomeButton("🔧 تشخیص مشکل") { onNavigate(Routes.DIAGNOSE_TEXT) }
                HomeButton("📷 تحلیل عکس") { onNavigate(Routes.DIAGNOSE_IMAGE) }
                HomeButton("🎙️ تحلیل صدای موتور") { onNavigate(Routes.DIAGNOSE_AUDIO) }
                HomeButton("🎥 تحلیل ویدئو") { onNavigate(Routes.DIAGNOSE_VIDEO) }
                HomeButton("🚗 خودروهای من") { onNavigate(Routes.CARS) }
                HomeButton("📋 سوابق تشخیص") { onNavigate(Routes.HISTORY) }
                HomeButton("💳 اعتبار و خرید") { onNavigate(Routes.CREDITS) }
            }
        }
    }
}

@Composable
private fun HomeButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text)
    }
}

@Composable
private fun stringResourceSafe(): String =
    "مشکل خودروت را توضیح بده، عکس بفرست یا صدای موتور و ویدئو ضبط کن."
