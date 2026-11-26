// Important instructions:
// - Act as the single source of truth for mood presets and mini visual preview cards.
// - Adding, removing, or editing presets here updates customization cards automatically.

package shree.tvhome.core

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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

    // Central list of mood presets; modify this list to add, edit, or remove presets everywhere.
    val PRESETS: List<MoodPreset> = listOf(
        MoodPreset("cinema", "🎬", "Cinema", "dark", "sunset", "silk", LiveBackdrop.MODE_SUNSET),
        MoodPreset("cyber", "⚡", "Cyber", "cyber", "magenta", "jelly", LiveBackdrop.MODE_PASTEL),
        MoodPreset("crystal", "💎", "Crystal", "crystal", "royal", "levitate", LiveBackdrop.MODE_MORNING)
    )

    // Builds preset cards with visual gradient/accent mini-previews and syncs them automatically.
    fun buildPresetCards(
        ctx: Context,
        settings: AppSettings,
        onLiveRefresh: () -> Unit,
        onPreviewPreset: ((MoodPreset?) -> Unit)? = null
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }

        val cardUpdaters = CopyOnWriteArrayList<() -> Unit>()

        // Refresh all preset cards whenever accent or glass theme changes.
        val refreshAllCards: () -> Unit = {
            for (updater in cardUpdaters) updater()
        }
        settings.addStyleListener(refreshAllCards)

        PRESETS.forEachIndexed { idx, preset ->
            val presetTheme = Themes.getTheme(preset.tileTheme)
            val presetAccent = Themes.getAccentByName(preset.accentKey)
            val presetGrad = Themes.getAccentGradientColors(presetAccent)

            // Mini visual preview strip showing the preset's backdrop scene, palette, and gradient accent.
            val previewStrip = FrameLayout(ctx).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        presetTheme.normalColors.firstOrNull() ?: presetAccent,
                        presetGrad.lastOrNull() ?: presetAccent
                    )
                ).apply {
                    cornerRadius = LayoutMetrics.dpF(ctx, 8f)
                    setStroke(LayoutMetrics.dp(ctx, 1), presetAccent)
                }
                addView(
                    TextView(ctx).apply {
                        text = preset.icon
                        textSize = 13f
                        gravity = Gravity.CENTER
                    },
                    FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)
                )
            }

            val titleTv = TextView(ctx).apply {
                text = preset.title
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
                addView(
                    previewStrip,
                    LinearLayout.LayoutParams(-1, LayoutMetrics.dp(ctx, 24)).apply {
                        bottomMargin = LayoutMetrics.dp(ctx, 4)
                    }
                )
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
            }
            cardUpdaters.add(syncSingleCard)
            syncSingleCard()

            // Trigger live preview on focus without committing until clicked; restore on blur if not clicked.
            card.setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 4f)
                syncSingleCard()
                if (f) {
                    onPreviewPreset?.invoke(preset)
                } else {
                    onPreviewPreset?.invoke(null)
                }
            }

            // Commit preset values on click and keep focus on this card.
            card.setOnClickListener { v ->
                applyPreset(settings, preset)
                onPreviewPreset?.invoke(null)
                onLiveRefresh()
                refreshAllCards()
                v.post {
                    if (v.isAttachedToWindow && v.visibility == View.VISIBLE) {
                        v.requestFocus()
                    }
                }
                Toast.makeText(ctx, "${preset.title} Mood Applied", Toast.LENGTH_SHORT).show()
            }

            // Remove card updater when detached so memory stays clean over long usage.
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

    // Applies theme, accent, motion, and live backdrop scene for the chosen preset.
    fun applyPreset(settings: AppSettings, preset: MoodPreset) {
        settings.tileColor = preset.tileTheme
        settings.accentColor = Themes.getAccentByName(preset.accentKey)
        settings.motionStyle = preset.motionKey
        settings.prefs.edit().remove(AppSettings.PREF_WALLPAPER).apply()
        settings.liveWallpaperMode = preset.sceneKey
    }
}
