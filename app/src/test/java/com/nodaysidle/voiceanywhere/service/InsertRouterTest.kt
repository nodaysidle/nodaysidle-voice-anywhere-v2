package com.nodaysidle.voiceanywhere.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
            InsertRouter.channels(a11yBound = true, imeAvailable = false)
        )
    }

    @Test
    fun deadA11yWithImeUsesImeBeforeClipboard() {
        assertEquals(
            listOf(InsertRouter.Channel.IME, InsertRouter.Channel.CLIPBOARD),
            InsertRouter.channels(a11yBound = false, imeAvailable = true)
        )
    }

    @Test
    fun deadA11yWithoutImeFallsToClipboardOnly() {
        assertEquals(
            listOf(InsertRouter.Channel.CLIPBOARD),
            InsertRouter.channels(a11yBound = false, imeAvailable = false)
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
            InsertRouter.channels(a11yBound = true, imeAvailable = true)
        )
    }

    @Test
    fun winnerPrefersSetTextWhenBound() {
        assertEquals(
            InsertRouter.Channel.A11Y_SET_TEXT,
            InsertRouter.winner(
                a11yBound = true,
                imeAvailable = true,
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
                imeAvailable = false,
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
                imeAvailable = true,
                setTextOk = false,
                pasteOk = false,
                imeOk = true
            )
        )
    }

    @Test
    fun winnerIgnoresImeOkUnlessAvailable() {
        assertEquals(
            InsertRouter.Channel.CLIPBOARD,
            InsertRouter.winner(
                a11yBound = false,
                imeAvailable = false,
                setTextOk = false,
                pasteOk = false,
                imeOk = true
            )
        )
    }

    @Test
    fun winnerClipboardIsLastResortOnly() {
        assertEquals(
            InsertRouter.Channel.CLIPBOARD,
            InsertRouter.winner(
                a11yBound = true,
                imeAvailable = true,
                setTextOk = false,
                pasteOk = false,
                imeOk = false
            )
        )
    }

    @Test
    fun clipboardNotificationOnlyForClipboardChannel() {
        assertTrue(InsertRouter.shouldNotifyClipboard(InsertRouter.Channel.CLIPBOARD))
        assertFalse(InsertRouter.shouldNotifyClipboard(InsertRouter.Channel.A11Y_SET_TEXT))
        assertFalse(InsertRouter.shouldNotifyClipboard(InsertRouter.Channel.A11Y_PASTE))
        assertFalse(InsertRouter.shouldNotifyClipboard(InsertRouter.Channel.IME))
    }
}
