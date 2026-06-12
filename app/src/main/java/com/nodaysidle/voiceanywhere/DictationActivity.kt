package com.nodaysidle.voiceanywhere

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import com.nodaysidle.voiceanywhere.service.VoiceAccessibilityService

/**
 * Transparent bridge activity that delegates speech recognition to FUTO Voice Input.
 *
 * FUTO handles mic permission and its own UI. VoiceAccessibilityService
 * auto-selects the FUTO language picker when multiple FUTO languages are enabled.
 * We launch it via startActivityForResult, receive the transcript, deliver it to
 * VoiceAccessibilityService, then finish immediately.
 */
class DictationActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launchFuto()
    }

    private fun launchFuto() {
        val lang = intent.getStringExtra(EXTRA_LANGUAGE) ?: "en-US"
        val futoIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            setPackage("org.futo.voiceinput")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching {
            startActivityForResult(futoIntent, REQUEST_FUTO)
            Log.d(TAG, "FUTO RecognizeActivity launched lang=$lang")
        }.onFailure {
            Log.e(TAG, "Failed to launch FUTO", it)
            VoiceAccessibilityService.deliverDictationError()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FUTO) {
            if (resultCode == RESULT_OK) {
                val text = data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    .orEmpty()
                Log.d(TAG, "FUTO result length=${text.length}")
                if (text.isNotBlank()) {
                    VoiceAccessibilityService.deliverDictationResult(text)
                } else {
                    VoiceAccessibilityService.deliverDictationError()
                }
            } else {
                Log.w(TAG, "FUTO cancelled or failed resultCode=$resultCode")
                VoiceAccessibilityService.deliverDictationError()
            }
            finish()
        }
    }

    companion object {
        private const val TAG = "DictationActivity"
        private const val REQUEST_FUTO = 42
        const val EXTRA_LANGUAGE = "extra_language"
    }
}
