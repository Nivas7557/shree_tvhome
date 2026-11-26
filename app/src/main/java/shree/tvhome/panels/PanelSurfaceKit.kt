// Important instructions:
// - Serve as the shared surface kit with Canva-style gradient fills, focus previews, and D-Pad sliders.
// - Support focus preview callbacks so options preview on focus and apply only when clicked/confirmed.

package shree.tvhome.panels

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes
import java.util.Locale

object PanelSurfaceKit {

    // Adds a theme-aware section header that automatically updates when palette or accent changes.
    fun addSectionHeader(ctx: Context, parent: LinearLayout, settings: AppSettings, label: String) {
        val tv = TextView(ctx).apply {
            text = label.uppercase(Locale.ROOT)
            textSize = 8.8f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.09f
        }
        val applyColor = {
            val surfaceColors = Themes.getTheme(settings.tileColor).normalColors
            tv.setTextColor(
                if (Themes.isLightSurface(surfaceColors)) {
                    Themes.getAdaptiveTextColor(surfaceColors, secondary = false)
                } else {
                    settings.accentColor
                }
            )
        }
        applyColor()
        settings.addStyleListener(applyColor)
        parent.addView(tv, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(ctx, 10)
            bottomMargin = dp(ctx, 5)
        })
    }

    // Builds a glass card background blended with the Canva-style accent gradient.
    fun glassCardBg(ctx: Context, accent: Int, active: Boolean, themeKey: String? = null) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        if (active) {
            val grad = Themes.getAccentGradientColors(accent)
            intArrayOf(
                LayoutMetrics.withAlpha(grad.firstOrNull() ?: accent, 225),
                LayoutMetrics.withAlpha(grad.lastOrNull() ?: accent, 165)
            )
        } else {
            val base = Themes.getTheme(themeKey).normalColors
            val topCol = base.firstOrNull() ?: Color.rgb(22, 34, 72)
            val botCol = base.lastOrNull() ?: topCol
            intArrayOf(
                LayoutMetrics.blendWithAccent(topCol, accent, 0.16f, 185),
                LayoutMetrics.withAlpha(botCol, 155)
            )
        }
    ).apply {
        cornerRadius = LayoutMetrics.dpF(ctx, 14f)
        setStroke(
            dp(ctx, if (active) 2 else 1),
            if (active) Color.WHITE else LayoutMetrics.withAlpha(accent, 115)
        )
    }

    // Adds a horizontal segmented bar that previews on focus and applies only on click/OK.
    fun addSegmentedTrack(
        ctx: Context,
        parent: LinearLayout,
        settings: AppSettings,
        options: List<Pair<String, String>>,
        getSelected: () -> String,
        onFocusPreview: ((String?) -> Unit)? = null,
        onSelect: (String) -> Unit
    ) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3))
            clipChildren = false
            clipToPadding = false
        }

        val buttons = ArrayList<Pair<String, TextView>>()
        val refreshChips = {
            val grad = Themes.getAccentGradientColors(settings.accentColor)
            row.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(85, 10, 16, 38), Color.argb(55, 22, 34, 72))
            ).apply {
                cornerRadius = LayoutMetrics.dpF(ctx, 14f)
                setStroke(dp(ctx, 1), LayoutMetrics.withAlpha(grad.lastOrNull() ?: settings.accentColor, 105))
            }
            val current = getSelected()
            val textColor = Themes.getPrimaryTextColor(settings.tileColor)
            val activeChipColors = grad
            buttons.forEach { (k, tv) ->
                val isSel = k == current
                tv.setTextColor(
                    if (isSel || tv.hasFocus()) Themes.getAdaptiveTextColor(activeChipColors, secondary = false)
                    else textColor
                )
                tv.background = if (isSel || tv.hasFocus()) {
                    GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(
                            LayoutMetrics.withAlpha(grad.firstOrNull() ?: settings.accentColor, 230),
                            LayoutMetrics.withAlpha(grad.lastOrNull() ?: settings.accentColor, 175)
                        )
                    ).apply {
                        cornerRadius = LayoutMetrics.dpF(ctx, 11f)
                        setStroke(dp(ctx, if (tv.hasFocus()) 2 else 1), Color.WHITE)
                    }
                } else null
            }
        }

        options.forEachIndexed { idx, (key, label) ->
            val tv = TextView(ctx).apply {
                text = label
                textSize = 9.2f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                isFocusable = true
                isClickable = true
                setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.05f, 4f)
                    refreshChips()
                    if (f) onFocusPreview?.invoke(key) else onFocusPreview?.invoke(null)
                }
                setOnClickListener {
                    onFocusPreview?.invoke(null)
                    onSelect(key)
                    refreshChips()
                }
            }
            buttons.add(key to tv)
            row.addView(tv, LinearLayout.LayoutParams(0, dp(ctx, 30), 1f).apply {
                if (idx < options.lastIndex) rightMargin = dp(ctx, 3)
            })
        }
        refreshChips()
        settings.addStyleListener(refreshChips)
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })
    }

    // Adds a D-Pad Left/Right and touch percentage slider with gradient fill.
    fun addGlossyPillSlider(
        ctx: Context,
        parent: LinearLayout,
        settings: AppSettings,
        icon: String,
        title: String,
        initialPct: Int,
        onChange: (Int) -> Unit
    ) {
        var currentPct = initialPct.coerceIn(0, 100)
        val sliderView = object : View(ctx) {
            private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.DEFAULT_BOLD
            }
            private val clipPath = Path()
            private val rect = RectF()
            private var isFocusedState = false

            init {
                tag = "pill_slider"
                isFocusable = true
                isClickable = true
                settings.addStyleListener { invalidate() }
                setOnFocusChangeListener { v, f ->
                    isFocusedState = f
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.02f, 5f)
                    invalidate()
                }
                setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                currentPct = (currentPct - 5).coerceIn(0, 100)
                                onChange(currentPct)
                                invalidate()
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                currentPct = (currentPct + 5).coerceIn(0, 100)
                                onChange(currentPct)
                                invalidate()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
            }

            // Scrubs slider percentage smoothly on pointer down/move events.
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                    val ratio = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    currentPct = (ratio * 100).toInt()
                    onChange(currentPct)
                    invalidate()
                    return true
                }
                return super.onTouchEvent(event)
            }

            // Draws the pill track, gradient accent fill bar, border stroke, title, and percentage.
            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0f || h <= 0f) return
                val r = LayoutMetrics.dpF(ctx, 14f)
                rect.set(0f, 0f, w, h)
                clipPath.reset()
                clipPath.addRoundRect(rect, r, r, Path.Direction.CW)

                canvas.save()
                canvas.clipPath(clipPath)

                trackPaint.color = Color.argb(68, 16, 24, 52)
                canvas.drawRect(rect, trackPaint)

                val fillW = w * (currentPct / 100f)
                val grad = Themes.getAccentGradientColors(settings.accentColor)
                fillPaint.shader = LinearGradient(
                    0f, 0f, fillW.coerceAtLeast(1f), 0f,
                    intArrayOf(
                        LayoutMetrics.withAlpha(grad.firstOrNull() ?: settings.accentColor, 225),
                        LayoutMetrics.withAlpha(grad.lastOrNull() ?: settings.accentColor, 175)
                    ),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, fillW, h, fillPaint)
                canvas.restore()

                borderPaint.strokeWidth = LayoutMetrics.dpF(ctx, if (isFocusedState) 2f else 1f)
                borderPaint.color = if (isFocusedState) Color.WHITE else LayoutMetrics.withAlpha(settings.accentColor, 110)
                canvas.drawRoundRect(rect, r, r, borderPaint)

                textPaint.color = Themes.getPrimaryTextColor(settings.tileColor)
                textPaint.textSize = LayoutMetrics.dpF(ctx, 10.5f)
                textPaint.textAlign = Paint.Align.LEFT
                val ty = (h / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
                canvas.drawText("$icon  $title", LayoutMetrics.dpF(ctx, 12f), ty, textPaint)

                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("$currentPct%", w - LayoutMetrics.dpF(ctx, 12f), ty, textPaint)
            }
        }
        parent.addView(sliderView, LinearLayout.LayoutParams(-1, dp(ctx, 40)).apply { bottomMargin = dp(ctx, 7) })
    }

    // Creates a circular gradient orb view for palette or accent previews.
    fun createThemeOrbView(ctx: Context, colors: IntArray, selected: Boolean): View = object : View(ctx) {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        // Renders gradient fill and selection ring inside the circular orb.
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(cx, cy) - LayoutMetrics.dpF(ctx, 2f)
            if (r <= 0f) return
            fillPaint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors, null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, fillPaint)
            ringPaint.strokeWidth = LayoutMetrics.dpF(ctx, if (selected) 2f else 1f)
            ringPaint.color = if (selected) Color.WHITE else Color.argb(120, 255, 255, 255)
            canvas.drawCircle(cx, cy, r, ringPaint)
        }
    }

    // Creates a standard action row with optional tag and focus preview callback.
    fun makeActionRow(
        ctx: Context,
        settings: AppSettings,
        icon: String,
        title: String,
        sub: String,
        trailingText: String = "›",
        tagName: String? = null,
        onFocusPreview: ((Boolean) -> Unit)? = null,
        onClick: () -> Unit
    ) = LinearLayout(ctx).apply {
        if (tagName != null) tag = tagName
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9))
        isFocusable = true
        isClickable = true

        val iconTv = TextView(ctx).apply {
            text = icon
            textSize = 14f
            setPadding(0, 0, dp(ctx, 10), 0)
        }
        val titleTv = TextView(ctx).apply {
            text = title
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        val subTv = TextView(ctx).apply {
            text = sub
            textSize = 8.5f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        val trailTv = TextView(ctx).apply {
            text = trailingText
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }

        val syncVisuals = {
            val primaryCol = Themes.getPrimaryTextColor(settings.tileColor)
            val secondaryCol = Themes.getSecondaryTextColor(settings.tileColor)
            background = glassCardBg(ctx, settings.accentColor, hasFocus(), settings.tileColor)
            iconTv.setTextColor(settings.accentColor)
            titleTv.setTextColor(primaryCol)
            subTv.setTextColor(secondaryCol)
            trailTv.setTextColor(primaryCol)
        }
        syncVisuals()
        settings.addStyleListener(syncVisuals)

        setOnFocusChangeListener { v, f ->
            AnimationController.animateFocus(v, f, settings.motionStyle, 1.02f, 4f)
            syncVisuals()
            onFocusPreview?.invoke(f)
        }
        setOnClickListener { onClick() }

        addView(iconTv)
        val mid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(titleTv)
        mid.addView(subTv)
        addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        addView(trailTv)
    }

    private fun dp(ctx: Context, v: Int): Int = LayoutMetrics.dp(ctx, v)
}
