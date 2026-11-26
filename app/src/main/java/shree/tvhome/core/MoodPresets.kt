// Important instructions:
// - Act as the single source of truth for mood presets and their preview cards.
// - Render a live mini visual preview directly on each preset card and apply only on click/OK.

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

    // Central list of mood presets; add/edit/remove here to update preset cards everywhere.
    val PRESETS: List<MoodPreset> = listOf(
        MoodPreset("cinema", "🎬", "Cinema", "dark", "ember", "silk", LiveBackdrop.MODE_SUNSET),
        MoodPreset("cyber", "⚡", "Cyber", "cyber", "orchid", "jelly", LiveBackdrop.MODE_PASTEL),
        MoodPreset("crystal", "💎", "Crystal", "crystal", "aurora", "levitate", LiveBackdrop.MODE_MORNING)
    )

    // Builds preset cards with an embedded mini visual preview bar on each card.
    fun buildPresetCards(
        ctx: Context,
        settings: AppSettings,
        onLiveRefresh: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }

        val cardUpdaters = CopyOnWriteArrayList<() -> Unit>()
        val refreshAllCards: () -> Unit = {
            for (updater in cardUpdaters) updater()
        }
        settings.addStyleListener(refreshAllCards)

        PRESETS.forEachIndexed { idx, preset ->
            val presetAccent = Themes.getAccentByName(preset.accentKey)
            val presetAccentSpec = Themes.getAccentSpec(presetAccent, preset.accentKey)
            val presetScene = LiveBackdrop.getScene(preset.sceneKey)

            // Mini preview banner drawn directly on the preset card showing scene, accent gradient, and motion tag.
            val miniPreview = object : View(ctx) {
                private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                private val accentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

                    val barH = h * 0.24f
                    accentBarPaint.shader = LinearGradient(
                        0f, h - barH, w, h,
                        presetAccentSpec.gradientColors, null, Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(
                        RectF(0f, h - barH, w, h),
                        r, r, accentBarPaint
                    )

                    textPaint.textSize = LayoutMetrics.dpF(context, 8.2f)
                    val ty = (h * 0.42f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
                    canvas.drawText(presetScene.name, w / 2f, ty, textPaint)
                }
            }

            val titleTv = TextView(ctx).apply {
                text = "${preset.icon} ${preset.title}"
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }

            val card = LinearLayout(ctx).apply {
                tag = "preset_${preset.key}"
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(
                    LayoutMetrics.dp(ctx, 6),
                    LayoutMetrics.dp(ctx, 6),
                    LayoutMetrics.dp(ctx, 6),
                    LayoutMetrics.dp(ctx, 7)
                )
                isFocusable = true
                isClickable = true
                addView(miniPreview, LinearLayout.LayoutParams(-1, LayoutMetrics.dp(ctx, 26)).apply {
                    bottomMargin = LayoutMetrics.dp(ctx, 5)
                })
                addView(titleTv)
            }

            val syncSingleCard: () -> Unit = {
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
            cardUpdaters.add(syncSingleCard)
            syncSingleCard()

            // Show live preview when focus lands on a preset card without saving until clicked.
            card.setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 4f)
                if (f) {
                    applyPreset(settings, preset)
                    onLiveRefresh()
                }
                syncSingleCard()
            }

            // Commit preset on click/OK and keep focus on this card.
            card.setOnClickListener { v ->
                applyPreset(settings, preset)
                onLiveRefresh()
                refreshAllCards()
                v.post {
                    if (v.isAttachedToWindow && v.visibility == View.VISIBLE) {
                        v.requestFocus()
                    }
                }
                Toast.makeText(ctx, "${preset.title} Mood Set", Toast.LENGTH_SHORT).show()
            }

            card.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    cardUpdaters.remove(syncSingleCard)
                }
            })

            row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (idx < PRESETS.lastIndex) rightMargin = LayoutMetrics.dp(ctx, 6)
            })
        }

        return row
    }

    private fun applyPreset(settings: AppSettings, preset: MoodPreset) {
        settings.tileColor = preset.tileTheme
        settings.accentColor = Themes.getAccentByName(preset.accentKey)
        settings.motionStyle = preset.motionKey
        settings.prefs.edit().remove(AppSettings.PREF_WALLPAPER).apply()
        settings.liveWallpaperMode = preset.sceneKey
    }
}
