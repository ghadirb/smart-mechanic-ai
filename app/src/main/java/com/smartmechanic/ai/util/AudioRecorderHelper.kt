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

    fun startRecording(): File {
        val file = MediaFileFactory.newAudioFile(context)
        outputFile = file

        @Suppress("DEPRECATION")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

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
        return file
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
