// Important instructions:
// - Define 4 mood presets with live mini previews and adaptive column count based on side panel width.
// - When clicked/committed, persist all preset settings and notify listeners so every panel updates immediately.

package shree.tvhome.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import shree.tvhome.panels.PanelSurfaceKit
import java.util.concurrent.CopyOnWriteArrayList

object MoodPresets {

    data class MoodPreset(
        val key: String,
        val icon: String,
        val title: String,
        val tileTheme: String,
        val accentKey: String,
        val motionKey: String,
        val sceneKey: String
    )

    // Four curated mood presets; add or edit here to update cards and panels automatically.
    val PRESETS: List<MoodPreset> = listOf(
        MoodPreset("velvet_cinema", "🎬", "Velvet Cinema", "dark", "ember", "silk", LiveBackdrop.MODE_SUNSET),
        MoodPreset("neon_pulse", "⚡", "Neon Pulse", "cyber", "orchid", "jelly", LiveBackdrop.MODE_PASTEL),
        MoodPreset("emerald_zen", "🌿", "Emerald Zen", "emerald", "meadow", "levitate", LiveBackdrop.MODE_MORNING),
        MoodPreset("solar_horizon", "🌊", "Solar Horizon", "ocean", "solar", "flip3d", LiveBackdrop.MODE_AQUA)
    )

    // Computes adaptive column count from actual side panel width so cards are never cramped or tiny.
    fun computeDynamicColumns(ctx: Context, panelWidthPct: Int, minCardWidthDp: Int, maxCols: Int): Int {
        val screenW = ctx.resources.displayMetrics.widthPixels
        val panelPx = maxOf(LayoutMetrics.dp(ctx, Themes.PANEL_MIN_WIDTH_DP), (screenW * (panelWidthPct / 100f)).toInt())
        val usablePx = (panelPx * 0.88f).toInt()
        val minItemPx = LayoutMetrics.dp(ctx, minCardWidthDp).coerceAtLeast(1)
        return (usablePx / minItemPx).coerceIn(minOf(2, maxCols), maxCols)
    }

