package com.nodaysidle.voiceanywhere

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.text.format.DateFormat
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nodaysidle.voiceanywhere.history.TranscriptHistoryItem
import com.nodaysidle.voiceanywhere.history.TranscriptHistoryStore
import com.nodaysidle.voiceanywhere.security.DeepSeekKeyStore
import com.nodaysidle.voiceanywhere.service.VoiceAccessibilityService

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var readinessLabel: TextView
    private lateinit var micStatus: TextView
    private lateinit var futoStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var cloudStatus: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var historyList: LinearLayout

    private val prefs by lazy { getSharedPreferences("voice_anywhere", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC || requestCode == REQUEST_NOTIFICATIONS) refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onPause() {
        saveApiKey()
        super.onPause()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(40), dp(18), dp(28))
        }

        val shell = FrameLayout(this).apply {
            background = appBackground()
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                root.setPadding(
                    dp(18),
                    bars.top + dp(18),
                    dp(18),
                    bars.bottom + dp(24)
                )
                insets
            }
            addView(ScrollView(this@MainActivity).apply {
                isFillViewport = true
                clipToPadding = false
                addView(root)
            })
        }

        root.addView(hero())
        root.addView(setupPanel())
        root.addView(polishPanel())
        root.addView(historyPanel())
        root.addView(usagePanel())

        setContentView(shell)
        refreshStatus()
    }

    private fun hero(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, dp(16))

        addView(TextView(this@MainActivity).apply {
            text = "NODAYSIDLE / VOICE LAYER"
            setTextColor(COLOR_VOLT_DIM)
            textSize = 11f
            letterSpacing = 0.22f
            typeface = Typeface.MONOSPACE
        })

        addView(TextView(this@MainActivity).apply {
            text = "Speak anywhere.\nText lands there."
            setTextColor(Color.WHITE)
            textSize = 38f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            includeFontPadding = false
            setLineSpacing(-dp(2).toFloat(), 0.94f)
            setPadding(0, dp(8), 0, dp(10))
        })

        addView(TextView(this@MainActivity).apply {
            text = "A floating dictation layer for Android. FUTO captures speech; Voice Anywhere restores focus, cleans text, and inserts it into the active field."
            setTextColor(COLOR_TEXT_MUTED)
            textSize = 15f
            setLineSpacing(0f, 1.16f)
        })
    }

    private fun setupPanel(): LinearLayout = panel(accent = true).apply {
        addView(rowHeader("SYSTEM", "Launch readiness"))
        readinessLabel = TextView(this@MainActivity).apply {
            text = "Checking signal"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(12))
        }
        addView(readinessLabel)

        addView(statusGrid().apply {
            micStatus = statusTile("MIC")
            futoStatus = statusTile("FUTO")
            notificationStatus = statusTile("ALERTS")
            accessibilityStatus = statusTile("ACCESS")
            addView(micStatus)
            addView(futoStatus)
            addView(notificationStatus)
            addView(accessibilityStatus)
        })

        addView(buttonRow(
            actionPill("Grant mic", strong = true) {
                ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            },
            actionPill("Grant alerts", strong = false) {
                requestNotificationPermission()
            }
        ))

        addView(actionPill("Open Accessibility Settings", strong = true) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply {
            topMargin = dp(10)
        })
    }

    private fun polishPanel(): LinearLayout = panel().apply {
        addView(rowHeader("POLISH", "Optional cloud cleanup"))
        addView(bodyText("Offline cleanup is always active and keeps dictated text on-device. Leave the key blank for local-only mode. If you save a DeepSeek key, every dictation is sent to DeepSeek for grammar repair before insertion."))

        cloudStatus = statusTile("CLOUD").apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply {
                topMargin = dp(14)
                bottomMargin = 0
            }
        }
        addView(cloudStatus)

        apiKeyInput = EditText(this@MainActivity).apply {
            setText(DeepSeekKeyStore.read(this@MainActivity))
            hint = "DeepSeek API key"
            setHintTextColor(Color.parseColor("#60645F"))
            setTextColor(Color.WHITE)
            textSize = 15f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            transformationMethod = PasswordTransformationMethod.getInstance()
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = rounded(Color.parseColor("#101310"), dp(18), Color.parseColor("#2B3328"), dp(1))
            setPadding(dp(16), 0, dp(16), 0)
            minHeight = dp(56)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    saveApiKey()
                    clearFocus()
                    true
                } else false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    DeepSeekKeyStore.write(this@MainActivity, s?.toString()?.trim().orEmpty())
                    refreshStatus()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        addView(apiKeyInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)).apply {
            topMargin = dp(14)
        })
    }

    private fun usagePanel(): LinearLayout = panel().apply {
        addView(rowHeader("FLOW", "Four moves"))
        addView(step("01", "Focus any text field", "Chat, browser, notes, search — wherever the cursor is."))
        addView(step("02", "Tap the floating mic", "The accessibility overlay stays available above every app."))
        addView(step("03", "Speak through FUTO", "FUTO owns speech recognition; this app owns insertion."))
        addView(step("04", "Watch the result", "SET = direct insert, PST = paste fallback, CPY = clipboard fallback."))
    }

    private fun historyPanel(): LinearLayout = panel().apply {
        addView(rowHeader("HISTORY", "Last dictations"))
        addView(bodyText("Local transcript history is off by default. Enable it only if you want this phone to keep recent dictated text for copy/retry. Disabling history clears saved transcripts."))
        historyList = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        addView(historyList)
    }

    private fun panel(accent: Boolean = false): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = if (accent) accentPanelBackground() else panelBackground()
        setPadding(dp(18), dp(18), dp(18), dp(18))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        }
    }

    private fun rowHeader(kicker: String, title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(chip(kicker), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30)))
        addView(TextView(this@MainActivity).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), 0, 0, 0)
        })
    }

    private fun statusGrid(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(4))
    }

    private fun statusTile(label: String): TextView = TextView(this).apply {
        text = "$label · CHECKING"
        setTextColor(COLOR_TEXT_MUTED)
        textSize = 14f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(Color.parseColor("#0A0D0A"), dp(16), Color.parseColor("#253020"), dp(1))
        setPadding(dp(14), 0, dp(14), 0)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply {
            bottomMargin = dp(8)
        }
    }

    private fun buttonRow(vararg buttons: TextView): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, 0)
        buttons.forEachIndexed { index, button ->
            addView(button, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                if (index > 0) leftMargin = dp(10)
            })
        }
    }

    private fun actionPill(label: String, strong: Boolean, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label.uppercase()
        gravity = Gravity.CENTER
        setTextColor(if (strong) Color.BLACK else COLOR_VOLT)
        textSize = 12f
        letterSpacing = 0.08f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        background = if (strong) {
            rounded(COLOR_VOLT, dp(18), COLOR_VOLT, dp(1))
        } else {
            rounded(Color.parseColor("#11170D"), dp(18), Color.parseColor("#4B6500"), dp(1))
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun chip(textValue: String): TextView = TextView(this).apply {
        text = textValue
        gravity = Gravity.CENTER
        setTextColor(Color.BLACK)
        textSize = 11f
        letterSpacing = 0.12f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setPadding(dp(10), 0, dp(10), 0)
        background = rounded(COLOR_VOLT, dp(999), COLOR_VOLT, dp(1))
    }

    private fun step(number: String, title: String, detail: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        setPadding(0, dp(14), 0, 0)
        addView(TextView(this@MainActivity).apply {
            text = number
            setTextColor(COLOR_VOLT)
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rounded(Color.parseColor("#10180B"), dp(14), Color.parseColor("#34451A"), dp(1))
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@MainActivity).apply {
                text = detail
                setTextColor(COLOR_TEXT_MUTED)
                textSize = 13f
                setLineSpacing(0f, 1.12f)
                setPadding(0, dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun bodyText(textValue: String): TextView = TextView(this).apply {
        text = textValue
        setTextColor(COLOR_TEXT_MUTED)
        textSize = 14f
        setLineSpacing(0f, 1.18f)
        setPadding(0, dp(12), 0, 0)
    }

    private fun historyRow(item: TranscriptHistoryItem): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(Color.parseColor("#0A0D0A"), dp(18), Color.parseColor("#253020"), dp(1))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        }

        addView(TextView(this@MainActivity).apply {
            text = "${item.resultLabel} · ${targetAppLabel(item.targetPackage)} · ${DateFormat.format("HH:mm", item.createdAtMillis)}"
            setTextColor(COLOR_VOLT_DIM)
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        })

        addView(TextView(this@MainActivity).apply {
            text = item.text
            setTextColor(Color.WHITE)
            textSize = 15f
            setLineSpacing(0f, 1.12f)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, 0)
        })

        addView(buttonRow(
            actionPill("Copy", strong = false) { copyHistoryItem(item) },
            actionPill("Retry", strong = true) { retryHistoryItem(item) },
            actionPill("Delete", strong = false) {
                TranscriptHistoryStore.delete(this@MainActivity, item.id)
                refreshHistory()
            }
        ))
    }

    private fun saveApiKey() {
        if (!::apiKeyInput.isInitialized) return
        DeepSeekKeyStore.write(this, apiKeyInput.text.toString().trim())
        refreshStatus()
    }

    private fun refreshStatus() {
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val futo = isFutoInstalled()
        val notifications = hasNotificationPermission()
        val accessibility = isAccessibilityEnabled()
        renderStatus(micStatus, "MIC", mic, "READY", "GRANT")
        renderStatus(futoStatus, "FUTO", futo, "INSTALLED", "MISSING")
        renderStatus(notificationStatus, "ALERTS", notifications, "READY", "GRANT")
        renderStatus(accessibilityStatus, "ACCESS", accessibility, "ENABLED", "ENABLE")
        if (::cloudStatus.isInitialized) renderCloudStatus(DeepSeekKeyStore.read(this).isNotBlank())
        if (::historyList.isInitialized) refreshHistory()
        val readyCount = listOf(mic, futo, notifications, accessibility).count { it }
        readinessLabel.text = when (readyCount) {
            4 -> "Ready to dictate"
            3 -> "One switch left"
            else -> "$readyCount / 4 systems ready"
        }
        readinessLabel.setTextColor(if (readyCount == 4) COLOR_VOLT else Color.WHITE)
    }

    private fun renderCloudStatus(enabled: Boolean) {
        cloudStatus.text = if (enabled) "CLOUD · DEEPSEEK ON" else "CLOUD · OFFLINE"
        cloudStatus.setTextColor(if (enabled) COLOR_WARN else COLOR_VOLT)
        cloudStatus.background = rounded(
            fill = if (enabled) Color.parseColor("#1A1203") else Color.parseColor("#0D1709"),
            radius = dp(16),
            stroke = if (enabled) Color.parseColor("#72510A") else Color.parseColor("#415D12"),
            strokeWidth = dp(1)
        )
    }

    private fun refreshHistory() {
        historyList.removeAllViews()
        if (!TranscriptHistoryStore.isEnabled(this)) {
            historyList.addView(bodyText("History is off. Dictations are not saved after insertion."))
            historyList.addView(actionPill("Enable local history", strong = false) {
                TranscriptHistoryStore.setEnabled(this, true)
                refreshHistory()
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
                topMargin = dp(12)
            })
            return
        }

        val items = TranscriptHistoryStore.list(this)
        if (items.isEmpty()) {
            historyList.addView(bodyText("No dictations yet."))
        } else {
            items.forEach { historyList.addView(historyRow(it)) }
        }
        historyList.addView(actionPill("Clear history", strong = false) {
            TranscriptHistoryStore.clear(this)
            refreshHistory()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(12)
        })
        historyList.addView(actionPill("Disable & clear", strong = false) {
            TranscriptHistoryStore.setEnabled(this, false)
            refreshHistory()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(10)
        })
    }

    private fun copyHistoryItem(item: TranscriptHistoryItem) {
        copyTextToClipboard(item.text)
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun retryHistoryItem(item: TranscriptHistoryItem) {
        copyTextToClipboard(item.text)
        val queued = VoiceAccessibilityService.reinsertHistoryText(item.text)
        Toast.makeText(this, if (queued) "Retrying insert" else "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun copyTextToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Voice Anywhere", text))
    }

    private fun targetAppLabel(packageName: String): String {
        if (packageName.isBlank()) return "Unknown app"
        KNOWN_APP_LABELS[packageName]?.let { return it }
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    private fun renderStatus(view: TextView, label: String, ok: Boolean, readyText: String, missingText: String) {
        view.text = "$label · ${if (ok) readyText else missingText}"
        view.setTextColor(if (ok) COLOR_VOLT else COLOR_WARN)
        view.background = rounded(
            fill = if (ok) Color.parseColor("#0D1709") else Color.parseColor("#1A1203"),
            radius = dp(16),
            stroke = if (ok) Color.parseColor("#415D12") else Color.parseColor("#72510A"),
            strokeWidth = dp(1)
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        } else {
            refreshStatus()
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        if (enabledServices.contains(packageName) && enabledServices.contains("VoiceAccessibilityService")) {
            return true
        }

        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return services.any {
            it.resolveInfo.serviceInfo.name.contains("VoiceAccessibilityService") ||
                it.resolveInfo.serviceInfo.packageName == packageName
        }
    }

    private fun isFutoInstalled(): Boolean = runCatching {
        packageManager.getPackageInfo("org.futo.voiceinput", 0)
        true
    }.getOrDefault(false)

    private fun appBackground(): LayerDrawable = LayerDrawable(arrayOf(
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            Color.parseColor("#050704"),
            Color.parseColor("#020302")
        )),
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
            Color.argb(92, 200, 255, 0),
            Color.TRANSPARENT,
            Color.argb(38, 200, 255, 0)
        ))
    ))

    private fun panelBackground(): GradientDrawable = rounded(Color.parseColor("#080A08"), dp(26), Color.parseColor("#242A22"), dp(1))

    private fun accentPanelBackground(): LayerDrawable = LayerDrawable(arrayOf(
        rounded(Color.parseColor("#080B07"), dp(28), Color.parseColor("#314020"), dp(1)),
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
            Color.argb(54, 200, 255, 0),
            Color.TRANSPARENT
        )).apply { cornerRadius = dp(28).toFloat() }
    ))

    private fun rounded(fill: Int, radius: Int, stroke: Int, strokeWidth: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
        setStroke(strokeWidth, stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_MIC = 10
        const val REQUEST_NOTIFICATIONS = 11
        val COLOR_VOLT: Int = Color.parseColor("#C8FF00")
        val COLOR_VOLT_DIM: Int = Color.parseColor("#9DD100")
        val COLOR_WARN: Int = Color.parseColor("#FFB300")
        val COLOR_TEXT_MUTED: Int = Color.parseColor("#A9AEA5")
        val KNOWN_APP_LABELS: Map<String, String> = mapOf(
            "ai.perplexity.app.android" to "Perplexity",
            "ai.perplexity.comet" to "Comet",
            "com.discord" to "Discord",
            "com.google.android.apps.docs" to "Google Docs",
            "com.google.android.apps.messaging" to "Google Messages",
            "com.google.android.apps.youtube.app" to "YouTube",
            "com.google.android.gm" to "Gmail",
            "com.google.android.googlequicksearchbox" to "Google",
            "com.google.android.keep" to "Google Keep",
            "com.openai.chatgpt" to "ChatGPT",
            "com.whatsapp" to "WhatsApp",
            "org.telegram.messenger" to "Telegram"
        )
    }
}
