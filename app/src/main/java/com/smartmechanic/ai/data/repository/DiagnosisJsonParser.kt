package com.smartmechanic.ai.data.repository

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.smartmechanic.ai.data.model.DiagnosisResult
import com.smartmechanic.ai.util.AppError
import com.smartmechanic.ai.util.AppResult

/**
 * پارس پاسخ خام مدل (که ممکن است در بلاک ```json``` بسته‌بندی شده باشد) به DiagnosisResult.
 * به‌صورت مجزا نگه‌داشته شده تا به‌سادگی قابل تست واحد باشد.
 */
object DiagnosisJsonParser {

    fun parse(rawText: String, gson: Gson = Gson()): AppResult<DiagnosisResult> {
        val cleaned = rawText.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        return try {
            val result = gson.fromJson(cleaned, DiagnosisResult::class.java)
            if (result == null || result.summary.isBlank()) {
                AppResult.Error(AppError.InvalidAiResponse(rawText))
            } else {
                AppResult.Success(result)
            }
        } catch (e: JsonSyntaxException) {
            AppResult.Error(AppError.InvalidAiResponse(rawText))
        }
    }
}
