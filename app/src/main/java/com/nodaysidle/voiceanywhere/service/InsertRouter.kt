package com.nodaysidle.voiceanywhere.service

/**
 * Pure insert-path router: a11y SET_TEXT → a11y PASTE → IME → clipboard.
 * Clipboard is last resort only — never the preferred cursor path.
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
     * [imeAvailable] means the Voice Anywhere IME is selected/running
     * (cursor commit possible now or on next input start).
     */
    fun channels(a11yBound: Boolean, imeAvailable: Boolean): List<Channel> = buildList {
        if (a11yBound) {
            add(Channel.A11Y_SET_TEXT)
            add(Channel.A11Y_PASTE)
        }
        if (imeAvailable) add(Channel.IME)
        add(Channel.CLIPBOARD)
    }

    /**
     * Pick the winning channel from attempt results.
     * [imeOk] covers immediate commitText **or** a deferred IME queue flush
     * and is ignored unless [imeAvailable] is true.
     */
    fun winner(
        a11yBound: Boolean,
        imeAvailable: Boolean,
        setTextOk: Boolean,
        pasteOk: Boolean,
        imeOk: Boolean
    ): Channel = when {
        a11yBound && setTextOk -> Channel.A11Y_SET_TEXT
        a11yBound && pasteOk -> Channel.A11Y_PASTE
        imeAvailable && imeOk -> Channel.IME
        else -> Channel.CLIPBOARD
    }

    /** True only when no cursor path succeeded — safe to show clipboard notification. */
    fun shouldNotifyClipboard(channel: Channel): Boolean =
        channel == Channel.CLIPBOARD
}
