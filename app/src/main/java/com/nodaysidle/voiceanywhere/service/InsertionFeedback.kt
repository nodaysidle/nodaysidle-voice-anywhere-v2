package com.nodaysidle.voiceanywhere.service

data class InsertionFeedback(
    val state: FloatingMicOverlay.State,
    val label: String,
    val holdMillis: Long
) {
    companion object {
        fun from(setText: Boolean, paste: Boolean): InsertionFeedback = when {
            setText -> InsertionFeedback(
                state = FloatingMicOverlay.State.SUCCESS,
                label = "✓ SET",
                holdMillis = 1200L
            )
            paste -> InsertionFeedback(
                state = FloatingMicOverlay.State.PASTED,
                label = "✓ PST",
                holdMillis = 1200L
            )
            else -> InsertionFeedback(
                state = FloatingMicOverlay.State.COPIED,
                label = "↗ CPY",
                holdMillis = 2200L
            )
        }
    }
}
