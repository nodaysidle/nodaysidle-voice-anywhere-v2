package com.nodaysidle.voiceanywhere.ime

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Minimal Voice Anywhere IME — cursor insert when Accessibility binding is dead.
 *
 * User enables this keyboard once in system settings and selects it for the
 * focused field. [tryCommit] autopastes at the cursor via commitText /
 * setComposingText — not a share sheet, not clipboard-first.
 * Not a full keyboard; switch back to your normal IME for typing.
 */
class VoiceInputMethodService : InputMethodService() {
    private var statusLabel: TextView? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        Log.d(TAG, "IME created")
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        Log.d(TAG, "IME destroyed")
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(20), dp(16), dp(20))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0D0A"))
            }
        }

        root.addView(TextView(this).apply {
            text = "VOICE ANYWHERE"
            setTextColor(Color.parseColor("#C8FF00"))
            textSize = 12f
            letterSpacing = 0.16f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        })

        statusLabel = TextView(this).apply {
            text = "Dictation insert ready · switch back to your keyboard to type"
            setTextColor(Color.parseColor("#A9AEA5"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
            setLineSpacing(0f, 1.15f)
        }
        root.addView(statusLabel)

        root.addView(TextView(this).apply {
            text = "TAP TO HIDE"
            setTextColor(Color.parseColor("#9DD100"))
            textSize = 11f
            letterSpacing = 0.1f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            isClickable = true
            setOnClickListener { requestHideSelf(0) }
        })

        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        flushPendingIfAny()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        flushPendingIfAny()
    }

    private fun flushPendingIfAny() {
        val pending = pendingTranscript ?: return
        if (tryCommitInternal(pending)) {
            pendingTranscript = null
            statusLabel?.text = "Inserted · switch back to your keyboard to type"
            Log.d(TAG, "Flushed pending transcript length=${pending.length}")
        }
    }

    private fun tryCommitInternal(text: String): Boolean {
        val ic: InputConnection = currentInputConnection ?: return false
        if (text.isEmpty()) return false
        // Autopaste at the focused editor cursor.
        if (ic.commitText(text, 1)) {
            Log.d(TAG, "commitText ok length=${text.length}")
            return true
        }
        // Some editors accept composing then finish better than a single commit.
        ic.beginBatchEdit()
        val composed = runCatching {
            ic.setComposingText(text, 1)
            ic.finishComposingText()
        }.getOrDefault(false)
        ic.endBatchEdit()
        Log.d(TAG, "composing fallback ok=$composed length=${text.length}")
        return composed
    }

    companion object {
        private const val TAG = "VoiceAnywhereIme"

        @Volatile
        private var activeInstance: VoiceInputMethodService? = null

        @Volatile
        private var pendingTranscript: String? = null

        data class CommitResult(
            /** commitText / composing wrote into the focused editor now. */
            val committed: Boolean,
            /** Held for onStartInput flush — still a cursor path, not clipboard. */
            val queued: Boolean
        )

        /** True when this IME is selected and has an active input connection. */
        fun isConnected(): Boolean {
            val ime = activeInstance ?: return false
            return ime.currentInputConnection != null
        }

        fun isRunning(): Boolean = activeInstance != null

        /**
         * IME can take cursor insert now (InputConnection) or after the next
         * focused-field start (service selected). Used to avoid clipboard-first.
         */
        fun isAvailableForInsert(): Boolean = isRunning()

        /**
         * Autopaste at the focused editor cursor.
         * Returns [CommitResult.committed] on immediate write, [CommitResult.queued]
         * when the IME is selected but will flush on the next input start.
         * Never opens a share sheet.
         */
        fun tryCommit(text: String): CommitResult {
            if (text.isBlank()) return CommitResult(committed = false, queued = false)
            val ime = activeInstance
            if (ime != null && ime.currentInputConnection != null) {
                val ok = ime.tryCommitInternal(text)
                if (ok) {
                    pendingTranscript = null
                    return CommitResult(committed = true, queued = false)
                }
            }
            if (ime != null) {
                pendingTranscript = text
                Log.d(TAG, "Queued transcript for IME cursor flush length=${text.length}")
                return CommitResult(committed = false, queued = true)
            }
            Log.d(TAG, "IME unavailable for cursor insert length=${text.length}")
            return CommitResult(committed = false, queued = false)
        }

        fun clearPending() {
            pendingTranscript = null
        }

        fun hasPending(): Boolean = pendingTranscript != null
    }
}
