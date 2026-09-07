package com.smartmechanic.ai.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore

/**
 * اجرای امن اینتنت دوربین برای عکس/ویدئو.
 *
 * چرا این کلاس لازم است؟
 * ۱) از اندروید 11 به بعد بدون <queries> در Manifest، سیستم ممکن است اعلام کند هیچ
 *    برنامه‌ای برای دوربین وجود ندارد؛ اگر روی گوشی هم دوربین پیش‌فرض خراب/غیرفعال باشد،
 *    اجرای مستقیم اینتنت باعث ActivityNotFoundException و کرش کامل برنامه می‌شود.
 * ۲) با ساختن اینتنت به‌صورت دستی و عبور آن از Intent.createChooser، همیشه یک انتخاب‌گر
 *    («انتخاب برنامه دوربین») نمایش داده می‌شود؛ کاربر می‌تواند هر برنامه دوربین نصب‌شده روی
 *    گوشی (حتی برنامه‌های جانبی مثل Open Camera، Footej Camera و ...) را انتخاب کند، نه فقط
 *    برنامه دوربین پیش‌فرض/سیستمی.
 * ۳) هرگز اینتنت مستقیماً launch نمی‌شود بدون بررسی این‌که آیا اصلاً برنامه‌ای برای مدیریت آن
 *    وجود دارد؛ در غیر این صورت پیام فارسی مناسب نمایش داده می‌شود، نه کرش.
 */
object SafeCameraLauncher {

    sealed class LaunchResult {
        data class Ready(val chooserIntent: Intent) : LaunchResult()
        data object NoCameraAppFound : LaunchResult()
    }

    fun buildImageCaptureChooser(context: Context, outputUri: Uri): LaunchResult =
        buildChooser(context, MediaStore.ACTION_IMAGE_CAPTURE, outputUri, "انتخاب برنامه دوربین برای عکس")

    fun buildVideoCaptureChooser(context: Context, outputUri: Uri): LaunchResult =
        buildChooser(context, MediaStore.ACTION_VIDEO_CAPTURE, outputUri, "انتخاب برنامه دوربین برای ویدئو")

    private fun buildChooser(context: Context, action: String, outputUri: Uri, title: String): LaunchResult {
        val baseIntent = Intent(action).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // پیدا کردن تمام برنامه‌هایی که می‌توانند این اینتنت را مدیریت کنند
        val resolvedApps = context.packageManager.queryIntentActivities(baseIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolvedApps.isEmpty()) {
            return LaunchResult.NoCameraAppFound
        }

        // به هر برنامه یافت‌شده اجازه صریح نوشتن روی فایل خروجی داده می‌شود
        // (لازم برای عملکرد صحیح FileProvider با برنامه‌های دوربین شخص‌ثالث)
        for (info in resolvedApps) {
            context.grantUriPermission(
                info.activityInfo.packageName,
                outputUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val chooser = Intent.createChooser(baseIntent, title)
        return LaunchResult.Ready(chooser)
    }

    /** بررسی این‌که آیا لانچ اینتنت با خطای ActivityNotFoundException مواجه شد یا خیر، بدون کرش برنامه. */
    fun <T> tryLaunch(launch: () -> T): Boolean =
        try {
            launch()
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
}
