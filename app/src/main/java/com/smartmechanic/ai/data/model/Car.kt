package com.smartmechanic.ai.data.model

data class Car(
    val id: Long = 0,
    val brand: String,          // برند: مثلاً سایپا
    val model: String,          // مدل: مثلاً تیبا
    val trim: String? = null,   // تیپ
    val year: Int,              // سال ساخت
    val engineType: String? = null,
    val engineDisplacement: String? = null, // حجم موتور در صورت اطلاع
    val fuelType: String? = null,
    val transmission: String? = null,
    val currentMileageKm: Int,
    val extraNotes: String? = null
)

enum class InputType { TEXT, IMAGE, AUDIO, VIDEO }
