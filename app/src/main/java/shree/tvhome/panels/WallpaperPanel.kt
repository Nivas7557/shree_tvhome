// Important instructions:
// - Read smart modes and live scenes directly from LiveBackdrop (single source of truth).
// - Support focus live preview with click-to-commit and dynamic spacing without hardcoded paddings or requestFocus().

package shree.tvhome.panels

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.LiveBackdrop

object WallpaperPanel {

    // Builds smart modes, live scenes, custom gallery photo options, and zoom/pan pills.
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

        val gapH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 4, maxDp = 12, horizontal = true)
        val gapV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 4, maxDp = 10, horizontal = false)
        val smartCardH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.072f, minDp = 46, maxDp = 62, horizontal = false)
        val sceneCardH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.066f, minDp = 42, maxDp = 58, horizontal = false)
        val pillH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.052f, minDp = 32, maxDp = 44, horizontal = false)

      var savedWallpaper = settings.prefs.getString(AppSettings.PREF_WALLPAPER, null)
      var savedMode = settings.liveWallpaperMode
      val hasCustomImage = savedWallpaper != null
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
                        !hasCustomImage && curMode == spec.key,
                        onFocusPreview = {
                            settings.prefs.edit().remove(AppSettings.PREF_WALLPAPER).apply()
                            settings.liveWallpaperMode = spec.key
                            onLiveUpdate()
                        },
                        onBlurRevert = {
                            if (savedWallpaper != null) {
                                settings.prefs.edit().putString(AppSettings.PREF_WALLPAPER, savedWallpaper).apply()
                            }
                            settings.liveWallpaperMode = savedMode
                            onLiveUpdate()
                        }
                  ) {
                      savedWallpaper = null
                      savedMode = spec.key
                      settings.prefs.edit().remove(AppSettings.PREF_WALLPAPER).apply()
                      settings.liveWallpaperMode = spec.key
                      onLiveUpdate()
                      rebuild(ctx, panel, settings, onLiveUpdate, onPickNewImage, onDone)
                  }
                    LinearLayout.LayoutParams(0, smartCardH, 1f).apply {
                        if (idx < modePair.lastIndex) rightMargin = gapH
                    }
                )
            }
            panel.addView(autoRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
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
                    spec.particleKind, spec.swatch, sel,
                    onFocusPreview = {
                        settings.prefs.edit().remove(AppSettings.PREF_WALLPAPER).apply()
                        settings.liveWallpaperMode = spec.key
                        onLiveUpdate()
                    },
                    onBlurRevert = {
                        if (savedWallpaper != null) {
                            settings.prefs.edit().putString(AppSettings.PREF_WALLPAPER, savedWallpaper).apply()
                        }
                        settings.liveWallpaperMode = savedMode
                        onLiveUpdate()
                    }
                  ) {
                      savedWallpaper = null
                      savedMode = spec.key
                      settings.prefs.edit().remove(AppSettings.PREF_WALLPAPER).apply()
                      settings.liveWallpaperMode = spec.key
                      onLiveUpdate()
                      rebuild(ctx, panel, settings, onLiveUpdate, onPickNewImage, onDone)
                  }
                val p = LinearLayout.LayoutParams(0, sceneCardH, 1f).apply {
                    if (idx < scenePair.lastIndex) rightMargin = gapH
                }
                row.addView(card, p)
            }
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
        }

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "CUSTOM GALLERY PHOTO")
        val pickCard = makeVisualCard(
            ctx, settings, "🖼", "Choose Photo",
            settings.wpScaleMode,
            intArrayOf(Color.argb(85, 255, 255, 255), Color.argb(38, 255, 255, 255)),
            hasCustomImage
        ) {
            onPickNewImage()
        }
        panel.addView(pickCard, LinearLayout.LayoutParams(-1, sceneCardH).apply { bottomMargin = gapV })

        if (hasCustomImage) {
            val auraOn = settings.photoWeatherAura
            val auraCard = makeVisualCard(
                ctx, settings, if (auraOn) "🌦️" else "🚫",
                "Weather Aura",
                settings.cachedWeatherCode.toString(),
                intArrayOf(Color.argb(80, 68, 165, 235), Color.argb(40, 32, 52, 95)),
                auraOn
            ) {
                settings.photoWeatherAura = !auraOn
                onLiveUpdate()
                rebuild(ctx, panel, settings, onLiveUpdate, onPickNewImage, onDone)
            }
            panel.addView(auraCard, LinearLayout.LayoutParams(-1, sceneCardH).apply { bottomMargin = gapV })

            PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "PHOTO FIT & ZOOM")
            val curScaleMode = settings.wpScaleMode
            addPillRow(ctx, panel, settings, pillH, gapH, listOf("Fit", "Fill"), listOf(curScaleMode == "fit", curScaleMode == "width")) { idx ->
                settings.wpScaleMode = if (idx == 0) "fit" else "width"
                onLiveUpdate()
                rebuild(ctx, panel, settings, onLiveUpdate, onPickNewImage, onDone)
            }
            addPillRow(ctx, panel, settings, pillH, gapH, listOf("Zoom -", "Reset", "Zoom +"), listOf(false, false, false)) { idx ->
                var z = settings.wpZoom
                z = when (idx) {
                    0 -> maxOf(1.0f, z - 0.1f)
                    1 -> 1.08f
                    else -> minOf(2.2f, z + 0.1f)
                }
                settings.wpZoom = z
                onLiveUpdate()
            }
            addPillRow(ctx, panel, settings, pillH, gapH, listOf("⬅", "⬆", "⬇", "➡"), listOf(false, false, false, false)) { idx ->
                var ox = settings.wpOffsetX
                var oy = settings.wpOffsetY
                val step = LayoutMetrics.dynamicSpace(ctx, ratio = 0.02f, minDp = 16, maxDp = 32, horizontal = true)
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
            isClickable = true
            background = pillBg(ctx, false, true, settings.accentColor)
            setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.02f, 3f)
                v.background = pillBg(ctx, f, true, settings.accentColor)
            }
            setOnClickListener { onDone() }
        }
        panel.addView(donePill, LinearLayout.LayoutParams(-1, pillH).apply {
            topMargin = gapV
            bottomMargin = gapV * 3
        })

        // Revert any unconfirmed focus preview when leaving the panel.
        panel.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                val nowWp = settings.prefs.getString(AppSettings.PREF_WALLPAPER, null)
                if (nowWp != savedWallpaper || settings.liveWallpaperMode != savedMode) {
                    if (savedWallpaper != null) {
                        settings.prefs.edit().putString(AppSettings.PREF_WALLPAPER, savedWallpaper).apply()
                    } else {
                        settings.prefs.edit().remove(AppSettings.PREF_WALLPAPER).apply()
                    }
                    settings.liveWallpaperMode = savedMode
                    onLiveUpdate()
                }
            }
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
        onFocusPreview: (() -> Unit)? = null,
        onBlurRevert: (() -> Unit)? = null,
        onClick: () -> Unit
    ) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.009f, minDp = 8, maxDp = 14, horizontal = true)
        val padV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 5, maxDp = 10, horizontal = false)
        setPadding(padH, padV, padH, padV)
        isFocusable = true
        isClickable = true
        background = cardBg(ctx, false, selected, gradColors, settings.accentColor)
        setOnFocusChangeListener { v, f ->
            AnimationController.animateFocus(v, f, settings.motionStyle, 1.02f, 4f)
            if (f) onFocusPreview?.invoke() else onBlurRevert?.invoke()
            v.background = cardBg(ctx, f, selected, gradColors, settings.accentColor)
        }
        setOnClickListener { onClick() }

        addView(TextView(ctx).apply {
            text = icon
            textSize = 14.5f
            setPadding(0, 0, padH / 2, 0)
        })
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
        pillH: Int,
        gapH: Int,
        labels: List<String>,
        selectedFlags: List<Boolean>,
        onClick: (Int) -> Unit
    ) {
        val gapV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 4, maxDp = 10, horizontal = false)
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
                isClickable = true
                background = pillBg(ctx, false, sel, settings.accentColor)
                setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 3f)
                    v.background = pillBg(ctx, f, sel, settings.accentColor)
                }
                setOnClickListener { onClick(i) }
            }
            row.addView(pill, LinearLayout.LayoutParams(0, pillH, 1f).apply {
                if (i < labels.lastIndex) rightMargin = gapH
            })
        }
        panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
    }

    private fun cardBg(ctx: Context, focused: Boolean, selected: Boolean, colors: IntArray, accent: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, colors
    ).apply {
        cornerRadius = LayoutMetrics.dpF(ctx, 14f)
        setStroke(
            LayoutMetrics.dp(ctx, if (focused || selected) 2 else 1),
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
        setStroke(LayoutMetrics.dp(ctx, if (focused || selected) 2 else 1), if (focused) Color.WHITE else accent)
    }
}
