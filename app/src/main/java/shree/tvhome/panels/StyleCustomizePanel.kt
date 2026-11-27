// Important instructions:
// - Wire generic auto-closing horizontal sub-menus with automatic saved-option focus and Up/Down parent exit.
// - Never rebuild or clear views on width changes; let weight-based rows reflow smoothly like airflow.

package shree.tvhome.panels

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.FocusFinder
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.PanelHub
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.MoodPresets
import shree.tvhome.core.Themes

object StyleCustomizePanel {

    private fun isInside(targetView: View?, container: View): Boolean {
        var parent = targetView?.parent
        while (parent != null) {
            if (parent === container) return true
            parent = parent.parent
        }
        return false
    }

    // Builds the Customize panel with generic auto-closing sub-menus, saved-option focus, and Mood Preset sync.
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

        val gapH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 5, maxDp = 12, horizontal = true)
        val gapV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 5, maxDp = 10, horizontal = false)
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
        var activeExpandedMenu: LinearLayout? = null
        var activeTriggerCard: View? = null

        val collapseSubMenu: (LinearLayout) -> Unit = { box ->
            if (box.visibility == View.VISIBLE) {
                box.visibility = View.GONE
                if (activeExpandedMenu === box) {
                    activeExpandedMenu = null
                    activeTriggerCard = null
                }
            }
        }

        // Opens an inline sub-menu, focuses its currently saved option, and wires generic Up/Down parent exit.
        val openSubMenuWithFocus: (LinearLayout, View, () -> View?) -> Unit = { targetBox, triggerView, findSavedOption ->
            val willOpen = targetBox.visibility != View.VISIBLE
            subMenuContainers.forEach { box -> if (box !== targetBox) collapseSubMenu(box) }
            if (willOpen) {
                activeExpandedMenu = targetBox
                activeTriggerCard = triggerView
                targetBox.alpha = 0f
                targetBox.scaleY = 0.92f
                targetBox.visibility = View.VISIBLE
                targetBox.animate().alpha(1f).scaleY(1f).setDuration(220L).setInterpolator(LayoutMetrics.FLUID_SPRING).start()

                InlineStyleSubMenus.registerExitHandler(targetBox) { dir ->
                    collapseSubMenu(targetBox)
                    if (dir == View.FOCUS_UP) {
                        triggerView.requestFocus()
                        true
                    } else if (dir == View.FOCUS_DOWN) {
                        val nextDown = FocusFinder.getInstance().findNextFocus(panel, triggerView, View.FOCUS_DOWN)
                        if (nextDown != null && nextDown !== triggerView) {
                            nextDown.requestFocus()
                            true
                        } else {
                            triggerView.requestFocus()
                            true
                        }
                    } else false
                }

                targetBox.post {
                    findSavedOption()?.requestFocus()
                }
            } else {
                collapseSubMenu(targetBox)
            }
        }

        val focusWatcher = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            val openBox = activeExpandedMenu ?: return@OnGlobalFocusChangeListener
            if (newFocus != null && !isInside(newFocus, openBox) && newFocus !== activeTriggerCard) {
                collapseSubMenu(openBox)
            }
        }
        panel.viewTreeObserver.addOnGlobalFocusChangeListener(focusWatcher)
        // 1. User Profile & Studio Shortcuts (minimum 2 columns, smooth airflow width scaling).
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "USER PROFILE & STUDIO")
        val hiddenCount = settings.getHiddenApps().size
        val greetSub = settings.greetingPhrase.ifBlank { "Auto Time" }
        val userStudioItems = listOf(
            makeActionTile(ctx, settings, "👤", settings.userName, "Profile & Avatar") { onEditProfile() },
            makeActionTile(ctx, settings, "👁", "Hidden Apps", "$hiddenCount Hidden") {
                onCloseDrawer(); onOpenHidden()
            },
            makeActionTile(ctx, settings, "💬", "Greeting", greetSub) {
                showGreetingDialog(ctx, settings, onLiveRefresh, onRefreshPanel)
            },
            makeActionTile(ctx, settings, "🖼", "Wallpaper", settings.liveWallpaperMode) { v ->
                onOpenWallpaper((v.tag as? String) ?: PanelHub.TAG_WALLPAPER_TRIGGER)
            }.apply { tag = PanelHub.TAG_WALLPAPER_TRIGGER }
        )
        val summaryCols = MoodPresets.computeDynamicColumns(ctx, settings.panelWidthPct, Themes.MIN_SUMMARY_CARD_DP, 2)
        addDynamicRowGrid(panel, userStudioItems, summaryCols, tileH, gapH, gapV)

        // 2. Mood Presets (updates committed baseline immediately so all panels reflect applied mood).
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "MOOD PRESETS")
        val moodBlock = MoodPresets.buildPresetCards(
            ctx = ctx,
            settings = settings,
            onLiveRefresh = onLiveRefresh,
            onPresetCommitted = {
                savedAccent = settings.accentColor
                savedAccentName = settings.accentName
                savedTheme = settings.tileColor
                savedMotion = settings.motionStyle
                onRefreshPanel()
            }
        )
        panel.addView(moodBlock, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        panel.addView(
            PanelSurfaceKit.makeActionRow(ctx, settings, "🕰️", "Clock Widget Settings", "Styles, location & live preview") {
                onOpenClockSettings()
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV }
        )

        // 3. Display & Shelf Layout triggers + horizontal auto-closing sub-menus.
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "DISPLAY & SHELF LAYOUT")
        val sizeSubMenu = InlineStyleSubMenus.createContainer(ctx, settings).also { subMenuContainers.add(it) }
        val widthSubMenu = InlineStyleSubMenus.createContainer(ctx, settings).also { subMenuContainers.add(it) }

        lateinit var sizeCard: LinearLayout
        lateinit var findSavedSize: () -> View?
        sizeCard = makeExpandableTile(ctx, settings, "📐", "Tile Size", { Themes.getTileSizeSpec(settings.tileSize).label }) {
            openSubMenuWithFocus(sizeSubMenu, sizeCard, findSavedSize)
        }
        lateinit var widthCard: LinearLayout
        lateinit var findSavedWidth: () -> View?
        widthCard = makeExpandableTile(ctx, settings, "◫", "Panel Width", { "${settings.panelWidthPct}%" }) {
            openSubMenuWithFocus(widthSubMenu, widthCard, findSavedWidth)
        }
        addDynamicRowGrid(panel, listOf(sizeCard, widthCard), summaryCols, tileH, gapH, gapV)

        findSavedSize = InlineStyleSubMenus.populateOptionChips(
            ctx, sizeSubMenu, settings, Themes.TILE_SIZES.map { it.key to it.label },
            optH = optH, gapH = gapH,
            getCommitted = { savedTileSize }, getCurrent = { settings.tileSize },
            onPreview = { settings.tileSize = it; onLiveRefresh() },
            onCommit = { savedTileSize = it; settings.tileSize = it; onLiveRefresh(); collapseSubMenu(sizeSubMenu); sizeCard.requestFocus() }
        )
        panel.addView(sizeSubMenu, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        findSavedWidth = InlineStyleSubMenus.populateOptionChips(
            ctx, widthSubMenu, settings, Themes.PANEL_WIDTH_OPTIONS.map { it.toString() to "$it%" },
            optH = optH, gapH = gapH,
            getCommitted = { savedPanelWidth.toString() }, getCurrent = { settings.panelWidthPct.toString() },
            onPreview = { settings.panelWidthPct = it.toIntOrNull() ?: savedPanelWidth; onLiveRefresh() },
            onCommit = {
                val v = it.toIntOrNull() ?: savedPanelWidth
                savedPanelWidth = v; settings.panelWidthPct = v; onLiveRefresh()
                collapseSubMenu(widthSubMenu); widthCard.requestFocus()
            }
        )
        panel.addView(widthSubMenu, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        // 4. Tile Shape & Nameplates triggers + horizontal auto-closing sub-menus.
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "TILE SHAPE & NAMEPLATES")
        val shapeSubMenu = InlineStyleSubMenus.createContainer(ctx, settings).also { subMenuContainers.add(it) }
        val nameplateSubMenu = InlineStyleSubMenus.createContainer(ctx, settings).also { subMenuContainers.add(it) }

        lateinit var shapeCard: LinearLayout
        lateinit var findSavedShape: () -> View?
        shapeCard = makeExpandableTile(
            ctx, settings, "❖", "Silhouette",
            { Themes.TILE_SHAPES.find { it.key == settings.tileShape }?.label ?: Themes.TILE_SHAPES.first().label }
        ) { openSubMenuWithFocus(shapeSubMenu, shapeCard, findSavedShape) }

        lateinit var nameplateCard: LinearLayout
        lateinit var findSavedNameplate: () -> View?
        nameplateCard = makeExpandableTile(
            ctx, settings, "🏷", "Nameplates",
            { Themes.NAMEPLATE_MODES.find { it.key == settings.nameplateMode }?.label ?: Themes.NAMEPLATE_MODES.first().label }
        ) { openSubMenuWithFocus(nameplateSubMenu, nameplateCard, findSavedNameplate) }

        addDynamicRowGrid(panel, listOf(shapeCard, nameplateCard), summaryCols, tileH, gapH, gapV)

        findSavedShape = InlineStyleSubMenus.populateOptionChips(
            ctx, shapeSubMenu, settings, Themes.TILE_SHAPES.map { spec -> spec.key to spec.label },
            optH = optH, gapH = gapH,
            getCommitted = { savedTileShape }, getCurrent = { settings.tileShape },
            onPreview = { settings.tileShape = it; onLiveRefresh() },
            onCommit = { savedTileShape = it; settings.tileShape = it; onLiveRefresh(); collapseSubMenu(shapeSubMenu); shapeCard.requestFocus() }
        )
        panel.addView(shapeSubMenu, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        findSavedNameplate = InlineStyleSubMenus.populateOptionChips(
            ctx, nameplateSubMenu, settings, Themes.NAMEPLATE_MODES.map { it.key to it.label },
            optH = optH, gapH = gapH,
            getCommitted = { savedNameplate }, getCurrent = { settings.nameplateMode },
            onPreview = { settings.nameplateMode = it; onLiveRefresh() },
            onCommit = { savedNameplate = it; settings.nameplateMode = it; onLiveRefresh(); collapseSubMenu(nameplateSubMenu); nameplateCard.requestFocus() }
        )
        panel.addView(nameplateSubMenu, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
        // 5. Accent Aura, Motion Style & Glass Palette horizontal sub-menus.
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "ACCENT, MOTION & PALETTE")
        val accentSubMenu = InlineStyleSubMenus.createContainer(ctx, settings).also { subMenuContainers.add(it) }
        val motionSubMenu = InlineStyleSubMenus.createContainer(ctx, settings).also { subMenuContainers.add(it) }
        val paletteSubMenu = InlineStyleSubMenus.createContainer(ctx, settings).also { subMenuContainers.add(it) }

        lateinit var accentCard: LinearLayout
        lateinit var findSavedAccent: () -> View?
        accentCard = makeExpandableTile(ctx, settings, "🌈", "Accent Aura", { settings.accentName }) {
            openSubMenuWithFocus(accentSubMenu, accentCard, findSavedAccent)
        }
        lateinit var motionCard: LinearLayout
        lateinit var findSavedMotion: () -> View?
        motionCard = makeExpandableTile(ctx, settings, "✨", "Motion Style", { AnimationController.getSpec(settings.motionStyle).label }) {
            openSubMenuWithFocus(motionSubMenu, motionCard, findSavedMotion)
        }
        lateinit var paletteCard: LinearLayout
        lateinit var findSavedPalette: () -> View?
        paletteCard = makeExpandableTile(ctx, settings, "🎨", "Glass Palette", { Themes.getTheme(settings.tileColor).name }) {
            openSubMenuWithFocus(paletteSubMenu, paletteCard, findSavedPalette)
        }

        addDynamicRowGrid(panel, listOf(accentCard, motionCard, paletteCard), summaryCols, tileH, gapH, gapV)

        val (syncSwatches, accentLookup) = InlineStyleSubMenus.populateAccentSwatches(
            ctx, accentSubMenu, settings, optH, gapH,
            getCommittedColor = { savedAccent }, getCommittedName = { savedAccentName },
            onCommit = { col, lbl ->
                savedAccent = col; savedAccentName = lbl
                collapseSubMenu(accentSubMenu); accentCard.requestFocus()
            }
        )
        findSavedAccent = accentLookup
        panel.addView(accentSubMenu, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        val hostDrawer = (panel.parent?.parent as? View) ?: panel
        val (syncMotion, motionLookup) = InlineStyleSubMenus.populateMotionOptions(
            ctx, motionSubMenu, hostDrawer, settings, optH, gapH,
            getCommittedMotion = { savedMotion },
            onCommit = { key ->
                savedMotion = key
                collapseSubMenu(motionSubMenu); motionCard.requestFocus()
            }
        )
        findSavedMotion = motionLookup
        panel.addView(motionSubMenu, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        val (syncOrbs, paletteLookup) = InlineStyleSubMenus.populatePaletteOrbs(
            ctx, paletteSubMenu, settings, optH, gapH,
            getCommittedTheme = { savedTheme },
            onCommit = { key ->
                savedTheme = key
                collapseSubMenu(paletteSubMenu); paletteCard.requestFocus()
            }
        )
        findSavedPalette = paletteLookup
        panel.addView(paletteSubMenu, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV * 2 })

        val unifiedStyleListener: () -> Unit = { syncMotion(); syncSwatches(); syncOrbs() }
        settings.addStyleListener(unifiedStyleListener)

        panel.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                panel.viewTreeObserver.removeOnGlobalFocusChangeListener(focusWatcher)
                settings.removeStyleListener(unifiedStyleListener)
                var restored = false
                if (settings.accentColor != savedAccent) { settings.accentColor = savedAccent; settings.accentName = savedAccentName; restored = true }
                if (settings.tileColor != savedTheme) { settings.tileColor = savedTheme; restored = true }
                if (settings.motionStyle != savedMotion) { settings.motionStyle = savedMotion; restored = true }
                if (settings.tileSize != savedTileSize) { settings.tileSize = savedTileSize; restored = true }
                if (settings.tileShape != savedTileShape) { settings.tileShape = savedTileShape; restored = true }
                if (settings.nameplateMode != savedNameplate) { settings.nameplateMode = savedNameplate; restored = true }
                if (settings.panelWidthPct != savedPanelWidth) { settings.panelWidthPct = savedPanelWidth; restored = true }
                if (restored) onLiveRefresh()
            }
        })
    }

    private fun addDynamicRowGrid(parent: LinearLayout, items: List<View>, cols: Int, itemH: Int, gapH: Int, gapV: Int) {
        items.chunked(cols.coerceAtLeast(1)).forEach { rowItems ->
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            rowItems.forEachIndexed { idx, v ->
                row.addView(v, LinearLayout.LayoutParams(0, itemH, 1f).apply {
                    if (idx < rowItems.lastIndex) rightMargin = gapH
                })
            }
            parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
        }
    }

    private fun makeExpandableTile(
        ctx: Context, settings: AppSettings, icon: String, title: String, getSub: () -> String, onToggle: () -> Unit
    ) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padH = LayoutMetrics.dynamicSpace(ctx, 0.01f, 8, 14, true)
        val padV = LayoutMetrics.dynamicSpace(ctx, 0.008f, 5, 10, false)
        setPadding(padH, padV, padH, padV)
        isFocusable = true; isClickable = true
        val leftCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val titleTv = TextView(ctx).apply { text = "$icon $title"; textSize = 9.6f; typeface = Typeface.DEFAULT_BOLD; isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
        val subTv = TextView(ctx).apply { text = getSub(); textSize = 8.2f; isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
        leftCol.addView(titleTv); leftCol.addView(subTv)
        val arrowTv = TextView(ctx).apply { text = "▾"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD }
        val syncVisuals = {
            background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, hasFocus(), settings.tileColor)
            titleTv.setTextColor(Themes.getPrimaryTextColor(settings.tileColor))
            subTv.text = getSub()
            val surfaceColors = Themes.getTheme(settings.tileColor).normalColors
            val subCol = if (Themes.isLightSurface(surfaceColors)) Themes.getAdaptiveTextColor(surfaceColors, true) else settings.accentColor
            subTv.setTextColor(subCol); arrowTv.setTextColor(subCol)
        }
        syncVisuals(); settings.addStyleListener(syncVisuals)
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { settings.removeStyleListener(syncVisuals) }
        })
        setOnFocusChangeListener { v, f -> AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 4f); syncVisuals() }
        setOnClickListener { onToggle(); syncVisuals() }
        addView(leftCol, LinearLayout.LayoutParams(0, -2, 1f)); addView(arrowTv)
    }

    private fun makeActionTile(
        ctx: Context, settings: AppSettings, icon: String, title: String, sub: String, onClick: (View) -> Unit
    ) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        val padH = LayoutMetrics.dynamicSpace(ctx, 0.01f, 8, 14, true)
        val padV = LayoutMetrics.dynamicSpace(ctx, 0.008f, 5, 10, false)
        setPadding(padH, padV, padH, padV)
        isFocusable = true; isClickable = true
        val titleTv = TextView(ctx).apply { text = "$icon $title"; textSize = 9.6f; typeface = Typeface.DEFAULT_BOLD; isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
        val subTv = TextView(ctx).apply { text = sub; textSize = 8.2f; isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
        val syncColors = {
            background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, hasFocus(), settings.tileColor)
            titleTv.setTextColor(Themes.getPrimaryTextColor(settings.tileColor))
            val surfaceColors = Themes.getTheme(settings.tileColor).normalColors
            subTv.setTextColor(if (Themes.isLightSurface(surfaceColors)) Themes.getAdaptiveTextColor(surfaceColors, true) else settings.accentColor)
        }
        syncColors(); settings.addStyleListener(syncColors)
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { settings.removeStyleListener(syncColors) }
        })
        setOnFocusChangeListener { v, f -> AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 4f); syncColors() }
        setOnClickListener { v -> onClick(v) }
        addView(titleTv); addView(subTv)
    }

                    // Opens the Greeting & Typography Studio dialog with live preview, 4 phrase modes, and 3 font families.
    private fun showGreetingDialog(ctx: Context, settings: AppSettings, onLiveRefresh: () -> Unit, onRefreshPanel: () -> Unit) {
        val dialog = Dialog(ctx).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val padH = LayoutMetrics.dynamicSpace(ctx, 0.022f, 16, 28, true)
        val padV = LayoutMetrics.dynamicSpace(ctx, 0.020f, 14, 22, false)
        val gap = LayoutMetrics.dynamicSpace(ctx, 0.010f, 6, 12, false)

        var selectedStyle = settings.greetingStyle
        var selectedFont = settings.greetingFont

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(20, 28, 52), Color.rgb(12, 16, 32))
            ).apply {
                cornerRadius = LayoutMetrics.dpF(ctx, 24f)
                setStroke(LayoutMetrics.dp(ctx, 1), Color.argb(120, 255, 255, 255))
            }
        }

        box.addView(TextView(ctx).apply {
            text = "Greeting & Font Studio"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, gap)
        })

        val previewTv = TextView(ctx).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(padH, padV / 2, padH, padV / 2)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.argb(55, 255, 255, 255), Color.argb(30, 20, 27, 45))
            ).apply {
                cornerRadius = LayoutMetrics.dpF(ctx, 999f)
                setStroke(LayoutMetrics.dp(ctx, 1), settings.accentColor)
            }
        }
        box.addView(previewTv, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap })

        val input = EditText(ctx).apply {
            isSingleLine = true
            hint = "Custom prefix (e.g. Namaste, Welcome)"
            setText(settings.greetingPhrase)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(140, 200, 220, 245))
            setPadding(padH / 2, padV / 2, padH / 2, padV / 2)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = GradientDrawable().apply {
                setColor(Color.argb(45, 255, 255, 255))
                cornerRadius = LayoutMetrics.dpF(ctx, 12f)
                setStroke(LayoutMetrics.dp(ctx, 1), settings.accentColor)
            }
        }

        val updateLivePreview = {
            val name = settings.userName.ifBlank { "Shree" }
            val textOut = when (selectedStyle) {
                "minimal" -> "☀️  $name"
                "welcome" -> "✨  Welcome Back, $name"
                "custom" -> "🌙  ${input.text.toString().trim().ifBlank { "Hello" }}, $name"
                else -> "☀️  Good Morning, $name"
            }
            val fSpec = Themes.GREETING_FONTS.find { it.key == selectedFont } ?: Themes.GREETING_FONTS.first()
            previewTv.typeface = Typeface.create(fSpec.family, Typeface.BOLD)
            previewTv.text = textOut
            input.visibility = if (selectedStyle == "custom") View.VISIBLE else View.GONE
        }

        val styleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val styleViews = ArrayList<TextView>()
        val chipPadH = LayoutMetrics.dp(ctx, 6)
        val chipPadV = LayoutMetrics.dp(ctx, 8)

        Themes.GREETING_STYLES.forEachIndexed { idx, spec ->
            val chip = TextView(ctx).apply {
                text = spec.label
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                isFocusable = true; isClickable = true
                setPadding(chipPadH, chipPadV, chipPadH, chipPadV)
                setOnClickListener {
                    selectedStyle = spec.key
                    styleViews.forEachIndexed { i, v ->
                        val active = Themes.GREETING_STYLES[i].key == selectedStyle
                        v.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, active, settings.tileColor)
                        v.setTextColor(Color.WHITE)
                    }
                    updateLivePreview()
                }
                setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 4f)
                }
            }
            chip.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, spec.key == selectedStyle, settings.tileColor)
            chip.setTextColor(Color.WHITE)
            styleViews.add(chip)
            styleRow.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (idx < Themes.GREETING_STYLES.lastIndex) rightMargin = gap / 2
            })
        }
        box.addView(styleRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap })

        val fontRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val fontViews = ArrayList<TextView>()
        Themes.GREETING_FONTS.forEachIndexed { idx, fSpec ->
            val chip = TextView(ctx).apply {
                text = fSpec.label
                textSize = 10f
                typeface = Typeface.create(fSpec.family, Typeface.BOLD)
                gravity = Gravity.CENTER
                isFocusable = true; isClickable = true
                setPadding(chipPadH, chipPadV, chipPadH, chipPadV)
                setOnClickListener {
                    selectedFont = fSpec.key
                    fontViews.forEachIndexed { i, v ->
                        val active = Themes.GREETING_FONTS[i].key == selectedFont
                        v.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, active, settings.tileColor)
                    }
                    updateLivePreview()
                }
                setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 4f)
                }
            }
            chip.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, fSpec.key == selectedFont, settings.tileColor)
            chip.setTextColor(Color.WHITE)
            fontViews.add(chip)
            fontRow.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (idx < Themes.GREETING_FONTS.lastIndex) rightMargin = gap / 2
            })
        }
        box.addView(fontRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap })
        box.addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap })
        updateLivePreview()

        val btnH = LayoutMetrics.dynamicSpace(ctx, 0.056f, 36, 48, false)
        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(makeDialogBtn(ctx, settings, "Cancel", false) { dialog.dismiss() }, LinearLayout.LayoutParams(0, btnH, 1f).apply { rightMargin = gap })
        btnRow.addView(makeDialogBtn(ctx, settings, "✓ Apply", true) {
            settings.greetingStyle = selectedStyle
            settings.greetingFont = selectedFont
            settings.greetingPhrase = input.text.toString().trim()
            onLiveRefresh()
            onRefreshPanel()
            dialog.dismiss()
        }, LinearLayout.LayoutParams(0, btnH, 1f))
        box.addView(btnRow, LinearLayout.LayoutParams(-1, -2))

        dialog.setContentView(box, LinearLayout.LayoutParams(LayoutMetrics.dynamicSpace(ctx, 0.36f, 280, 440, true), -2))
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
        dialog.show()
    }
