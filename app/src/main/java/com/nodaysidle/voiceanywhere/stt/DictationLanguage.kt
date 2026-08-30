package com.nodaysidle.voiceanywhere.stt

/**
 * Long-press language cycle: EN → IT → SL.
 */
enum class DictationLanguage(
    val tag: String,
    val localeTag: String,
    val iso6391: String,
    val futoPickerLabel: String
) {
    EN("EN", "en-US", "en", "English"),
    IT("IT", "it-IT", "it", "Italian"),
    SL("SL", "sl-SI", "sl", "Slovenian");

    companion object {
        val cycle: List<DictationLanguage> = entries

        fun fromIndex(index: Int): DictationLanguage =
            cycle[index.coerceIn(0, cycle.lastIndex)]
    }
}
