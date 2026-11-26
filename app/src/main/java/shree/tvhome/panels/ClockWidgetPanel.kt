// Important instructions:
// - Render a crisp Clock Widget Settings & Live Preview panel without fixed paddings or forced focus requests.
// - Drive clock styles and location modes directly from Themes (single source of truth).

package shree.tvhome.panels

import android.content.Context
import android.widget.LinearLayout
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes
import shree.tvhome.design.ClockWeatherCardDesign

object ClockWidgetPanel {

    // Builds the Clock Widget Settings panel with a live preview stage, style track, and location controls.
    fun build(
        context: Context,
        panel: LinearLayout,
        settings: AppSettings,
        onLiveRefresh: () -> Unit,
        onBack: () -> Unit
    ) {
        panel.clipChildren = false
        panel.clipToPadding = false

        val gapV = LayoutMetrics.dynamicSpace(context, ratio = 0.01f, minDp = 6, maxDp = 14, horizontal = false)
        val previewH = LayoutMetrics.dynamicSpace(context, ratio = 0.28f, minDp = 175, maxDp = 260, horizontal = false)

        PanelSurfaceKit.addSectionHeader(context, panel, settings, "LIVE WIDGET PREVIEW")
        val previewStage = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            background = PanelSurfaceKit.glassCardBg(context, settings.accentColor, false, settings.tileColor)
            val padH = LayoutMetrics.dynamicSpace(context, ratio = 0.012f, minDp = 8, maxDp = 16, horizontal = true)
            val padV = LayoutMetrics.dynamicSpace(context, ratio = 0.012f, minDp = 8, maxDp = 16, horizontal = false)
            setPadding(padH, padV, padH, padV)
        }
        val previewWidget = ClockWeatherCardDesign(context, settings).apply {
            isFocusable = false
            isClickable = false
        }
        previewStage.addView(previewWidget, LinearLayout.LayoutParams(-1, previewH))
        panel.addView(previewStage, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        PanelSurfaceKit.addSectionHeader(context, panel, settings, "CLOCK STYLE")
        PanelSurfaceKit.addSegmentedTrack(
            ctx = context,
            parent = panel,
            settings = settings,
            options = Themes.CLOCK_STYLES.map { it.key to it.label },
            getSelected = { settings.clockStyle },
            onSelect = { key ->
                settings.clockStyle = key
                previewWidget.refreshStyle()
                onLiveRefresh()
            }
        )

        PanelSurfaceKit.addSectionHeader(context, panel, settings, "LOCATION MODE")
        PanelSurfaceKit.addSegmentedTrack(
            ctx = context,
            parent = panel,
            settings = settings,
            options = Themes.LOCATION_MODES.map { it.key to it.label },
            getSelected = { settings.locationMode },
            onSelect = { key ->
                settings.locationMode = key
                if (key == Themes.LOCATION_MODES.first().key) {
                    settings.weatherCity = ""
                    settings.prefs.edit()
                        .remove(AppSettings.PREF_SAVED_LAT)
                        .remove(AppSettings.PREF_SAVED_LON)
                        .remove(AppSettings.PREF_CACHED_CITY)
                        .apply()
                }
                previewWidget.triggerWeatherRefresh(true)
                onLiveRefresh()
            }
        )

        val currentCity = settings.weatherCity.ifBlank {
            settings.prefs.getString(AppSettings.PREF_CACHED_CITY, Themes.LOCATION_MODES.first().label)
                ?: Themes.LOCATION_MODES.first().label
        }
        panel.addView(
            PanelSurfaceKit.makeActionRow(
                context, settings, "📍", "Choose Manual Location", currentCity
            ) {
                previewWidget.showLocationSelectorDialog()
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV }
        )

        panel.addView(
            PanelSurfaceKit.makeActionRow(
                context, settings, "‹", "Back to Customize", "Return to launcher customization", ""
            ) {
                onBack()
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV * 2 }
        )
    }
}
