package com.smartmechanic.ai.data.repository

import com.smartmechanic.ai.data.model.Car
import com.smartmechanic.ai.data.model.DiagnosisResult
import com.smartmechanic.ai.util.AppResult
import java.io.File

/**
 * لایه انتزاعی سرویس هوش مصنوعی (بخش ۱۲ سند طراحی).
 * بقیه برنامه فقط با این Interface کار می‌کند، نه مستقیماً با Gemini؛
 * بنابراین تعویض ارائه‌دهنده مدل در آینده فقط نیازمند یک پیاده‌سازی جدید است.
 */
interface AIService {

    suspend fun analyzeText(
        car: Car?,
        userDescription: String
    ): AppResult<DiagnosisResult>

    suspend fun analyzeImage(
        car: Car?,
        userNote: String?,
        imageFile: File
    ): AppResult<DiagnosisResult>

    suspend fun analyzeAudio(
        car: Car?,
        userNote: String?,
        audioFile: File
    ): AppResult<DiagnosisResult>

    suspend fun analyzeVideo(
        car: Car?,
        userNote: String?,
        videoFile: File
    ): AppResult<DiagnosisResult>
}
