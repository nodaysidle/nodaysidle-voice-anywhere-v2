package com.nodaysidle.voiceanywhere.service

import org.junit.Assert.assertEquals
import org.junit.Test

class InsertionFeedbackTest {
    @Test
    fun setTextOutcomeShowsDirectInsertionMode() {
        val feedback = InsertionFeedback.from(setText = true, paste = false)

        assertEquals("✓ SET", feedback.label)
        assertEquals(1200L, feedback.holdMillis)
        assertEquals(FloatingMicOverlay.State.SUCCESS, feedback.state)
    }

    @Test
    fun pasteOutcomeShowsPasteInsertionMode() {
        val feedback = InsertionFeedback.from(setText = false, paste = true)

        assertEquals("✓ PST", feedback.label)
        assertEquals(1200L, feedback.holdMillis)
        assertEquals(FloatingMicOverlay.State.PASTED, feedback.state)
    }

    @Test
    fun fallbackOutcomeShowsCopiedSoUserKnowsToPasteManually() {
        val feedback = InsertionFeedback.from(setText = false, paste = false)

        assertEquals("↗ CPY", feedback.label)
        assertEquals(2200L, feedback.holdMillis)
        assertEquals(FloatingMicOverlay.State.COPIED, feedback.state)
    }

    @Test
    fun overlaySupportsNoFieldGuardState() {
        assertEquals(FloatingMicOverlay.State.NO_FIELD, FloatingMicOverlay.State.valueOf("NO_FIELD"))
    }
}
