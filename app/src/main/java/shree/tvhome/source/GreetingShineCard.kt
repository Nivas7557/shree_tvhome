package shree.tvhome.source

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
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
import android.view.View
import android.view.ViewOutlineProvider
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
    private val avatarSizePx: Int = LauncherUtils.dp(context, 58)
) : FrameLayout(context) {

    private val titleView = TextView(context)
    private val subView = TextView(context)
    private val avatarView = ImageView(context)
    private val avatarLetterView = TextView(context)
    private val avatarBadge = FrameLayout(context)

    private val greetingPill = object : LinearLayout(context) {
        private var shimmerX = 0f
        private var shimmerAnim: ValueAnimator? = null
        private val shaderMatrix = Matrix()
        private val clipRect = RectF()
        private val clipPath = Path()
        private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return
            val r = h * 0.5f
            clipRect.set(0f, 0f, w.toFloat(), h.toFloat())
            clipPath.reset()
            clipPath.addRoundRect(clipRect, r, r, Path.Direction.CW)

            shimmerPaint.shader = LinearGradient(
                0f, 0f, w * 0.35f, h * 0.4f,
                intArrayOf(Color.TRANSPARENT, Color.argb(45, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
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
            val r = height * 0.5f
            clipRect.set(0f, 0f, width.toFloat(), height.toFloat())
            clipPath.reset()
            clipPath.addRoundRect(clipRect, r, r, Path.Direction.CW)

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

    init {
        clipChildren = false
        clipToPadding = false

        val pillHeightPx = (avatarSizePx * 0.58f).toInt().coerceAtLeast(LauncherUtils.dp(context, 32))
        val sproutOverlapPx = (avatarSizePx * 0.30f).toInt()
        val pillStartOffsetPx = avatarSizePx - sproutOverlapPx

        // 1. Greeting pill emerging from the vertical center of the big circular DP
        greetingPill.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                sproutOverlapPx + LauncherUtils.dp(context, 10),
                LauncherUtils.dp(context, 3),
                LauncherUtils.dp(context, 16),
                LauncherUtils.dp(context, 3)
            )
        }

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
            textSize = 8.8f
            setTextColor(Color.argb(220, 225, 238, 255))
            isSingleLine = true
            maxLines = 1
        }

        textColumn.addView(titleView)
        textColumn.addView(subView)
        greetingPill.addView(textColumn)

        addView(
            greetingPill,
            LayoutParams(LayoutParams.WRAP_CONTENT, pillHeightPx, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                leftMargin = pillStartOffsetPx
            }
        )

        // 2. Large circular DP on top-left so the greeting pill sprouts out from its vertical center
        avatarBadge.apply {
            background = buildAvatarBadgeDrawable()
            elevation = LauncherUtils.dpF(context, 4f)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }

        avatarView.scaleType = ImageView.ScaleType.CENTER_CROP
        avatarLetterView.apply {
            text = resolveInitial(initialName)
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        avatarBadge.addView(avatarView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        avatarBadge.addView(avatarLetterView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        addView(
            avatarBadge,
            LayoutParams(avatarSizePx, avatarSizePx, Gravity.START or Gravity.CENTER_VERTICAL)
        )

        applySurfaceStyle(focused = false)
        updateGreeting(initialName, initialPhrase)
    }

    private fun resolveInitial(name: String): String =
        name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"

    private fun buildAvatarBadgeDrawable(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(LauncherUtils.withAlpha(accentColor, 235))
        setStroke(LauncherUtils.dp(context, 2), Color.argb(220, 255, 255, 255))
    }

    private fun applySurfaceStyle(focused: Boolean) {
        greetingPill.background = GlassSurfaceSpec.createDrawable(
            ctx = context,
            style = GlassSurfaceSpec.GREETING_CARD,
            accentColor = accentColor,
            focused = focused,
            active = true,
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
        ).apply {
            cornerRadius = LauncherUtils.dpF(context, 20f)
        }
        avatarBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(LauncherUtils.withAlpha(accentColor, if (focused) 255 else 235))
            setStroke(
                LauncherUtils.dp(context, if (focused) 3 else 2),
                if (focused) Color.WHITE else Color.argb(215, 255, 255, 255)
            )
        }
    }

    fun setAccentColor(accent: Int) {
        accentColor = accent
        applySurfaceStyle(isFocused)
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
}
