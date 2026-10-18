package com.kaamchor.util

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/**
 * Minimal MediaRecorder wrapper for voice notes (MPEG-4 / AAC in an .m4a file).
 * Records into the app cache and exposes a content Uri that the uploader reads.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt: Long = 0

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean {
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val out = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(64_000)
            rec.setAudioSamplingRate(44_100)
            rec.setOutputFile(out.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            file = out
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { rec.release() }
            false
        }
    }

    /** Returns (contentUri, durationMillis) or null if nothing usable was recorded. */
    fun stop(): Pair<Uri, Long>? {
        val rec = recorder ?: return null
        val out = file
        recorder = null
        file = null
        return try {
            rec.stop()
            rec.release()
        } catch (e: Exception) {
            runCatching { rec.release() }
            out?.delete()
            return null
        }
        val duration = System.currentTimeMillis() - startedAt
        if (duration < 500 || out == null || !out.exists()) {
            out?.delete()
            return null
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        uri to duration
    }

    fun cancel() {
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
        file?.delete()
        file = null
    }
}
