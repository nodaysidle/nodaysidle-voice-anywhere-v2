package com.nodaysidle.voiceanywhere.stt

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Cloud STT via OpenRouter `/api/v1/audio/transcriptions`.
 * Default model: Deepgram Nova-3 (low-latency, multilingual including SL).
 */
class OpenRouterSttClient(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    suspend fun transcribe(audioFile: File, languageIso6391: String): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext ""
            if (!audioFile.exists() || audioFile.length() == 0L) return@withContext ""

            val format = when (audioFile.extension.lowercase()) {
                "m4a", "mp4", "aac" -> "m4a"
                "mp3" -> "mp3"
                "ogg" -> "ogg"
                "webm" -> "webm"
                "flac" -> "flac"
                else -> "wav"
            }
            val base64 = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
            val body = JSONObject()
                .put("model", model)
                .put(
                    "input_audio",
                    JSONObject()
                        .put("data", base64)
                        .put("format", format)
                )
                .put("language", languageIso6391)
                .toString()

            val request = Request.Builder()
                .url(TRANSCRIPTIONS_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://nodaysidle.com")
                .addHeader("X-Title", "Voice Anywhere")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("OpenRouter STT HTTP ${response.code}: ${payload.take(200)}")
                }
                JSONObject(payload).optString("text").trim()
            }
        }

    companion object {
        const val DEFAULT_MODEL = "deepgram/nova-3"
        private const val TRANSCRIPTIONS_URL = "https://openrouter.ai/api/v1/audio/transcriptions"
    }
}
