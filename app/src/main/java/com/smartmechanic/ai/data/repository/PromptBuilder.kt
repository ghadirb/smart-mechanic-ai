package com.smartmechanic.ai.data.repository

import com.smartmechanic.ai.data.model.Car

object PromptBuilder {

    fun carSpecsBlock(car: Car?): String {
        if (car == null) return "مشخصات خودرو ثبت نشده است."
        return buildString {
            appendLine("مشخصات خودرو:")
            appendLine("- برند: ${car.brand}")
            appendLine("- مدل: ${car.model}")
            car.trim?.let { appendLine("- تیپ: $it") }
            appendLine("- سال ساخت: ${car.year}")
            car.engineType?.let { appendLine("- نوع موتور: $it") }
            car.engineDisplacement?.let { appendLine("- حجم موتور: $it") }
            car.fuelType?.let { appendLine("- نوع سوخت: $it") }
            car.transmission?.let { appendLine("- گیربکس: $it") }
            appendLine("- کیلومتر فعلی: ${car.currentMileageKm}")
            car.extraNotes?.let { appendLine("- توضیحات اضافی: $it") }
        }
    }

    fun buildTextPrompt(car: Car?, userDescription: String): String = buildString {
        appendLine(carSpecsBlock(car))
        appendLine()
        appendLine("توضیح مشکل توسط کاربر:")
        appendLine(userDescription)
    }

    fun buildMediaPrompt(car: Car?, userNote: String?, mediaKindFa: String): String = buildString {
        appendLine(carSpecsBlock(car))
        appendLine()
        appendLine("کاربر یک $mediaKindFa برای بررسی ارسال کرده است.")
        if (!userNote.isNullOrBlank()) {
            appendLine("توضیح تکمیلی کاربر: $userNote")
        }
        appendLine("لطفاً محتوای ضمیمه‌شده را همراه با مشخصات خودرو تحلیل کن.")
    }
}
