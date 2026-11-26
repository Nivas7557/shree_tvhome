// Important instructions:
// - Cache gradient shaders when size or style changes instead of allocating shaders inside draw loops.
// - Render a soft diagonal air-fog dissolve sheen and luminous aura on focused tiles without heavy RAM overhead.

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
    private val outerCardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outerSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val diagonalFogPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunrayGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coreBloomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val rectF = RectF()
    private val sheenRect = RectF()
    private val outerPath = Path()
    private val innerIconPath = Path()
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

    // Updates colors, shape, and theme palette and rebuilds geometry/shaders when sized.
    fun setStyle(color: Int, shape: String?, themeKey: String?, dominantColor: Int = color) {
        accentColor = color
        appDominantColor = dominantColor
        iconShape = shape ?: Themes.TILE_SHAPES.first().key
        tileKey = themeKey ?: Themes.THEMES.first().key
        cachedSunrayKey = ""
        if (width > 0 && height > 0) {
            buildDoublePaths(width.toFloat(), height.toFloat())
            updateShaders(width.toFloat(), height.toFloat())
        }
        invalidate()
    }

    // Transitions focus glow state and fades out residual glow smoothly on unfocus.
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

    // Rebuilds outer/inner silhouette paths and shaders whenever view bounds change.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val wf = w.toFloat()
        val hf = h.toFloat()
        cachedSunrayKey = ""
        buildDoublePaths(wf, hf)
        updateShaders(wf, hf)
    }

    // Builds linear and radial gradient shaders for the outer frame, sheen, diagonal air-fog, and bloom.
    private fun updateShaders(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return

        val colors = Themes.getTopBottomColors(tileKey, tileFocused)
        outerCardPaint.shader = LinearGradient(0f, 0f, w * 0.35f, h, colors, null, Shader.TileMode.CLAMP)

        sheenRect.set(0f, 0f, w, h * 0.46f)
        outerSheenPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.46f,
            intArrayOf(
                Color.argb(if (tileFocused) 165 else 105, 255, 255, 255),
                Color.argb(if (tileFocused) 50 else 24, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )

        // Diagonal air-fog dissolve band that glides softly across focused tiles.
        diagonalFogPaint.shader = LinearGradient(
            0f, 0f, w * 0.85f, h * 0.85f,
            intArrayOf(
                Color.TRANSPARENT,
                LayoutMetrics.withAlpha(accentColor, 38),
                Color.argb(82, 255, 255, 255),
                LayoutMetrics.withAlpha(appDominantColor, 44),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.25f, 0.50f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )

        coreBloomPaint.shader = RadialGradient(
            w / 2f, h * 0.45f, maxOf(w, h) * 0.65f,
            intArrayOf(
                LayoutMetrics.withAlpha(accentColor, 165),
                LayoutMetrics.withAlpha(appDominantColor, 60),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // Creates concentric outer frame and inner icon paths with a visible gap.
    private fun buildDoublePaths(w: Float, h: Float) {
        val outerInset = dp(1.5f)
        val visibleGap = minOf(w, h) * 0.095f
        buildShapeIntoPath(outerPath, w, h, outerInset)
        buildShapeIntoPath(innerIconPath, w, h, outerInset + visibleGap)
    }
        // Populates a shape path for heart, circle, classic square, or rounded squircle.
    private fun buildShapeIntoPath(target: Path, w: Float, h: Float, inset: Float) {
        target.reset()
        val sw = w - inset * 2f
        val sh = h - inset * 2f
        if (sw <= 0f || sh <= 0f) return

        val s = minOf(sw, sh)
        val left = (w - s) / 2f
        val top = (h - s) / 2f
        val right = left + s
        val bottom = top + s
        val cx = w / 2f
        val cy = h / 2f

        when (iconShape) {
            "heart" -> {
                val topDipY = top + s * 0.26f
                val bottomTipY = bottom - s * 0.04f
                target.moveTo(cx, bottomTipY)
                target.cubicTo(
                    left - s * 0.06f, top + s * 0.62f,
                    left + s * 0.02f, top + s * 0.04f,
                    cx, topDipY
                )
                target.cubicTo(
                    right - s * 0.02f, top + s * 0.04f,
                    right + s * 0.06f, top + s * 0.62f,
                    cx, bottomTipY
                )
                target.close()
            }
            "circle" -> {
                target.addCircle(cx, cy, s / 2f, Path.Direction.CW)
            }
            else -> {
                val radius = if (iconShape == "classic") dp(6f) else s * 0.22f
                rectF.set(left, top, right, bottom)
                target.addRoundRect(rectF, radius, radius, Path.Direction.CW)
            }
        }
    }

    // Draws sunray aura, outer glass frame, clipped icon bitmap, diagonal air-fog sheen, and rim strokes.
    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val baseRadius = maxOf(w, h) * 0.56f

        if (tileFocused || glowIntensity > 0.01f) {
            val strength = if (tileFocused) (0.60f + 0.40f * glowIntensity) else glowIntensity
            val sunrayRadius = baseRadius * (1.08f + 0.14f * glowIntensity)
            val bucket = (glowIntensity * 10f).toInt()
            val key = "${w.toInt()}x${h.toInt()}_${accentColor}_${appDominantColor}_$bucket"
            if (key != cachedSunrayKey || cachedSunrayShader == null) {
                cachedSunrayKey = key
                cachedSunrayShader = RadialGradient(
                    cx, cy, sunrayRadius,
                    intArrayOf(
                        LayoutMetrics.withAlpha(accentColor, (225 * strength).toInt()),
                        LayoutMetrics.withAlpha(appDominantColor, (125 * strength).toInt()),
                        LayoutMetrics.withAlpha(accentColor, (32 * strength).toInt()),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0.25f, 0.56f, 0.82f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            sunrayGlowPaint.shader = cachedSunrayShader
            canvas.drawCircle(cx, cy, sunrayRadius, sunrayGlowPaint)
        }

        canvas.drawPath(outerPath, outerCardPaint)
        canvas.save()
        canvas.clipPath(outerPath)
        canvas.drawRect(sheenRect, outerSheenPaint)
        if (tileFocused || glowIntensity > 0.01f) {
            coreBloomPaint.alpha = (255 * if (tileFocused) (0.65f + 0.35f * glowIntensity) else glowIntensity).toInt()
            canvas.drawRect(0f, 0f, w, h, coreBloomPaint)
        }
        canvas.restore()

        canvas.save()
        canvas.clipPath(innerIconPath)
        super.dispatchDraw(canvas)
        canvas.restore()

        // Drift a soft diagonal air-fog mist across the focused tile silhouette that dissolves at edges.
        if (tileFocused && diagonalFogPaint.shader != null) {
            val fogEnvelope = sin(glowIntensity * Math.PI).toFloat().coerceIn(0f, 1f)
            diagonalFogPaint.alpha = (255 * fogEnvelope).toInt()
            val shiftX = (-w * 0.55f) + (w * 1.15f * glowIntensity)
            val shiftY = (-h * 0.55f) + (h * 1.15f * glowIntensity)
            canvas.save()
            canvas.clipPath(outerPath)
            fogMatrix.setTranslate(shiftX, shiftY)
            diagonalFogPaint.shader.setLocalMatrix(fogMatrix)
            canvas.drawRect(0f, 0f, w, h, diagonalFogPaint)
            canvas.restore()
        }

        innerRimPaint.strokeWidth = dp(if (tileFocused) 1.6f else 1.1f)
        innerRimPaint.color = if (tileFocused) Color.argb(225, 255, 255, 255) else Color.argb(110, 255, 255, 255)
        canvas.drawPath(outerPath, innerRimPaint)
        canvas.drawPath(innerIconPath, innerRimPaint)
    }

    // Cancels running exit animator on detach.
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        exitAnimator?.cancel()
    }

    private fun dp(value: Float): Float = LayoutMetrics.dpF(context, value)
}
