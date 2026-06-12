package com.nodaysidle.voiceanywhere.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

import com.nodaysidle.voiceanywhere.DictationActivity
import com.nodaysidle.voiceanywhere.history.TranscriptHistoryStore
import com.nodaysidle.voiceanywhere.polish.DeepSeekTextPolisher
import com.nodaysidle.voiceanywhere.polish.TextPostProcessor
import com.nodaysidle.voiceanywhere.security.DeepSeekKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VoiceAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlay: FloatingMicOverlay? = null
    private var isListening = false
    private var lastEditableNode: AccessibilityNodeInfo? = null
    private var lastEditableText: String = ""
    private var lastEditableCursor: Int = 0
    private var lastEditablePackage: String = ""
    private var lastEditableCapturedAtMillis: Long = 0
    private var futoAutoSelectInFlight = false
    private var lastFutoAutoSelectAtMillis: Long = 0

    // Language cycling: long-press overlay to cycle
    private val supportedLanguages = listOf("EN", "IT")
    private var currentLangIndex = 0
    private val prefs by lazy { getSharedPreferences("voice_anywhere", Context.MODE_PRIVATE) }
    private val selectedLanguage get() = supportedLanguages[currentLangIndex]
    private val selectedLocale get() = when (selectedLanguage) {
        "IT" -> "it-IT"
        else -> "en-US"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        runCatching {
            activeService = this
            currentLangIndex = prefs.getInt(PREF_LANGUAGE_INDEX, 0).coerceIn(0, supportedLanguages.lastIndex)
            overlay = FloatingMicOverlay(
                this,
                onTap = { toggleListening() },
                onLongPress = { cycleLanguage() }
            ).also { it.show() }
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage)
            Log.d(TAG, "Floating overlay initialized")
        }.onFailure {
            Log.e(TAG, "Overlay init failed", it)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val source = event?.source
        val packageName = event?.packageName?.toString().orEmpty()
        if (isListening && packageName == FUTO_PACKAGE) {
            scheduleFutoLanguageAutoSelect()
        }
        if (source?.isEditable == true && shouldCacheEditablePackage(packageName)) {
            cacheEditableNode(source, packageName)
        }
    }

    override fun onInterrupt() {
        stopListening(cancel = true)
    }

    override fun onDestroy() {
        stopListening(cancel = true)
        if (activeService === this) activeService = null
        overlay?.hide()
        scope.cancel()
        super.onDestroy()
    }

    private fun toggleListening() {
        if (isListening) stopListening(cancel = false) else startListening()
    }

    private fun cycleLanguage() {
        currentLangIndex = (currentLangIndex + 1) % supportedLanguages.size
        prefs.edit().putInt(PREF_LANGUAGE_INDEX, currentLangIndex).apply()
        Log.d(TAG, "Language cycled to $selectedLanguage ($selectedLocale)")
        overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage)
    }

    private fun startListening() {
        // FUTO owns mic permission — no RECORD_AUDIO check needed here.
        // DictationActivity launches FUTO's RecognizeActivity, which handles everything.
        runCatching {
            if (!cacheCurrentFocusedEditable()) {
                showNoFieldFeedback()
                return
            }
            isListening = true
            overlay?.setState(FloatingMicOverlay.State.RECORDING)
            val intent = Intent(this, DictationActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra(DictationActivity.EXTRA_LANGUAGE, selectedLocale)
            }
            startActivity(intent)
            Log.d(TAG, "DictationActivity launched lang=$selectedLocale")
        }.onFailure {
            Log.e(TAG, "DictationActivity launch failed", it)
            isListening = false
            overlay?.setState(FloatingMicOverlay.State.ERROR)
        }
    }

    private fun stopListening(cancel: Boolean) {
        if (!isListening) return
        isListening = false
        if (cancel) {
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage)
        }
    }

    private fun showTransientError() {
        isListening = false
        overlay?.setState(FloatingMicOverlay.State.ERROR)
        scope.launch {
            delay(ERROR_HOLD_MS)
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage)
        }
    }

    private fun showNoFieldFeedback() {
        isListening = false
        overlay?.setState(FloatingMicOverlay.State.NO_FIELD)
        Log.w(TAG, "Dictation blocked: no focused editable field")
        scope.launch {
            delay(NO_FIELD_HOLD_MS)
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage)
        }
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

        val label = selectedFutoLanguageLabel()
        val labelNode = findNodeWithText(root, label) ?: return false
        val target = clickableSelfOrAncestor(labelNode) ?: return false
        val clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (clicked) Log.d(TAG, "FUTO language picker auto-selected $label")
        return clicked
    }

    private fun selectedFutoLanguageLabel(): String = when (selectedLanguage) {
        "IT" -> "Italian"
        else -> "English"
    }

    private fun handleTranscript(rawText: String) {
        isListening = false
        if (rawText.isBlank()) {
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage)
            return
        }
        overlay?.setState(FloatingMicOverlay.State.PROCESSING)
        val key = DeepSeekKeyStore.read(this)
        scope.launch {
            val finalText = if (key.isNotBlank()) DeepSeekTextPolisher(key).polish(rawText) else TextPostProcessor.clean(rawText)
            val (setTextOk, pasteOk) = injectText(finalText)
            val feedback = InsertionFeedback.from(setTextOk, pasteOk)
            TranscriptHistoryStore.add(
                context = this@VoiceAccessibilityService,
                text = finalText,
                targetPackage = lastEditablePackage,
                resultLabel = feedback.label
            )
            Log.d(TAG, "Dictation handled rawLength=${rawText.length} finalLength=${finalText.length} setText=$setTextOk paste=$pasteOk feedback=${feedback.label}")
            if (!setTextOk && !pasteOk) ClipboardNotification.show(this@VoiceAccessibilityService, finalText)
            overlay?.setState(feedback.state)
            delay(feedback.holdMillis)
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage)
        }
    }

    private fun handleHistoryReinsert(text: String) {
        if (text.isBlank()) return
        overlay?.setState(FloatingMicOverlay.State.PROCESSING)
        scope.launch {
            val (setTextOk, pasteOk) = injectText(text)
            val feedback = InsertionFeedback.from(setTextOk, pasteOk)
            Log.d(TAG, "History reinsert handled length=${text.length} setText=$setTextOk paste=$pasteOk feedback=${feedback.label}")
            if (!setTextOk && !pasteOk) ClipboardNotification.show(this@VoiceAccessibilityService, text)
            overlay?.setState(feedback.state)
            delay(feedback.holdMillis)
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage)
        }
    }

    private suspend fun injectText(text: String): Pair<Boolean, Boolean> {
        setClipboard(text)
        val focusedViaLive = waitForFocusedEditable()
        val usingStaleNode = focusedViaLive == null
        val focused = focusedViaLive
            ?: lastEditableNode?.takeIf { isLastEditableFresh() }
            ?: return Pair(false, false).also { Log.w(TAG, "No editable node available for insertion; clipboard still updated") }

        if (!focused.refresh()) {
            return Pair(false, false).also { Log.w(TAG, "Editable node refresh failed; clipboard still updated") }
        }

        // Use the pre-dictation snapshot for existing text and cursor position.
        // Reading .text from the node AFTER recognition returns is unreliable — some apps (WhatsApp,
        // YouTube, Google Messages) return their hint/placeholder string via .text with .hintText null.
        // The snapshot captured in onAccessibilityEvent (before mic tap) is always accurate.
        val existing = lastEditableText
        val safePos = lastEditableCursor.coerceIn(0, existing.length)

        Log.d(TAG, "injectText: usingStale=$usingStaleNode existingLength=${existing.length} cursor=$safePos")

        val plan = TextInsertionMerger.merge(existing, safePos, text)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, plan.mergedText)
        }
        val setTextOk = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        // After insertion, move cursor to end of inserted text so the user can keep typing
        if (setTextOk) {
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, plan.cursorAfterInsert)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, plan.cursorAfterInsert)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        }

        val pasteOk = if (!setTextOk) focused.performAction(AccessibilityNodeInfo.ACTION_PASTE) else false
        Log.d(TAG, "Insert attempt setText=$setTextOk paste=$pasteOk cursor=$safePos existingLength=${existing.length} textLength=${text.length}")
        return Pair(setTextOk, pasteOk)
    }

    private fun setClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Voice Anywhere", text))
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
        // Strip hint at snapshot time.
        // WhatsApp (and similar apps) return placeholder string via .text with hint='' and
        // selStart=-1 / selEnd=-1. A real typed field always has selStart >= 0.
        // So: if selStart == -1 the field is empty (showing placeholder only).
        lastEditableText = when {
            rawText == hint -> ""
            source.textSelectionStart == -1 -> "".also { Log.d(TAG, "Hint strip: placeholder detected length=${rawText.length}") }
            else -> rawText
        }
        lastEditableCursor = if (lastEditableText.isEmpty()) 0
            else source.textSelectionStart.coerceAtLeast(0)
        Log.d(TAG, "Cached editable node package=$packageName textLength=${lastEditableText.length} hintLength=${hint.length} selStart=${source.textSelectionStart} selEnd=${source.textSelectionEnd} cursor=$lastEditableCursor")
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
        private const val ERROR_HOLD_MS = 1200L
        private const val NO_FIELD_HOLD_MS = 1400L
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
        @Volatile private var activeService: VoiceAccessibilityService? = null

        fun deliverDictationResult(text: String) {
            activeService?.handleTranscript(text)
        }

        fun deliverDictationError() {
            activeService?.showTransientError()
        }

        fun reinsertHistoryText(text: String): Boolean {
            val service = activeService ?: return false
            if (!service.isLastEditableFresh()) return false
            service.handleHistoryReinsert(text)
            return true
        }
    }
}
