package com.nodaysidle.voiceanywhere.polish

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DeepSeekTextPolisher(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    suspend fun polish(rawText: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext TextPostProcessor.clean(rawText)
        val body = JSONObject()
            .put("model", "deepseek-chat")
            .put("temperature", 0.1)
            .put("max_tokens", 500)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", "Clean dictated speech into final text. Remove fillers, repeated words, and false starts. Preserve meaning. Return only the final text, no quotes."))
                .put(JSONObject().put("role", "user").put("content", rawText))
            )
            .toString()

        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use TextPostProcessor.clean(rawText)
                val json = JSONObject(response.body?.string().orEmpty())
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                    .ifBlank { TextPostProcessor.clean(rawText) }
            }
        }.getOrElse { TextPostProcessor.clean(rawText) }
    }
}
