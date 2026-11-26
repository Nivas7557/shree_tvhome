// Important instructions:
// - Render single-row horizontal sub-menus with a subtle Left/Right navigation hint and smooth airflow resizing.
// - Never reload or rebuild views on width change; support Left/Right-only movement and Up/Down parent exit.

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
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes
import java.util.WeakHashMap

object InlineStyleSubMenus {

    // Stores Up/Down exit handlers per sub-menu without requiring XML resource IDs.
    private val exitHandlers = WeakHashMap<ViewGroup, (Int) -> Boolean>()

    fun registerExitHandler(subMenu: ViewGroup, handler: (Int) -> Boolean) {
        exitHandlers[subMenu] = handler
    }

    fun dispatchExit(subMenu: ViewGroup, direction: Int): Boolean {
        return exitHandlers[subMenu]?.invoke(direction) ?: false
    }

    // Creates a glass sub-menu box with a subtle Left/Right instruction label and single-row horizontal strip.
    fun createContainer(ctx: Context, settings: AppSettings): LinearLayout =
        LinearLayout(ctx).apply {
            tag = "inline_sub_menu"
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            clipChildren = false
            clipToPadding = false
            val padH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 6, maxDp = 10, horizontal = true)
            val padV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.006f, minDp = 4, maxDp = 8, horizontal = false)
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(132, 14, 22, 48), Color.argb(102, 22, 34, 72))
            ).apply {
                cornerRadius = LayoutMetrics.dpF(ctx, 14f)
                setStroke(LayoutMetrics.dp(ctx, 1), LayoutMetrics.withAlpha(settings.accentColor, 140))
            }
            addView(TextView(ctx).apply {
                text = "◄  Left / Right to preview • Up / Down to exit  ►"
                textSize = 7.8f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.argb(185, 210, 232, 255))
                setPadding(0, 0, 0, padV / 2)
            }, LinearLayout.LayoutParams(-1, -2))
        }

    // Builds a single horizontal row of option chips (smooth airflow resize, Left/Right-only navigation).
    fun populateOptionChips(
        ctx: Context,
        box: LinearLayout,
        settings: AppSettings,
        options: List<Pair<String, String>>,
        optH: Int,
        gapH: Int,
        getCommitted: () -> String,
        getCurrent: () -> String,
        onPreview: (String) -> Unit,
        onCommit: (String) -> Unit
    ): () -> View? {
        val hScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
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

        options.forEachIndexed { idx, (key, label) ->
            val tv = TextView(ctx).apply {
                text = label
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                val chipPad = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 6, maxDp = 12, horizontal = true)
                setPadding(chipPad, 0, chipPad, 0)
                isFocusable = true
                isClickable = true
                setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.05f, 4f)
                    if (f) {
                        hScroll.smoothScrollTo(maxOf(0, v.left - (hScroll.width - v.width) / 2), 0)
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
                if (idx < options.lastIndex) rightMargin = gapH
            })
        }
        hScroll.addView(row, LinearLayout.LayoutParams(-1, -2))
        box.addView(hScroll, LinearLayout.LayoutParams(-1, -2))
        refreshButtons()

        return {
            val savedKey = getCommitted()
            buttons.find { it.first == savedKey }?.second ?: buttons.firstOrNull()?.second
        }
    }

    // Builds a single horizontal strip of pure circular gradient swatches with auto-focus target lookup.
    fun populateAccentSwatches(
        ctx: Context,
        box: LinearLayout,
        settings: AppSettings,
        optH: Int,
        gapH: Int,
        getCommittedColor: () -> Int,
        getCommittedName: () -> String,
        onCommit: (Int, String) -> Unit
    ): Pair<() -> Unit, () -> View?> {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }
        val swatches = ArrayList<Pair<Int, View>>()
        val syncSwatches: () -> Unit = { swatches.forEach { (_, v) -> v.invalidate() } }

        Themes.ACCENTS.forEachIndexed { idx, spec ->
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
            swatches.add(spec.color to swatch)
            row.addView(swatch, LinearLayout.LayoutParams(0, optH, 1f).apply {
                if (idx < Themes.ACCENTS.lastIndex) rightMargin = gapH
            })
        }
        box.addView(row, LinearLayout.LayoutParams(-1, -2))
        val findSaved: () -> View? = {
            val col = getCommittedColor()
            swatches.find { it.first == col }?.second ?: swatches.firstOrNull()?.second
        }
        return syncSwatches to findSaved
    }
    // Builds a single horizontal scrollable strip of motion styles so Left/Right scrolls smoothly across all styles.
    fun populateMotionOptions(
        ctx: Context,
        box: LinearLayout,
        hostDrawer: View,
        settings: AppSettings,
        optH: Int,
        gapH: Int,
        getCommittedMotion: () -> String,
        onCommit: (String) -> Unit
    ): Pair<() -> Unit, () -> View?> {
        val hScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val chipMinW = LayoutMetrics.dp(ctx, Themes.MIN_TEXT_CHIP_DP + 10)
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

        AnimationController.STYLES.forEachIndexed { idx, spec ->
            val btn = TextView(ctx).apply {
                tag = "motion_${spec.key}"
                text = spec.label
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                isSingleLine = true
                minWidth = chipMinW
                val pad = LayoutMetrics.dynamicSpace(ctx, ratio = 0.01f, minDp = 8, maxDp = 14, horizontal = true)
                setPadding(pad, 0, pad, 0)
                isFocusable = true
                isClickable = true
                setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 3f)
                    if (f) {
                        hScroll.smoothScrollTo(maxOf(0, v.left - (hScroll.width - v.width) / 2), 0)
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
            row.addView(btn, LinearLayout.LayoutParams(-2, optH).apply {
                if (idx < AnimationController.STYLES.lastIndex) rightMargin = gapH
            })
        }
        hScroll.addView(row)
        box.addView(hScroll, LinearLayout.LayoutParams(-1, -2))
        syncButtons()
        val findSaved: () -> View? = {
            val m = getCommittedMotion()
            buttons.find { it.first == m }?.second ?: buttons.firstOrNull()?.second
        }
        return syncButtons to findSaved
    }

    // Builds a single horizontal scrollable strip of pure glass palette orbs (no text labels).
    fun populatePaletteOrbs(
        ctx: Context,
        box: LinearLayout,
        settings: AppSettings,
        optH: Int,
        gapH: Int,
        getCommittedTheme: () -> String,
        onCommit: (String) -> Unit
    ): Pair<() -> Unit, () -> View?> {
        val hScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val orbSize = optH
        val orbs = ArrayList<Pair<String, View>>()
        val syncOrbs: () -> Unit = { orbs.forEach { (_, v) -> v.invalidate() } }

        Themes.THEMES.forEachIndexed { idx, theme ->
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
                        hScroll.smoothScrollTo(maxOf(0, v.left - (hScroll.width - v.width) / 2), 0)
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
            orbs.add(theme.key to orb)
            row.addView(orb, LinearLayout.LayoutParams(orbSize, orbSize).apply {
                if (idx < Themes.THEMES.lastIndex) rightMargin = gapH
            })
        }
        hScroll.addView(row)
        box.addView(hScroll, LinearLayout.LayoutParams(-1, -2))
        val findSaved: () -> View? = {
            val t = getCommittedTheme()
            orbs.find { it.first == t }?.second ?: orbs.firstOrNull()?.second
        }
        return syncOrbs to findSaved
    }
}
