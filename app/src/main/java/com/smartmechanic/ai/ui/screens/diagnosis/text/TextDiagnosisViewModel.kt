package com.smartmechanic.ai.ui.screens.diagnosis.text

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartmechanic.ai.data.model.Car
import com.smartmechanic.ai.data.model.InputType
import com.smartmechanic.ai.data.repository.AIService
import com.smartmechanic.ai.data.repository.DiagnosisRepository
import com.smartmechanic.ai.util.AppError
import com.smartmechanic.ai.util.AppResult
import com.smartmechanic.ai.util.ErrorMessageMapper
import com.smartmechanic.ai.util.ResultHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class TextDiagnosisUiState {
    data object Idle : TextDiagnosisUiState()
    data object Loading : TextDiagnosisUiState()
    data class Error(val message: String) : TextDiagnosisUiState()
    data object Success : TextDiagnosisUiState()
}

class TextDiagnosisViewModel(
    private val aiService: AIService,
    private val diagnosisRepository: DiagnosisRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TextDiagnosisUiState>(TextDiagnosisUiState.Idle)
    val uiState: StateFlow<TextDiagnosisUiState> = _uiState

    fun startReview(car: Car?, description: String) {
        if (description.isBlank()) {
            _uiState.value = TextDiagnosisUiState.Error("لطفاً مشکل خودرو را توضیح دهید.")
            return
        }
        _uiState.value = TextDiagnosisUiState.Loading
        viewModelScope.launch {
            when (val result = aiService.analyzeText(car, description)) {
                is AppResult.Success -> {
                    ResultHolder.set(result.data)
                    diagnosisRepository.save(
                        carId = car?.id,
                        inputType = InputType.TEXT,
                        userDescription = description,
                        result = result.data
                    )
                    _uiState.value = TextDiagnosisUiState.Success
                }
                is AppResult.Error -> {
                    _uiState.value = TextDiagnosisUiState.Error(ErrorMessageMapper.toPersianMessage(result.error))
                }
                AppResult.Loading -> Unit
            }
        }
    }

    fun resetState() {
        _uiState.value = TextDiagnosisUiState.Idle
    }

    class Factory(
        private val aiService: AIService,
        private val diagnosisRepository: DiagnosisRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TextDiagnosisViewModel(aiService, diagnosisRepository) as T
    }
}
