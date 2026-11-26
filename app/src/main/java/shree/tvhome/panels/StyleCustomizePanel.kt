// Important instructions:
// - Render compact 2-column preset cards that expand inline live-preview sub-menus on click, inspired by TV studio UI.
// - Never destroy focused views inside focus/style callbacks; update state in-place and clean up listeners on detach.

package shree.tvhome.panels

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import shree.tvhome.PanelHub
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.MoodPresets
import shree.tvhome.core.Themes

object StyleCustomizePanel {

    // Builds the priority-ordered Customize panel with inline expandable live-preview sub-menus.
    fun build(
        ctx: Context,
        panel: LinearLayout,
        settings: AppSettings,
        onLiveRefresh: () -> Unit,
        onRefreshPanel: () -> Unit,
        onEditProfile: () -> Unit,
        onOpenWallpaper: (String) -> Unit,
        onOpenHidden: () -> Unit,
        onOpenClockSettings: () -> Unit,
        onCloseDrawer: () -> Unit
    ) {
        panel.clipChildren = false
        panel.clipToPadding = false

        val gapH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 4, maxDp = 12, horizontal = true)
        val gapV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 4, maxDp = 10, horizontal = false)
        val tileH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.072f, minDp = 46, maxDp = 62, horizontal = false)
        val optH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.050f, minDp = 32, maxDp = 42, horizontal = false)

        var savedAccent = settings.accentColor
        var savedAccentName = settings.accentName
        var savedTheme = settings.tileColor
        var savedMotion = settings.motionStyle
        var savedTileSize = settings.tileSize
        var savedTileShape = settings.tileShape
        var savedNameplate = settings.nameplateMode
        var savedPanelWidth = settings.panelWidthPct

        val subMenuContainers = ArrayList<LinearLayout>()
        val toggleInlineSubMenu: (LinearLayout) -> Unit = { targetBox ->
            val willOpen = targetBox.visibility != View.VISIBLE
            subMenuContainers.forEach { box ->
                if (box !== targetBox && box.visibility == View.VISIBLE) {
                    box.visibility = View.GONE
                }
            }
            if (willOpen) {
                targetBox.alpha = 0f
                targetBox.scaleY = 0.92f
                targetBox.visibility = View.VISIBLE
                targetBox.animate()
                    .alpha(1f).scaleY(1f)
                    .setDuration(240L)
                    .setInterpolator(LayoutMetrics.FLUID_SPRING)
                    .start()
            } else {
                targetBox.visibility = View.GONE
            }
        }

        // 1. User Profile & Quick Studio Shortcuts in 2-column cards.
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "USER PROFILE & STUDIO")

        val rowOne = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        rowOne.addView(
            makeActionTile(ctx, settings, "👤", settings.userName, "Profile & Avatar") { onEditProfile() },
            LinearLayout.LayoutParams(0, tileH, 1f).apply { rightMargin = gapH }
        )
        val hiddenCount = settings.getHiddenApps().size
        rowOne.addView(
            makeActionTile(ctx, settings, "👁", "Hidden Apps", "$hiddenCount Hidden") {
                onCloseDrawer()
                onOpenHidden()
            },
            LinearLayout.LayoutParams(0, tileH, 1f)
        )
        panel.addView(rowOne, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        val rowTwo = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        val greetSub = settings.greetingPhrase.ifBlank { "Auto Time" }
        rowTwo.addView(
            makeActionTile(ctx, settings, "💬", "Greeting", greetSub) {
                showGreetingDialog(ctx, settings, onLiveRefresh, onRefreshPanel)
            },
            LinearLayout.LayoutParams(0, tileH, 1f).apply { rightMargin = gapH }
        )
        val wpTile = makeActionTile(ctx, settings, "🖼", "Wallpaper", settings.liveWallpaperMode) { v ->
            val tag = (v.tag as? String) ?: PanelHub.TAG_WALLPAPER_TRIGGER
            onOpenWallpaper(tag)
        }.apply {
            tag = PanelHub.TAG_WALLPAPER_TRIGGER
        }
        rowTwo.addView(wpTile, LinearLayout.LayoutParams(0, tileH, 1f))
        panel.addView(rowTwo, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        // 2. Mood Presets & Clock Widget Studio.
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "MOOD PRESETS")
        val moodBlock = MoodPresets.buildPresetCards(ctx, settings, onLiveRefresh)
        panel.addView(moodBlock, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        panel.addView(
            PanelSurfaceKit.makeActionRow(
                ctx, settings, "🕰️", "Clock Widget Settings", "Styles, location & live preview"
            ) {
                onOpenClockSettings()
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV }
        )

        // 3. Display & Shelf Layout (2-column summary cards + inline live-preview sub-menus).
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "DISPLAY & SHELF LAYOUT")

        val sizeSubMenu = createInlineSubMenuBox(ctx, settings).also { subMenuContainers.add(it) }
        val widthSubMenu = createInlineSubMenuBox(ctx, settings).also { subMenuContainers.add(it) }

        val sizeCard = makeExpandableTile(
            ctx, settings, "📐", "Tile Size",
            getSub = { Themes.getTileSizeSpec(settings.tileSize).label }
        ) { toggleInlineSubMenu(sizeSubMenu) }

        val widthCard = makeExpandableTile(
            ctx, settings, "◫", "Side Panel Width",
            getSub = { "Dock: ${settings.panelWidthPct}%" }
        ) { toggleInlineSubMenu(widthSubMenu) }

        val displayRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            addView(sizeCard, LinearLayout.LayoutParams(0, tileH, 1f).apply { rightMargin = gapH })
            addView(widthCard, LinearLayout.LayoutParams(0, tileH, 1f))
        }
        panel.addView(displayRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        populateInlineOptions(
            ctx, sizeSubMenu, settings,
            header = "SELECT TILE SIZE (LIVE PREVIEW)",
            options = Themes.TILE_SIZES.map { it.key to it.label },
            optH = optH, gapH = gapH, gapV = gapV,
            getCommitted = { savedTileSize },
            getCurrent = { settings.tileSize },
            onPreview = { key -> settings.tileSize = key; onLiveRefresh() },
            onCommit = { key -> savedTileSize = key; settings.tileSize = key; onLiveRefresh() }
        )
        panel.addView(sizeSubMenu, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        populateInlineOptions(
            ctx, widthSubMenu, settings,
            header = "SELECT STAGE WIDTH (LIVE PREVIEW)",
            options = Themes.PANEL_WIDTH_OPTIONS.map { it.toString() to "$it%" },
            optH = optH, gapH = gapH, gapV = gapV,
            getCommitted = { savedPanelWidth.toString() },
            getCurrent = { settings.panelWidthPct.toString() },
            onPreview = { key ->
                settings.panelWidthPct = key.toIntOrNull() ?: savedPanelWidth
                onLiveRefresh()
            },
            onCommit = { key ->
                val v = key.toIntOrNull() ?: savedPanelWidth
                savedPanelWidth = v
                settings.panelWidthPct = v
                onLiveRefresh()
            }
        )
        panel.addView(widthSubMenu, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
        // 4. Tile Shape & Nameplates (2-column summary cards + inline live-preview sub-menus).
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "TILE SHAPE & NAMEPLATES")

        val shapeSubMenu = createInlineSubMenuBox(ctx, settings).also { subMenuContainers.add(it) }
        val nameplateSubMenu = createInlineSubMenuBox(ctx, settings).also { subMenuContainers.add(it) }

        val shapeCard = makeExpandableTile(
            ctx, settings, "❖", "Tile Silhouette",
            getSub = { Themes.TILE_SHAPES.find { it.key == settings.tileShape }?.label ?: "Squircle" }
        ) { toggleInlineSubMenu(shapeSubMenu) }

        val nameplateCard = makeExpandableTile(
            ctx, settings, "🏷", "App Nameplates",
            getSub = {
                when (settings.nameplateMode) {
                    "focus" -> "On Focus"
                    "hidden" -> "Hidden"
                    else -> "Always"
                }
            }
        ) { toggleInlineSubMenu(nameplateSubMenu) }

        val shapeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            addView(shapeCard, LinearLayout.LayoutParams(0, tileH, 1f).apply { rightMargin = gapH })
            addView(nameplateCard, LinearLayout.LayoutParams(0, tileH, 1f))
        }
        panel.addView(shapeRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        populateInlineOptions(
            ctx, shapeSubMenu, settings,
            header = "TILE SILHOUETTE (LIVE PREVIEW)",
            options = Themes.TILE_SHAPES.map { it.key to it.label },
            optH = optH, gapH = gapH, gapV = gapV,
            getCommitted = { savedTileShape },
            getCurrent = { settings.tileShape },
            onPreview = { key -> settings.tileShape = key; onLiveRefresh() },
            onCommit = { key -> savedTileShape = key; settings.tileShape = key; onLiveRefresh() }
        )
        panel.addView(shapeSubMenu, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        populateInlineOptions(
            ctx, nameplateSubMenu, settings,
            header = "NAMEPLATE VISIBILITY (LIVE PREVIEW)",
            options = listOf("always" to "Always", "focus" to "On Focus", "hidden" to "Hidden"),
            optH = optH, gapH = gapH, gapV = gapV,
            getCommitted = { savedNameplate },
            getCurrent = { settings.nameplateMode },
            onPreview = { key -> settings.nameplateMode = key; onLiveRefresh() },
            onCommit = { key -> savedNameplate = key; settings.nameplateMode = key; onLiveRefresh() }
        )
        panel.addView(nameplateSubMenu, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        // 5. Accent Aura, Motion & Glass Palette (inline live-preview sub-menus with zero view destruction).
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "ACCENT, MOTION & PALETTE")

        val accentSubMenu = createInlineSubMenuBox(ctx, settings).also { subMenuContainers.add(it) }
        val motionSubMenu = createInlineSubMenuBox(ctx, settings).also { subMenuContainers.add(it) }
        val paletteSubMenu = createInlineSubMenuBox(ctx, settings).also { subMenuContainers.add(it) }

        val accentCard = makeExpandableTile(
            ctx, settings, "🌈", "Accent Aura",
            getSub = { settings.accentName }
        ) { toggleInlineSubMenu(accentSubMenu) }

        val motionCard = makeExpandableTile(
            ctx, settings, "✨", "Motion Style",
            getSub = { AnimationController.getSpec(settings.motionStyle).label }
        ) { toggleInlineSubMenu(motionSubMenu) }

        val styleTopRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            addView(accentCard, LinearLayout.LayoutParams(0, tileH, 1f).apply { rightMargin = gapH })
            addView(motionCard, LinearLayout.LayoutParams(0, tileH, 1f))
        }
        panel.addView(styleTopRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        // Build Gradient Accents inside inline sub-menu.
        addInlineHeader(ctx, accentSubMenu, settings, "LUMINOUS GRADIENT AURA (LIVE PREVIEW)")
        val accentCards = ArrayList<LinearLayout>()
        val syncAccentCards: () -> Unit = { accentCards.forEach { it.invalidate() } }

        Themes.ACCENTS.chunked(2).forEach { pair ->
            val r = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            pair.forEachIndexed { idx, spec ->
                val card = object : LinearLayout(ctx) {
                    private val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
                    private val rect = RectF()

                    override fun onDraw(canvas: Canvas) {
                        val w = width.toFloat()
                        val h = height.toFloat()
                        if (w <= 0f || h <= 0f) return
                        val pad = LayoutMetrics.dpF(context, 1.5f)
                        rect.set(pad, pad, w - pad, h - pad)
                        gradPaint.shader = LinearGradient(
                            pad, pad, w - pad, h - pad,
                            spec.gradientColors, null, Shader.TileMode.CLAMP
                        )
                        val rad = LayoutMetrics.dpF(context, 11f)
                        canvas.drawRoundRect(rect, rad, rad, gradPaint)

                        val active = hasFocus() || settings.accentColor == spec.color
                        ringPaint.strokeWidth = LayoutMetrics.dpF(context, if (active) 2.2f else 1f)
                        ringPaint.color = if (active) Color.WHITE else Color.argb(115, 255, 255, 255)
                        canvas.drawRoundRect(rect, rad, rad, ringPaint)
                        super.onDraw(canvas)
                    }
                }.apply {
                    tag = "accent_${spec.key}"
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setWillNotDraw(false)
                    isFocusable = true
                    isClickable = true
                    addView(TextView(ctx).apply {
                        text = spec.label
                        textSize = 9.2f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                        setShadowLayer(4f, 0f, 1f, Color.argb(160, 0, 0, 0))
                    })
                    setOnFocusChangeListener { v, f ->
                        AnimationController.animateFocus(v, f, settings.motionStyle, 1.05f, 4f)
                        if (f) {
                            settings.accentColor = spec.color
                            settings.accentName = spec.label
                        } else if (settings.accentColor != savedAccent) {
                            settings.accentColor = savedAccent
                            settings.accentName = savedAccentName
                        }
                        syncAccentCards()
                    }
                    setOnClickListener {
                        settings.accentColor = spec.color
                        settings.accentName = spec.label
                        savedAccent = spec.color
                        savedAccentName = spec.label
                        syncAccentCards()
                        Toast.makeText(ctx, "${spec.label} Aura Set", Toast.LENGTH_SHORT).show()
                    }
                }
                accentCards.add(card)
                r.addView(card, LinearLayout.LayoutParams(0, optH, 1f).apply {
                    if (idx < pair.lastIndex) rightMargin = gapH
                })
            }
            accentSubMenu.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
        }
        panel.addView(accentSubMenu, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        // Build Global Motion Styles inside inline sub-menu.
        addInlineHeader(ctx, motionSubMenu, settings, "GLOBAL MOTION PHYSICS (LIVE PREVIEW)")
        val motionButtons = ArrayList<Pair<String, TextView>>()
        val syncMotionCards: () -> Unit = {
            motionButtons.forEach { (key, btn) ->
                val active = btn.hasFocus() || settings.motionStyle == key
                btn.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, active, settings.tileColor)
                btn.setTextColor(
                    if (active) Themes.getAdaptiveTextColor(intArrayOf(settings.accentColor))
                    else Themes.getPrimaryTextColor(settings.tileColor)
                )
            }
        }
        AnimationController.STYLES.chunked(2).forEach { rowChunk ->
            val r = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            rowChunk.forEachIndexed { idx, spec ->
                val btn = TextView(ctx).apply {
                    tag = "motion_${spec.key}"
                    text = spec.label
                    textSize = 9.2f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    isFocusable = true
                    isClickable = true
                    setOnFocusChangeListener { v, f ->
                        AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 3f)
                        if (f) {
                            settings.motionStyle = spec.key
val hostPanel = (panel.parent?.parent as? View) ?: panel
AnimationController.playDemoPreview(hostPanel, spec.key)
} else if (settings.motionStyle != savedMotion) {
settings.motionStyle = savedMotion
}
syncMotionCards()
}
setOnClickListener {
settings.motionStyle = spec.key
savedMotion = spec.key
syncMotionCards()
Toast.makeText(ctx, "${spec.label} Motion Set", Toast.LENGTH_SHORT).show()
}
}
motionButtons.add(spec.key to btn)
r.addView(btn, LinearLayout.LayoutParams(0, optH, 1f).apply {
if (idx < rowChunk.lastIndex) rightMargin = gapH
})
}
motionSubMenu.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
}
syncMotionCards()
panel.addView(motionSubMenu, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
// Full-width Tile Glass Palette trigger + inline sub-menu.
        val paletteCard = makeExpandableTile(
            ctx, settings, "🎨", "Tile Glass Palette",
            getSub = { Themes.getTheme(settings.tileColor).name }
        ) { toggleInlineSubMenu(paletteSubMenu) }
        panel.addView(paletteCard, LinearLayout.LayoutParams(-1, tileH).apply { bottomMargin = gapV })

        addInlineHeader(ctx, paletteSubMenu, settings, "GLASS SURFACE PALETTE (LIVE PREVIEW)")
        val themeEntries = ArrayList<Triple<String, LinearLayout, TextView>>()
        val syncThemeCards: () -> Unit = {
            themeEntries.forEach { (key, card, labelTv) ->
                val active = card.hasFocus() || settings.tileColor == key
                card.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, active, settings.tileColor)
                labelTv.setTextColor(
                    if (active) Themes.getAdaptiveTextColor(intArrayOf(settings.accentColor))
                    else Themes.getPrimaryTextColor(settings.tileColor)
                )
            }
        }
        Themes.THEMES.chunked(2).forEach { rowChunk ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            rowChunk.forEachIndexed { idx, theme ->
                val isSel = settings.tileColor == theme.key
                val labelTv = TextView(ctx).apply {
                    text = theme.name
                    textSize = 9.2f
                    typeface = Typeface.DEFAULT_BOLD
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                }
                val orbSize = LayoutMetrics.dynamicSpace(ctx, ratio = 0.02f, minDp = 16, maxDp = 22, horizontal = true)
                val card = LinearLayout(ctx).apply {
                    tag = "theme_${theme.key}"
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(gapH, gapV, gapH, gapV)
                    isFocusable = true
                    isClickable = true
                    setOnFocusChangeListener { v, f ->
                        AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 4f)
                        if (f) {
                            settings.tileColor = theme.key
                        } else if (settings.tileColor != savedTheme) {
                            settings.tileColor = savedTheme
                        }
                        syncThemeCards()
                    }
                    setOnClickListener {
                        settings.tileColor = theme.key
                        savedTheme = theme.key
                        syncThemeCards()
                        Toast.makeText(ctx, "${theme.name} Palette Set", Toast.LENGTH_SHORT).show()
                    }
                    addView(
                        PanelSurfaceKit.createThemeOrbView(ctx, theme.focusedColors, isSel),
                        LinearLayout.LayoutParams(orbSize, orbSize).apply { rightMargin = gapH }
                    )
                    addView(labelTv)
                }
                themeEntries.add(Triple(theme.key, card, labelTv))
                row.addView(card, LinearLayout.LayoutParams(0, optH, 1f).apply {
                    if (idx < rowChunk.lastIndex) rightMargin = gapH
                })
            }
            paletteSubMenu.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
        }
        syncThemeCards()
        panel.addView(paletteSubMenu, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV * 2 })

        val unifiedStyleListener: () -> Unit = {
            syncMotionCards()
            syncAccentCards()
            syncThemeCards()
        }
        settings.addStyleListener(unifiedStyleListener)

        // Clean up style listeners and revert any unconfirmed focus previews on detach.
        panel.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                settings.removeStyleListener(unifiedStyleListener)
                var restored = false
                if (settings.accentColor != savedAccent) {
                    settings.accentColor = savedAccent
                    settings.accentName = savedAccentName
                    restored = true
                }
                if (settings.tileColor != savedTheme) {
                    settings.tileColor = savedTheme
                    restored = true
                }
                if (settings.motionStyle != savedMotion) {
                    settings.motionStyle = savedMotion
                    restored = true
                }
                if (settings.tileSize != savedTileSize) {
                    settings.tileSize = savedTileSize
                    restored = true
                }
                if (settings.tileShape != savedTileShape) {
                    settings.tileShape = savedTileShape
                    restored = true
                }
                if (settings.nameplateMode != savedNameplate) {
                    settings.nameplateMode = savedNameplate
                    restored = true
                }
                if (settings.panelWidthPct != savedPanelWidth) {
                    settings.panelWidthPct = savedPanelWidth
                    restored = true
                }
                if (restored) onLiveRefresh()
            }
        })
    }

    // Creates a sleek glass sub-menu container that expands inline beneath its trigger card.
    private fun createInlineSubMenuBox(ctx: Context, settings: AppSettings): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            clipChildren = false
            clipToPadding = false
            val padH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.009f, minDp = 8, maxDp = 12, horizontal = true)
            val padV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 6, maxDp = 10, horizontal = false)
            setPadding(padH, padV, padH, padV / 2)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(120, 14, 22, 48), Color.argb(95, 22, 34, 72))
            ).apply {
                cornerRadius = LayoutMetrics.dpF(ctx, 14f)
                setStroke(LayoutMetrics.dp(ctx, 1), LayoutMetrics.withAlpha(settings.accentColor, 140))
            }
        }

    private fun addInlineHeader(ctx: Context, box: LinearLayout, settings: AppSettings, title: String) {
        box.addView(TextView(ctx).apply {
            text = title
            textSize = 8.2f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setTextColor(settings.accentColor)
            setPadding(0, 0, 0, LayoutMetrics.dp(ctx, 5))
        })
    }

    // Populates inline option pills that live-preview on focus and commit permanently on click.
    private fun populateInlineOptions(
        ctx: Context,
        box: LinearLayout,
        settings: AppSettings,
        header: String,
        options: List<Pair<String, String>>,
        optH: Int,
        gapH: Int,
        gapV: Int,
        getCommitted: () -> String,
        getCurrent: () -> String,
        onPreview: (String) -> Unit,
        onCommit: (String) -> Unit
    ) {
        addInlineHeader(ctx, box, settings, header)
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

        options.chunked(2).forEach { pair ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            pair.forEachIndexed { idx, (key, label) ->
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
                    if (idx < pair.lastIndex) rightMargin = gapH
                })
            }
            box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
        }
        refreshButtons()
    }
    // Builds an expandable 2-column summary tile with live subtitle sync.
