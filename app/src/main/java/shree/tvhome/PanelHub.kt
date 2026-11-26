// Important instructions:
// - Coordinate drawer panels and live visual updates without forcing focus traps or focus jumps.
// - Derive drawer margins and paddings proportionally from LayoutMetrics.

package shree.tvhome

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes
import shree.tvhome.design.AppsShelfDesign
import shree.tvhome.panels.AlertsPowerPanel
import shree.tvhome.panels.AvatarEditorPanel
import shree.tvhome.panels.ClockWidgetPanel
import shree.tvhome.panels.PanelSurfaceKit
import shree.tvhome.panels.PictureAudioPanel
import shree.tvhome.panels.QuickControlsPanel
import shree.tvhome.panels.StyleCustomizePanel
import shree.tvhome.panels.WallpaperPanel
import shree.tvhome.panels.WifiPanel

class PanelHub(
    private val context: Context,
    private val drawerLayout: LinearLayout,
    private val scrim: View,
    private val mainPage: View,
    private val settings: AppSettings,
    private val onLiveRefresh: () -> Unit,
    private val onWallpaperRefresh: () -> Unit,
    private val onPickWallpaper: () -> Unit,
    private val onPickProfilePic: () -> Unit,
    private val onOpenHiddenRibbon: () -> Unit
) {

    companion object {
        const val MODE_CUSTOMIZE = "customize"
        const val MODE_QUICK = "quick"
        const val MODE_WIFI = "wifi"
        const val MODE_ALERTS = "alerts"
        const val MODE_POWER = "power"
        const val MODE_WALLPAPER_EDIT = "wp_edit"
        const val MODE_CLOCK_WIDGET = "clock_widget_settings"
        const val MODE_SUB_INPUTS = "sub_inputs"
        const val MODE_SUB_PICTURE = "sub_picture"
        const val MODE_SUB_SOUND = "sub_sound"
        const val MODE_SUB_BLUETOOTH = "sub_bluetooth"
        const val MODE_SUB_STORAGE = "sub_storage"

        const val TAG_WALLPAPER_TRIGGER = "customize_wallpaper_btn"
        const val TAG_CLOCK_TRIGGER = "customize_clock_btn"
    }

    private data class PanelSpec(
        val mode: String,
        val icon: String,
        val title: String,
        val navTag: String,
        val render: (LinearLayout) -> Unit
    )

    private var returnFocusTagInDrawer: String? = null

    private val panelRegistry: List<PanelSpec> by lazy {
        listOf(
            PanelSpec(MODE_QUICK, "⚙  ", "Quick Settings", "nav_quick") { panel ->
                QuickControlsPanel.build(context, panel, settings) { target -> open(target) }
            },
            PanelSpec(MODE_CUSTOMIZE, "✦  ", "Customize Launcher", "nav_customize") { panel ->
                StyleCustomizePanel.build(
                    context, panel, settings, onLiveRefresh, { populateActivePanel() },
                    ::showProfileEditDialog,
                    onOpenWallpaper = { triggerTag ->
                        returnFocusTagInDrawer = triggerTag
                        open(MODE_WALLPAPER_EDIT)
                    },
                    onOpenHidden = onOpenHiddenRibbon,
                    onOpenClockSettings = {
                        returnFocusTagInDrawer = TAG_CLOCK_TRIGGER
                        open(MODE_CLOCK_WIDGET)
                    },
                    onCloseDrawer = ::close
                )
            },
            PanelSpec(MODE_CLOCK_WIDGET, "🕰️  ", "Clock Widget Settings", "nav_customize") { panel ->
                ClockWidgetPanel.build(context, panel, settings, onLiveRefresh) {
                    open(MODE_CUSTOMIZE)
                }
            },
            PanelSpec(MODE_WIFI, "📶  ", "Network & Internet", "nav_wifi") { panel ->
                WifiPanel.build(context, panel, settings)
            },
            PanelSpec(MODE_ALERTS, "🔔  ", "Alerts & Notifications", "nav_alerts") { panel ->
                AlertsPowerPanel.buildAlertsSubPanel(context, panel, settings) { populateActivePanel() }
            },
            PanelSpec(MODE_POWER, "⏻  ", "Power & System", "nav_power") { panel ->
                AlertsPowerPanel.build(
                    context, panel, settings,
                    onCloseDrawer = ::close,
                    onRestartLauncher = {
                        AppsShelfDesign.clearCache()
                        onLiveRefresh()
                    }
                )
            },
            PanelSpec(MODE_WALLPAPER_EDIT, "🖼  ", "Wallpaper Studio", "nav_customize") { panel ->
                WallpaperPanel.build(context, panel, settings, onWallpaperRefresh, onPickWallpaper) {
                    open(MODE_CUSTOMIZE)
                }
            },
            PanelSpec(MODE_SUB_INPUTS, "📺  ", "TV Inputs & HDMI", "nav_quick") { panel ->
                PictureAudioPanel.buildInputsSubPanel(context, panel, settings) { open(MODE_QUICK) }
            },
            PanelSpec(MODE_SUB_PICTURE, "☀  ", "Display & Sleep Timer", "nav_quick") { panel ->
                PictureAudioPanel.buildPictureAndDisplayPanel(context, panel, settings) { open(MODE_QUICK) }
            },
            PanelSpec(MODE_SUB_SOUND, "🔊  ", "Sound & Audio Out", "nav_quick") { panel ->
                PictureAudioPanel.buildSoundSubPanel(context, panel, settings) { open(MODE_QUICK) }
            },
            PanelSpec(MODE_SUB_BLUETOOTH, "🔵  ", "Bluetooth & Remotes", "nav_quick") { panel ->
                PictureAudioPanel.buildBluetoothPanel(context, panel, settings) { open(MODE_QUICK) }
            },
            PanelSpec(MODE_SUB_STORAGE, "💾  ", "Storage & Installed Apps", "nav_quick") { panel ->
                PictureAudioPanel.buildStorageAndAppsPanel(context, panel, settings) { open(MODE_QUICK) }
            }
        )
    }

    var isOpen = false
        private set
    private var currentMode = ""
    private var activeScroll: ScrollView? = null
    private var activePanel: LinearLayout? = null
    private var stageWidget: View? = null

    init {
        settings.addStyleListener {
            if (isOpen) {
                drawerLayout.background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    Themes.getGlassPanelColors(settings.tileColor, settings.accentColor, elevated = true)
                ).apply {
                    cornerRadius = LayoutMetrics.dpF(context, 24f)
                    setStroke(LayoutMetrics.dp(context, 2), LayoutMetrics.withAlpha(settings.accentColor, 185))
                }
            }
        }
    }

    fun attachStageWidget(widget: View) {
        stageWidget = widget
    }

    fun getActivePanelView(): ViewGroup? = activePanel

    fun getParentNavTag(): String =
        panelRegistry.find { it.mode == currentMode }?.navTag ?: panelRegistry.first().navTag

    fun toggle(mode: String) {
        if (isOpen && mode == currentMode) close() else open(mode)
    }

    private fun setBackgroundSiblingsFocusable(enabled: Boolean) {
        val parent = drawerLayout.parent as? ViewGroup ?: return
        val focusability = if (enabled) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child !== drawerLayout && child !== scrim) {
                (child as? ViewGroup)?.descendantFocusability = focusability
            }
        }
    }

    fun open(mode: String) {
        val wasOpen = isOpen
        currentMode = mode
        isOpen = true

        setBackgroundSiblingsFocusable(false)

        val screenW = context.resources.displayMetrics.widthPixels
        val minStageW = LayoutMetrics.dp(context, Themes.PANEL_MIN_WIDTH_DP)
        val dynamicDrawerW = maxOf(minStageW, (screenW * (settings.panelWidthPct / 100f)).toInt())
        drawerLayout.layoutParams.width = dynamicDrawerW
        drawerLayout.clipChildren = true
        drawerLayout.clipToPadding = true
        drawerLayout.requestLayout()

        drawerLayout.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            Themes.getGlassPanelColors(settings.tileColor, settings.accentColor, elevated = true)
        ).apply {
            cornerRadius = LayoutMetrics.dpF(context, 24f)
            setStroke(LayoutMetrics.dp(context, 2), LayoutMetrics.withAlpha(settings.accentColor, 165))
        }
        drawerLayout.outlineProvider = null

        scrim.setBackgroundColor(Color.TRANSPARENT)
        scrim.visibility = View.VISIBLE
        drawerLayout.visibility = View.VISIBLE

        setupDrawerSkeleton()
        populateActivePanel()

        if (!wasOpen) {
            AnimationController.resetTransform(drawerLayout)
            AnimationController.animatePanelEnter(drawerLayout, stageWidget, dynamicDrawerW, settings.motionStyle) {}
        } else {
            AnimationController.playDemoPreview(drawerLayout, settings.motionStyle)
        }
    }

    fun close() {
        if (!isOpen) return
        val returnTag = getParentNavTag()
        isOpen = false
        currentMode = ""
        returnFocusTagInDrawer = null
        val out = maxOf(LayoutMetrics.dp(context, 340), drawerLayout.width + LayoutMetrics.dp(context, 30))

        setBackgroundSiblingsFocusable(true)

        AnimationController.animatePanelExit(drawerLayout, stageWidget, out, settings.motionStyle) {
            scrim.visibility = View.GONE
            drawerLayout.visibility = View.GONE
            mainPage.findViewWithTag<View>(returnTag)
        }
        drawerLayout.post { onLiveRefresh() }
    }

    fun showProfileEditDialog() {
        AvatarEditorPanel.show(context, settings, onPickProfilePic) {
            onLiveRefresh()
            if (isOpen) populateActivePanel()
        }
    }

    private fun setupDrawerSkeleton() {
        val padH = LayoutMetrics.dynamicSpace(context, ratio = 0.014f, minDp = 10, maxDp = 20, horizontal = true)
        val padV = LayoutMetrics.dynamicSpace(context, ratio = 0.016f, minDp = 10, maxDp = 20, horizontal = false)
        val closeSize = LayoutMetrics.dynamicSpace(context, ratio = 0.032f, minDp = 24, maxDp = 34, horizontal = false)

        drawerLayout.removeAllViews()
        drawerLayout.setPadding(padH, padV, padH, padV)

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(padH / 2, padV / 3, padH / 2, padV / 2)
        }

        val headerSpec = panelRegistry.find { it.mode == currentMode } ?: panelRegistry.first()
        val isSubPanel = currentMode.startsWith("sub_") || currentMode == MODE_WALLPAPER_EDIT || currentMode == MODE_CLOCK_WIDGET
        if (isSubPanel) {
            val parentMode = if (currentMode == MODE_WALLPAPER_EDIT || currentMode == MODE_CLOCK_WIDGET) MODE_CUSTOMIZE else MODE_QUICK
            val parentLabel = if (currentMode == MODE_WALLPAPER_EDIT || currentMode == MODE_CLOCK_WIDGET) "Customize" else "Quick Settings"
            val backCrumb = TextView(context).apply {
                text = "‹ $parentLabel"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(settings.accentColor)
                isFocusable = true
                isClickable = true
                setPadding(padH / 2, padV / 4, padH / 2, padV / 4)
                background = PanelSurfaceKit.glassCardBg(context, settings.accentColor, false, settings.tileColor)
                setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 3f)
                    v.background = PanelSurfaceKit.glassCardBg(context, settings.accentColor, f, settings.tileColor)
                }
                setOnClickListener { open(parentMode) }
            }
            topRow.addView(backCrumb, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = padH / 2 })
        }

        topRow.addView(TextView(context).apply {
            text = "${headerSpec.icon}${headerSpec.title}"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        topRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))

        val closeX = TextView(context).apply {
            text = "✕"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            background = closeBtnBg(false)
            setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.12f, 4f)
                v.background = closeBtnBg(f)
            }
            setOnClickListener { close() }
        }
        topRow.addView(closeX, LinearLayout.LayoutParams(closeSize, closeSize))
        drawerLayout.addView(topRow)

        drawerLayout.addView(
            View(context).apply { setBackgroundColor(Color.argb(60, 255, 255, 255)) },
            LinearLayout.LayoutParams(-1, LayoutMetrics.dp(context, 1)).apply { bottomMargin = padV / 2 }
        )

        activeScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
            isFillViewport = true
            clipChildren = true
            clipToPadding = true
        }
        activePanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = true
            clipToPadding = true
            setPadding(padH / 2, padV / 2, padH / 2, padV)
        }
        drawerLayout.addView(activeScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        activeScroll?.addView(activePanel)
    }

    private fun populateActivePanel() {
        val panel = activePanel ?: return
        panel.removeAllViews()
        val spec = panelRegistry.find { it.mode == currentMode } ?: panelRegistry.first()
        spec.render(panel)
    }

    private fun closeBtnBg(focused: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (focused) Color.argb(165, 255, 75, 85) else Color.argb(55, 255, 255, 255))
        setStroke(LayoutMetrics.dp(context, 1), if (focused) Color.WHITE else Color.argb(85, 255, 255, 255))
    }
}
