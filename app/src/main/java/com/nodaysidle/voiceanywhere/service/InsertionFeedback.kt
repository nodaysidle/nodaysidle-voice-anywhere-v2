package com.nodaysidle.voiceanywhere.service

data class InsertionFeedback(
    val state: FloatingMicOverlay.State,
    val label: String,
    val holdMillis: Long
) {
    companion object {
        fun from(setText: Boolean, paste: Boolean): InsertionFeedback =
            from(InsertRouter.winner(
                a11yBound = setText || paste,
                imeConnected = false,
                setTextOk = setText,
                pasteOk = paste,
                imeOk = false
            ))

        fun from(setText: Boolean, paste: Boolean, ime: Boolean): InsertionFeedback =
            from(InsertRouter.winner(
                a11yBound = setText || paste,
                imeConnected = ime,
                setTextOk = setText,
                pasteOk = paste,
                imeOk = ime
            ))

        fun from(channel: InsertRouter.Channel): InsertionFeedback = when (channel) {
            InsertRouter.Channel.A11Y_SET_TEXT -> InsertionFeedback(
                state = FloatingMicOverlay.State.SUCCESS,
                label = "✓ SET",
                holdMillis = 1200L
            )
            InsertRouter.Channel.A11Y_PASTE -> InsertionFeedback(
                state = FloatingMicOverlay.State.PASTED,
                label = "✓ PST",
                holdMillis = 1200L
            )
            InsertRouter.Channel.IME -> InsertionFeedback(
                state = FloatingMicOverlay.State.IME,
                label = "✓ IME",
                holdMillis = 1200L
            )
            InsertRouter.Channel.CLIPBOARD -> InsertionFeedback(
                state = FloatingMicOverlay.State.COPIED,
                label = "↗ CPY",
                holdMillis = 2200L
            )
        }
    }
}
