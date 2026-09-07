package com.smartmechanic.ai.ui.screens.diagnosis.image

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import coil.compose.rememberAsyncImagePainter
import com.smartmechanic.ai.data.model.Car
import com.smartmechanic.ai.ui.components.ErrorDialog
import com.smartmechanic.ai.ui.components.FullScreenLoading
import com.smartmechanic.ai.ui.components.PrivacyConsentDialog
import com.smartmechanic.ai.ui.screens.diagnosis.MediaDiagnosisUiState
import com.smartmechanic.ai.ui.screens.diagnosis.MediaDiagnosisViewModel
import com.smartmechanic.ai.util.MediaFileFactory
import com.smartmechanic.ai.util.SafeCameraLauncher
import java.io.File

@Composable
fun ImageDiagnosisScreen(
    viewModel: MediaDiagnosisViewModel,
    selectedCar: Car?,
    onNavigateToResult: () -> Unit
) {
    val context = LocalContext.current
    var pickedFile by remember { mutableStateOf<File?>(null) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var note by remember { mutableStateOf("") }
    var showConsent by remember { mutableStateOf(false) }
    var cameraErrorMsg by remember { mutableStateOf<String?>(null) }
    val uiState by viewModel.uiState.collectAsState()

    var cameraFile by remember { mutableStateOf<File?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    // اجرای اینتنت دوربین (که خودمان با انتخاب‌گر برنامه ساختیم) از طریق کنترکت عمومی
    // StartActivityForResult تا بتوانیم انتخاب‌گر چند-برنامه‌ای را کنترل کنیم.
    val cameraChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            cameraFile?.let {
                pickedFile = it
                pickedUri = cameraUri
            }
        }
    }

    fun launchCamera() {
        val file = MediaFileFactory.newImageFile(context)
        val uri = MediaFileFactory.uriFor(context, file)
        cameraFile = file
        cameraUri = uri

        when (val launchResult = SafeCameraLauncher.buildImageCaptureChooser(context, uri)) {
            is SafeCameraLauncher.LaunchResult.Ready -> {
                val launched = SafeCameraLauncher.tryLaunch { cameraChooserLauncher.launch(launchResult.chooserIntent) }
                if (!launched) {
                    cameraErrorMsg = "برنامه دوربین در دسترس نیست یا اجرا نشد. لطفاً از گزینه گالری استفاده کنید."
                }
            }
            SafeCameraLauncher.LaunchResult.NoCameraAppFound -> {
                cameraErrorMsg = "هیچ برنامه دوربینی روی گوشی شما یافت نشد. لطفاً از گزینه «گالری» استفاده کنید یا یک برنامه دوربین (مثلاً از Google Play) نصب کنید."
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            cameraErrorMsg = "برای گرفتن عکس، اجازه دسترسی به دوربین لازم است."
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val tempFile = MediaFileFactory.newImageFile(context)
            runCatching {
                context.contentResolver.openInputStream(it)?.use { input -> tempFile.outputStream().use { out -> input.copyTo(out) } }
                pickedFile = tempFile
                pickedUri = Uri.fromFile(tempFile)
            }.onFailure {
                cameraErrorMsg = "خواندن فایل انتخاب‌شده از گالری ممکن نشد. لطفاً دوباره تلاش کنید."
            }
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is MediaDiagnosisUiState.Success) {
            onNavigateToResult()
            viewModel.resetState()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("تحلیل عکس") }) }) { padding ->
        if (uiState is MediaDiagnosisUiState.Loading) {
            FullScreenLoading()
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("مثال: موتور، چراغ Check Engine، روغن‌ریزی، دود، لاستیک، ترمز یا داشبورد را عکس بگیرید.")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.weight(1f)
                ) { Text("📷 دوربین") }
                Button(onClick = { pickImageLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("🖼️ گالری") }
            }

            cameraErrorMsg?.let {
                Text(it, color = androidx.compose.ui.graphics.Color.Red)
            }

            pickedUri?.let {
                Image(
                    painter = rememberAsyncImagePainter(it),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("توضیح تکمیلی (اختیاری)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { if (pickedFile != null) showConsent = true },
                enabled = pickedFile != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("ارسال برای تحلیل") }
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
