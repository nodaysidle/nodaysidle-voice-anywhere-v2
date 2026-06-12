package com.nodaysidle.voiceanywhere.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

class FloatingMicOverlay(
    context: Context,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit = {}
) : FrameLayout(context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences("voice_anywhere", Context.MODE_PRIVATE)
    private val label: TextView
    private val params: WindowManager.LayoutParams
    private var shown = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dragging = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false

    init {
        isClickable = true
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = PillDrawable(Color.parseColor("#C8FF00"), dp(22).toFloat())

        label = TextView(context).apply {
            text = "● MIC"
            setTextColor(Color.BLACK)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        addView(label, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

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
        runCatching { windowManager.removeView(this) }
        shown = false
    }

    fun setState(state: State, langTag: String = "") {
        label.text = when (state) {
            State.IDLE -> if (langTag.isNotEmpty()) "● MIC · $langTag" else "● MIC"
            State.RECORDING -> "■ REC"
            State.PROCESSING -> "… AI"
            State.SUCCESS -> "✓ SET"
            State.PASTED -> "✓ PST"
            State.COPIED -> "↗ CPY"
            State.NO_FIELD -> "NO FIELD"
            State.ERROR -> "! ERR"
        }
        background = PillDrawable(
            when (state) {
                State.IDLE -> Color.parseColor("#C8FF00")
                State.RECORDING -> Color.parseColor("#FF1744")
                State.PROCESSING -> Color.parseColor("#FFFFFF")
                State.SUCCESS -> Color.parseColor("#00E676")
                State.PASTED -> Color.parseColor("#7C4DFF")
                State.COPIED -> Color.parseColor("#40C4FF")
                State.NO_FIELD -> Color.parseColor("#FFB300")
                State.ERROR -> Color.parseColor("#FFB300")
            },
            dp(22).toFloat()
        )
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
    }
}
