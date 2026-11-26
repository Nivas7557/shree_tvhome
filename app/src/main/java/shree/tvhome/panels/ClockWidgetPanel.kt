// Important instructions:
// - Drive clock styles and location modes directly from Themes (single source of truth).
// - Support live preview updates and D-Pad friendly manual city selection without hardcoded style branches.

package shree.tvhome.panels

import android.content.Context
import android.widget.LinearLayout
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes
import shree.tvhome.design.ClockWeatherCardDesign

object ClockWidgetPanel {

    // Builds the Clock Widget Settings panel with a live preview, style selector, and location controls.
    fun build(
        context: Context,
        panel: LinearLayout,
        settings: AppSettings,
        onLiveRefresh: () -> Unit,
        onBack: () -> Unit
    ) {
        panel.clipChildren = false
        panel.clipToPadding = false

        PanelSurfaceKit.addSectionHeader(context, panel, settings, "LIVE PREVIEW")
        val previewWidget = ClockWeatherCardDesign(context, settings).apply {
            isClickable = false
        }
        panel.addView(
            previewWidget,
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 8) }
        )

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
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 10) }
        )

        panel.addView(
            PanelSurfaceKit.makeActionRow(
                context, settings, "‹", "Back to Customize", "Return to launcher customization", ""
            ) {
                onBack()
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 16) }
        )
    }

    private fun dp(ctx: Context, v: Int): Int = LayoutMetrics.dp(ctx, v)
}
