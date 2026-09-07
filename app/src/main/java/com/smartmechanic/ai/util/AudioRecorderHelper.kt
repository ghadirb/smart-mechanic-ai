package com.smartmechanic.ai.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.smartmechanic.ai.config.AIConfig
import java.io.File

/**
 * Wrapper ساده روی MediaRecorder برای ضبط صدای موتور (بخش ۷ سند طراحی).
 * حداکثر مدت ضبط توسط AIConfig.MAX_AUDIO_DURATION_SECONDS کنترل می‌شود.
 */
class AudioRecorderHelper(private val context: Context) {

    private var recorder: MediaRecorder? = null
    var outputFile: File? = null
        private set

    /** در صورت هر گونه خطای سخت‌افزار/سیستمی (میکروفون در دسترس نیست، مجوز داده نشده و ...)
     *  به‌جای پرتاب Exception و کرش برنامه، مقدار null برمی‌گرداند. */
    fun startRecording(): File? {
        val file = MediaFileFactory.newAudioFile(context)

        @Suppress("DEPRECATION")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        return try {
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                setMaxDuration(AIConfig.MAX_AUDIO_DURATION_SECONDS * 1000)
                prepare()
                start()
            }
            recorder = mr
            outputFile = file
            file
        } catch (e: Exception) {
            // مثلاً IllegalStateException یا IOException وقتی میکروفون در دسترس نیست
            runCatching { mr.release() }
            recorder = null
            outputFile = null
            file.delete()
            null
        }
    }

    fun stopRecording(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            outputFile
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            null
        }
    }

    fun cancelRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

}
