// Important instructions:
// - Read smart modes and live scenes directly from LiveBackdrop (single source of truth).
// - Support custom gallery image picking, weather aura toggle, scaling, zoom, and pan offsets.

package shree.tvhome.panels

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.LiveBackdrop

object WallpaperPanel {

    // Builds wallpaper presets, live canvas scenes, and custom photo controls.
    fun build(
        ctx: Context,
        panel: LinearLayout,
        settings: AppSettings,
        onLiveUpdate: () -> Unit,
        onPickNewImage: () -> Unit,
        onDone: () -> Unit
    ) {
        panel.clipChildren = false
        panel.clipToPadding = false
        val hasCustomImage = settings.prefs.getString(AppSettings.PREF_WALLPAPER, null) != null
        val curMode = settings.liveWallpaperMode

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "SMART DYNAMIC MODES")
        LiveBackdrop.SMART_MODES.chunked(2).forEach { modePair ->
            val autoRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            modePair.forEachIndexed { idx, spec ->
                autoRow.addView(
                    makeVisualCard(
                        ctx, settings, spec.icon, spec.name, spec.subtitle,
                        spec.swatch,
                        !hasCustomImage && curMode == spec.key
                    ) {
                        settings.prefs.edit().remove(AppSettings.PREF_WALLPAPER).apply()
                        settings.liveWallpaperMode = spec.key
                        onLiveUpdate()
                        rebuild(ctx, panel, settings, onLiveUpdate, onPickNewImage, onDone)
                    },
                    LinearLayout.LayoutParams(0, dp(ctx, 54), 1f).apply {
                        if (idx < modePair.lastIndex) rightMargin = dp(ctx, 7)
                    }
                )
            }
            panel.addView(autoRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 8) })
        }

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "LIVE CANVAS SCENES")
        LiveBackdrop.SCENES.chunked(2).forEach { scenePair ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            scenePair.forEachIndexed { idx, spec ->
                val sel = !hasCustomImage && curMode == spec.key
                val card = makeVisualCard(
                    ctx, settings, spec.icon, spec.name,
                    if (sel) "Active" else "Tap to set", spec.swatch, sel
                ) {
                    settings.prefs.edit().remove(AppSettings.PREF_WALLPAPER).apply()
                    settings.liveWallpaperMode = spec.key
                    onLiveUpdate()
                    rebuild(ctx, panel, settings, onLiveUpdate, onPickNewImage, onDone)
                }
                val p = LinearLayout.LayoutParams(0, dp(ctx, 50), 1f).apply {
                    if (idx < scenePair.lastIndex) rightMargin = dp(ctx, 7)
                }
                row.addView(card, p)
            }
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 7) })
        }

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "CUSTOM GALLERY PHOTO")
        val pickCard = makeVisualCard(
            ctx, settings, "🖼", "Choose Photo from Storage",
            if (hasCustomImage) "Custom photo active • Tap to change" else "Select 4K / HD image",
            intArrayOf(Color.argb(85, 255, 255, 255), Color.argb(38, 255, 255, 255)),
            hasCustomImage
        ) {
            onPickNewImage()
        }
        panel.addView(pickCard, LinearLayout.LayoutParams(-1, dp(ctx, 50)).apply { bottomMargin = dp(ctx, 8) })

        if (hasCustomImage) {
            val auraOn = settings.photoWeatherAura
            val auraCard = makeVisualCard(
                ctx, settings, if (auraOn) "🌦️" else "🚫",
                "Subtle Corner Weather Aura",
                if (auraOn) "Enabled • Soft edge glow (Tap to Off)" else "Disabled • Pure photo (Tap to On)",
                intArrayOf(Color.argb(80, 68, 165, 235), Color.argb(40, 32, 52, 95)),
                auraOn
            ) {
                settings.photoWeatherAura = !auraOn
                onLiveUpdate()
                rebuild(ctx, panel, settings, onLiveUpdate, onPickNewImage, onDone)
            }
            panel.addView(auraCard, LinearLayout.LayoutParams(-1, dp(ctx, 50)).apply { bottomMargin = dp(ctx, 8) })

            PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "PHOTO FIT & ZOOM")
            val curScaleMode = settings.wpScaleMode
            addPillRow(ctx, panel, settings, listOf("Fit Screen", "Fill Screen"), listOf(curScaleMode == "fit", curScaleMode == "width")) { idx ->
                settings.wpScaleMode = if (idx == 0) "fit" else "width"
                onLiveUpdate()
                rebuild(ctx, panel, settings, onLiveUpdate, onPickNewImage, onDone)
            }
            addPillRow(ctx, panel, settings, listOf("Zoom -", "Reset", "Zoom +"), listOf(false, false, false)) { idx ->
                var z = settings.wpZoom
                z = when (idx) {
                    0 -> maxOf(1.0f, z - 0.1f)
                    1 -> 1.08f
                    else -> minOf(2.2f, z + 0.1f)
                }
                settings.wpZoom = z
                onLiveUpdate()
            }
            addPillRow(ctx, panel, settings, listOf("⬅", "⬆", "⬇", "➡"), listOf(false, false, false, false)) { idx ->
                var ox = settings.wpOffsetX
                var oy = settings.wpOffsetY
                val step = dp(ctx, 24)
                when (idx) {
                    0 -> ox -= step
                    1 -> oy -= step
                    2 -> oy += step
                    else -> ox += step
                }
                settings.wpOffsetX = ox
                settings.wpOffsetY = oy
                onLiveUpdate()
            }
        }

        val donePill = TextView(ctx).apply {
            text = "✓  Apply & Back"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isFocusable = true
            background = pillBg(ctx, false, true, settings.accentColor)
            setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.02f, 3f)
                v.background = pillBg(ctx, f, true, settings.accentColor)
            }
            setOnClickListener { onDone() }
        }
        panel.addView(donePill, LinearLayout.LayoutParams(-1, dp(ctx, 42)).apply {
            topMargin = dp(ctx, 10)
            bottomMargin = dp(ctx, 36)
        })
    }

    private fun rebuild(
        ctx: Context,
        panel: LinearLayout,
        settings: AppSettings,
        onLive: () -> Unit,
        onPick: () -> Unit,
        onDone: () -> Unit
    ) {
        panel.removeAllViews()
        build(ctx, panel, settings, onLive, onPick, onDone)
    }

    private fun makeVisualCard(
        ctx: Context,
        settings: AppSettings,
        icon: String,
        title: String,
        sub: String,
        gradColors: IntArray,
        selected: Boolean,
        onClick: () -> Unit
    ) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6))
        isFocusable = true
        background = cardBg(ctx, false, selected, gradColors, settings.accentColor)
        setOnFocusChangeListener { v, f ->
            AnimationController.animateFocus(v, f, settings.motionStyle, 1.02f, 4f)
            v.background = cardBg(ctx, f, selected, gradColors, settings.accentColor)
        }
        setOnClickListener { onClick() }

        addView(TextView(ctx).apply { text = icon; textSize = 15f; setPadding(0, 0, dp(ctx, 8), 0) })
        val mid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(TextView(ctx).apply {
            text = title
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            isSingleLine = true
        })
        mid.addView(TextView(ctx).apply {
            text = sub
            textSize = 7.8f
            setTextColor(Color.argb(210, 235, 245, 255))
            isSingleLine = true
        })
        addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        if (selected) addView(TextView(ctx).apply {
            text = "✓"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
    }

    private fun addPillRow(
        ctx: Context,
        panel: LinearLayout,
        settings: AppSettings,
        labels: List<String>,
        selectedFlags: List<Boolean>,
        onClick: (Int) -> Unit
    ) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        labels.forEachIndexed { i, txt ->
            val sel = selectedFlags.getOrElse(i) { false }
            val pill = TextView(ctx).apply {
                text = txt
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                isFocusable = true
                background = pillBg(ctx, false, sel, settings.accentColor)
                setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 3f)
                    v.background = pillBg(ctx, f, sel, settings.accentColor)
                }
                setOnClickListener { onClick(i) }
            }
            row.addView(pill, LinearLayout.LayoutParams(0, dp(ctx, 36), 1f).apply {
                if (i < labels.lastIndex) rightMargin = dp(ctx, 6)
            })
        }
        panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 7) })
    }

    private fun cardBg(ctx: Context, focused: Boolean, selected: Boolean, colors: IntArray, accent: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, colors
    ).apply {
        cornerRadius = LayoutMetrics.dpF(ctx, 14f)
        setStroke(
            dp(ctx, if (focused || selected) 2 else 1),
            when {
                focused -> Color.WHITE
                selected -> accent
                else -> Color.argb(85, 255, 255, 255)
            }
        )
    }

    private fun pillBg(ctx: Context, focused: Boolean, selected: Boolean, accent: Int) = GradientDrawable().apply {
        setColor(
            when {
                focused -> Color.argb(120, 255, 255, 255)
                selected -> LayoutMetrics.withAlpha(accent, 130)
                else -> Color.argb(45, 255, 255, 255)
            }
        )
        cornerRadius = LayoutMetrics.dpF(ctx, 12f)
        setStroke(dp(ctx, if (focused || selected) 2 else 1), if (focused) Color.WHITE else accent)
    }

    private fun dp(ctx: Context, v: Int): Int = LayoutMetrics.dp(ctx, v)
}
