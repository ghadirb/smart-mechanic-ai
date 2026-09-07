package com.smartmechanic.ai.data.repository

import com.google.gson.Gson
import com.smartmechanic.ai.data.local.dao.DiagnosisDao
import com.smartmechanic.ai.data.local.entities.DiagnosisEntity
import com.smartmechanic.ai.data.model.DiagnosisResult
import com.smartmechanic.ai.data.model.InputType
import com.smartmechanic.ai.data.model.PossibleCause
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * ذخیره سوابق تشخیص (بخش ۱۱ سند).
 * توجه: مسیر فایل رسانه‌ای فقط در صورت انتخاب صریح کاربر برای نگهداری ذخیره می‌شود
 * (حریم خصوصی: بخش ۱۶) و کاربر همیشه می‌تواند رکورد را حذف کند.
 */
class DiagnosisRepository(
    private val dao: DiagnosisDao,
    private val gson: Gson = Gson()
) {
    fun observeHistory(): Flow<List<HistoryItem>> =
        dao.observeAll().map { list -> list.map { it.toHistoryItem() } }

    fun observeHistoryForCar(carId: Long): Flow<List<HistoryItem>> =
        dao.observeForCar(carId).map { list -> list.map { it.toHistoryItem() } }

    suspend fun save(
        carId: Long?,
        inputType: InputType,
        userDescription: String?,
        result: DiagnosisResult,
        mediaFilePath: String? = null
    ): Long {
        val entity = DiagnosisEntity(
            carId = carId,
            timestampMillis = System.currentTimeMillis(),
            inputType = inputType.name,
            userDescription = userDescription,
            summary = result.summary,
            possibleCausesJson = gson.toJson(result.possibleCauses),
            urgency = result.urgency.name,
            recommendationsJson = gson.toJson(result.recommendations),
            safetyWarning = result.safetyWarning,
            followUpQuestionsJson = gson.toJson(result.followUpQuestions),
            mechanicNeeded = result.mechanicNeeded,
            mediaFilePath = mediaFilePath
        )
        return dao.insert(entity)
    }

    suspend fun delete(item: HistoryItem) {
        // حذف رکورد و در صورت وجود، فایل رسانه‌ای مرتبط (بخش ۱۶: امکان حذف سوابق)
        item.mediaFilePath?.let { path ->
            runCatching { java.io.File(path).delete() }
        }
        dao.deleteById(item.id)
    }

    private fun DiagnosisEntity.toHistoryItem() = HistoryItem(
        id = id,
        carId = carId,
        timestampMillis = timestampMillis,
        inputType = InputType.valueOf(inputType),
        userDescription = userDescription,
        summary = summary,
        possibleCauses = gson.fromJson(possibleCausesJson, Array<PossibleCause>::class.java)?.toList() ?: emptyList(),
        urgency = urgency,
        recommendations = gson.fromJson(recommendationsJson, Array<String>::class.java)?.toList() ?: emptyList(),
        safetyWarning = safetyWarning,
        mechanicNeeded = mechanicNeeded,
        mediaFilePath = mediaFilePath
    )
}

data class HistoryItem(
    val id: Long,
    val carId: Long?,
    val timestampMillis: Long,
    val inputType: InputType,
    val userDescription: String?,
    val summary: String,
    val possibleCauses: List<PossibleCause>,
    val urgency: String,
    val recommendations: List<String>,
    val safetyWarning: String?,
    val mechanicNeeded: Boolean,
    val mediaFilePath: String?
)
