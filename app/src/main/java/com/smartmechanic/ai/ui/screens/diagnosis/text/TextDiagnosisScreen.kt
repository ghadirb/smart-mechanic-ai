package com.smartmechanic.ai.ui.screens.diagnosis.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartmechanic.ai.data.model.Car
import com.smartmechanic.ai.ui.components.ErrorDialog
import com.smartmechanic.ai.ui.components.FullScreenLoading

@Composable
fun TextDiagnosisScreen(
    viewModel: TextDiagnosisViewModel,
    selectedCar: Car?,
    onNavigateToResult: () -> Unit
) {
    var description by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is TextDiagnosisUiState.Success) {
            onNavigateToResult()
            viewModel.resetState()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("تشخیص مشکل") }) }) { padding ->
        if (uiState is TextDiagnosisUiState.Loading) {
            FullScreenLoading()
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            selectedCar?.let {
                Text("خودرو انتخاب‌شده: ${it.brand} ${it.model} (${it.year})", style = MaterialTheme.typography.bodyMedium)
            } ?: Text("خودرویی انتخاب نشده — می‌توانید بدون انتخاب خودرو ادامه دهید.", style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("مثال: وقتی ماشین را گاز می‌دهم صدای تق‌تق از موتور می‌آید...") },
                modifier = Modifier.fillMaxWidth().height(160.dp)
            )

            Button(onClick = { viewModel.startReview(selectedCar, description) }, modifier = Modifier.fillMaxWidth()) {
                Text("شروع بررسی")
            }
        }

        val state = uiState
        if (state is TextDiagnosisUiState.Error) {
            ErrorDialog(
                message = state.message,
                onDismiss = { viewModel.resetState() },
                onRetry = { viewModel.startReview(selectedCar, description) }
            )
        }
    }
}
