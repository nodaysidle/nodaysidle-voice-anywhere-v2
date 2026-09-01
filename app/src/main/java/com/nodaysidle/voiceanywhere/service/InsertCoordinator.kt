package com.nodaysidle.voiceanywhere.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.nodaysidle.voiceanywhere.ime.VoiceInputMethodService

/**
 * Runs the insert cascade at the focused cursor:
 * a11y SET_TEXT → a11y PASTE → IME commitText → clipboard last resort.
 * Clipboard is always primed so ACTION_PASTE can work; it is not the primary path.
 */
object InsertCoordinator {
    private const val TAG = "VoiceAnywhereInsert"

    suspend fun insert(context: Context, text: String): InsertionFeedback {
        setClipboard(context, text)

        val a11yBound = VoiceAccessibilityService.isBound()
        var setTextOk = false
        var pasteOk = false
        if (a11yBound) {
            val result = VoiceAccessibilityService.tryInject(text)
            setTextOk = result?.first == true
            pasteOk = result?.second == true
        }

        val imeConnected = VoiceInputMethodService.isConnected()
        var imeOk = false
        if (!setTextOk && !pasteOk && imeConnected) {
            imeOk = VoiceInputMethodService.tryCommit(text)
        }

        val channel = InsertRouter.winner(
            a11yBound = a11yBound,
            imeConnected = imeConnected,
            setTextOk = setTextOk,
            pasteOk = pasteOk,
            imeOk = imeOk
        )
        val feedback = InsertionFeedback.from(channel)
        Log.d(
            TAG,
            "insert channel=$channel a11yBound=$a11yBound setText=$setTextOk paste=$pasteOk imeConnected=$imeConnected imeOk=$imeOk"
        )
        if (channel == InsertRouter.Channel.CLIPBOARD) {
            ClipboardNotification.show(context, text)
        }
        return feedback
    }

    fun setClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Voice Anywhere", text))
    }
}
