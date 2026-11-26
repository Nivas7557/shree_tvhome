// Important instructions:
// - Act as the universal panel controller (CPU) coordinating all panels, theme updates, and animations.
// - Drive panel routing and styling from a single registry so updates propagate automatically.

package shree.tvhome

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
        const val MODE_SUB_INPUTS = "sub_inputs"
        const val MODE_SUB_PICTURE = "sub_picture"
        const val MODE_SUB_SOUND = "sub_sound"
        const val MODE_SUB_BLUETOOTH = "sub_bluetooth"
        const val MODE_SUB_STORAGE = "sub_storage"
    }

    private data class PanelSpec(
        val mode: String,
        val icon: String,
        val title: String,
        val navTag: String,
        val render: (LinearLayout) -> Unit
    )

    // Single registry mapping every panel mode to its header metadata, parent nav tag, and builder.
    private val panelRegistry: List<PanelSpec> by lazy {
        listOf(
            PanelSpec(MODE_QUICK, "⚙  ", "Quick Settings", "nav_quick") { panel ->
                QuickControlsPanel.build(context, panel, settings) { target -> open(target) }
            },
            PanelSpec(MODE_CUSTOMIZE, "✦  ", "Customize Launcher", "nav_customize") { panel ->
                StyleCustomizePanel.build(
                    context, panel, settings, onLiveRefresh, { populateActivePanel() },
                    ::showProfileEditDialog,
                    { open(MODE_WALLPAPER_EDIT) },
                    onOpenHiddenRibbon,
                    ::close
                )
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
                WallpaperPanel.build(context, panel, settings, onWallpaperRefresh, onPickWallpaper) { open(MODE_CUSTOMIZE) }
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

    // Automatically updates the open drawer glass surface when accent or palette settings change.
    init {
        settings.addStyleListener {
            if (isOpen) {
                drawerLayout.background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    Themes.getGlassPanelColors(settings.tileColor, settings.accentColor, elevated = true)
                ).apply {
                    cornerRadius = LayoutMetrics.dpF(context, 24f)
                    setStroke(dp(1.5f), LayoutMetrics.withAlpha(settings.accentColor, 185))
                }
            }
        }
    }

    // Attaches the ambient clock/weather widget so panel open/close transitions coordinate with it.
    fun attachStageWidget(widget: View) {
        stageWidget = widget
    }

    // Returns the active panel container for D-Pad focus routing.
    fun getActivePanelView(): ViewGroup? = activePanel

    // Returns the top navigation button tag associated with the active panel.
    fun getParentNavTag(): String =
        panelRegistry.find { it.mode == currentMode }?.navTag ?: panelRegistry.first().navTag

    // Toggles a panel open or closed when its trigger is activated again.
    fun toggle(mode: String) {
        if (isOpen && mode == currentMode) close() else open(mode)
    }

    // Blocks or restores focusability on sibling containers while the side drawer is open.
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

    // Opens the requested panel mode, applies theme surface, populates content, and runs motion.
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
            setStroke(dp(1.5f), LayoutMetrics.withAlpha(settings.accentColor, 165))
        }
        drawerLayout.outlineProvider = null

        scrim.setBackgroundColor(Color.TRANSPARENT)
        scrim.visibility = View.VISIBLE
        drawerLayout.visibility = View.VISIBLE

        setupDrawerSkeleton()
        populateActivePanel()

        FocusRouter.findFirstFocusable(activePanel ?: drawerLayout)?.let { firstView ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                firstView.isFocusedByDefault = true
            }
        }

        if (!wasOpen) {
            AnimationController.resetTransform(drawerLayout)
            AnimationController.animatePanelEnter(drawerLayout, stageWidget, dynamicDrawerW, settings.motionStyle) {}
        } else {
            AnimationController.playDemoPreview(drawerLayout, settings.motionStyle)
        }
    }

    // Closes the active panel drawer, restores background focus, and returns focus to the header trigger.
    fun close() {
        if (!isOpen) return
        val returnTag = getParentNavTag()
        isOpen = false
        currentMode = ""
        val out = maxOf(dp(340f), drawerLayout.width + dp(30f))

        setBackgroundSiblingsFocusable(true)

        AnimationController.animatePanelExit(drawerLayout, stageWidget, out, settings.motionStyle) {
            scrim.visibility = View.GONE
            drawerLayout.visibility = View.GONE
            mainPage.findViewWithTag<View>(returnTag)?.requestFocus()
        }
        drawerLayout.post { onLiveRefresh() }
    }

    // Opens the profile and avatar editor dialog and refreshes visuals upon saving.
    fun showProfileEditDialog() {
        AvatarEditorPanel.show(context, settings, onPickProfilePic) {
            onLiveRefresh()
            if (isOpen) populateActivePanel()
        }
    }

    // Builds the common header, breadcrumb back button, close button, and scrollable panel body.
    private fun setupDrawerSkeleton() {
        drawerLayout.removeAllViews()
        drawerLayout.setPadding(dp(14f), dp(14f), dp(14f), dp(14f))

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(6f), dp(4f), dp(6f), dp(10f))
        }

        val headerSpec = panelRegistry.find { it.mode == currentMode } ?: panelRegistry.first()
        val isSubPanel = currentMode.startsWith("sub_") || currentMode == MODE_WALLPAPER_EDIT
        if (isSubPanel) {
            val parentMode = if (currentMode == MODE_WALLPAPER_EDIT) MODE_CUSTOMIZE else MODE_QUICK
            val parentLabel = if (currentMode == MODE_WALLPAPER_EDIT) "Customize" else "Quick Settings"
            val backCrumb = TextView(context).apply {
                text = "‹ $parentLabel"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(settings.accentColor)
                isFocusable = true
                isClickable = true
                setPadding(dp(6f), dp(3f), dp(8f), dp(3f))
                background = PanelSurfaceKit.glassCardBg(context, settings.accentColor, false, settings.tileColor)
                setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 3f)
                    v.background = PanelSurfaceKit.glassCardBg(context, settings.accentColor, f, settings.tileColor)
                }
                setOnClickListener { open(parentMode) }
            }
            topRow.addView(backCrumb, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8f) })
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
        topRow.addView(closeX, LinearLayout.LayoutParams(dp(28f), dp(28f)))
        drawerLayout.addView(topRow)

        drawerLayout.addView(
            View(context).apply { setBackgroundColor(Color.argb(60, 255, 255, 255)) },
            LinearLayout.LayoutParams(-1, dp(1f)).apply { bottomMargin = dp(8f) }
        )

        activeScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            clipChildren = true
            clipToPadding = true
            setPadding(0, dp(2f), 0, dp(2f))
        }
        activePanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = true
            clipToPadding = true
            setPadding(dp(6f), dp(6f), dp(6f), dp(20f))
        }
        drawerLayout.addView(activeScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        activeScroll?.addView(activePanel)
    }

    // Clears and renders the panel matching the active mode from panelRegistry.
    private fun populateActivePanel() {
        val panel = activePanel ?: return
        panel.removeAllViews()
        val spec = panelRegistry.find { it.mode == currentMode } ?: panelRegistry.first()
        spec.render(panel)
    }

    // Builds circular close button styling for the drawer header.
    private fun closeBtnBg(focused: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (focused) Color.argb(165, 255, 75, 85) else Color.argb(55, 255, 255, 255))
        setStroke(dp(1f), if (focused) Color.WHITE else Color.argb(85, 255, 255, 255))
    }

    private fun dp(v: Float): Int = LayoutMetrics.dpF(context, v).toInt()
}
