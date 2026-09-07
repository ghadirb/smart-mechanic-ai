package com.smartmechanic.ai.util

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ساخت فایل‌های موقت برای خروجی دوربین/ضبط صدا در دایرکتوری کش اپ (media_cache). */
object MediaFileFactory {

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "media_cache").apply { mkdirs() }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun newImageFile(context: Context): File =
        File(cacheDir(context), "IMG_${timestamp()}.jpg")

    fun newAudioFile(context: Context): File =
        File(cacheDir(context), "AUD_${timestamp()}.m4a")

    fun newVideoFile(context: Context): File =
        File(cacheDir(context), "VID_${timestamp()}.mp4")

    fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** پاک‌سازی فایل‌های قدیمی کش رسانه (بخش ۱۶: عدم نگهداری غیرضروری فایل‌های حجیم). */
    fun clearOldCacheFiles(context: Context, olderThanMillis: Long = 24 * 60 * 60 * 1000) {
        val dir = cacheDir(context)
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { f ->
            if (now - f.lastModified() > olderThanMillis) f.delete()
        }
    }
}
