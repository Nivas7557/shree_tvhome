package shree.tvhome.design

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.core.LayoutMetrics
import java.util.Calendar

class GreetingCardDesign(
    ctx: Context,
    private var userName: String,
    private var customPhrase: String,
    private var accentColor: Int
) : LinearLayout(ctx) {

    private data class GreetingPeriodSpec(val hours: IntRange, val emoji: String, val label: String)

    private val greetingPeriods = listOf(
        GreetingPeriodSpec(5..11, "☀️", "Good Morning"),
        GreetingPeriodSpec(12..16, "🌤️", "Good Afternoon"),
        GreetingPeriodSpec(17..20, "🌆", "Good Evening")
    )

    private val iconTv = TextView(ctx)
    private val titleTv = TextView(ctx)
    private var overlayBubble: TextView? = null
    private var fogAnim: ValueAnimator? = null
    private var fogProgress = 0f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val airFogPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shaderMatrix = Matrix()
    private val shapePath = Path()
    private val avatarCutoutPath = Path()
    private val clipRect = RectF()

    private var cutoutCenterX = -1f
    private var cutoutCenterY = -1f
    private var cutoutRadius = 0f

    // Initializes greeting typography, pill geometry, and default time-based text.
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        outlineProvider = null
        setWillNotDraw(false)
        setPadding(dp(14), dp(7), dp(16), dp(7))

        iconTv.apply {
            textSize = 15f
            setPadding(0, 0, dp(8), 0)
        }
        titleTv.apply {
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        addView(iconTv)
        addView(titleTv)
        updateGreeting(userName, customPhrase)
        setAccentColor(accentColor)
    }

    // Stores optional floating bubble reference associated with this greeting card.
    fun attachOverlayBubble(bubble: TextView) {
        overlayBubble = bubble
    }

    // Updates avatar circular cutout geometry and rebuilds the card path.
    fun setAvatarCutout(centerXInCard: Float, centerYInCard: Float, radiusWithGap: Float) {
        cutoutCenterX = centerXInCard
        cutoutCenterY = centerYInCard
        cutoutRadius = radiusWithGap
        rebuildShapePath(width, height)
        invalidate()
    }

    // Updates the accent gradient tint and rebuilds the diagonal air-fog shader.
    fun setAccentColor(accent: Int) {
        accentColor = accent
        if (width > 0 && height > 0) {
            rebuildShaders(width.toFloat(), height.toFloat())
        }
        invalidate()
    }

    // Updates greeting phrase, font family, and user name based on Greeting Studio settings (preserving air-fog & avatar cutout).
    fun updateGreeting(name: String, phrase: String, styleKey: String = "timeOfDay", fontFamily: String = "sans-serif-medium") {
        userName = name
        customPhrase = phrase

        val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val matched = greetingPeriods.find { hr in it.hours }
        val emoji = matched?.emoji ?: "🌙"
        val autoGreet = matched?.label ?: "Good Night"

        iconTv.text = emoji
        titleTv.typeface = Typeface.create(fontFamily, Typeface.BOLD)
        val cleanName = userName.trim()

        titleTv.text = when (styleKey) {
            "minimal" -> cleanName.ifEmpty { autoGreet }
            "welcome" -> if (cleanName.isNotEmpty()) "Welcome Back, $cleanName" else "Welcome Back"
            "custom" -> {
                val p = customPhrase.trim().ifEmpty { "Hello" }
                if (cleanName.isNotEmpty()) "$p, $cleanName" else p
            }
            else -> if (cleanName.isNotEmpty()) "$autoGreet, $cleanName" else autoGreet
        }
    }

    // Subtracts the avatar circle path from the rounded pill path so the seam stays crisp.
    private fun rebuildShapePath(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val r = h / 2f
        clipRect.set(0f, 0f, w.toFloat(), h.toFloat())
        shapePath.reset()
        shapePath.addRoundRect(clipRect, r, r, Path.Direction.CW)

        if (cutoutRadius > 0f) {
            avatarCutoutPath.reset()
            avatarCutoutPath.addCircle(cutoutCenterX, cutoutCenterY, cutoutRadius, Path.Direction.CW)
            shapePath.op(avatarCutoutPath, Path.Op.DIFFERENCE)
        }
    }

    // Builds base glass gradient and soft 45-degree diagonal air-fog dissolve shader.
    private fun rebuildShaders(w: Float, h: Float) {
        bgPaint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                LayoutMetrics.withAlpha(accentColor, 128),
                Color.argb(95, 22, 34, 72),
                LayoutMetrics.withAlpha(accentColor, 65)
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )

        // Wide diagonal mist gradient that dissolves smoothly at both leading and trailing edges.
        airFogPaint.shader = LinearGradient(
            0f, 0f, w * 0.72f, h * 1.15f,
            intArrayOf(
                Color.TRANSPARENT,
                LayoutMetrics.withAlpha(accentColor, 26),
                Color.argb(64, 255, 255, 255),
                LayoutMetrics.withAlpha(accentColor, 34),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.26f, 0.50f, 0.74f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // Rebuilds path and starts continuous diagonal air-fog drift animation.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        rebuildShapePath(w, h)
        rebuildShaders(w.toFloat(), h.toFloat())

        fogAnim?.cancel()
        fogAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                fogProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // Draws the sculpted greeting background shape and subtle glass border.
    override fun onDraw(canvas: Canvas) {
        if (width > 0 && height > 0) {
            canvas.drawPath(shapePath, bgPaint)
            rimStrokePaint.strokeWidth = LayoutMetrics.dpF(context, 1f)
            rimStrokePaint.color = LayoutMetrics.withAlpha(accentColor, 120)
            canvas.drawPath(shapePath, rimStrokePaint)
        }
        super.onDraw(canvas)
    }

    // Clips and drifts the diagonal air-fog mist across the greeting card while dissolving its alpha near edges.
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (width > 0 && height > 0 && airFogPaint.shader != null) {
            val w = width.toFloat()
            val h = height.toFloat()
            // Dissolve opacity smoothly at start (0.0) and end (1.0) of the drift cycle.
            val dissolveEnvelope = kotlin.math.sin(fogProgress * Math.PI).toFloat().coerceIn(0f, 1f)
            airFogPaint.alpha = (255 * dissolveEnvelope).toInt()

            val dx = (-w * 0.65f) + (w * 1.45f * fogProgress)
            val dy = (-h * 0.25f) + (h * 0.50f * fogProgress)
            canvas.save()
            canvas.clipPath(shapePath)
            shaderMatrix.setTranslate(dx, dy)
            airFogPaint.shader.setLocalMatrix(shaderMatrix)
            canvas.drawRect(0f, 0f, w, h, airFogPaint)
            canvas.restore()
        }
    }

    // Stops fog animation when detached from window.
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        fogAnim?.cancel()
    }

    private fun dp(v: Int): Int = LayoutMetrics.dp(context, v)
}
