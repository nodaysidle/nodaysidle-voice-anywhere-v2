package com.nodaysidle.voiceanywhere.history

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TranscriptHistoryItem(
    val id: String,
    val createdAtMillis: Long,
    val text: String,
    val targetPackage: String,
    val resultLabel: String
)

object TranscriptHistoryStore {
    private const val PREFS = "voice_anywhere"
    private const val KEY_HISTORY_ENABLED = "transcript_history_enabled_v1"
    private const val KEY_HISTORY = "transcript_history_v1"
    private const val MAX_ITEMS = 50

    fun isEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_HISTORY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HISTORY_ENABLED, enabled)
            .apply()
        if (!enabled) clear(context)
    }

    fun list(context: Context): List<TranscriptHistoryItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, null)
            .orEmpty()
        return decode(raw)
    }

    fun add(
        context: Context,
        text: String,
        targetPackage: String,
        resultLabel: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (!isEnabled(context)) return

        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        val item = TranscriptHistoryItem(
            id = "$nowMillis-${cleanText.hashCode()}",
            createdAtMillis = nowMillis,
            text = cleanText,
            targetPackage = targetPackage,
            resultLabel = resultLabel
        )
        val updated = (listOf(item) + list(context))
            .distinctBy { it.id }
            .take(MAX_ITEMS)
        save(context, updated)
    }

    fun delete(context: Context, id: String) {
        save(context, list(context).filterNot { it.id == id })
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    internal fun decode(raw: String): List<TranscriptHistoryItem> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val id = json.optString("id")
                    val text = json.optString("text")
                    if (id.isBlank() || text.isBlank()) continue
                    add(
                        TranscriptHistoryItem(
                            id = id,
                            createdAtMillis = json.optLong("createdAtMillis"),
                            text = text,
                            targetPackage = json.optString("targetPackage"),
                            resultLabel = json.optString("resultLabel")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    internal fun encode(items: List<TranscriptHistoryItem>): String {
        val array = JSONArray()
        items.take(MAX_ITEMS).forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("createdAtMillis", item.createdAtMillis)
                .put("text", item.text)
                .put("targetPackage", item.targetPackage)
                .put("resultLabel", item.resultLabel)
            )
        }
        return array.toString()
    }

    private fun save(context: Context, items: List<TranscriptHistoryItem>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, encode(items))
            .apply()
    }
}
