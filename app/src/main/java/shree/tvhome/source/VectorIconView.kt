package shree.tvhome.source

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

class VectorIconView(
    context: Context,
    private var iconKind: String,
    private var tintColor: Int = Color.WHITE
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = tintColor
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = tintColor
    }

    private val path = Path()
    private val rect = RectF()

    fun setTint(color: Int) {
        tintColor = color
        paint.color = color
        fillPaint.color = color
        invalidate()
    }

    fun setIconKind(kind: String) {
        iconKind = kind
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val s = minOf(w, h)
        val strokeW = (s * 0.11f).coerceAtLeast(2f)
        paint.strokeWidth = strokeW
        paint.color = tintColor
        fillPaint.color = tintColor

        val cx = w * 0.5f
        val cy = h * 0.5f

        when (iconKind) {
            "mic" -> {
                val mw = s * 0.28f
                val mh = s * 0.44f
                rect.set(cx - mw * 0.5f, cy - mh * 0.65f, cx + mw * 0.5f, cy + mh * 0.2f)
                paint.style = Paint.Style.STROKE
                canvas.drawRoundRect(rect, mw * 0.5f, mw * 0.5f, paint)

                rect.set(cx - mw * 0.9f, cy - mh * 0.35f, cx + mw * 0.9f, cy + mh * 0.45f)
                canvas.drawArc(rect, 15f, 150f, false, paint)
                canvas.drawLine(cx, cy + mh * 0.45f, cx, cy + mh * 0.78f, paint)
                canvas.drawLine(cx - mw * 0.55f, cy + mh * 0.78f, cx + mw * 0.55f, cy + mh * 0.78f, paint)
            }
            "wifi" -> {
                val r1 = s * 0.12f
                val r2 = s * 0.28f
                val r3 = s * 0.44f
                val baseY = cy + s * 0.24f
                paint.style = Paint.Style.STROKE
                rect.set(cx - r3, baseY - r3, cx + r3, baseY + r3)
                canvas.drawArc(rect, 220f, 100f, false, paint)
                rect.set(cx - r2, baseY - r2, cx + r2, baseY + r2)
                canvas.drawArc(rect, 220f, 100f, false, paint)
                fillPaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, baseY - r1 * 0.4f, strokeW * 0.85f, fillPaint)
            }
            "bell" -> {
                val bw = s * 0.46f
                val bh = s * 0.48f
                path.reset()
                path.moveTo(cx - bw * 0.5f, cy + bh * 0.28f)
                path.quadTo(cx - bw * 0.5f, cy - bh * 0.35f, cx, cy - bh * 0.45f)
                path.quadTo(cx + bw * 0.5f, cy - bh * 0.35f, cx + bw * 0.5f, cy + bh * 0.28f)
                path.lineTo(cx + bw * 0.65f, cy + bh * 0.42f)
                path.lineTo(cx - bw * 0.65f, cy + bh * 0.42f)
                path.close()
                paint.style = Paint.Style.STROKE
                canvas.drawPath(path, paint)
                canvas.drawCircle(cx, cy + bh * 0.56f, strokeW * 0.75f, fillPaint)
            }
            "palette" -> {
                rect.set(cx - s * 0.42f, cy - s * 0.38f, cx + s * 0.42f, cy + s * 0.38f)
                paint.style = Paint.Style.STROKE
                canvas.drawOval(rect, paint)
                canvas.drawCircle(cx - s * 0.14f, cy - s * 0.1f, strokeW * 0.7f, fillPaint)
                canvas.drawCircle(cx + s * 0.12f, cy - s * 0.12f, strokeW * 0.7f, fillPaint)
                canvas.drawCircle(cx + s * 0.15f, cy + s * 0.1f, strokeW * 0.7f, fillPaint)
            }
            "sliders" -> {
                val y1 = cy - s * 0.22f
                val y2 = cy + s * 0.22f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx - s * 0.42f, y1, cx + s * 0.42f, y1, paint)
                canvas.drawCircle(cx - s * 0.15f, y1, strokeW * 1.1f, fillPaint)
                canvas.drawLine(cx - s * 0.42f, y2, cx + s * 0.42f, y2, paint)
                canvas.drawCircle(cx + s * 0.18f, y2, strokeW * 1.1f, fillPaint)
            }
            "power" -> {
                val r = s * 0.34f
                rect.set(cx - r, cy - r + s * 0.04f, cx + r, cy + r + s * 0.04f)
                paint.style = Paint.Style.STROKE
                canvas.drawArc(rect, 305f, 290f, false, paint)
                canvas.drawLine(cx, cy - s * 0.42f, cx, cy, paint)
            }
            else -> {
                canvas.drawCircle(cx, cy, s * 0.32f, paint)
            }
        }
    }
}
