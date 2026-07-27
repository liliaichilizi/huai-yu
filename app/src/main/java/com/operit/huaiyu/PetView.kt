package com.operit.huaiyu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

class PetView(context: Context) : View(context) {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF555555.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val earPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFB6C1.toInt() // 浅粉色耳朵内侧
        style = Paint.Style.FILL
    }

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF333333.toInt()
        style = Paint.Style.FILL
    }

    private val cheekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40FF6B8A.toInt() // 半透明腮红
        style = Paint.Style.FILL
    }

    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF555555.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
    }

    private val tearMolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF333333.toInt()
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h * 0.55f
        val radius = w * 0.38f

        // 画耳朵（三角形）
        drawEar(canvas, cx - radius * 0.65f, cy - radius * 0.85f, radius * 0.45f, true)
        drawEar(canvas, cx + radius * 0.65f, cy - radius * 0.85f, radius * 0.45f, false)

        // 画圆脸
        canvas.drawCircle(cx, cy, radius, bodyPaint)
        canvas.drawCircle(cx, cy, radius, outlinePaint)

        // 画眼睛 - 大大的圆眼睛
        val eyeY = cy - radius * 0.1f
        val eyeOffsetX = radius * 0.35f
        val eyeRadius = radius * 0.13f
        // 左眼
        canvas.drawCircle(cx - eyeOffsetX, eyeY, eyeRadius, eyePaint)
        // 右眼
        canvas.drawCircle(cx + eyeOffsetX, eyeY, eyeRadius, eyePaint)
        // 眼睛高光
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx - eyeOffsetX + eyeRadius * 0.3f, eyeY - eyeRadius * 0.3f, eyeRadius * 0.4f, highlightPaint)
        canvas.drawCircle(cx + eyeOffsetX + eyeRadius * 0.3f, eyeY - eyeRadius * 0.3f, eyeRadius * 0.4f, highlightPaint)

        // 泪痣 - 右眼下方
        canvas.drawCircle(cx + eyeOffsetX + eyeRadius * 0.2f, eyeY + eyeRadius * 1.5f, eyeRadius * 0.2f, tearMolePaint)

        // 腮红
        canvas.drawCircle(cx - eyeOffsetX - radius * 0.1f, cy + radius * 0.15f, radius * 0.15f, cheekPaint)
        canvas.drawCircle(cx + eyeOffsetX + radius * 0.1f, cy + radius * 0.15f, radius * 0.15f, cheekPaint)

        // 鼻子 - 小三角
        val noseY = cy + radius * 0.15f
        val nosePath = Path().apply {
            moveTo(cx, noseY - radius * 0.05f)
            lineTo(cx - radius * 0.06f, noseY + radius * 0.05f)
            lineTo(cx + radius * 0.06f, noseY + radius * 0.05f)
            close()
        }
        canvas.drawPath(nosePath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF9CAA.toInt()
            style = Paint.Style.FILL
        })

        // 嘴巴 - w形
        val mouthY = noseY + radius * 0.1f
        val mouthPath = Path().apply {
            moveTo(cx - radius * 0.15f, mouthY)
            quadTo(cx - radius * 0.07f, mouthY + radius * 0.1f, cx, mouthY)
            quadTo(cx + radius * 0.07f, mouthY + radius * 0.1f, cx + radius * 0.15f, mouthY)
        }
        canvas.drawPath(mouthPath, mouthPaint)
    }

    private fun drawEar(canvas: Canvas, tipX: Float, tipY: Float, size: Float, isLeft: Boolean) {
        val earPath = Path()
        if (isLeft) {
            earPath.moveTo(tipX, tipY)
            earPath.lineTo(tipX - size * 0.5f, tipY + size * 1.2f)
            earPath.lineTo(tipX + size * 0.7f, tipY + size * 0.9f)
        } else {
            earPath.moveTo(tipX, tipY)
            earPath.lineTo(tipX + size * 0.5f, tipY + size * 1.2f)
            earPath.lineTo(tipX - size * 0.7f, tipY + size * 0.9f)
        }
        earPath.close()
        canvas.drawPath(earPath, bodyPaint)
        canvas.drawPath(earPath, outlinePaint)

        // 耳朵内侧粉色
        val innerPath = Path()
        val scale = 0.5f
        if (isLeft) {
            val cx = (tipX + tipX - size * 0.5f + tipX + size * 0.7f) / 3f
            val cy = (tipY + tipY + size * 1.2f + tipY + size * 0.9f) / 3f
            innerPath.moveTo(tipX + (tipX - tipX) * scale, tipY + (tipY - cy) * 0.3f)
            innerPath.lineTo(cx + (tipX - size * 0.5f - cx) * scale, cy + (tipY + size * 1.2f - cy) * scale * 0.6f)
            innerPath.lineTo(cx + (tipX + size * 0.7f - cx) * scale, cy + (tipY + size * 0.9f - cy) * scale * 0.6f)
        } else {
            val cx = (tipX + tipX + size * 0.5f + tipX - size * 0.7f) / 3f
            val cy = (tipY + tipY + size * 1.2f + tipY + size * 0.9f) / 3f
            innerPath.moveTo(tipX + (tipX - tipX) * scale, tipY + (tipY - cy) * 0.3f)
            innerPath.lineTo(cx + (tipX + size * 0.5f - cx) * scale, cy + (tipY + size * 1.2f - cy) * scale * 0.6f)
            innerPath.lineTo(cx + (tipX - size * 0.7f - cx) * scale, cy + (tipY + size * 0.9f - cy) * scale * 0.6f)
        }
        innerPath.close()
        canvas.drawPath(innerPath, earPaint)
    }
}
