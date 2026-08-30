package com.nodaysidle.voiceanywhere.stt

import org.junit.Assert.assertEquals
import org.junit.Test

class DictationLanguageTest {
    @Test
    fun cycleIncludesSlovenian() {
        assertEquals(listOf("EN", "IT", "SL"), DictationLanguage.cycle.map { it.tag })
    }

    @Test
    fun localeTagsMatchRecognizerExtras() {
        assertEquals("en-US", DictationLanguage.EN.localeTag)
        assertEquals("it-IT", DictationLanguage.IT.localeTag)
        assertEquals("sl-SI", DictationLanguage.SL.localeTag)
    }

    @Test
    fun iso6391CodesForOpenRouter() {
        assertEquals("en", DictationLanguage.EN.iso6391)
        assertEquals("it", DictationLanguage.IT.iso6391)
        assertEquals("sl", DictationLanguage.SL.iso6391)
    }

    @Test
    fun futoPickerLabels() {
        assertEquals("English", DictationLanguage.EN.futoPickerLabel)
        assertEquals("Italian", DictationLanguage.IT.futoPickerLabel)
        assertEquals("Slovenian", DictationLanguage.SL.futoPickerLabel)
    }

    @Test
    fun fromIndexWrapsSafely() {
        assertEquals(DictationLanguage.EN, DictationLanguage.fromIndex(0))
        assertEquals(DictationLanguage.SL, DictationLanguage.fromIndex(2))
        assertEquals(DictationLanguage.SL, DictationLanguage.fromIndex(99))
        assertEquals(DictationLanguage.EN, DictationLanguage.fromIndex(-1))
    }
}
