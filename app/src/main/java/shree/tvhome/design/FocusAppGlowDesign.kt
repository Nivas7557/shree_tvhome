// Important instructions:
// - Render a clean, full-bleed TV banner card without an outer double-glass border frame.
// - Cache gradient shaders and draw luminous aura + diagonal air-fog dissolve sheen on focus.

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
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes
import kotlin.math.sin

class FocusAppGlowDesign(context: Context) : FrameLayout(context) {
    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val topSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val diagonalFogPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunrayGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val rectF = RectF()
    private val sheenRect = RectF()
    private val cardPath = Path()
    private val fogMatrix = Matrix()

    private var tileFocused = false
    private var glowIntensity = 0f
    private var exitAnimator: ValueAnimator? = null
    private var accentColor = Themes.ACCENTS.first().color
    private var appDominantColor = Themes.ACCENTS.first().color
    private var iconShape = Themes.TILE_SHAPES.first().key
    private var tileKey = Themes.THEMES.first().key
    private var cachedSunrayShader: RadialGradient? = null
    private var cachedSunrayKey = ""

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        outlineProvider = null
    }

    // Updates colors, silhouette shape, and palette and rebuilds paths/shaders when sized.
    fun setStyle(color: Int, shape: String?, themeKey: String?, dominantColor: Int = color) {
        accentColor = color
        appDominantColor = dominantColor
        iconShape = shape ?: Themes.TILE_SHAPES.first().key
        tileKey = themeKey ?: Themes.THEMES.first().key
        cachedSunrayKey = ""
        if (width > 0 && height > 0) {
            buildCardPath(width.toFloat(), height.toFloat())
            updateShaders(width.toFloat(), height.toFloat())
        }
        invalidate()
    }

    // Transitions focus glow state and fades out residual aura smoothly on unfocus.
    fun setFocusState(focused: Boolean) {
        if (tileFocused == focused) return
        tileFocused = focused
        exitAnimator?.cancel()

        if (focused) {
            if (width > 0 && height > 0) updateShaders(width.toFloat(), height.toFloat())
            invalidate()
        } else {
            val startGlow = glowIntensity.coerceAtLeast(0.45f)
            exitAnimator = ValueAnimator.ofFloat(startGlow, 0f).apply {
                duration = 360L
                interpolator = DecelerateInterpolator(1.7f)
                addUpdateListener { anim ->
                    glowIntensity = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    // Updates animated pulse intensity (0..1) during focus breathing and diagonal air-fog drift.
    fun updateGlowPulse(intensity: Float) {
        glowIntensity = intensity.coerceIn(0f, 1f)
        invalidate()
    }

    // Rebuilds single full-bleed card path and shaders whenever view bounds change.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val wf = w.toFloat()
        val hf = h.toFloat()
        cachedSunrayKey = ""
        buildCardPath(wf, hf)
        updateShaders(wf, hf)
    }

    // Builds rich app-tinted backdrop gradient, subtle glass top sheen, and diagonal air-fog shaders.
    private fun updateShaders(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return

        val baseColors = Themes.getTopBottomColors(tileKey, tileFocused)
        val c1 = LayoutMetrics.blendWithAccent(baseColors.first(), appDominantColor, 0.32f, 235)
        val c2 = LayoutMetrics.blendWithAccent(baseColors.last(), appDominantColor, 0.18f, 245)
        backdropPaint.shader = LinearGradient(0f, 0f, w, h, intArrayOf(c1, c2), null, Shader.TileMode.CLAMP)

        sheenRect.set(0f, 0f, w, h * 0.42f)
        topSheenPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.42f,
            intArrayOf(
                Color.argb(if (tileFocused) 95 else 52, 255, 255, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )

        diagonalFogPaint.shader = LinearGradient(
            0f, 0f, w * 0.85f, h * 0.85f,
            intArrayOf(
                Color.TRANSPARENT,
                LayoutMetrics.withAlpha(accentColor, 34),
                Color.argb(76, 255, 255, 255),
                LayoutMetrics.withAlpha(appDominantColor, 40),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.25f, 0.50f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // Builds a single clean TV card silhouette (squircle, circle, heart, or classic rounded rectangle).
    private fun buildCardPath(w: Float, h: Float) {
        cardPath.reset()
        val inset = dp(1f)
        val sw = w - inset * 2f
        val sh = h - inset * 2f
        if (sw <= 0f || sh <= 0f) return

        val left = inset
        val top = inset
        val right = w - inset
        val bottom = h - inset
        val cx = w / 2f
        val cy = h / 2f

        when (iconShape) {
            "heart" -> {
                val s = minOf(sw, sh)
                val hLeft = (w - s) / 2f
                val hTop = (h - s) / 2f
                val hRight = hLeft + s
                val hBottom = hTop + s
                val topDipY = hTop + s * 0.26f
                val bottomTipY = hBottom - s * 0.04f
                cardPath.moveTo(cx, bottomTipY)
                cardPath.cubicTo(
                    hLeft - s * 0.06f, hTop + s * 0.62f,
                    hLeft + s * 0.02f, hTop + s * 0.04f,
                    cx, topDipY
                )
                cardPath.cubicTo(
                    hRight - s * 0.02f, hTop + s * 0.04f,
                    hRight + s * 0.06f, hTop + s * 0.62f,
                    cx, bottomTipY
                )
                cardPath.close()
            }
            "circle" -> {
                val r = minOf(sw, sh) / 2f
                cardPath.addCircle(cx, cy, r, Path.Direction.CW)
            }
            else -> {
                val radius = if (iconShape == "classic") dp(8f) else minOf(sw, sh) * 0.22f
                rectF.set(left, top, right, bottom)
                cardPath.addRoundRect(rectF, radius, radius, Path.Direction.CW)
            }
        }
    }

    // Draws outer focus aura, full-bleed TV banner card, diagonal air-fog sheen, and crisp focus rim.
    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val baseRadius = maxOf(w, h) * 0.58f

        if (tileFocused || glowIntensity > 0.01f) {
            val strength = if (tileFocused) (0.62f + 0.38f * glowIntensity) else glowIntensity
            val sunrayRadius = baseRadius * (1.06f + 0.14f * glowIntensity)
            val bucket = (glowIntensity * 10f).toInt()
            val key = "${w.toInt()}x${h.toInt()}_${accentColor}_${appDominantColor}_$bucket"
            if (key != cachedSunrayKey || cachedSunrayShader == null) {
                cachedSunrayKey = key
                cachedSunrayShader = RadialGradient(
                    cx, cy, sunrayRadius,
                    intArrayOf(
                        LayoutMetrics.withAlpha(accentColor, (220 * strength).toInt()),
                        LayoutMetrics.withAlpha(appDominantColor, (125 * strength).toInt()),
                        LayoutMetrics.withAlpha(accentColor, (30 * strength).toInt()),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0.25f, 0.56f, 0.82f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            sunrayGlowPaint.shader = cachedSunrayShader
            canvas.drawCircle(cx, cy, sunrayRadius, sunrayGlowPaint)
        }

        // Clip all card content directly to the single TV banner silhouette without any outer glass border box.
        canvas.save()
        canvas.clipPath(cardPath)
        canvas.drawRect(0f, 0f, w, h, backdropPaint)
        super.dispatchDraw(canvas)
        canvas.drawRect(sheenRect, topSheenPaint)

        // Drift diagonal air-fog mist across the focused card with soft edge dissolve.
        if (tileFocused && diagonalFogPaint.shader != null) {
            val fogEnvelope = sin(glowIntensity * Math.PI).toFloat().coerceIn(0f, 1f)
            diagonalFogPaint.alpha = (255 * fogEnvelope).toInt()
            val shiftX = (-w * 0.55f) + (w * 1.15f * glowIntensity)
            val shiftY = (-h * 0.55f) + (h * 1.15f * glowIntensity)
            fogMatrix.setTranslate(shiftX, shiftY)
            diagonalFogPaint.shader.setLocalMatrix(fogMatrix)
            canvas.drawRect(0f, 0f, w, h, diagonalFogPaint)
        }
        canvas.restore()

        // Draw crisp rim border only on focus (and subtle hairline when unfocused).
        rimPaint.strokeWidth = dp(if (tileFocused) 2.2f else 0.8f)
        rimPaint.color = if (tileFocused) Color.WHITE else Color.argb(65, 255, 255, 255)
        canvas.drawPath(cardPath, rimPaint)
    }

    // Cancels running exit animator on detach.
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
