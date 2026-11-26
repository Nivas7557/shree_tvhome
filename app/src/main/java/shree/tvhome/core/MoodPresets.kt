// Important instructions:
// - Define 4 fresh mood presets in one place with live mini visual previews on each card.
// - Show live preview on focus without committing until clicked; restore previous state if focus leaves without click.

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

    // Four fresh mood presets; add/edit/remove here to update cards automatically.
    val PRESETS: List<MoodPreset> = listOf(
        MoodPreset("velvet_cinema", "🎬", "Velvet Cinema", "dark", "ember", "silk", LiveBackdrop.MODE_SUNSET),
        MoodPreset("neon_pulse", "⚡", "Neon Pulse", "cyber", "orchid", "jelly", LiveBackdrop.MODE_PASTEL),
        MoodPreset("emerald_zen", "🌿", "Emerald Zen", "emerald", "meadow", "levitate", LiveBackdrop.MODE_MORNING),
        MoodPreset("solar_horizon", "🌅", "Solar Horizon", "ocean", "solar", "flip3d", LiveBackdrop.MODE_AQUA)
    )

    // Builds a 2x2 dynamic grid of preset cards with mini live previews and zero hardcoded paddings.
    fun buildPresetCards(
        ctx: Context,
        settings: AppSettings,
        onLiveRefresh: () -> Unit
    ): LinearLayout {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val committedTheme = settings.tileColor
        val committedAccent = settings.accentColor
        val committedAccentName = settings.accentName
        val committedMotion = settings.motionStyle
        val committedScene = settings.liveWallpaperMode

        val cardUpdaters = CopyOnWriteArrayList<() -> Unit>()
        val refreshAllCards: () -> Unit = {
            for (updater in cardUpdaters) updater()
        }
        settings.addStyleListener(refreshAllCards)

        val hGap = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 4, maxDp = 12, horizontal = true)
        val vGap = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 4, maxDp = 10, horizontal = false)
        val cardPadH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.007f, minDp = 4, maxDp = 10, horizontal = true)
        val cardPadV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 5, maxDp = 10, horizontal = false)
        val previewH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.036f, minDp = 22, maxDp = 34, horizontal = false)

        PRESETS.chunked(2).forEachIndexed { rowIdx, pair ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }

            pair.forEachIndexed { colIdx, preset ->
                val presetAccent = Themes.getAccentByName(preset.accentKey)
                val presetAccentSpec = Themes.getAccentSpec(presetAccent, preset.accentKey)
                val presetScene = LiveBackdrop.getScene(preset.sceneKey)

                // Mini preview drawn directly on the preset card showing sky scene and gradient accent strip.
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
                        val r = LayoutMetrics.dpF(context, 6f)
                        rect.set(0f, 0f, w, h)

                        bgPaint.shader = LinearGradient(
                            0f, 0f, w, h,
                            presetScene.skyColors, null, Shader.TileMode.CLAMP
                        )
                        canvas.drawRoundRect(rect, r, r, bgPaint)

                        val stripH = h * 0.25f
                        stripPaint.shader = LinearGradient(
                            0f, h - stripH, w, h,
                            presetAccentSpec.gradientColors, null, Shader.TileMode.CLAMP
                        )
                        canvas.drawRoundRect(RectF(0f, h - stripH, w, h), r, r, stripPaint)

                        labelPaint.textSize = LayoutMetrics.dpF(context, 8.2f)
                        val ty = (h * 0.40f) - ((labelPaint.descent() + labelPaint.ascent()) / 2f)
                        canvas.drawText(presetScene.name, w / 2f, ty, labelPaint)
                    }
                }

                val titleTv = TextView(ctx).apply {
                    text = "${preset.icon} ${preset.title}"
                    textSize = 9.2f
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

                val syncCard: () -> Unit = {
                    val active = card.hasFocus()
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

                // Preview on focus without saving; revert if focus leaves without click/OK.
                card.setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 4f)
                    if (f) {
                        applyPreset(settings, preset)
                        onLiveRefresh()
                    } else if (settings.liveWallpaperMode != committedScene || settings.tileColor != committedTheme) {
                        settings.tileColor = committedTheme
                        settings.accentColor = committedAccent
                        settings.accentName = committedAccentName
                        settings.motionStyle = committedMotion
                        settings.liveWallpaperMode = committedScene
                        onLiveRefresh()
                    }
                    syncCard()
                }

                // Commit preset permanently only on click/OK.
                card.setOnClickListener {
                    applyPreset(settings, preset)
                    onLiveRefresh()
                    refreshAllCards()
                    Toast.makeText(ctx, "${preset.title} Mood Set", Toast.LENGTH_SHORT).show()
                }

                card.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                    override fun onViewDetachedFromWindow(v: View) {
                        cardUpdaters.remove(syncCard)
                    }
                })

                row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (colIdx < pair.lastIndex) rightMargin = hGap
                })
            }

            container.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                if (rowIdx < PRESETS.chunked(2).lastIndex) bottomMargin = vGap
            })
        }

        return container
    }

    private fun applyPreset(settings: AppSettings, preset: MoodPreset) {
        settings.tileColor = preset.tileTheme
        settings.accentColor = Themes.getAccentByName(preset.accentKey)
        settings.accentName = preset.accentKey
        settings.motionStyle = preset.motionKey
        settings.prefs.edit().remove(AppSettings.PREF_WALLPAPER).apply()
        settings.liveWallpaperMode = preset.sceneKey
    }
}
