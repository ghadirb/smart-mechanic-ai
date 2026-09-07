package com.smartmechanic.ai.ui.screens.diagnosis.audio

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smartmechanic.ai.config.AIConfig
import com.smartmechanic.ai.data.model.Car
import com.smartmechanic.ai.ui.components.ErrorDialog
import com.smartmechanic.ai.ui.components.FullScreenLoading
import com.smartmechanic.ai.ui.components.PrivacyConsentDialog
import com.smartmechanic.ai.ui.components.SafetyWarningBanner
import com.smartmechanic.ai.ui.screens.diagnosis.MediaDiagnosisUiState
import com.smartmechanic.ai.ui.screens.diagnosis.MediaDiagnosisViewModel
import com.smartmechanic.ai.util.AudioRecorderHelper
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun AudioDiagnosisScreen(
    viewModel: MediaDiagnosisViewModel,
    selectedCar: Car?,
    onNavigateToResult: () -> Unit
) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorderHelper(context) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var note by remember { mutableStateOf("") }
    var showConsent by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var micErrorMsg by remember { mutableStateOf<String?>(null) }
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = recorder.startRecording()
            if (file != null) {
                recordedFile = file
                isRecording = true
                elapsedSeconds = 0
            } else {
                permissionDenied = false
                micErrorMsg = "امکان دسترسی به میکروفون وجود ندارد. لطفاً بررسی کنید برنامه دیگری در حال استفاده از میکروفون نباشد و دوباره تلاش کنید."
            }
        } else {
            permissionDenied = true
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording && elapsedSeconds < AIConfig.MAX_AUDIO_DURATION_SECONDS) {
            delay(1000)
            elapsedSeconds++
        }
        if (isRecording && elapsedSeconds >= AIConfig.MAX_AUDIO_DURATION_SECONDS) {
            recordedFile = recorder.stopRecording()
            isRecording = false
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is MediaDiagnosisUiState.Success) {
            onNavigateToResult()
            viewModel.resetState()
        }
    }

    DisposableEffect(Unit) {
        onDispose { if (isRecording) recorder.cancelRecording() }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("تحلیل صدای موتور") }) }) { padding ->
        if (uiState is MediaDiagnosisUiState.Loading) {
            FullScreenLoading()
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SafetyWarningBanner(
                "برای نتیجه بهتر، تلفن را در فاصله ایمن از قطعات متحرک قرار دهید و به هیچ عنوان دست یا تلفن را نزدیک تسمه، فن یا قطعات متحرک نبرید."
            )

            if (!isRecording && recordedFile == null) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🎙️ شروع ضبط (حداکثر ${AIConfig.MAX_AUDIO_DURATION_SECONDS} ثانیه)") }
            }

            if (isRecording) {
                Text("در حال ضبط... $elapsedSeconds ثانیه")
                Button(
                    onClick = {
                        recordedFile = recorder.stopRecording()
                        isRecording = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("توقف ضبط") }
            }

            recordedFile?.let { file ->
                Text("صدا ضبط شد (${elapsedSeconds} ثانیه). آماده ارسال برای تحلیل.")
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیح تکمیلی (اختیاری)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { showConsent = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("ارسال برای تحلیل")
                }
                Button(
                    onClick = { recordedFile?.delete(); recordedFile = null; elapsedSeconds = 0 },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ضبط مجدد") }
            }

            if (permissionDenied) {
                Text("برای ضبط صدا، اجازه دسترسی به میکروفون لازم است.")
            }
            micErrorMsg?.let { Text(it, color = androidx.compose.ui.graphics.Color.Red) }
        }

        if (showConsent) {
            PrivacyConsentDialog(
                onAccept = {
                    showConsent = false
                    recordedFile?.let { viewModel.analyze(selectedCar, note.ifBlank { null }, it, keepFile = false) }
                },
                onDismiss = { showConsent = false }
            )
        }

        val state = uiState
        if (state is MediaDiagnosisUiState.Error) {
            ErrorDialog(message = state.message, onDismiss = { viewModel.resetState() })
        }
    }
}
