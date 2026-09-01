package com.nodaysidle.voiceanywhere.service

import org.junit.Assert.assertEquals
import org.junit.Test

class InsertRouterTest {
    @Test
    fun boundA11yPrefersSetTextThenPasteBeforeClipboard() {
        assertEquals(
            listOf(
                InsertRouter.Channel.A11Y_SET_TEXT,
                InsertRouter.Channel.A11Y_PASTE,
                InsertRouter.Channel.CLIPBOARD
            ),
            InsertRouter.channels(a11yBound = true, imeConnected = false)
        )
    }

    @Test
    fun deadA11yWithImeUsesImeBeforeClipboard() {
        assertEquals(
            listOf(InsertRouter.Channel.IME, InsertRouter.Channel.CLIPBOARD),
            InsertRouter.channels(a11yBound = false, imeConnected = true)
        )
    }

    @Test
    fun deadA11yWithoutImeFallsToClipboardOnly() {
        assertEquals(
            listOf(InsertRouter.Channel.CLIPBOARD),
            InsertRouter.channels(a11yBound = false, imeConnected = false)
        )
    }

    @Test
    fun boundPlusImeOrdersSetPasteImeClipboard() {
        assertEquals(
            listOf(
                InsertRouter.Channel.A11Y_SET_TEXT,
                InsertRouter.Channel.A11Y_PASTE,
                InsertRouter.Channel.IME,
                InsertRouter.Channel.CLIPBOARD
            ),
            InsertRouter.channels(a11yBound = true, imeConnected = true)
        )
    }

    @Test
    fun winnerPrefersSetTextWhenBound() {
        assertEquals(
            InsertRouter.Channel.A11Y_SET_TEXT,
            InsertRouter.winner(
                a11yBound = true,
                imeConnected = true,
                setTextOk = true,
                pasteOk = true,
                imeOk = true
            )
        )
    }

    @Test
    fun winnerUsesPasteWhenSetTextFails() {
        assertEquals(
            InsertRouter.Channel.A11Y_PASTE,
            InsertRouter.winner(
                a11yBound = true,
                imeConnected = false,
                setTextOk = false,
                pasteOk = true,
                imeOk = false
            )
        )
    }

    @Test
    fun winnerUsesImeWhenA11yDead() {
        assertEquals(
            InsertRouter.Channel.IME,
            InsertRouter.winner(
                a11yBound = false,
                imeConnected = true,
                setTextOk = false,
                pasteOk = false,
                imeOk = true
            )
        )
    }

    @Test
    fun winnerIgnoresImeOkUnlessConnected() {
        assertEquals(
            InsertRouter.Channel.CLIPBOARD,
            InsertRouter.winner(
                a11yBound = false,
                imeConnected = false,
                setTextOk = false,
                pasteOk = false,
                imeOk = true
            )
        )
    }

    @Test
    fun winnerClipboardIsLastResort() {
        assertEquals(
            InsertRouter.Channel.CLIPBOARD,
            InsertRouter.winner(
                a11yBound = true,
                imeConnected = true,
                setTextOk = false,
                pasteOk = false,
                imeOk = false
            )
        )
    }
}
