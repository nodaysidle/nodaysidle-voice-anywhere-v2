package com.nodaysidle.voiceanywhere.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Wispr-adjacent floating pill: dark, quiet, compact.
 * Waveform appears while recording; idle shows mic + language tag.
 */
class FloatingMicOverlay(
    context: Context,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit = {}
) : FrameLayout(context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences("voice_anywhere", Context.MODE_PRIVATE)
    private val row: LinearLayout
    private val label: TextView
    private val waveform: WaveformView
    private val params: WindowManager.LayoutParams
    private var shown = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dragging = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false
    private var currentState = State.IDLE

    init {
        isClickable = true
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = PillDrawable(COLOR_PILL_IDLE, dp(20).toFloat(), COLOR_STROKE_IDLE, dp(1).toFloat())
        elevation = dp(6).toFloat()

        row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        label = TextView(context).apply {
            text = "● EN"
            setTextColor(COLOR_TEXT_IDLE)
            textSize = 12f
            letterSpacing = 0.06f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        waveform = WaveformView(context).apply {
            visibility = View.GONE
        }

        row.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(
            waveform,
            LinearLayout.LayoutParams(dp(56), dp(18)).apply {
                leftMargin = dp(8)
            }
        )
        addView(row, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(PREF_X, dp(20))
            y = prefs.getInt(PREF_Y, dp(180))
        }
    }

    fun show() {
        if (shown) return
        runCatching {
            windowManager.addView(this, params)
            post {
                clampToScreen()
                runCatching { windowManager.updateViewLayout(this, params) }
            }
            shown = true
        }
    }

    fun hide() {
        if (!shown) return
        waveform.stop()
        runCatching { windowManager.removeView(this) }
        shown = false
    }

    fun setState(state: State, langTag: String = "") {
        currentState = state
        val showWave = state == State.RECORDING
        waveform.visibility = if (showWave) View.VISIBLE else View.GONE
        if (showWave) waveform.start() else waveform.stop()

        label.text = when (state) {
            State.IDLE -> if (langTag.isNotEmpty()) "● $langTag" else "●"
            State.RECORDING -> "REC"
            State.PROCESSING -> "…"
            State.SUCCESS -> "✓"
            State.PASTED -> "PST"
            State.COPIED -> "CPY"
            State.NO_FIELD -> "NO FIELD"
            State.ERROR -> "!"
        }

        val (fill, stroke, text) = when (state) {
            State.IDLE -> Triple(COLOR_PILL_IDLE, COLOR_STROKE_IDLE, COLOR_TEXT_IDLE)
            State.RECORDING -> Triple(COLOR_PILL_REC, COLOR_STROKE_REC, COLOR_TEXT_REC)
            State.PROCESSING -> Triple(COLOR_PILL_PROC, COLOR_STROKE_PROC, COLOR_TEXT_PROC)
            State.SUCCESS -> Triple(COLOR_PILL_OK, COLOR_STROKE_OK, COLOR_TEXT_OK)
            State.PASTED -> Triple(COLOR_PILL_PST, COLOR_STROKE_PST, COLOR_TEXT_PST)
            State.COPIED -> Triple(COLOR_PILL_CPY, COLOR_STROKE_CPY, COLOR_TEXT_CPY)
            State.NO_FIELD, State.ERROR -> Triple(COLOR_PILL_WARN, COLOR_STROKE_WARN, COLOR_TEXT_WARN)
        }
        label.setTextColor(text)
        background = PillDrawable(fill, dp(20).toFloat(), stroke, dp(1).toFloat())
        requestLayout()
    }

    /** Feed live amplitude 0..1 while recording for waveform motion. */
    fun setAmplitude(normalized: Float) {
        if (currentState == State.RECORDING) {
            waveform.setAmplitude(normalized)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                longPressTriggered = false
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                longPressHandler.postDelayed({
                    if (!dragging) {
                        longPressTriggered = true
                        onLongPress()
                    }
                }, LONG_PRESS_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (abs(dx) > dp(8) || abs(dy) > dp(8)) {
                    dragging = true
                    longPressHandler.removeCallbacksAndMessages(null)
                }
                if (dragging) {
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    clampToScreen()
                    runCatching { windowManager.updateViewLayout(this, params) }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                longPressHandler.removeCallbacksAndMessages(null)
                if (dragging) {
                    snapToNearestEdge()
                    savePosition()
                } else if (!longPressTriggered) {
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacksAndMessages(null)
                if (dragging) savePosition()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap()
        return true
    }

    private fun snapToNearestEdge() {
        val margin = dp(12)
        val width = screenWidth()
        val bubbleWidth = measuredWidth.takeIf { it > 0 } ?: dp(96)
        params.x = if (params.x + bubbleWidth / 2 < width / 2) margin else width - bubbleWidth - margin
        clampToScreen()
        runCatching { windowManager.updateViewLayout(this, params) }
    }

    private fun clampToScreen() {
        val margin = dp(8)
        val bubbleWidth = measuredWidth.takeIf { it > 0 } ?: dp(96)
        val bubbleHeight = measuredHeight.takeIf { it > 0 } ?: dp(44)
        params.x = params.x.coerceIn(margin, (screenWidth() - bubbleWidth - margin).coerceAtLeast(margin))
        params.y = params.y.coerceIn(margin, (screenHeight() - bubbleHeight - margin).coerceAtLeast(margin))
    }

    private fun savePosition() {
        prefs.edit()
            .putInt(PREF_X, params.x)
            .putInt(PREF_Y, params.y)
            .apply()
    }

    private fun screenWidth(): Int = windowManager.currentWindowMetrics.bounds.width()

    private fun screenHeight(): Int = windowManager.currentWindowMetrics.bounds.height()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    enum class State { IDLE, RECORDING, PROCESSING, SUCCESS, PASTED, COPIED, NO_FIELD, ERROR }

    private companion object {
        const val PREF_X = "floating_mic_x"
        const val PREF_Y = "floating_mic_y"
        const val LONG_PRESS_MS = 500L
        val COLOR_PILL_IDLE = Color.parseColor("#141414")
        val COLOR_STROKE_IDLE = Color.parseColor("#2A2A2A")
        val COLOR_TEXT_IDLE = Color.parseColor("#E8E8E8")
        val COLOR_PILL_REC = Color.parseColor("#1A1214")
        val COLOR_STROKE_REC = Color.parseColor("#C8FF00")
        val COLOR_TEXT_REC = Color.parseColor("#C8FF00")
        val COLOR_PILL_PROC = Color.parseColor("#161616")
        val COLOR_STROKE_PROC = Color.parseColor("#3A3A3A")
        val COLOR_TEXT_PROC = Color.parseColor("#B0B0B0")
        val COLOR_PILL_OK = Color.parseColor("#0F1A12")
        val COLOR_STROKE_OK = Color.parseColor("#3D8B4F")
        val COLOR_TEXT_OK = Color.parseColor("#7CFF9A")
        val COLOR_PILL_PST = Color.parseColor("#141018")
        val COLOR_STROKE_PST = Color.parseColor("#6B5CA5")
        val COLOR_TEXT_PST = Color.parseColor("#C4B5FD")
        val COLOR_PILL_CPY = Color.parseColor("#0E161A")
        val COLOR_STROKE_CPY = Color.parseColor("#3A7A9A")
        val COLOR_TEXT_CPY = Color.parseColor("#7DD3FC")
        val COLOR_PILL_WARN = Color.parseColor("#1A1408")
        val COLOR_STROKE_WARN = Color.parseColor("#A67C00")
        val COLOR_TEXT_WARN = Color.parseColor("#FFB300")
    }
}

/** Compact animated bars driven by live mic amplitude. */
class WaveformView(context: Context) : View(context) {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C8FF00")
        style = Paint.Style.FILL
    }
    private val levels = FloatArray(BAR_COUNT) { 0.18f }
    private var targetAmplitude = 0.2f
    private var running = false
    private var lastFrameAt = 0L
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val dt = min(32f, (now - lastFrameAt).toFloat()) / 16f
            lastFrameAt = now
            for (i in levels.indices) {
                val jitter = 0.04f + Random.nextFloat() * 0.08f
                val goal = if (i % 2 == 0) {
                    max(0.12f, targetAmplitude * (0.55f + jitter))
                } else {
                    max(0.1f, targetAmplitude * (0.35f + jitter * 1.4f))
                }
                levels[i] += (goal - levels[i]) * min(1f, 0.35f * dt)
            }
            invalidate()
            postOnAnimation(this)
        }
    }

    fun start() {
        if (running) return
        running = true
        lastFrameAt = SystemClock.uptimeMillis()
        postOnAnimation(ticker)
    }

    fun stop() {
        running = false
        removeCallbacks(ticker)
        for (i in levels.indices) levels[i] = 0.14f
        invalidate()
    }

    fun setAmplitude(normalized: Float) {
        targetAmplitude = normalized.coerceIn(0.08f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val gap = w / (BAR_COUNT * 2f)
        val barWidth = gap
        val radius = barWidth / 2f
        for (i in 0 until BAR_COUNT) {
            val barH = max(barWidth, h * levels[i])
            val cx = gap + i * (barWidth + gap) + barWidth / 2f
            val top = (h - barH) / 2f
            canvas.drawRoundRect(
                cx - barWidth / 2f,
                top,
                cx + barWidth / 2f,
                top + barH,
                radius,
                radius,
                barPaint
            )
        }
    }

    private companion object {
        const val BAR_COUNT = 5
    }
}
