package com.smartmechanic.ai.ui.screens.result

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartmechanic.ai.ui.components.DiagnosisResultView
import com.smartmechanic.ai.util.ResultHolder

@Composable
fun DiagnosisResultScreen() {
    val result = ResultHolder.latestResult

    Scaffold(topBar = { TopAppBar(title = { Text("نتیجه تشخیص") }) }) { padding ->
        if (result == null) {
            Text(
                "نتیجه‌ای برای نمایش وجود ندارد.",
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
            )
        } else {
            DiagnosisResultView(
                result = result,
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
            )
        }
    }
}
