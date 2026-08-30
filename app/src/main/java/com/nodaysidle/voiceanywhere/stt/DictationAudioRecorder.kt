package com.nodaysidle.voiceanywhere.stt

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Compact AAC/M4A recorder for the OpenRouter STT path.
 * Amplitude is polled for the overlay waveform.
 */
class DictationAudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(): File {
        stopInternal(discard = true)
        val file = File(context.cacheDir, "dictation_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioSamplingRate(16_000)
        mediaRecorder.setAudioChannels(1)
        mediaRecorder.setAudioEncodingBitRate(64_000)
        mediaRecorder.setOutputFile(file.absolutePath)
        mediaRecorder.prepare()
        mediaRecorder.start()
        recorder = mediaRecorder
        outputFile = file
        return file
    }

    fun maxAmplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun stop(): File? {
        val file = outputFile
        stopInternal(discard = false)
        return file?.takeIf { it.exists() && it.length() > 0L }
    }

    fun cancel() {
        stopInternal(discard = true)
    }

    private fun stopInternal(discard: Boolean) {
        val active = recorder
        recorder = null
        val file = outputFile
        outputFile = null
        if (active != null) {
            runCatching {
                active.stop()
            }
            runCatching { active.reset() }
            runCatching { active.release() }
        }
        if (discard) {
            file?.delete()
        }
    }
}
