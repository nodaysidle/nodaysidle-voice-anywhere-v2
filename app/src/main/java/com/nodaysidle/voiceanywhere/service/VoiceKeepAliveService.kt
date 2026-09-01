package com.nodaysidle.voiceanywhere.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.nodaysidle.voiceanywhere.DictationActivity
import com.nodaysidle.voiceanywhere.MainActivity
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

/**
 * Foreground keep-alive that owns the floating pill ([TYPE_APPLICATION_OVERLAY])
 * and dictation/STT. Survives Xiaomi Accessibility binding death so the user
 * does not need to re-toggle Accessibility after MIUI kills the a11y connection.
 */
class VoiceKeepAliveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlay: FloatingMicOverlay? = null
    private var isListening = false
    private var cloudRecording = false
    private var amplitudeJob: Job? = null
    private var cloudTranscribeJob: Job? = null
    private var audioRecorder: DictationAudioRecorder? = null
    private var currentLangIndex = 0
    private val prefs by lazy { getSharedPreferences("voice_anywhere", Context.MODE_PRIVATE) }
    private val selectedLanguage get() = DictationLanguage.fromIndex(currentLangIndex)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
        currentLangIndex = prefs.getInt(PREF_LANGUAGE_INDEX, 0)
            .coerceIn(0, DictationLanguage.cycle.lastIndex)
        audioRecorder = DictationAudioRecorder(this)
        ensureNotificationChannel()
        startAsForeground(recording = false)
        ensureOverlay()
        Log.d(TAG, "Keep-alive created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startAsForeground(recording = cloudRecording)
                ensureOverlay()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopListening(cancel = true)
        overlay?.hide()
        overlay = null
        if (activeService === this) activeService = null
        scope.cancel()
        Log.d(TAG, "Keep-alive destroyed")
        super.onDestroy()
    }

    private fun startAsForeground(recording: Boolean) {
        val notification = buildNotification(recording)
        val type = if (recording) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        startForeground(NOTIFICATION_ID, notification, type)
    }

    private fun ensureOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing — pill not shown")
            overlay?.hide()
            overlay = null
            return
        }
        if (overlay != null) return
        runCatching {
            overlay = FloatingMicOverlay(
                context = this,
                onTap = { toggleListening() },
                onLongPress = { cycleLanguage() },
                useApplicationOverlay = true
            ).also {
                it.show()
                it.setState(FloatingMicOverlay.State.IDLE, selectedLanguage.tag)
            }
            Log.d(TAG, "Application overlay shown")
        }.onFailure {
            Log.e(TAG, "Overlay show failed", it)
            overlay = null
        }
    }

    private fun toggleListening() {
        if (isListening) stopListening(cancel = false) else startListening()
    }

    private fun cycleLanguage() {
        currentLangIndex = (currentLangIndex + 1) % DictationLanguage.cycle.size
        prefs.edit().putInt(PREF_LANGUAGE_INDEX, currentLangIndex).apply()
        Log.d(TAG, "Language cycled to ${selectedLanguage.tag}")
        overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage.tag)
    }

    private fun startListening() {
        runCatching {
            // When a11y is bound, keep the focused-field guard (SET_TEXT fast path).
            // When a11y is dead, still allow dictation — IME or clipboard will insert.
            if (VoiceAccessibilityService.isBound()) {
                if (!VoiceAccessibilityService.hasFreshEditable()) {
                    showNoFieldFeedback()
                    return
                }
            }

            val openRouterKey = OpenRouterKeyStore.read(this)
            if (openRouterKey.isNotBlank()) {
                startCloudRecording()
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

    private fun startCloudRecording() {
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
        startAsForeground(recording = true)
        overlay?.setState(FloatingMicOverlay.State.RECORDING)
        startAmplitudePolling()
        cloudTranscribeJob?.cancel()
        Log.d(TAG, "OpenRouter STT recording started lang=${selectedLanguage.iso6391}")
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
            if (cancel) cancelCloudRecording() else finishCloudRecording()
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
        startAsForeground(recording = false)
        overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage.tag)
    }

    private fun finishCloudRecording() {
        amplitudeJob?.cancel()
        val file = audioRecorder?.stop()
        cloudRecording = false
        isListening = false
        startAsForeground(recording = false)
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
        Log.w(TAG, "Dictation blocked: no focused editable field (a11y bound)")
        scope.launch {
            delay(NO_FIELD_HOLD_MS)
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage.tag)
        }
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
            val feedback = InsertCoordinator.insert(this@VoiceKeepAliveService, cleaned)
            TranscriptHistoryStore.add(
                context = this@VoiceKeepAliveService,
                text = cleaned,
                targetPackage = VoiceAccessibilityService.lastTargetPackage(),
                resultLabel = feedback.label
            )
            Log.d(
                TAG,
                "Dictation handled rawLength=${rawText.length} cleanedLength=${cleaned.length} feedback=${feedback.label}"
            )
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
            }
        }
    }

    private fun handleHistoryReinsert(text: String) {
        if (text.isBlank()) return
        overlay?.setState(FloatingMicOverlay.State.PROCESSING)
        scope.launch {
            val feedback = InsertCoordinator.insert(this@VoiceKeepAliveService, text)
            Log.d(TAG, "History reinsert length=${text.length} feedback=${feedback.label}")
            overlay?.setState(feedback.state)
            delay(feedback.holdMillis)
            overlay?.setState(FloatingMicOverlay.State.IDLE, selectedLanguage.tag)
        }
    }

    private fun isFutoInstalled(): Boolean = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(FUTO_PACKAGE, 0)
        true
    }.getOrDefault(false)

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Voice Anywhere keep-alive",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps the floating mic available when Accessibility dies"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(recording: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (recording) "Recording…" else "Voice Anywhere running"
        val text = if (recording) {
            "Tap the floating pill to stop"
        } else {
            "Overlay stay-alive · Battery unrestricted recommended on Xiaomi"
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        private const val TAG = "VoiceAnywhereKeepAlive"
        private const val CHANNEL_ID = "voice_anywhere_keepalive"
        private const val NOTIFICATION_ID = 1002
        private const val PREF_LANGUAGE_INDEX = "selected_language_index"
        private const val ERROR_HOLD_MS = 1200L
        private const val NO_FIELD_HOLD_MS = 1400L
        private const val FUTO_PACKAGE = "org.futo.voiceinput"
        const val ACTION_STOP = "com.nodaysidle.voiceanywhere.action.STOP_KEEPALIVE"

        @Volatile
        private var activeService: VoiceKeepAliveService? = null

        fun isRunning(): Boolean = activeService != null

        fun start(context: Context) {
            val intent = Intent(context, VoiceKeepAliveService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun deliverDictationResult(text: String) {
            activeService?.handleTranscript(text)
        }

        fun deliverDictationError() {
            activeService?.showTransientError()
        }

        fun reinsertHistoryText(text: String): Boolean {
            val service = activeService ?: return false
            service.handleHistoryReinsert(text)
            return true
        }
    }
}
