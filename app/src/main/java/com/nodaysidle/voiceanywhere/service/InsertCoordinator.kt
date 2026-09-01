package com.nodaysidle.voiceanywhere.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.nodaysidle.voiceanywhere.ime.VoiceInputMethodService

/**
 * Cursor autopaste cascade — never clipboard-first:
 * 1. Accessibility bound → ACTION_SET_TEXT, then ACTION_PASTE into the focused field
 * 2. Else IME available → commitText / setComposingText at the editor cursor
 * 3. Clipboard + notification only when neither cursor path can run
 *
 * Clipboard is primed only for a11y PASTE (needs clip) or true last-resort fallback.
 */
object InsertCoordinator {
    private const val TAG = "VoiceAnywhereInsert"

    suspend fun insert(context: Context, text: String): InsertionFeedback {
        val a11yBound = VoiceAccessibilityService.isBound()
        var setTextOk = false
        var pasteOk = false

        if (a11yBound) {
            // PASTE needs the clip; SET_TEXT does not — priming here serves PASTE only.
            setClipboard(context, text)
            val result = VoiceAccessibilityService.tryInject(text)
            setTextOk = result?.first == true
            pasteOk = result?.second == true
        }

        val imeAvailable = VoiceInputMethodService.isAvailableForInsert()
        var imeOk = false
        if (!setTextOk && !pasteOk && imeAvailable) {
            val imeResult = VoiceInputMethodService.tryCommit(text)
            imeOk = imeResult.committed || imeResult.queued
            Log.d(
                TAG,
                "IME cursor path committed=${imeResult.committed} queued=${imeResult.queued}"
            )
        }

        val channel = InsertRouter.winner(
            a11yBound = a11yBound,
            imeAvailable = imeAvailable,
            setTextOk = setTextOk,
            pasteOk = pasteOk,
            imeOk = imeOk
        )
        val feedback = InsertionFeedback.from(channel)
        Log.d(
            TAG,
            "insert channel=$channel a11yBound=$a11yBound setText=$setTextOk paste=$pasteOk imeAvailable=$imeAvailable imeOk=$imeOk"
        )

        if (InsertRouter.shouldNotifyClipboard(channel)) {
            setClipboard(context, text)
            ClipboardNotification.show(context, text)
        }
        return feedback
    }

    fun setClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Voice Anywhere", text))
    }
}
