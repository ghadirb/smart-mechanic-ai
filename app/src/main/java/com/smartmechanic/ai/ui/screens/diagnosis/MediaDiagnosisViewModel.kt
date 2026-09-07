package com.smartmechanic.ai.ui.screens.diagnosis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartmechanic.ai.data.model.Car
import com.smartmechanic.ai.data.model.InputType
import com.smartmechanic.ai.data.repository.AIService
import com.smartmechanic.ai.data.repository.DiagnosisRepository
import com.smartmechanic.ai.util.AppResult
import com.smartmechanic.ai.util.ErrorMessageMapper
import com.smartmechanic.ai.util.ResultHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class MediaDiagnosisUiState {
    data object Idle : MediaDiagnosisUiState()
    data object Loading : MediaDiagnosisUiState()
    data class Error(val message: String) : MediaDiagnosisUiState()
    data object Success : MediaDiagnosisUiState()
}

/**
 * ViewModel مشترک برای هر سه نوع رسانه (عکس/صدا/ویدئو) تا از تکرار منطق
 * فراخوانی AIService و ذخیره سوابق جلوگیری شود.
 */
class MediaDiagnosisViewModel(
    private val aiService: AIService,
    private val diagnosisRepository: DiagnosisRepository,
    private val inputType: InputType
) : ViewModel() {

    private val _uiState = MutableStateFlow<MediaDiagnosisUiState>(MediaDiagnosisUiState.Idle)
    val uiState: StateFlow<MediaDiagnosisUiState> = _uiState

    fun analyze(car: Car?, note: String?, file: File, keepFile: Boolean) {
        _uiState.value = MediaDiagnosisUiState.Loading
        viewModelScope.launch {
            val result = when (inputType) {
                InputType.IMAGE -> aiService.analyzeImage(car, note, file)
                InputType.AUDIO -> aiService.analyzeAudio(car, note, file)
                InputType.VIDEO -> aiService.analyzeVideo(car, note, file)
                InputType.TEXT -> error("MediaDiagnosisViewModel does not support TEXT")
            }
            when (result) {
                is AppResult.Success -> {
                    ResultHolder.set(result.data)
                    diagnosisRepository.save(
                        carId = car?.id,
                        inputType = inputType,
                        userDescription = note,
                        result = result.data,
                        mediaFilePath = if (keepFile) file.absolutePath else null
                    )
                    if (!keepFile) {
                        runCatching { file.delete() }
                    }
                    _uiState.value = MediaDiagnosisUiState.Success
                }
                is AppResult.Error -> {
                    _uiState.value = MediaDiagnosisUiState.Error(ErrorMessageMapper.toPersianMessage(result.error))
                }
                AppResult.Loading -> Unit
            }
        }
    }

    fun resetState() {
        _uiState.value = MediaDiagnosisUiState.Idle
    }

    class Factory(
        private val aiService: AIService,
        private val diagnosisRepository: DiagnosisRepository,
        private val inputType: InputType
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MediaDiagnosisViewModel(aiService, diagnosisRepository, inputType) as T
    }
}