private fun makeExpandableTile(
ctx: Context,
settings: AppSettings,
icon: String,
title: String,
getSub: () -> String,
onToggle: () -> Unit
) = LinearLayout(ctx).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
val padH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.01f, minDp = 8, maxDp = 14, horizontal = true)
val padV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 5, maxDp = 10, horizontal = false)
setPadding(padH, padV, padH, padV)
isFocusable = true
isClickable = true
val leftCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
val titleTv = TextView(ctx).apply {
text = "$icon  $title"
textSize = 9.8f
typeface = Typeface.DEFAULT_BOLD
isSingleLine = true
ellipsize = TextUtils.TruncateAt.END
}
val subTv = TextView(ctx).apply {
text = getSub()
textSize = 8.2f
isSingleLine = true
ellipsize = TextUtils.TruncateAt.END
}
leftCol.addView(titleTv)
leftCol.addView(subTv)
val arrowTv = TextView(ctx).apply {
text = "▾"
textSize = 11f
typeface = Typeface.DEFAULT_BOLD
}
val syncVisuals = {
background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, hasFocus(), settings.tileColor)
titleTv.setTextColor(Themes.getPrimaryTextColor(settings.tileColor))
subTv.text = getSub()
val surfaceColors = Themes.getTheme(settings.tileColor).normalColors
val subColor = if (Themes.isLightSurface(surfaceColors)) {
Themes.getAdaptiveTextColor(surfaceColors, secondary = true)
} else {
settings.accentColor
}
subTv.setTextColor(subColor)
arrowTv.setTextColor(subColor)
}
syncVisuals()
settings.addStyleListener(syncVisuals)
addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
override fun onViewAttachedToWindow(v: View) {}
override fun onViewDetachedFromWindow(v: View) {
settings.removeStyleListener(syncVisuals)
}
})
setOnFocusChangeListener { v, f ->
AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 4f)
syncVisuals()
}
setOnClickListener {
onToggle()
syncVisuals()
}
addView(leftCol, LinearLayout.LayoutParams(0, -2, 1f))
addView(arrowTv)
}
private fun showGreetingDialog(
ctx: Context,
settings: AppSettings,
onLiveRefresh: () -> Unit,
onRefreshPanel: () -> Unit
) {
val dialog = Dialog(ctx)
dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
val padH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.022f, minDp = 16, maxDp = 28, horizontal = true)
val padV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.024f, minDp = 14, maxDp = 24, horizontal = false)
val box = LinearLayout(ctx).apply {
orientation = LinearLayout.VERTICAL
setPadding(padH, padV, padH, padV)
background = GradientDrawable(
GradientDrawable.Orientation.TL_BR,
intArrayOf(Color.rgb(20, 32, 62), Color.rgb(12, 18, 38))
).apply {
cornerRadius = LayoutMetrics.dpF(ctx, 20f)
setStroke(LayoutMetrics.dp(ctx, 1), Color.argb(120, 255, 255, 255))
}
}
box.addView(TextView(ctx).apply {
text = "Custom Greeting Phrase"
textSize = 15f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
setPadding(0, 0, 0, padV / 2)
})
val input = EditText(ctx).apply {
isSingleLine = true
hint = "Custom phrase"
setText(settings.greetingPhrase)
setTextColor(Color.WHITE)
setHintTextColor(Color.argb(150, 200, 220, 245))
setPadding(padH / 2, padV / 2, padH / 2, padV / 2)
inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
background = GradientDrawable().apply {
setColor(Color.argb(55, 255, 255, 255))
cornerRadius = LayoutMetrics.dpF(ctx, 12f)
setStroke(LayoutMetrics.dp(ctx, 1), settings.accentColor)
}
}
val inputW = LayoutMetrics.dynamicSpace(ctx, ratio = 0.32f, minDp = 240, maxDp = 420, horizontal = true)
box.addView(input, LinearLayout.LayoutParams(inputW, -2))
val btnH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.056f, minDp = 36, maxDp = 48, horizontal = false)
val btnGap = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 6, maxDp = 12, horizontal = true)
val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
btnRow.addView(
makeDialogButton(ctx, settings, "↺  Auto", false) {
settings.greetingPhrase = ""
onLiveRefresh()
onRefreshPanel()
dialog.dismiss()
},
LinearLayout.LayoutParams(0, btnH, 1f).apply { rightMargin = btnGap }
)
btnRow.addView(
makeDialogButton(ctx, settings, "✓  Save", true) {
settings.greetingPhrase = input.text.toString().trim()
onLiveRefresh()
onRefreshPanel()
dialog.dismiss()
},
LinearLayout.LayoutParams(0, btnH, 1f)
)
box.addView(btnRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = padV / 2 })
dialog.setContentView(box)
dialog.window?.apply {
setBackgroundDrawableResource(android.R.color.transparent)
clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
}
dialog.show()
}
private fun makeDialogButton(
ctx: Context,
settings: AppSettings,
label: String,
highlight: Boolean,
onClick: () -> Unit
) = TextView(ctx).apply {
text = label
textSize = 10.5f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
gravity = Gravity.CENTER
isFocusable = true
isClickable = true
background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, highlight, settings.tileColor)
setOnFocusChangeListener { v, f ->
AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 4f)
v.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, f || highlight, settings.tileColor)
}
setOnClickListener { onClick() }
}
private fun makeActionTile(
ctx: Context,
settings: AppSettings,
icon: String,
title: String,
sub: String,
onClick: (View) -> Unit
) = LinearLayout(ctx).apply {
orientation = LinearLayout.VERTICAL
gravity = Gravity.CENTER_VERTICAL
val padH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.01f, minDp = 8, maxDp = 14, horizontal = true)
val padV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 5, maxDp = 10, horizontal = false)
setPadding(padH, padV, padH, padV)
isFocusable = true
isClickable = true
val titleTv = TextView(ctx).apply {
text = "$icon  $title"
textSize = 9.8f
typeface = Typeface.DEFAULT_BOLD
isSingleLine = true
ellipsize = TextUtils.TruncateAt.END
}
val subTv = TextView(ctx).apply {
text = sub
textSize = 8.2f
isSingleLine = true
ellipsize = TextUtils.TruncateAt.END
}
val syncColors = {
background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, hasFocus(), settings.tileColor)
titleTv.setTextColor(Themes.getPrimaryTextColor(settings.tileColor))
val surfaceColors = Themes.getTheme(settings.tileColor).normalColors
subTv.setTextColor(
if (Themes.isLightSurface(surfaceColors)) {
Themes.getAdaptiveTextColor(surfaceColors, secondary = true)
} else {
settings.accentColor
}
)
}
syncColors()
settings.addStyleListener(syncColors)
addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
override fun onViewAttachedToWindow(v: View) {}
override fun onViewDetachedFromWindow(v: View) {
settings.removeStyleListener(syncColors)
}
})
setOnFocusChangeListener { v, f ->
AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 4f)
syncColors()
}
setOnClickListener { v -> onClick(v) }
addView(titleTv)
addView(subTv)
}
}
