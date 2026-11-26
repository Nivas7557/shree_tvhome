// Important instructions:
// - Host all auto-closing inline live-preview sub-menus with dynamic column calculation from Themes constants.
// - Never call requestFocus() or hardcode widths; rely on LayoutMetrics and Themes single source of truth.

package shree.tvhome.panels

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.MoodPresets
import shree.tvhome.core.Themes

object InlineStyleSubMenus {

    // Creates a glass sub-menu container that expands inline and auto-closes on selection or blur.
    fun createContainer(ctx: Context, settings: AppSettings): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            clipChildren = false
            clipToPadding = false
            val padH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 6, maxDp = 10, horizontal = true)
            val padV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.007f, minDp = 5, maxDp = 8, horizontal = false)
            setPadding(padH, padV, padH, padV / 2)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(128, 14, 22, 48), Color.argb(98, 22, 34, 72))
            ).apply {
                cornerRadius = LayoutMetrics.dpF(ctx, 14f)
                setStroke(LayoutMetrics.dp(ctx, 1), LayoutMetrics.withAlpha(settings.accentColor, 140))
            }
        }

    // Populates dynamic-column option chips derived from Themes.MIN_TEXT_CHIP_DP and active panel width.
    fun populateOptionChips(
        ctx: Context,
        box: LinearLayout,
        settings: AppSettings,
        options: List<Pair<String, String>>,
        optH: Int,
        gapH: Int,
        gapV: Int,
        getCommitted: () -> String,
        getCurrent: () -> String,
        onPreview: (String) -> Unit,
        onCommit: (String) -> Unit
    ) {
        val cols = MoodPresets.computeDynamicColumns(
            ctx, settings.panelWidthPct, Themes.MIN_TEXT_CHIP_DP, options.size.coerceAtLeast(1)
        )
        val buttons = ArrayList<Pair<String, TextView>>()
        val refreshButtons = {
            val cur = getCurrent()
            buttons.forEach { (k, tv) ->
                val active = tv.hasFocus() || k == cur
                tv.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, active, settings.tileColor)
                tv.setTextColor(
                    if (active) Themes.getAdaptiveTextColor(intArrayOf(settings.accentColor))
                    else Themes.getPrimaryTextColor(settings.tileColor)
                )
            }
        }
        options.chunked(cols).forEach { rowItems ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            rowItems.forEachIndexed { idx, (key, label) ->
                val tv = TextView(ctx).apply {
                    text = label
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                    isFocusable = true
                    isClickable = true
                    setOnFocusChangeListener { v, f ->
                        AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 4f)
                        if (f) {
                            onPreview(key)
                        } else if (getCurrent() != getCommitted()) {
                            onPreview(getCommitted())
                        }
                        refreshButtons()
                    }
                    setOnClickListener {
                        onCommit(key)
                        refreshButtons()
                    }
                }
                buttons.add(key to tv)
                row.addView(tv, LinearLayout.LayoutParams(0, optH, 1f).apply {
                    if (idx < rowItems.lastIndex) rightMargin = gapH
                })
            }
            box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV / 2 })
        }
        refreshButtons()
    }
    // Populates pure circular gradient swatches with dynamic column count from Themes.MIN_SWATCH_ORB_DP.
    fun populateAccentSwatches(
        ctx: Context,
        box: LinearLayout,
        settings: AppSettings,
        optH: Int,
        gapH: Int,
        gapV: Int,
        getCommittedColor: () -> Int,
        getCommittedName: () -> String,
        onCommit: (Int, String) -> Unit
    ): () -> Unit {
        val cols = MoodPresets.computeDynamicColumns(
            ctx, settings.panelWidthPct, Themes.MIN_SWATCH_ORB_DP, Themes.ACCENTS.size
        )
        val swatches = ArrayList<View>()
        val syncSwatches: () -> Unit = { swatches.forEach { it.invalidate() } }
        Themes.ACCENTS.chunked(cols).forEach { rowSpecs ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
            }
            rowSpecs.forEachIndexed { idx, spec ->
                val swatch = object : View(ctx) {
                    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

                    override fun onDraw(canvas: Canvas) {
                        val cx = width / 2f
                        val cy = height / 2f
                        val r = minOf(cx, cy) - LayoutMetrics.dpF(context, 2.5f)
                        if (r <= 0f) return
                        fillPaint.shader = LinearGradient(
                            cx - r, cy - r, cx + r, cy + r,
                            spec.gradientColors, null, Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(cx, cy, r, fillPaint)
                        val active = hasFocus() || settings.accentColor == spec.color
                        ringPaint.strokeWidth = LayoutMetrics.dpF(context, if (active) 2.4f else 1f)
                        ringPaint.color = if (active) Color.WHITE else Color.argb(120, 255, 255, 255)
                        canvas.drawCircle(cx, cy, r, ringPaint)
                    }
                }.apply {
                    tag = "accent_${spec.key}"
                    isFocusable = true
                    isClickable = true
                    setOnFocusChangeListener { v, f ->
                        AnimationController.animateFocus(v, f, settings.motionStyle, 1.12f, 5f)
                        if (f) {
                            settings.accentColor = spec.color
                            settings.accentName = spec.label
                        } else if (settings.accentColor != getCommittedColor()) {
                            settings.accentColor = getCommittedColor()
                            settings.accentName = getCommittedName()
                        }
                        syncSwatches()
                    }
                    setOnClickListener {
                        settings.accentColor = spec.color
                        settings.accentName = spec.label
                        onCommit(spec.color, spec.label)
                        syncSwatches()
                    }
                }
                swatches.add(swatch)
                row.addView(swatch, LinearLayout.LayoutParams(0, optH, 1f).apply {
                    if (idx < rowSpecs.lastIndex) rightMargin = gapH
                })
            }
            box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV / 2 })
        }
        return syncSwatches
    }

    // Populates dynamic-column motion style buttons driven by Themes.MIN_TEXT_CHIP_DP.
    fun populateMotionOptions(
        ctx: Context,
        box: LinearLayout,
        hostDrawer: View,
        settings: AppSettings,
        optH: Int,
        gapH: Int,
        gapV: Int,
        getCommittedMotion: () -> String,
        onCommit: (String) -> Unit
    ): () -> Unit {
        val cols = MoodPresets.computeDynamicColumns(
            ctx, settings.panelWidthPct, Themes.MIN_TEXT_CHIP_DP, AnimationController.STYLES.size
        )
        val buttons = ArrayList<Pair<String, TextView>>()
        val syncButtons: () -> Unit = {
            buttons.forEach { (key, btn) ->
                val active = btn.hasFocus() || settings.motionStyle == key
                btn.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, active, settings.tileColor)
                btn.setTextColor(
                    if (active) Themes.getAdaptiveTextColor(intArrayOf(settings.accentColor))
                    else Themes.getPrimaryTextColor(settings.tileColor)
                )
            }
        }
        AnimationController.STYLES.chunked(cols).forEach { rowChunk ->
            val r = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            rowChunk.forEachIndexed { idx, spec ->
                val btn = TextView(ctx).apply {
                    tag = "motion_${spec.key}"
                    text = spec.label
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                    isFocusable = true
                    isClickable = true
                    setOnFocusChangeListener { v, f ->
                        AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 3f)
                        if (f) {
                            settings.motionStyle = spec.key
                            AnimationController.playDemoPreview(hostDrawer, spec.key)
                        } else if (settings.motionStyle != getCommittedMotion()) {
                            settings.motionStyle = getCommittedMotion()
                        }
                        syncButtons()
                    }
                    setOnClickListener {
                        settings.motionStyle = spec.key
                        onCommit(spec.key)
                        syncButtons()
                    }
                }
                buttons.add(spec.key to btn)
                r.addView(btn, LinearLayout.LayoutParams(0, optH, 1f).apply {
                    if (idx < rowChunk.lastIndex) rightMargin = gapH
                })
            }
            box.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV / 2 })
        }
        syncButtons()
        return syncButtons
    }

    // Populates pure glass palette orbs with dynamic column count from Themes.MIN_SWATCH_ORB_DP.
    fun populatePaletteOrbs(
        ctx: Context,
        box: LinearLayout,
        settings: AppSettings,
        optH: Int,
        gapH: Int,
        gapV: Int,
        getCommittedTheme: () -> String,
        onCommit: (String) -> Unit
    ): () -> Unit {
        val cols = MoodPresets.computeDynamicColumns(
            ctx, settings.panelWidthPct, Themes.MIN_SWATCH_ORB_DP, Themes.THEMES.size
        )
        val orbs = ArrayList<View>()
        val syncOrbs: () -> Unit = { orbs.forEach { it.invalidate() } }
        Themes.THEMES.chunked(cols).forEach { rowChunk ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
            }
            rowChunk.forEachIndexed { idx, theme ->
                val orb = object : View(ctx) {
                    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

                    override fun onDraw(canvas: Canvas) {
                        val cx = width / 2f
                        val cy = height / 2f
                        val r = minOf(cx, cy) - LayoutMetrics.dpF(context, 2.5f)
                        if (r <= 0f) return
                        fillPaint.shader = LinearGradient(
                            cx - r, cy - r, cx + r, cy + r,
                            theme.focusedColors, null, Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(cx, cy, r, fillPaint)
                        val active = hasFocus() || settings.tileColor == theme.key
                        ringPaint.strokeWidth = LayoutMetrics.dpF(context, if (active) 2.4f else 1f)
                        ringPaint.color = if (active) Color.WHITE else Color.argb(125, 255, 255, 255)
                        canvas.drawCircle(cx, cy, r, ringPaint)
                    }
                }.apply {
                    tag = "theme_${theme.key}"
                    isFocusable = true
                    isClickable = true
                    setOnFocusChangeListener { v, f ->
                        AnimationController.animateFocus(v, f, settings.motionStyle, 1.12f, 5f)
                        if (f) {
                            settings.tileColor = theme.key
                        } else if (settings.tileColor != getCommittedTheme()) {
                            settings.tileColor = getCommittedTheme()
                        }
                        syncOrbs()
                    }
                    setOnClickListener {
                        settings.tileColor = theme.key
                        onCommit(theme.key)
                        syncOrbs()
                    }
                }
                orbs.add(orb)
                row.addView(orb, LinearLayout.LayoutParams(0, optH, 1f).apply {
                    if (idx < rowChunk.lastIndex) rightMargin = gapH
                })
            }
            box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV / 2 })
        }
        return syncOrbs
    }
}
