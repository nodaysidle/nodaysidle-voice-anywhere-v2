package com.nodaysidle.voiceanywhere.service

/**
 * Pure insert-path router: a11y SET_TEXT → a11y PASTE → IME → clipboard.
 * No Android framework deps so unit tests can pin fallback order.
 */
object InsertRouter {
    enum class Channel {
        A11Y_SET_TEXT,
        A11Y_PASTE,
        IME,
        CLIPBOARD
    }

    /**
     * Ordered attempt list for the current runtime.
     * Clipboard is always last as the safety net.
     */
    fun channels(a11yBound: Boolean, imeConnected: Boolean): List<Channel> = buildList {
        if (a11yBound) {
            add(Channel.A11Y_SET_TEXT)
            add(Channel.A11Y_PASTE)
        }
        if (imeConnected) add(Channel.IME)
        add(Channel.CLIPBOARD)
    }

    /**
     * Pick the winning channel from attempt results.
     * [imeOk] is ignored unless [imeConnected] is true.
     */
    fun winner(
        a11yBound: Boolean,
        imeConnected: Boolean,
        setTextOk: Boolean,
        pasteOk: Boolean,
        imeOk: Boolean
    ): Channel = when {
        a11yBound && setTextOk -> Channel.A11Y_SET_TEXT
        a11yBound && pasteOk -> Channel.A11Y_PASTE
        imeConnected && imeOk -> Channel.IME
        else -> Channel.CLIPBOARD
    }
}
