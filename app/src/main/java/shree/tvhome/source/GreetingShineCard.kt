package shree.tvhome.source

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.core.GlassSurfaceSpec

class GreetingShineCard(
    context: Context,
    initialName: String,
    initialPhrase: String,
    private var accentColor: Int,
    private val avatarSizePx: Int = LauncherUtils.dp(context, 32)
) : FrameLayout(context) {

    private val titleView = TextView(context)
    private val subView = TextView(context)
    private val avatarView = ImageView(context)
    private val avatarLetterView = TextView(context)
    private val avatarBadge = FrameLayout(context)

    private var shimmerX = 0f
    private var shimmerAnim: ValueAnimator? = null
    private val shaderMatrix = Matrix()
    private val clipRect = RectF()
    private val clipPath = Path()

    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }

    init {
        clipChildren = true
        clipToPadding = false
        val innerGap = LauncherUtils.dp(context, 10)
        val vPad = LauncherUtils.dp(context, 4)
        val hPad = LauncherUtils.dp(context, 6)
        setPadding(hPad, vPad, LauncherUtils.dp(context, 16), vPad)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        avatarBadge.background = buildAvatarBadgeDrawable()

        avatarView.scaleType = ImageView.ScaleType.CENTER_CROP
        avatarLetterView.apply {
            text = resolveInitial(initialName)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        avatarBadge.addView(avatarView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        avatarBadge.addView(avatarLetterView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        row.addView(avatarBadge, LinearLayout.LayoutParams(avatarSizePx, avatarSizePx))

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        titleView.apply {
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            isSingleLine = true
            maxLines = 1
        }
        subView.apply {
            textSize = 9f
            setTextColor(Color.argb(215, 225, 238, 255))
            isSingleLine = true
            maxLines = 1
        }

        textColumn.addView(titleView)
        textColumn.addView(subView)

        row.addView(
            textColumn,
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                leftMargin = innerGap
            }
        )

        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))

        applySurfaceStyle(focused = false)
        updateGreeting(initialName, initialPhrase)
    }

    private fun resolveInitial(name: String): String =
        name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"

    private fun buildAvatarBadgeDrawable(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(LauncherUtils.withAlpha(accentColor, 210))
        setStroke(LauncherUtils.dp(context, 1), Color.argb(180, 255, 255, 255))
    }

    private fun applySurfaceStyle(focused: Boolean) {
        background = GlassSurfaceSpec.createDrawable(
            ctx = context,
            style = GlassSurfaceSpec.GREETING_CARD,
            accentColor = accentColor,
            focused = focused,
            active = true,
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
        )
    }

    fun setAccentColor(accent: Int) {
        accentColor = accent
        applySurfaceStyle(isFocused)
        avatarBadge.background = buildAvatarBadgeDrawable()
        invalidate()
    }

    fun setAvatarDrawable(drawable: Drawable?) {
        if (drawable != null) {
            avatarView.setImageDrawable(drawable)
            avatarView.visibility = VISIBLE
            avatarLetterView.visibility = GONE
        } else {
            avatarView.setImageDrawable(null)
            avatarView.visibility = GONE
            avatarLetterView.visibility = VISIBLE
        }
    }

    fun updateGreeting(name: String, phrase: String) {
        val cleanName = name.ifBlank { "Shree" }
        val cleanPhrase = phrase.ifBlank { "Welcome back" }
        avatarLetterView.text = resolveInitial(cleanName)
        titleView.text = "$cleanPhrase, $cleanName"
        subView.text = cleanPhrase
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        applySurfaceStyle(gainFocus)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val radius = LauncherUtils.dpF(context, GlassSurfaceSpec.GREETING_CARD.radiusDp)
        clipRect.set(0f, 0f, w.toFloat(), h.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(clipRect, radius, radius, Path.Direction.CW)

        shimmerPaint.shader = LinearGradient(
            0f, 0f, w * 0.35f, h * 0.4f,
            intArrayOf(Color.TRANSPARENT, Color.argb(42, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        shimmerAnim?.cancel()
        shimmerAnim = ValueAnimator.ofFloat(-w * 0.4f, w * 1.35f).apply {
            duration = 3400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                shimmerX = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) {
            super.dispatchDraw(canvas)
            return
        }
        val radius = LauncherUtils.dpF(context, GlassSurfaceSpec.GREETING_CARD.radiusDp)
        clipRect.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(clipRect, radius, radius, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(clipPath)
        super.dispatchDraw(canvas)
        shimmerPaint.shader?.let { shader ->
            shaderMatrix.setTranslate(shimmerX, 0f)
            shader.setLocalMatrix(shaderMatrix)
            canvas.drawRect(clipRect, shimmerPaint)
        }
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shimmerAnim?.cancel()
    }
}
