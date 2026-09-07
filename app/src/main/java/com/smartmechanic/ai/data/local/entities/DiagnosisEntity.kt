package com.smartmechanic.ai.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnosis_records")
data class DiagnosisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val carId: Long?,
    val timestampMillis: Long,
    val inputType: String,              // TEXT / IMAGE / AUDIO / VIDEO
    val userDescription: String?,
    val summary: String,
    val possibleCausesJson: String,     // ذخیره به‌صورت JSON serialize شده
    val urgency: String,
    val recommendationsJson: String,
    val safetyWarning: String?,
    val followUpQuestionsJson: String,
    val mechanicNeeded: Boolean,
    // مسیر محلی فایل رسانه‌ای موقت (در صورت نگهداری کوتاه‌مدت)؛ در صورت null یعنی فایل نگهداری نشده
    val mediaFilePath: String?
)
