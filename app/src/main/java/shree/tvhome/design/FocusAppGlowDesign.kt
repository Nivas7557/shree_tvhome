// Important instructions:
// - Dedicated focus glow & glass silhouette surface for app tiles (Heart, Circle, Squircle, Classic).
// - Renders per-app dominant icon backlight aura, exponential breathing focus halo, and diagonal air-fog shimmer.
package shree.tvhome.design

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes
import kotlin.math.sin

class FocusAppGlowDesign(
    ctx: Context,
    private val settings: AppSettings,
    private var appDominantColor: Int
) : FrameLayout(ctx) {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val topSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val outerHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shapePath = Path()
    private val rect = RectF()
    private val sheenRect = RectF()
    private val shimmerMatrix = Matrix()
    private var shimmerFraction = -1f
    private var breathPhase = 0f
    private var focusAnim: ValueAnimator? = null
    private var cachedWidth = 0f
    private var cachedHeight = 0f
    private var cachedThemeKey = ""
    private var cachedFocusState = false
    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        outlineProvider = null
    }
    // Updates the app's dominant icon color without recreating the view.
    fun setDominantColor(color: Int) {
        if (appDominantColor != color) {
            appDominantColor = color
            cachedThemeKey = ""
            invalidate()
        }
    }
    // Starts or stops the smooth breathing halo and diagonal air-fog shimmer on focus change.
    fun setFocusVisualActive(active: Boolean) {
        focusAnim?.cancel()
        cachedThemeKey = ""
        if (active) {
            focusAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2600L
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val t = anim.animatedValue as Float
                    breathPhase = sin(t * Math.PI).toFloat()
                    shimmerFraction = if (t < 0.65f) (t / 0.65f) else -1f
                    invalidate()
                }
                start()
            }
        } else {
            breathPhase = 0f
            shimmerFraction = -1f
            invalidate()
        }
    }
    // Cancels active animations cleanly when detached from window to keep RAM and CPU idle.
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        focusAnim?.cancel()
        focusAnim = null
    }
    // Builds the silhouette path (Heart, Circle, Classic, or Squircle) dynamically from current bounds.
    private fun buildSilhouettePath(w: Float, h: Float, inset: Float) {
        shapePath.reset()
        rect.set(inset, inset, w - inset, h - inset)
        when (settings.tileShape) {
            "circle" -> {
                val r = (minOf(rect.width(), rect.height()) / 2f).coerceAtLeast(4f)
                shapePath.addCircle(rect.centerX(), rect.centerY(), r, Path.Direction.CW)
            }
            "heart" -> {
                val l = rect.left; val t = rect.top; val rw = rect.width(); val rh = rect.height()
                shapePath.moveTo(l + rw * 0.50f, t + rh * 0.94f)
                shapePath.cubicTo(l + rw * 0.44f, t + rh * 0.88f, l + rw * 0.06f, t + rh * 0.58f, l + rw * 0.06f, t + rh * 0.32f)
                shapePath.cubicTo(l + rw * 0.06f, t + rh * 0.14f, l + rw * 0.21f, t + rh * 0.05f, l + rw * 0.36f, t + rh * 0.05f)
                shapePath.cubicTo(l + rw * 0.44f, t + rh * 0.05f, l + rw * 0.48f, t + rh * 0.10f, l + rw * 0.50f, t + rh * 0.17f)
                shapePath.cubicTo(l + rw * 0.52f, t + rh * 0.10f, l + rw * 0.56f, t + rh * 0.05f, l + rw * 0.64f, t + rh * 0.05f)
                shapePath.cubicTo(l + rw * 0.79f, t + rh * 0.05f, l + rw * 0.94f, t + rh * 0.14f, l + rw * 0.94f, t + rh * 0.32f)
                shapePath.cubicTo(l + rw * 0.94f, t + rh * 0.58f, l + rw * 0.56f, t + rh * 0.88f, l + rw * 0.50f, t + rh * 0.94f)
                shapePath.close()
            }
            else -> {
                val cornerDp = Themes.getCornerRadiusDp(settings.tileShape)
                val radius = LayoutMetrics.dpF(context, cornerDp)
                shapePath.addRoundRect(rect, radius, radius, Path.Direction.CW)
            }
        }
    }
    // Rebuilds cached gradient shaders only when view dimensions, theme, or focus state change.
    private fun ensureShaders(w: Float, h: Float, focused: Boolean, glowColor: Int) {
        if (w == cachedWidth && h == cachedHeight && settings.tileColor == cachedThemeKey && focused == cachedFocusState) return
        cachedWidth = w
        cachedHeight = h
        cachedThemeKey = settings.tileColor
        cachedFocusState = focused
        val theme = Themes.getTheme(settings.tileColor)
        val baseCols = if (focused) theme.focusedColors else theme.normalColors
        val tintedCols = baseCols.mapIndexed { idx, col ->
            if (idx == 0) LayoutMetrics.blendWithAccent(col, glowColor, if (focused) 0.28f else 0.12f, Color.alpha(col)) else col
        }.toIntArray()
        bgPaint.shader = LinearGradient(0f, 0f, w * 0.65f, h, tintedCols, null, Shader.TileMode.CLAMP)
        sheenRect.set(0f, 0f, w, h * 0.42f)
        topSheenPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.42f,
            intArrayOf(Color.argb(if (focused) 85 else 42, 255, 255, 255), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
    }
    // Draws glass fill, per-app dominant backlight aura, child icon/label, shimmer sweep, and breathing border.
    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val focused = hasFocus()
        val glowColor = if (appDominantColor != 0) appDominantColor else settings.accentColor
        ensureShaders(w, h, focused, glowColor)
        buildSilhouettePath(w, h, LayoutMetrics.dpF(context, 1.5f))
        canvas.drawPath(shapePath, bgPaint)
        if (focused) {
            val auraAlpha = (95 + (breathPhase * 75)).toInt().coerceIn(80, 185)
            auraPaint.shader = RadialGradient(
                w * 0.5f, h * 0.38f, maxOf(w, h) * 0.70f,
                intArrayOf(LayoutMetrics.withAlpha(glowColor, auraAlpha), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawPath(shapePath, auraPaint)
        }
        val saveCount = canvas.save()
        canvas.clipPath(shapePath)
        super.dispatchDraw(canvas)
        canvas.drawRect(sheenRect, topSheenPaint)
        if (focused && shimmerFraction in 0f..1f) {
            val sweepX = (-0.5f + shimmerFraction * 2.0f) * w
            shimmerPaint.shader = LinearGradient(
                0f, 0f, w * 0.55f, h * 0.25f,
                intArrayOf(Color.TRANSPARENT, Color.argb(58, 255, 255, 255), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            shimmerMatrix.setTranslate(sweepX, 0f)
            shimmerPaint.shader.setLocalMatrix(shimmerMatrix)
            canvas.drawRect(0f, 0f, w, h, shimmerPaint)
        }
        canvas.restoreToCount(saveCount)
        if (focused) {
            outerHaloPaint.strokeWidth = LayoutMetrics.dpF(context, 4.5f + (breathPhase * 2.5f))
            outerHaloPaint.color = LayoutMetrics.withAlpha(glowColor, (120 + breathPhase * 110).toInt())
            canvas.drawPath(shapePath, outerHaloPaint)
            rimPaint.strokeWidth = LayoutMetrics.dpF(context, 2.3f)
            rimPaint.color = Color.WHITE
            canvas.drawPath(shapePath, rimPaint)
        } else {
            rimPaint.strokeWidth = LayoutMetrics.dpF(context, 1.0f)
            rimPaint.color = Color.argb(65, 255, 255, 255)
            canvas.drawPath(shapePath, rimPaint)
        }
    }
}
