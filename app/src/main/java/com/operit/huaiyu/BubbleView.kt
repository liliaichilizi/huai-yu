package com.operit.huaiyu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View

class BubbleView(context: Context, private val text: String) : View(context) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
        setShadowLayer(dp(4f), 0f, dp(2f), 0x33000000)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF333333.toInt()
        textSize = sp(12f)
        textAlign = Paint.Align.CENTER
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val padding = dp(8f)
        val cornerRadius = dp(12f)
        val arrowHeight = dp(6f)

        // Draw bubble body
        val rect = RectF(padding, padding, w - padding, h - padding - arrowHeight)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        // Draw arrow pointing down
        val arrowPath = Path().apply {
            moveTo(w / 2f - dp(6f), h - padding - arrowHeight)
            lineTo(w / 2f, h - padding)
            lineTo(w / 2f + dp(6f), h - padding - arrowHeight)
            close()
        }
        canvas.drawPath(arrowPath, bgPaint)

        // Draw text
        val textY = (rect.top + rect.bottom) / 2f + textPaint.textSize / 3f
        canvas.drawText(text, w / 2f, textY, textPaint)
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }

    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    }
}
