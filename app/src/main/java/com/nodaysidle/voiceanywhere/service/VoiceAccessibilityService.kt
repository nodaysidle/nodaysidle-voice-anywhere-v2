package com.nodaysidle.voiceanywhere.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nodaysidle.voiceanywhere.stt.DictationLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Accessibility helper: editable-node cache + ACTION_SET_TEXT / ACTION_PASTE.
 *
 * Overlay and STT live in [VoiceKeepAliveService] so the pill survives when
 * MIUI kills this binding. Do not host TYPE_ACCESSIBILITY_OVERLAY here anymore.
 */
class VoiceAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastEditableNode: AccessibilityNodeInfo? = null
    private var lastEditableText: String = ""
    private var lastEditableCursor: Int = 0
    private var lastEditablePackage: String = ""
    private var lastEditableCapturedAtMillis: Long = 0
    private var futoAutoSelectInFlight = false
    private var lastFutoAutoSelectAtMillis: Long = 0
    private var currentLangIndex = 0
    private val prefs by lazy { getSharedPreferences("voice_anywhere", Context.MODE_PRIVATE) }
    private val selectedLanguage get() = DictationLanguage.fromIndex(currentLangIndex)

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        currentLangIndex = prefs.getInt(PREF_LANGUAGE_INDEX, 0)
            .coerceIn(0, DictationLanguage.cycle.lastIndex)
        // Kick the keep-alive FGS so overlay + process persistence start with a11y.
        VoiceKeepAliveService.start(this)
        Log.d(TAG, "Accessibility bound — keep-alive requested")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val source = event?.source
        val packageName = event?.packageName?.toString().orEmpty()
        if (packageName == FUTO_PACKAGE) {
            scheduleFutoLanguageAutoSelect()
        }
        if (source?.isEditable == true && shouldCacheEditablePackage(packageName)) {
            cacheEditableNode(source, packageName)
        }
        // Keep language index in sync with KeepAlive long-press cycles.
        currentLangIndex = prefs.getInt(PREF_LANGUAGE_INDEX, currentLangIndex)
            .coerceIn(0, DictationLanguage.cycle.lastIndex)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility interrupted")
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        scope.cancel()
        // Intentionally do NOT stop VoiceKeepAliveService — overlay must survive.
        Log.w(TAG, "Accessibility destroyed/unbound — keep-alive left running")
        super.onDestroy()
    }

    private fun scheduleFutoLanguageAutoSelect() {
        val now = SystemClock.uptimeMillis()
        if (futoAutoSelectInFlight || now - lastFutoAutoSelectAtMillis < FUTO_AUTO_SELECT_DEBOUNCE_MS) return
        futoAutoSelectInFlight = true
        scope.launch {
            try {
                delay(FUTO_PICKER_SETTLE_MS)
                if (autoSelectFutoLanguageIfVisible()) {
                    lastFutoAutoSelectAtMillis = SystemClock.uptimeMillis()
                }
            } finally {
                futoAutoSelectInFlight = false
            }
        }
    }

    private fun autoSelectFutoLanguageIfVisible(): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != FUTO_PACKAGE) return false

        val label = selectedLanguage.futoPickerLabel
        val labelNode = findNodeWithText(root, label) ?: return false
        val target = clickableSelfOrAncestor(labelNode) ?: return false
        val clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (clicked) Log.d(TAG, "FUTO language picker auto-selected $label")
        return clicked
    }

    private suspend fun injectText(text: String): Pair<Boolean, Boolean> {
        // Clipboard is set by InsertCoordinator before this is called.
        cacheCurrentFocusedEditable()
        val focusedViaLive = waitForFocusedEditable()
        val usingStaleNode = focusedViaLive == null
        val focused = focusedViaLive
            ?: lastEditableNode?.takeIf { isLastEditableFresh() }
            ?: return Pair(false, false).also {
                Log.w(TAG, "No editable node available for insertion")
            }

        if (!focused.refresh()) {
            return Pair(false, false).also {
                Log.w(TAG, "Editable node refresh failed")
            }
        }

        val existing = lastEditableText
        val safePos = lastEditableCursor.coerceIn(0, existing.length)

        Log.d(TAG, "injectText: usingStale=$usingStaleNode existingLength=${existing.length} cursor=$safePos")

        val plan = TextInsertionMerger.merge(existing, safePos, text)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, plan.mergedText)
        }
        val setTextOk = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (setTextOk) {
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, plan.cursorAfterInsert)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, plan.cursorAfterInsert)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        }

        val pasteOk = if (!setTextOk) focused.performAction(AccessibilityNodeInfo.ACTION_PASTE) else false
        Log.d(TAG, "Insert attempt setText=$setTextOk paste=$pasteOk cursor=$safePos textLength=${text.length}")
        return Pair(setTextOk, pasteOk)
    }

    private fun cacheCurrentFocusedEditable(): Boolean {
        val focused = currentFocusedEditable()
        if (focused != null) {
            cacheEditableNode(focused, focused.packageName?.toString().orEmpty())
            return true
        }
        return false
    }

    private fun currentFocusedEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val rootPackage = root.packageName?.toString().orEmpty()
        if (!shouldCacheEditablePackage(rootPackage)) return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?.takeIf { it.isEditable }
            ?: findEditable(root)
    }

    private fun cacheEditableNode(source: AccessibilityNodeInfo, packageName: String) {
        lastEditableNode = AccessibilityNodeInfo.obtain(source)
        lastEditablePackage = packageName
        lastEditableCapturedAtMillis = SystemClock.uptimeMillis()
        val rawText = source.text?.toString().orEmpty()
        val hint = source.hintText?.toString().orEmpty()
        lastEditableText = when {
            rawText == hint -> ""
            source.textSelectionStart == -1 -> "".also {
                Log.d(TAG, "Hint strip: placeholder detected length=${rawText.length}")
            }
            else -> rawText
        }
        lastEditableCursor = if (lastEditableText.isEmpty()) 0
        else source.textSelectionStart.coerceAtLeast(0)
        Log.d(
            TAG,
            "Cached editable node package=$packageName textLength=${lastEditableText.length} cursor=$lastEditableCursor"
        )
    }

    private fun shouldCacheEditablePackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return packageName !in IGNORED_EDITABLE_PACKAGES &&
            packageName != this.packageName &&
            !packageName.contains("inputmethod", ignoreCase = true)
    }

    private fun isLastEditableFresh(): Boolean =
        lastEditableCapturedAtMillis > 0 &&
            SystemClock.uptimeMillis() - lastEditableCapturedAtMillis <= STALE_EDITABLE_NODE_MS

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isFocused) return node
        for (i in 0 until node.childCount) {
            val found = findEditable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun findNodeWithText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val nodeText = node.text?.toString()
        val nodeDescription = node.contentDescription?.toString()
        if (nodeText.equals(text, ignoreCase = true) || nodeDescription.equals(text, ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findNodeWithText(node.getChild(i), text)
            if (found != null) return found
        }
        return null
    }

    private fun clickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(MAX_CLICKABLE_PARENT_DEPTH) {
            val candidate = current ?: return null
            if (candidate.isEnabled && candidate.isClickable) return candidate
            current = candidate.parent
        }
        return null
    }

    private suspend fun waitForFocusedEditable(): AccessibilityNodeInfo? {
        repeat(FOCUS_RETRY_COUNT) {
            val focused = currentFocusedEditable()
            if (focused != null) return focused
            delay(FOCUS_RETRY_DELAY_MS)
        }
        return null
    }

    companion object {
        private const val TAG = "VoiceAnywhereService"
        private const val PREF_LANGUAGE_INDEX = "selected_language_index"
        private const val FOCUS_RETRY_COUNT = 6
        private const val FOCUS_RETRY_DELAY_MS = 120L
        private const val STALE_EDITABLE_NODE_MS = 120_000L
        private const val FUTO_PACKAGE = "org.futo.voiceinput"
        private const val FUTO_PICKER_SETTLE_MS = 60L
        private const val FUTO_AUTO_SELECT_DEBOUNCE_MS = 900L
        private const val MAX_CLICKABLE_PARENT_DEPTH = 6
        private val IGNORED_EDITABLE_PACKAGES = setOf(
            "com.android.systemui",
            FUTO_PACKAGE,
            "org.futo.inputmethod.latin"
        )

        @Volatile
        private var activeService: VoiceAccessibilityService? = null

        /** True only when the service instance is connected — not merely Settings-enabled. */
        fun isBound(): Boolean = activeService != null

        fun hasFreshEditable(): Boolean {
            val service = activeService ?: return false
            if (service.cacheCurrentFocusedEditable()) return true
            return service.isLastEditableFresh()
        }

        fun lastTargetPackage(): String = activeService?.lastEditablePackage.orEmpty()

        /**
         * Attempt SET_TEXT then PASTE on the cached/focused node.
         * Returns null when unbound; otherwise (setTextOk, pasteOk).
         */
        suspend fun tryInject(text: String): Pair<Boolean, Boolean>? {
            val service = activeService ?: return null
            return service.injectText(text)
        }

        @Deprecated("Dictation results go to VoiceKeepAliveService", ReplaceWith("VoiceKeepAliveService.deliverDictationResult(text)"))
        fun deliverDictationResult(text: String) {
            VoiceKeepAliveService.deliverDictationResult(text)
            // If keep-alive is not up yet, nothing to do — result is dropped with log.
            if (!VoiceKeepAliveService.isRunning()) {
                Log.w(TAG, "Dictation result with no keep-alive; dropped length=${text.length}")
            }
        }

        @Deprecated("Dictation errors go to VoiceKeepAliveService", ReplaceWith("VoiceKeepAliveService.deliverDictationError()"))
        fun deliverDictationError() {
            VoiceKeepAliveService.deliverDictationError()
        }

        fun reinsertHistoryText(text: String): Boolean =
            VoiceKeepAliveService.reinsertHistoryText(text)
    }
}