    // Builds an adaptive grid of preset cards and notifies onPresetCommitted when user clicks a preset.
    fun buildPresetCards(
        ctx: Context,
        settings: AppSettings,
        onLiveRefresh: () -> Unit,
        onPresetCommitted: (MoodPreset) -> Unit = {}
    ): LinearLayout {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        var committedTheme = settings.tileColor
        var committedAccent = settings.accentColor
        var committedAccentName = settings.accentName
        var committedMotion = settings.motionStyle
        var committedScene = settings.liveWallpaperMode

        val cardUpdaters = CopyOnWriteArrayList<() -> Unit>()
        val refreshAllCards: () -> Unit = {
            for (updater in cardUpdaters) updater()
        }
        settings.addStyleListener(refreshAllCards)

        val cols = computeDynamicColumns(ctx, settings.panelWidthPct, minCardWidthDp = 118, maxCols = 2)
        val hGap = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 5, maxDp = 12, horizontal = true)
        val vGap = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 5, maxDp = 10, horizontal = false)
        val cardPadH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 6, maxDp = 12, horizontal = true)
        val cardPadV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.009f, minDp = 6, maxDp = 12, horizontal = false)
        val previewH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.042f, minDp = 26, maxDp = 38, horizontal = false)

        val rows = PRESETS.chunked(cols)
        rows.forEachIndexed { rowIdx, rowItems ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }

            rowItems.forEachIndexed { colIdx, preset ->
                val presetAccent = Themes.getAccentByName(preset.accentKey)
                val presetAccentSpec = Themes.getAccentSpec(presetAccent, preset.accentKey)
                val presetScene = LiveBackdrop.getScene(preset.sceneKey)

                val miniPreview = object : View(ctx) {
                    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    private val stripPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    private val rect = RectF()

                    override fun onDraw(canvas: Canvas) {
                        val w = width.toFloat()
                        val h = height.toFloat()
                        if (w <= 0f || h <= 0f) return
                        val r = LayoutMetrics.dpF(context, 7f)
                        rect.set(0f, 0f, w, h)

                        bgPaint.shader = LinearGradient(
                            0f, 0f, w, h,
                            presetScene.skyColors, null, Shader.TileMode.CLAMP
                        )
                        canvas.drawRoundRect(rect, r, r, bgPaint)

                        val stripH = h * 0.24f
                        stripPaint.shader = LinearGradient(
                            0f, h - stripH, w, h,
                            presetAccentSpec.gradientColors, null, Shader.TileMode.CLAMP
                        )
                        canvas.drawRoundRect(RectF(0f, h - stripH, w, h), r, r, stripPaint)

                        labelPaint.textSize = LayoutMetrics.dpF(context, 8.6f)
                        val ty = (h * 0.40f) - ((labelPaint.descent() + labelPaint.ascent()) / 2f)
                        canvas.drawText(presetScene.name, w / 2f, ty, labelPaint)
                    }
                }

                val titleTv = TextView(ctx).apply {
                    text = "${preset.icon} ${preset.title}"
                    textSize = 9.6f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    isSingleLine = true
                }

                val card = LinearLayout(ctx).apply {
                    tag = "preset_${preset.key}"
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
                    isFocusable = true
                    isClickable = true
                    addView(miniPreview, LinearLayout.LayoutParams(-1, previewH).apply {
                        bottomMargin = vGap / 2
                    })
                    addView(titleTv)
                }

                val isThisPresetActive = {
                    settings.tileColor == preset.tileTheme &&
                        settings.accentName.equals(preset.accentKey, ignoreCase = true) &&
                        settings.motionStyle == preset.motionKey &&
                        settings.liveWallpaperMode == preset.sceneKey
                }

                val syncCard: () -> Unit = {
                    val active = card.hasFocus() || isThisPresetActive()
                    card.background = PanelSurfaceKit.glassCardBg(
                        ctx,
                        settings.accentColor,
                        active,
                        settings.tileColor
                    )
                    titleTv.setTextColor(Themes.getPrimaryTextColor(settings.tileColor))
                    miniPreview.invalidate()
                }
                cardUpdaters.add(syncCard)
                syncCard()

                // Preview on focus; restore committed values if focus exits without clicking.
                card.setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 4f)
                    if (f) {
                        applyPreset(settings, preset)
                        onLiveRefresh()
                    } else if (
                        settings.liveWallpaperMode != committedScene ||
                        settings.tileColor != committedTheme ||
                        settings.accentColor != committedAccent ||
                        settings.motionStyle != committedMotion
                    ) {
                        settings.tileColor = committedTheme
                        settings.accentColor = committedAccent
                        settings.accentName = committedAccentName
                        settings.motionStyle = committedMotion
                        settings.liveWallpaperMode = committedScene
                        onLiveRefresh()
                    }
                    syncCard()
                }

                // Permanently apply all preset properties and sync every panel & trigger card.
                card.setOnClickListener {
                    applyPreset(settings, preset)
                    committedTheme = settings.tileColor
                    committedAccent = settings.accentColor
                    committedAccentName = settings.accentName
                    committedMotion = settings.motionStyle
                    committedScene = settings.liveWallpaperMode
                    onPresetCommitted(preset)
                    onLiveRefresh()
                    refreshAllCards()
                    Toast.makeText(ctx, "${preset.title} Mood Applied", Toast.LENGTH_SHORT).show()
                }

                card.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                        override fun onViewDetachedFromWindow(v: View) {
                        cardUpdaters.remove(syncCard)
                        settings.removeStyleListener(refreshAllCards)
                    }
                    })
                row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (colIdx < rowItems.lastIndex) rightMargin = hGap
                })
            }
            container.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            if (rowIdx < rows.lastIndex) bottomMargin = vGap
            })
        }
    return container
}
    
// Applies all visual tokens defined by a MoodPreset to AppSettings.
private fun applyPreset(settings: AppSettings, preset: MoodPreset) {
val accentSpec = Themes.getAccentSpec(Themes.getAccentByName(preset.accentKey), preset.accentKey)
settings.prefs.edit().remove(AppSettings.PREF_WALLPAPER).apply()
settings.liveWallpaperMode = preset.sceneKey
settings.motionStyle = preset.motionKey
settings.accentName = accentSpec.label
settings.accentColor = accentSpec.color
settings.tileColor = preset.tileTheme
}
}
