package com.nodaysidle.voiceanywhere.polish

import org.junit.Assert.assertEquals
import org.junit.Test

class TextPostProcessorTest {
    @Test
    fun removesFillersNormalizesSpacingAndCapitalizes() {
        val input = "um hello  ndi  uh this is nodaysidle"
        assertEquals("Hello NDI this is NODAYSIDLE", TextPostProcessor.clean(input))
    }

    @Test
    fun keepsMeaningfulWordsAndTrimsTrailingSpaces() {
        val input = "  send this to kaly please   "
        assertEquals("Send this to Kaly please", TextPostProcessor.clean(input))
    }

    @Test
    fun removesLikeAndYouKnowFillers() {
        val input = "like I was thinking you know maybe we should do this"
        assertEquals("I was thinking maybe we should do this", TextPostProcessor.clean(input))
    }

    @Test
    fun collapsesRepeatedWords() {
        val input = "send the the message to to kaly"
        assertEquals("Send the message to Kaly", TextPostProcessor.clean(input))
    }

    @Test
    fun capitalizesAfterSentenceEnd() {
        val input = "hello. how are you? i'm fine. thanks"
        assertEquals("Hello. How are you? I'm fine. Thanks", TextPostProcessor.clean(input))
    }

    @Test
    fun handlesEmptyInput() {
        assertEquals("", TextPostProcessor.clean(""))
        assertEquals("", TextPostProcessor.clean("   "))
    }

    @Test
    fun handlesOnlyFillers() {
        assertEquals("", TextPostProcessor.clean("um uh like you know"))
    }

    @Test
    fun normalizesPunctuation() {
        val input = "hello ,world .how are you ?"
        assertEquals("Hello, world. How are you?", TextPostProcessor.clean(input))
    }

    @Test
    fun removesBasicallySoFillers() {
        val input = "basically so we need to ship this today right"
        assertEquals("We need to ship this today.", TextPostProcessor.clean(input))
    }

    @Test
    fun doesNotCollapseIntentionalRepetition() {
        // "very very" is intentional emphasis — should not collapse
        val input = "this is very very important"
        assertEquals("This is very very important", TextPostProcessor.clean(input))
    }
}
