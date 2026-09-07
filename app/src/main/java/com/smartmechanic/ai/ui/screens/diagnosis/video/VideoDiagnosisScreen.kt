package com.smartmechanic.ai.ui.screens.diagnosis.video

import android.Manifest
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smartmechanic.ai.config.AIConfig
import com.smartmechanic.ai.data.model.Car
import com.smartmechanic.ai.ui.components.ErrorDialog
import com.smartmechanic.ai.ui.components.FullScreenLoading
import com.smartmechanic.ai.ui.components.PrivacyConsentDialog
import com.smartmechanic.ai.ui.screens.diagnosis.MediaDiagnosisUiState
import com.smartmechanic.ai.ui.screens.diagnosis.MediaDiagnosisViewModel
import com.smartmechanic.ai.util.MediaFileFactory
import com.smartmechanic.ai.util.SafeCameraLauncher
import java.io.File

private fun videoDurationSeconds(context: android.content.Context, file: File): Int {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, Uri.fromFile(file))
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        retriever.release()
        (durationMs / 1000).toInt()
    } catch (e: Exception) {
        0
    }
}

@Composable
fun VideoDiagnosisScreen(
    viewModel: MediaDiagnosisViewModel,
    selectedCar: Car?,
    onNavigateToResult: () -> Unit
) {
    val context = LocalContext.current
    var pickedFile by remember { mutableStateOf<File?>(null) }
    var note by remember { mutableStateOf("") }
    var showConsent by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val uiState by viewModel.uiState.collectAsState()

    var cameraFile by remember { mutableStateOf<File?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun validateAndAccept(file: File) {
        val duration = videoDurationSeconds(context, file)
        when {
            duration > AIConfig.MAX_VIDEO_DURATION_SECONDS ->
                validationError = "طول ویدئو نباید بیش از ${AIConfig.MAX_VIDEO_DURATION_SECONDS} ثانیه باشد. لطفاً ویدئوی کوتاه‌تری انتخاب کنید."
            file.length() > AIConfig.MAX_VIDEO_SIZE_BYTES ->
                validationError = "حجم ویدئو بیش از حد مجاز است. لطفاً ویدئوی کوتاه‌تر یا با کیفیت پایین‌تر ضبط کنید."
            else -> {
                pickedFile = file
                validationError = null
            }
        }
    }

    val cameraChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            cameraFile?.let { validateAndAccept(it) }
        }
    }

    fun launchCamera() {
        val file = MediaFileFactory.newVideoFile(context)
        val uri = MediaFileFactory.uriFor(context, file)
        cameraFile = file
        cameraUri = uri

        when (val launchResult = SafeCameraLauncher.buildVideoCaptureChooser(context, uri)) {
            is SafeCameraLauncher.LaunchResult.Ready -> {
                val launched = SafeCameraLauncher.tryLaunch { cameraChooserLauncher.launch(launchResult.chooserIntent) }
                if (!launched) {
                    validationError = "برنامه دوربین در دسترس نیست یا اجرا نشد. لطفاً از گزینه گالری استفاده کنید."
                }
            }
            SafeCameraLauncher.LaunchResult.NoCameraAppFound -> {
                validationError = "هیچ برنامه دوربینی روی گوشی شما یافت نشد. لطفاً از گزینه «گالری» استفاده کنید یا یک برنامه دوربین نصب کنید."
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            validationError = "برای ضبط ویدئو، اجازه دسترسی به دوربین لازم است."
        }
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val tempFile = MediaFileFactory.newVideoFile(context)
            runCatching {
                context.contentResolver.openInputStream(it)?.use { input -> tempFile.outputStream().use { out -> input.copyTo(out) } }
                validateAndAccept(tempFile)
            }.onFailure {
                validationError = "خواندن ویدئوی انتخاب‌شده از گالری ممکن نشد. لطفاً دوباره تلاش کنید."
            }
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is MediaDiagnosisUiState.Success) {
            onNavigateToResult()
            viewModel.resetState()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("تحلیل ویدئو") }) }) { padding ->
        if (uiState is MediaDiagnosisUiState.Loading) {
            FullScreenLoading()
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("یک ویدئوی کوتاه (حداکثر ${AIConfig.MAX_VIDEO_DURATION_SECONDS} ثانیه) از موتور، صدای آن، دود اگزوز، لرزش یا قطعه مشکوک ضبط یا انتخاب کنید.")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.weight(1f)
                ) { Text("🎥 دوربین") }
                Button(onClick = { pickVideoLauncher.launch("video/*") }, modifier = Modifier.weight(1f)) { Text("🖼️ گالری") }
            }

            validationError?.let { Text(it, color = androidx.compose.ui.graphics.Color.Red) }

            pickedFile?.let {
                Text("ویدئو آماده ارسال است (${it.length() / 1024} کیلوبایت).")
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیح تکمیلی (اختیاری)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { showConsent = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("ارسال برای تحلیل")
                }
            }
        }

        if (showConsent) {
            PrivacyConsentDialog(
                onAccept = {
                    showConsent = false
                    pickedFile?.let { viewModel.analyze(selectedCar, note.ifBlank { null }, it, keepFile = false) }
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
