// Important instructions:
// - Act as the single source of truth for mood presets and their card rendering/updates.
// - Adding, removing, or editing presets here updates customization cards automatically without hardcoding in panels.

package shree.tvhome.core

import android.content.Context
import android.graphics.Color
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

    // Central list of mood presets; modify this list to add, edit, or remove presets everywhere.
    val PRESETS: List<MoodPreset> = listOf(
        MoodPreset("cinema", "🎬", "Cinema", "dark", "sunset", "silk", LiveBackdrop.MODE_SUNSET),
        MoodPreset("cyber", "⚡", "Cyber", "cyber", "magenta", "jelly", LiveBackdrop.MODE_PASTEL),
        MoodPreset("crystal", "💎", "Crystal", "crystal", "royal", "levitate", LiveBackdrop.MODE_MORNING)
    )

    // Builds preset cards dynamically from PRESETS and syncs them automatically on style changes.
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

        // Refresh all preset cards whenever accent or glass theme changes.
        val refreshAllCards: () -> Unit = {
            for (updater in cardUpdaters) updater()
        }
        settings.addStyleListener(refreshAllCards)

        PRESETS.forEachIndexed { idx, preset ->
            val iconTv = TextView(ctx).apply {
                text = preset.icon
                textSize = 14f
                gravity = Gravity.CENTER
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
                    LayoutMetrics.dp(ctx, 8),
                    LayoutMetrics.dp(ctx, 6),
                    LayoutMetrics.dp(ctx, 8)
                )
                isFocusable = true
                isClickable = true
                addView(iconTv)
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

            // Animate focus and keep visual styling synchronized with the active theme.
            card.setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 4f)
                syncSingleCard()
            }

            // Apply preset values and keep focus on this card so focus never jumps to TopHeaderBar.
            card.setOnClickListener { v ->
                applyPreset(settings, preset)
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
    private fun applyPreset(settings: AppSettings, preset: MoodPreset) {
        settings.tileColor = preset.tileTheme
        settings.accentColor = Themes.getAccentByName(preset.accentKey)
        settings.motionStyle = preset.motionKey
        settings.prefs.edit().remove(AppSettings.PREF_WALLPAPER).apply()
        settings.liveWallpaperMode = preset.sceneKey
    }
}
