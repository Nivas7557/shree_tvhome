// Important instructions:
// - Never call removeAllViews() inside focus or style listeners; update existing cards in-place to prevent focus crashes.
// - Unregister style listeners on detach and revert unconfirmed focus previews cleanly.

package shree.tvhome.panels

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import shree.tvhome.PanelHub
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.MoodPresets
import shree.tvhome.core.Themes

object StyleCustomizePanel {

    // Builds the priority-ordered Customize panel with safe in-place focus previews and zero view-tree destruction.
    fun build(
        ctx: Context,
        panel: LinearLayout,
        settings: AppSettings,
        onLiveRefresh: () -> Unit,
        onRefreshPanel: () -> Unit,
        onEditProfile: () -> Unit,
        onOpenWallpaper: (String) -> Unit,
        onOpenHidden: () -> Unit,
        onOpenClockSettings: () -> Unit,
        onCloseDrawer: () -> Unit
    ) {
        panel.clipChildren = false
        panel.clipToPadding = false

        val gapH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 4, maxDp = 12, horizontal = true)
        val gapV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 4, maxDp = 10, horizontal = false)
        val tileH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.072f, minDp = 46, maxDp = 62, horizontal = false)
        val rowH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.052f, minDp = 32, maxDp = 44, horizontal = false)

        var savedAccent = settings.accentColor
        var savedAccentName = settings.accentName
        var savedTheme = settings.tileColor
        var savedMotion = settings.motionStyle
        val savedTileSize = settings.tileSize
        val savedTileShape = settings.tileShape
        val savedNameplate = settings.nameplateMode
        val savedPanelWidth = settings.panelWidthPct

        // 1. Top Priority: User Settings in 2-column pairs.
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "USER SETTINGS")

        val rowOne = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        rowOne.addView(
            makeActionTile(ctx, settings, "👤 ", settings.userName, "Profile & Avatar") { onEditProfile() },
            LinearLayout.LayoutParams(0, tileH, 1f).apply { rightMargin = gapH }
        )
        val hiddenCount = settings.getHiddenApps().size
        rowOne.addView(
            makeActionTile(ctx, settings, "👁 ", "Hidden Apps", "$hiddenCount") {
                onCloseDrawer()
                onOpenHidden()
            },
            LinearLayout.LayoutParams(0, tileH, 1f)
        )
        panel.addView(rowOne, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        val rowTwo = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        val greetSub = settings.greetingPhrase.ifBlank { "Auto" }
        rowTwo.addView(
            makeActionTile(ctx, settings, "💬 ", "Greeting", greetSub) {
                showGreetingDialog(ctx, settings, onLiveRefresh, onRefreshPanel)
            },
            LinearLayout.LayoutParams(0, tileH, 1f).apply { rightMargin = gapH }
        )
        val wpTile = makeActionTile(ctx, settings, "🖼 ", "Wallpaper", settings.liveWallpaperMode) { v ->
            val tag = (v.tag as? String) ?: PanelHub.TAG_WALLPAPER_TRIGGER
            onOpenWallpaper(tag)
        }.apply {
            tag = PanelHub.TAG_WALLPAPER_TRIGGER
        }
        rowTwo.addView(wpTile, LinearLayout.LayoutParams(0, tileH, 1f))
        panel.addView(rowTwo, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        // 2. Second Priority: 4 Mood Preset cards with mini previews.
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "MOOD PRESETS")
        val moodBlock = MoodPresets.buildPresetCards(ctx, settings, onLiveRefresh)
        panel.addView(moodBlock, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        // 3. Third Priority: Single Clock Widget Settings button opening dedicated preview panel.
        panel.addView(
            PanelSurfaceKit.makeActionRow(
                ctx, settings, "🕰️", "Clock Widget Settings", "Styles, location & live preview"
            ) {
                onOpenClockSettings()
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV }
        )

        // 4. Visual & Layout Controls.
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "APP NAMEPLATES")
        PanelSurfaceKit.addSegmentedTrack(
            ctx, panel, settings,
            options = listOf("always" to "Always", "focus" to "On Focus", "hidden" to "Hidden"),
            getSelected = { settings.nameplateMode },
            onSelect = { key ->
                settings.nameplateMode = key
                onLiveRefresh()
            }
        )

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "TILE SIZE")
        PanelSurfaceKit.addSegmentedTrack(
            ctx, panel, settings,
            options = Themes.TILE_SIZES.map { it.key to it.label },
            getSelected = { settings.tileSize },
            onSelect = { key ->
                settings.tileSize = key
                onLiveRefresh()
            }
        )

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "STAGE & PANEL WIDTH")
        PanelSurfaceKit.addSegmentedTrack(
            ctx, panel, settings,
            options = Themes.PANEL_WIDTH_OPTIONS.map { pct -> pct.toString() to "$pct%" },
            getSelected = { settings.panelWidthPct.toString() },
            onSelect = { key ->
                settings.panelWidthPct = key.toIntOrNull() ?: Themes.PANEL_WIDTH_OPTIONS.first()
                onLiveRefresh()
            }
        )

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "ICON SHAPE")
        PanelSurfaceKit.addSegmentedTrack(
            ctx, panel, settings,
            options = Themes.TILE_SHAPES.map { it.key to it.label },
            getSelected = { settings.tileShape },
            onSelect = { key ->
                settings.tileShape = key
                onLiveRefresh()
            }
        )

        // 5. Global Motion Style (built once; updated in-place on focus/click so focus never drops or crashes).
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "GLOBAL MOTION STYLE")
        val motionButtons = ArrayList<Pair<String, TextView>>()
        val syncMotionCards: () -> Unit = {
            motionButtons.forEach { (key, btn) ->
                val active = btn.hasFocus() || settings.motionStyle == key
                btn.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, active, settings.tileColor)
                btn.setTextColor(
                    if (active) Themes.getAdaptiveTextColor(intArrayOf(settings.accentColor))
                    else Themes.getPrimaryTextColor(settings.tileColor)
                )
            }
        }

        AnimationController.STYLES.chunked(2).forEach { rowChunk ->
            val r = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            rowChunk.forEachIndexed { idx, spec ->
                val btn = TextView(ctx).apply {
                    tag = "motion_${spec.key}"
                    text = spec.label
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    isFocusable = true
                    isClickable = true
                    setOnFocusChangeListener { v, f ->
                        AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 3f)
                        if (f) {
                            settings.motionStyle = spec.key
                            val hostPanel = (panel.parent?.parent as? View) ?: panel
                            AnimationController.playDemoPreview(hostPanel, spec.key)
                        } else if (settings.motionStyle != savedMotion) {
                            settings.motionStyle = savedMotion
                        }
                        syncMotionCards()
                    }
                    setOnClickListener {
                        settings.motionStyle = spec.key
                        savedMotion = spec.key
                        syncMotionCards()
                        Toast.makeText(ctx, "${spec.label} Motion Set", Toast.LENGTH_SHORT).show()
                    }
                }
                motionButtons.add(spec.key to btn)
                r.addView(btn, LinearLayout.LayoutParams(0, rowH, 1f).apply {
                    if (idx < rowChunk.lastIndex) rightMargin = gapH
                })
            }
            panel.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
        }
        syncMotionCards()
        // 6. Gradient Accents (built once; invalidated in-place so focus never triggers removeAllViews()).
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "GRADIENT ACCENTS")
        val accentCards = ArrayList<LinearLayout>()
        val syncAccentCards: () -> Unit = {
            accentCards.forEach { it.invalidate() }
        }

        Themes.ACCENTS.chunked(2).forEach { pair ->
            val r = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            pair.forEachIndexed { idx, spec ->
                val card = object : LinearLayout(ctx) {
                    private val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
                    private val rect = RectF()

                    override fun onDraw(canvas: Canvas) {
                        val w = width.toFloat()
                        val h = height.toFloat()
                        if (w <= 0f || h <= 0f) return
                        val pad = LayoutMetrics.dpF(context, 1.5f)
                        rect.set(pad, pad, w - pad, h - pad)
                        gradPaint.shader = LinearGradient(
                            pad, pad, w - pad, h - pad,
                            spec.gradientColors, null, Shader.TileMode.CLAMP
                        )
                        val rad = LayoutMetrics.dpF(context, 12f)
                        canvas.drawRoundRect(rect, rad, rad, gradPaint)

                        val active = hasFocus() || settings.accentColor == spec.color
                        ringPaint.strokeWidth = LayoutMetrics.dpF(context, if (active) 2.2f else 1f)
                        ringPaint.color = if (active) Color.WHITE else Color.argb(110, 255, 255, 255)
                        canvas.drawRoundRect(rect, rad, rad, ringPaint)
                        super.onDraw(canvas)
                    }
                }.apply {
                    tag = "accent_${spec.key}"
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setWillNotDraw(false)
                    isFocusable = true
                    isClickable = true
                    addView(TextView(ctx).apply {
                        text = spec.label
                        textSize = 9.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                        setShadowLayer(4f, 0f, 1f, Color.argb(160, 0, 0, 0))
                    })
                    setOnFocusChangeListener { v, f ->
                        AnimationController.animateFocus(v, f, settings.motionStyle, 1.05f, 4f)
                        if (f) {
                            settings.accentColor = spec.color
                            settings.accentName = spec.label
                        } else if (settings.accentColor != savedAccent) {
                            settings.accentColor = savedAccent
                            settings.accentName = savedAccentName
                        }
                        syncAccentCards()
                    }
                    setOnClickListener {
                        settings.accentColor = spec.color
                        settings.accentName = spec.label
                        savedAccent = spec.color
                        savedAccentName = spec.label
                        syncAccentCards()
                        Toast.makeText(ctx, "${spec.label} Gradient Set", Toast.LENGTH_SHORT).show()
                    }
                }
                accentCards.add(card)
                r.addView(card, LinearLayout.LayoutParams(0, rowH, 1f).apply {
                    if (idx < pair.lastIndex) rightMargin = gapH
                })
            }
            panel.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
        }

        // 7. Tile Glass Palette (built once; updated in-place without view recreation).
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "TILE GLASS PALETTE")
        val themeEntries = ArrayList<Triple<String, LinearLayout, TextView>>()
        val syncThemeCards: () -> Unit = {
            themeEntries.forEach { (key, card, labelTv) ->
                val active = card.hasFocus() || settings.tileColor == key
                card.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, active, settings.tileColor)
                labelTv.setTextColor(
                    if (active) Themes.getAdaptiveTextColor(intArrayOf(settings.accentColor))
                    else Themes.getPrimaryTextColor(settings.tileColor)
                )
            }
        }

        Themes.THEMES.chunked(2).forEach { rowChunk ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            rowChunk.forEachIndexed { idx, theme ->
                val isSel = settings.tileColor == theme.key
                val labelTv = TextView(ctx).apply {
                    text = theme.name
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    isSingleLine = true
                }
                val orbSize = LayoutMetrics.dynamicSpace(ctx, ratio = 0.02f, minDp = 16, maxDp = 22, horizontal = true)
                val card = LinearLayout(ctx).apply {
                    tag = "theme_${theme.key}"
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(gapH, gapV, gapH, gapV)
                    isFocusable = true
                    isClickable = true
                    setOnFocusChangeListener { v, f ->
                        AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 4f)
                        if (f) {
                            settings.tileColor = theme.key
                        } else if (settings.tileColor != savedTheme) {
                            settings.tileColor = savedTheme
                        }
                        syncThemeCards()
                    }
                    setOnClickListener {
                        settings.tileColor = theme.key
                        savedTheme = theme.key
                        syncThemeCards()
                        Toast.makeText(ctx, "${theme.name} Palette Set", Toast.LENGTH_SHORT).show()
                    }
                    addView(
                        PanelSurfaceKit.createThemeOrbView(ctx, theme.focusedColors, isSel),
                        LinearLayout.LayoutParams(orbSize, orbSize).apply { rightMargin = gapH }
                    )
                    addView(labelTv)
                }
                themeEntries.add(Triple(theme.key, card, labelTv))
                row.addView(card, LinearLayout.LayoutParams(0, rowH, 1f).apply {
                    if (idx < rowChunk.lastIndex) rightMargin = gapH
                })
            }
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
        }
        syncThemeCards()

        val unifiedStyleListener: () -> Unit = {
            syncMotionCards()
            syncAccentCards()
            syncThemeCards()
        }
        settings.addStyleListener(unifiedStyleListener)

        // Clean up style listener and revert unconfirmed preview changes on detach.
        panel.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                settings.removeStyleListener(unifiedStyleListener)
                var restored = false
                if (settings.accentColor != savedAccent) {
                    settings.accentColor = savedAccent
                    settings.accentName = savedAccentName
                    restored = true
                }
                if (settings.tileColor != savedTheme) {
                    settings.tileColor = savedTheme
                    restored = true
                }
                if (settings.motionStyle != savedMotion) {
                    settings.motionStyle = savedMotion
                    restored = true
                }
                if (settings.tileSize != savedTileSize) {
                    settings.tileSize = savedTileSize
                    restored = true
                }
                if (settings.tileShape != savedTileShape) {
                    settings.tileShape = savedTileShape
                    restored = true
                }
                if (settings.nameplateMode != savedNameplate) {
                    settings.nameplateMode = savedNameplate
                    restored = true
                }
                if (settings.panelWidthPct != savedPanelWidth) {
                    settings.panelWidthPct = savedPanelWidth
                    restored = true
                }
                if (restored) onLiveRefresh()
            }
        })
    }

    private fun showGreetingDialog(
        ctx: Context,
        settings: AppSettings,
        onLiveRefresh: () -> Unit,
        onRefreshPanel: () -> Unit
    ) {
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val padH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.022f, minDp = 16, maxDp = 28, horizontal = true)
        val padV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.024f, minDp = 14, maxDp = 24, horizontal = false)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable(
GradientDrawable.Orientation.TL_BR,
intArrayOf(Color.rgb(20, 32, 62), Color.rgb(12, 18, 38))
).apply {
cornerRadius = LayoutMetrics.dpF(ctx, 20f)
setStroke(LayoutMetrics.dp(ctx, 1), Color.argb(120, 255, 255, 255))
}
}
box.addView(TextView(ctx).apply {
text = "Custom Greeting Phrase"
textSize = 15f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
setPadding(0, 0, 0, padV / 2)
})
val input = EditText(ctx).apply {
isSingleLine = true
hint = "Custom phrase"
setText(settings.greetingPhrase)
setTextColor(Color.WHITE)
setHintTextColor(Color.argb(150, 200, 220, 245))
setPadding(padH / 2, padV / 2, padH / 2, padV / 2)
inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
background = GradientDrawable().apply {
setColor(Color.argb(55, 255, 255, 255))
cornerRadius = LayoutMetrics.dpF(ctx, 12f)
setStroke(LayoutMetrics.dp(ctx, 1), settings.accentColor)
}
}
val inputW = LayoutMetrics.dynamicSpace(ctx, ratio = 0.32f, minDp = 240, maxDp = 420, horizontal = true)
box.addView(input, LinearLayout.LayoutParams(inputW, -2))
val btnH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.056f, minDp = 36, maxDp = 48, horizontal = false)
val btnGap = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 6, maxDp = 12, horizontal = true)
val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
btnRow.addView(
makeDialogButton(ctx, settings, "↺  Auto", false) {
settings.greetingPhrase = ""
onLiveRefresh()
onRefreshPanel()
dialog.dismiss()
},
LinearLayout.LayoutParams(0, btnH, 1f).apply { rightMargin = btnGap }
)
btnRow.addView(
makeDialogButton(ctx, settings, "✓  Save", true) {
settings.greetingPhrase = input.text.toString().trim()
onLiveRefresh()
onRefreshPanel()
dialog.dismiss()
},
LinearLayout.LayoutParams(0, btnH, 1f)
)
box.addView(btnRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = padV / 2 })
dialog.setContentView(box)
dialog.window?.apply {
setBackgroundDrawableResource(android.R.color.transparent)
clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
}
dialog.show()
}
private fun makeDialogButton(
ctx: Context,
settings: AppSettings,
label: String,
highlight: Boolean,
onClick: () -> Unit
) = TextView(ctx).apply {
text = label
textSize = 10.5f
typeface = Typeface.DEFAULT_BOLD
setTextColor(Color.WHITE)
gravity = Gravity.CENTER
isFocusable = true
isClickable = true
background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, highlight, settings.tileColor)
setOnFocusChangeListener { v, f ->
AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 4f)
v.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, f || highlight, settings.tileColor)
}
setOnClickListener { onClick() }
}
private fun makeActionTile(
ctx: Context,
settings: AppSettings,
icon: String,
title: String,
sub: String,
onClick: (View) -> Unit
) = LinearLayout(ctx).apply {
orientation = LinearLayout.VERTICAL
gravity = Gravity.CENTER_VERTICAL
val padH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.01f, minDp = 8, maxDp = 14, horizontal = true)
val padV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 5, maxDp = 10, horizontal = false)
setPadding(padH, padV, padH, padV)
isFocusable = true
isClickable = true
val titleTv = TextView(ctx).apply {
text = "$icon$title"
textSize = 10f
typeface = Typeface.DEFAULT_BOLD
isSingleLine = true
}
val subTv = TextView(ctx).apply {
text = sub
textSize = 8.2f
isSingleLine = true
}
val syncColors = {
background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, hasFocus(), settings.tileColor)
titleTv.setTextColor(Themes.getPrimaryTextColor(settings.tileColor))
val surfaceColors = Themes.getTheme(settings.tileColor).normalColors
subTv.setTextColor(
if (Themes.isLightSurface(surfaceColors)) {
Themes.getAdaptiveTextColor(surfaceColors, secondary = true)
} else {
settings.accentColor
}
)
}
syncColors()
settings.addStyleListener(syncColors)
addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
override fun onViewAttachedToWindow(v: View) {}
override fun onViewDetachedFromWindow(v: View) {
settings.removeStyleListener(syncColors)
}
})
setOnFocusChangeListener { v, f ->
AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 4f)
syncColors()
}
setOnClickListener { v -> onClick(v) }
addView(titleTv)
addView(subTv)
}
}
