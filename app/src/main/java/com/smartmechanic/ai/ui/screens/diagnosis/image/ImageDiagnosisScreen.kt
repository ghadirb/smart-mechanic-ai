package com.smartmechanic.ai.ui.screens.diagnosis.image

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
    val uiState by viewModel.uiState.collectAsState()

    val cameraFile = remember { MediaFileFactory.newImageFile(context) }
    val cameraUri = remember { MediaFileFactory.uriFor(context, cameraFile) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pickedFile = cameraFile
            pickedUri = cameraUri
        }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val tempFile = MediaFileFactory.newImageFile(context)
            context.contentResolver.openInputStream(it)?.use { input -> tempFile.outputStream().use { out -> input.copyTo(out) } }
            pickedFile = tempFile
            pickedUri = Uri.fromFile(tempFile)
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

            Row2(
                onCamera = { takePictureLauncher.launch(cameraUri) },
                onGallery = { pickImageLauncher.launch("image/*") }
            )

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

@Composable
private fun Row2(onCamera: () -> Unit, onGallery: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onCamera, modifier = Modifier.weight(1f)) { Text("📷 دوربین") }
        Button(onClick = onGallery, modifier = Modifier.weight(1f)) { Text("🖼️ گالری") }
    }
}
