package com.wasal.app.util

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/**
 * مدير التسجيل الصوتي للرسائل الصوتية داخل المحادثة.
 *
 * يسجل بصيغة AAC داخل حاوية MPEG-4 (audio/mp4)، وهي صيغة
 * متوافقة مع تحقق بصمة الملفات على خادم الوسائط.
 */
class VoiceRecorderManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    var isRecording: Boolean = false
        private set

    /**
     * يبدأ التسجيل في ملف مؤقت داخل ذاكرة التطبيق المؤقتة.
     * يعيد true عند نجاح بدء التسجيل.
     */
    fun start(): Boolean {
        if (isRecording) return false

        return try {
            val file = File.createTempFile(
                "wasal_voice_",
                ".m4a",
                context.cacheDir
            )

            val newRecorder = MediaRecorder()

            newRecorder.setAudioSource(
                MediaRecorder.AudioSource.MIC
            )
            newRecorder.setOutputFormat(
                MediaRecorder.OutputFormat.MPEG_4
            )
            newRecorder.setAudioEncoder(
                MediaRecorder.AudioEncoder.AAC
            )
            newRecorder.setAudioEncodingBitRate(96000)
            newRecorder.setAudioSamplingRate(44100)
            newRecorder.setOutputFile(file.absolutePath)
            newRecorder.prepare()
            newRecorder.start()

            recorder = newRecorder
            outputFile = file
            isRecording = true
            true
        } catch (_: Exception) {
            runCatching { recorder?.release() }
            recorder = null
            outputFile?.delete()
            outputFile = null
            false
        }
    }

    /**
     * يوقف التسجيل ويعيد الملف المسجل عند النجاح.
     * يعيد null عند فشل التسجيل أو كونه فارغًا.
     */
    fun stopAndReturnFile(): File? {
        if (!isRecording) return null

        val current = recorder
        val file = outputFile

        isRecording = false
        recorder = null
        outputFile = null

        if (current == null || file == null) return null

        return try {
            current.stop()
            current.release()

            if (file.length() > 0L) {
                file
            } else {
                file.delete()
                null
            }
        } catch (_: Exception) {
            runCatching { current.release() }
            file.delete()
            null
        }
    }

    /** يلغي التسجيل الحالي ويحذف الملف المؤقت. */
    fun cancel() {
        val current = recorder
        val file = outputFile

        isRecording = false
        recorder = null
        outputFile = null

        if (current != null) {
            runCatching {
                current.stop()
                current.release()
            }
        }

        file?.delete()
    }
}
