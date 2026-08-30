package com.nodaysidle.voiceanywhere.service

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable

class PillDrawable(
    private val fillColor: Int,
    private val radius: Float,
    private val strokeColor: Int = 0,
    private val strokeWidthPx: Float = 0f
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = strokeColor
        strokeWidth = strokeWidthPx
    }

    override fun draw(canvas: Canvas) {
        val rect = RectF(bounds)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        if (strokeWidthPx > 0f) {
            val inset = strokeWidthPx / 2f
            canvas.drawRoundRect(
                RectF(
                    rect.left + inset,
                    rect.top + inset,
                    rect.right - inset,
                    rect.bottom - inset
                ),
                radius,
                radius,
                strokePaint
            )
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
