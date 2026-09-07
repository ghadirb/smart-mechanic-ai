package com.smartmechanic.ai.util

import android.util.Base64
import com.smartmechanic.ai.config.AIConfig
import java.io.File

object FileUtils {

    fun mimeTypeForImage(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    fun mimeTypeForAudio(file: File): String = when (file.extension.lowercase()) {
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        else -> "audio/mpeg"
    }

    fun mimeTypeForVideo(file: File): String = when (file.extension.lowercase()) {
        "3gp" -> "video/3gpp"
        "webm" -> "video/webm"
        else -> "video/mp4"
    }

    fun encodeToBase64(file: File): String =
        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)

    fun validateSize(file: File, maxBytes: Long): AppError.FileTooLarge? {
        return if (file.length() > maxBytes) {
            AppError.FileTooLarge((maxBytes / (1024 * 1024)).toInt())
        } else null
    }

    fun validateImage(file: File) = validateSize(file, AIConfig.MAX_IMAGE_SIZE_BYTES)
    fun validateAudio(file: File) = validateSize(file, AIConfig.MAX_AUDIO_SIZE_BYTES)
    fun validateVideo(file: File) = validateSize(file, AIConfig.MAX_VIDEO_SIZE_BYTES)

    private val SUPPORTED_IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")
    private val SUPPORTED_AUDIO_EXT = setOf("mp3", "wav", "m4a", "ogg", "aac")
    private val SUPPORTED_VIDEO_EXT = setOf("mp4", "3gp", "webm", "mov")

    fun isSupportedImage(file: File) = file.extension.lowercase() in SUPPORTED_IMAGE_EXT
    fun isSupportedAudio(file: File) = file.extension.lowercase() in SUPPORTED_AUDIO_EXT
    fun isSupportedVideo(file: File) = file.extension.lowercase() in SUPPORTED_VIDEO_EXT
}
