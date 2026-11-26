// Important instructions:
// - Dedicated Clock Widget Settings panel showing clock styles, location mode/manual city, and live preview.
// - Preview options on focus and apply changes only when clicked/confirmed.

package shree.tvhome.panels

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes
import shree.tvhome.design.ClockWeatherCardDesign

object ClockWidgetPanel {

    // Builds clock style options, automatic/manual location controls, and live stage preview trigger.
    fun build(
        ctx: Context,
        panel: LinearLayout,
        settings: AppSettings,
        onLiveRefresh: () -> Unit,
        onBack: () -> Unit
    ) {
        val originalClockStyle = settings.clockStyle
        val originalLocationMode = settings.locationMode
        var previewActive = false

        val applyPreviewToStage: (String?, String?) -> Unit = { styleOverride, locModeOverride ->
            val clockWidget = findClockWidget(panel)
            if (clockWidget != null) {
                if (styleOverride != null || locModeOverride != null) {
                    previewActive = true
                    val tempStyle = styleOverride ?: settings.clockStyle
                    val tempLocMode = locModeOverride ?: settings.locationMode
                    clockWidget.previewStyleAndLocation(tempStyle, tempLocMode)
                } else if (previewActive) {
                    previewActive = false
                    clockWidget.previewStyleAndLocation(settings.clockStyle, settings.locationMode)
                }
            }
        }

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "CLOCK STYLE (FOCUS PREVIEW • OK TO SET)")
        PanelSurfaceKit.addSegmentedTrack(
            ctx, panel, settings,
            options = Themes.CLOCK_STYLES.map { it.key to it.label },
            getSelected = { settings.clockStyle },
            onFocusPreview = { hoveredKey ->
                applyPreviewToStage(hoveredKey, null)
            },
            onSelect = { key ->
                settings.clockStyle = key
                previewActive = false
                onLiveRefresh()
            }
        )

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "WEATHER LOCATION MODE")
        PanelSurfaceKit.addSegmentedTrack(
            ctx, panel, settings,
            options = Themes.LOCATION_MODES.map { it.key to it.label },
            getSelected = { settings.locationMode },
            onFocusPreview = { hoveredMode ->
                applyPreviewToStage(null, hoveredMode)
            },
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
                previewActive = false
                onLiveRefresh()
            }
        )

        val currentCity = settings.weatherCity.ifBlank {
            settings.prefs.getString(AppSettings.PREF_CACHED_CITY, Themes.LOCATION_MODES.first().label)
                ?: Themes.LOCATION_MODES.first().label
        }
        panel.addView(
            PanelSurfaceKit.makeActionRow(
                ctx, settings, "📍", "Manual City Picker (D-Pad)",
                currentCity
            ) {
                findClockWidget(panel)?.showLocationSelectorDialog()
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = LayoutMetrics.dp(ctx, 8) }
        )

        panel.addView(
            PanelSurfaceKit.makeActionRow(
                ctx, settings, "↺", "Restore Saved Clock & Location",
                "${Themes.getClockStyle(originalClockStyle).label} • ${originalLocationMode.uppercase()}"
            ) {
                settings.clockStyle = originalClockStyle
                settings.locationMode = originalLocationMode
                previewActive = false
                onLiveRefresh()
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = LayoutMetrics.dp(ctx, 8) }
        )

        panel.addView(
            PanelSurfaceKit.makeActionRow(ctx, settings, "‹", "Back to Customize", "") {
                applyPreviewToStage(null, null)
                onBack()
            },
            LinearLayout.LayoutParams(-1, -2)
        )
    }

    private fun findClockWidget(start: View): ClockWeatherCardDesign? {
        fun search(v: View?): ClockWeatherCardDesign? {
            if (v == null) return null
            if (v is ClockWeatherCardDesign) return v
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    val found = search(v.getChildAt(i))
                    if (found != null) return found
                }
            }
            return null
        }
        return search(start.rootView)
    }
}
