package com.nodaysidle.voiceanywhere.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import android.Manifest

import com.nodaysidle.voiceanywhere.DictationActivity
import com.nodaysidle.voiceanywhere.history.TranscriptHistoryStore
import com.nodaysidle.voiceanywhere.polish.DeepSeekTextPolisher
import com.nodaysidle.voiceanywhere.polish.TextPostProcessor
import com.nodaysidle.voiceanywhere.security.DeepSeekKeyStore
import com.nodaysidle.voiceanywhere.security.OpenRouterKeyStore
import com.nodaysidle.voiceanywhere.stt.DictationAudioRecorder
import com.nodaysidle.voiceanywhere.stt.DictationLanguage
import com.nodaysidle.voiceanywhere.stt.OpenRouterSttClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlay: FloatingMicOverlay? = null
    private var isListening = false
    private var cloudRecording = false
    private var lastEditableNode: AccessibilityNodeInfo? = null
    private var lastEditableText: String = ""
    private var lastEditableCursor: Int = 0
    private var lastEditablePackage: String = ""
    private var lastEditableCapturedAtMillis: Long = 0
    private var futoAutoSelectInFlight = false
    private var lastFutoAutoSelectAtMillis: Long = 0
    private var amplitudeJob: Job? = null
    private var cloudTranscribeJob: Job? = null
    private var audioRecorder: DictationAudioRecorder? = null

    private var currentLangIndex = 0
    private val prefs by lazy { getSharedPreferences("voice_anywhere", Context.MODE_PRIVATE) }
    private val selectedLanguage get() = DictationLanguage.fromIndex(currentLangIndex)

    override fun onServiceConnected() {
        super.onServiceConnected()
        runCatching {
            activeService = this
            currentLangIndex = prefs.getInt(PREF_LANGUAGE_INDEX, 0)
                .coerceIn(0, DictationLanguage.cycle.lastIndex)
            audioRecorder = DictationAudioRecorder(this)
            overlay = FloatingMicOverlay(
                this,
                onTap = { toggleListening() },
                onLongPress = { cycleLanguage() }
            ).also { it.show() }
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage.tag)
            Log.d(TAG, "Floating overlay initialized")
        }.onFailure {
            Log.e(TAG, "Overlay init failed", it)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val source = event?.source
        val packageName = event?.packageName?.toString().orEmpty()
        if (isListening && !cloudRecording && packageName == FUTO_PACKAGE) {
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
        currentLangIndex = (currentLangIndex + 1) % DictationLanguage.cycle.size
        prefs.edit().putInt(PREF_LANGUAGE_INDEX, currentLangIndex).apply()
        Log.d(TAG, "Language cycled to ${selectedLanguage.tag} (${selectedLanguage.localeTag})")
        overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage.tag)
    }

    private fun startListening() {
        runCatching {
            if (!cacheCurrentFocusedEditable()) {
                showNoFieldFeedback()
                return
            }

            val openRouterKey = OpenRouterKeyStore.read(this)
            if (openRouterKey.isNotBlank()) {
                startCloudRecording(openRouterKey)
                return
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "RECORD_AUDIO missing for system STT fallback")
                showTransientError()
                return
            }

            isListening = true
            cloudRecording = false
            overlay?.setState(FloatingMicOverlay.State.RECORDING)
            val intent = Intent(this, DictationActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra(DictationActivity.EXTRA_LANGUAGE, selectedLanguage.localeTag)
                putExtra(DictationActivity.EXTRA_PREFER_FUTO, isFutoInstalled())
            }
            startActivity(intent)
            Log.d(TAG, "DictationActivity launched lang=${selectedLanguage.localeTag}")
        }.onFailure {
            Log.e(TAG, "Dictation launch failed", it)
            isListening = false
            cloudRecording = false
            overlay?.setState(FloatingMicOverlay.State.ERROR)
        }
    }

    private fun startCloudRecording(apiKey: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO missing for OpenRouter STT")
            showTransientError()
            return
        }
        val recorder = audioRecorder ?: DictationAudioRecorder(this).also { audioRecorder = it }
        recorder.start()
        isListening = true
        cloudRecording = true
        overlay?.setState(FloatingMicOverlay.State.RECORDING)
        startAmplitudePolling()
        cloudTranscribeJob?.cancel()
        Log.d(TAG, "OpenRouter STT recording started lang=${selectedLanguage.iso6391} keyLen=${apiKey.length}")
    }

    private fun startAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            while (isActive && cloudRecording) {
                val amp = audioRecorder?.maxAmplitude() ?: 0
                val normalized = (amp / 32767f).coerceIn(0f, 1f)
                overlay?.setAmplitude(if (normalized < 0.05f) 0.15f else normalized)
                delay(50L)
            }
        }
    }

    private fun stopListening(cancel: Boolean) {
        if (!isListening) return
        if (cloudRecording) {
            if (cancel) {
                cancelCloudRecording()
            } else {
                finishCloudRecording()
            }
            return
        }
        isListening = false
        if (cancel) {
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage.tag)
        }
    }

    private fun cancelCloudRecording() {
        amplitudeJob?.cancel()
        cloudTranscribeJob?.cancel()
        audioRecorder?.cancel()
        cloudRecording = false
        isListening = false
        overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage.tag)
    }

    private fun finishCloudRecording() {
        amplitudeJob?.cancel()
        val file = audioRecorder?.stop()
        cloudRecording = false
        isListening = false
        if (file == null) {
            showTransientError()
            return
        }
        overlay?.setState(FloatingMicOverlay.State.PROCESSING)
        val key = OpenRouterKeyStore.read(this)
        if (key.isBlank()) {
            file.delete()
            showTransientError()
            return
        }
        val language = selectedLanguage.iso6391
        cloudTranscribeJob = scope.launch {
            val text = runCatching {
                OpenRouterSttClient(key).transcribe(file, language)
            }.onFailure {
                Log.e(TAG, "OpenRouter STT failed", it)
            }.getOrDefault("")
            withContext(Dispatchers.IO) { file.delete() }
            if (text.isBlank()) {
                showTransientError()
            } else {
                handleTranscript(text)
            }
        }
    }

    private fun showTransientError() {
        isListening = false
        cloudRecording = false
        overlay?.setState(FloatingMicOverlay.State.ERROR)
        scope.launch {
            delay(ERROR_HOLD_MS)
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage.tag)
        }
    }

    private fun showNoFieldFeedback() {
        isListening = false
        cloudRecording = false
        overlay?.setState(FloatingMicOverlay.State.NO_FIELD)
        Log.w(TAG, "Dictation blocked: no focused editable field")
        scope.launch {
            delay(NO_FIELD_HOLD_MS)
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage.tag)
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

        val label = selectedLanguage.futoPickerLabel
        val labelNode = findNodeWithText(root, label) ?: return false
        val target = clickableSelfOrAncestor(labelNode) ?: return false
        val clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (clicked) Log.d(TAG, "FUTO language picker auto-selected $label")
        return clicked
    }

    /**
     * Paste never waits on polish. Offline clean + insert first;
     * optional DeepSeek polish runs afterward and does not block insertion.
     */
    private fun handleTranscript(rawText: String) {
        isListening = false
        cloudRecording = false
        if (rawText.isBlank()) {
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage.tag)
            return
        }
        val cleaned = TextPostProcessor.clean(rawText)
        overlay?.setState(FloatingMicOverlay.State.PROCESSING)
        scope.launch {
            val (setTextOk, pasteOk) = injectText(cleaned)
            val feedback = InsertionFeedback.from(setTextOk, pasteOk)
            TranscriptHistoryStore.add(
                context = this@VoiceAccessibilityService,
                text = cleaned,
                targetPackage = lastEditablePackage,
                resultLabel = feedback.label
            )
            Log.d(
                TAG,
                "Dictation handled rawLength=${rawText.length} cleanedLength=${cleaned.length} setText=$setTextOk paste=$pasteOk feedback=${feedback.label}"
            )
            if (!setTextOk && !pasteOk) ClipboardNotification.show(this@VoiceAccessibilityService, cleaned)
            overlay?.setState(feedback.state)
            delay(feedback.holdMillis)
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage.tag)
        }

        val polishKey = DeepSeekKeyStore.read(this)
        if (polishKey.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    DeepSeekTextPolisher(polishKey).polish(rawText)
                }.onFailure {
                    Log.w(TAG, "Background DeepSeek polish failed", it)
                }
                // Intentionally not re-injected — paste already happened.
            }
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
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage.tag)
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

    private fun isFutoInstalled(): Boolean = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(FUTO_PACKAGE, 0)
        true
    }.getOrDefault(false)

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
