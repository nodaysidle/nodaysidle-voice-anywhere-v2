package com.nodaysidle.voiceanywhere

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import com.nodaysidle.voiceanywhere.service.VoiceAccessibilityService

/**
 * Transparent bridge activity that delegates speech recognition to an installed engine.
 *
 * Priority when launched (no OpenRouter key path — that records in-service):
 * 1. FUTO Voice Input if installed (optional, not required)
 * 2. System recognizer via ACTION_RECOGNIZE_SPEECH without setPackage
 */
class DictationActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launchRecognizer()
    }

    private fun launchRecognizer() {
        val lang = intent.getStringExtra(EXTRA_LANGUAGE) ?: "en-US"
        val preferFuto = intent.getBooleanExtra(EXTRA_PREFER_FUTO, true)
        val useFuto = preferFuto && isFutoInstalled()

        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            if (useFuto) setPackage(FUTO_PACKAGE)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        runCatching {
            startActivityForResult(speechIntent, REQUEST_SPEECH)
            Log.d(TAG, "Recognizer launched engine=${if (useFuto) "futo" else "system"} lang=$lang")
        }.onFailure { first ->
            Log.w(TAG, "Primary recognizer launch failed; trying system fallback", first)
            if (useFuto) {
                val systemIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                runCatching {
                    startActivityForResult(systemIntent, REQUEST_SPEECH)
                    Log.d(TAG, "System recognizer launched after FUTO failure lang=$lang")
                }.onFailure {
                    Log.e(TAG, "Failed to launch any recognizer", it)
                    VoiceAccessibilityService.deliverDictationError()
                    finish()
                }
            } else {
                Log.e(TAG, "Failed to launch system recognizer", first)
                VoiceAccessibilityService.deliverDictationError()
                finish()
            }
        }
    }

    private fun isFutoInstalled(): Boolean = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(FUTO_PACKAGE, 0)
        true
    }.getOrDefault(false)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SPEECH) {
            if (resultCode == RESULT_OK) {
                val text = data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    .orEmpty()
                Log.d(TAG, "Recognizer result length=${text.length}")
                if (text.isNotBlank()) {
                    VoiceAccessibilityService.deliverDictationResult(text)
                } else {
                    VoiceAccessibilityService.deliverDictationError()
                }
            } else {
                Log.w(TAG, "Recognizer cancelled or failed resultCode=$resultCode")
                VoiceAccessibilityService.deliverDictationError()
            }
            finish()
        }
    }

    companion object {
        private const val TAG = "DictationActivity"
        private const val REQUEST_SPEECH = 42
        private const val FUTO_PACKAGE = "org.futo.voiceinput"
        const val EXTRA_LANGUAGE = "extra_language"
        const val EXTRA_PREFER_FUTO = "extra_prefer_futo"
    }
}
