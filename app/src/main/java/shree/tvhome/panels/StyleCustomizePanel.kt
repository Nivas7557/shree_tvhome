// Important instructions:
// - Order customization sections by priority: User Settings first (2-column pairs), then Mood Presets with card previews,
//   then a single "Clock Widget Settings" button, followed by visual options with focus live-preview and click-to-set.
// - Render natural multi-stop gradient accents from Themes.ACCENTS.

package shree.tvhome.panels

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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

    // Builds the priority-ordered customization panel with 2-column user rows, preset preview cards, and widget button.
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
        // Snapshot committed values so focus-only previews can show live changes and commit only on click.
        val savedAccent = settings.accentColor
        val savedAccentName = settings.accentName
        val savedTheme = settings.tileColor
        val savedMotion = settings.motionStyle
        val savedTileSize = settings.tileSize
        val savedTileShape = settings.tileShape
        val savedNameplate = settings.nameplateMode
        val savedPanelWidth = settings.panelWidthPct

        // 1. Priority First: User Settings in 2-column rows.
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "USER SETTINGS")

        val accountRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        accountRow.addView(
            makeActionTile(ctx, settings, "👤 ", settings.userName, "Profile & Avatar") { onEditProfile() },
            LinearLayout.LayoutParams(0, dp(ctx, 52), 1f).apply { rightMargin = dp(ctx, 6) }
        )
        val hiddenCount = settings.getHiddenApps().size
        accountRow.addView(
            makeActionTile(ctx, settings, "👁 ", "Hidden Apps", "$hiddenCount") {
                onCloseDrawer()
                onOpenHidden()
            },
            LinearLayout.LayoutParams(0, dp(ctx, 52), 1f)
        )
        panel.addView(accountRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })

        val personalTwoCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        val greetSub = settings.greetingPhrase.ifBlank { "Auto" }
        personalTwoCol.addView(
            makeActionTile(ctx, settings, "💬 ", "Greeting", greetSub) {
                showGreetingDialog(ctx, settings, onLiveRefresh, onRefreshPanel)
            },
            LinearLayout.LayoutParams(0, dp(ctx, 52), 1f).apply { rightMargin = dp(ctx, 6) }
        )
        val wpTile = makeActionTile(ctx, settings, "🖼 ", "Wallpaper", settings.liveWallpaperMode) { v ->
            val tag = (v.tag as? String) ?: PanelHub.TAG_WALLPAPER_TRIGGER
            onOpenWallpaper(tag)
        }.apply {
            tag = PanelHub.TAG_WALLPAPER_TRIGGER
        }
        personalTwoCol.addView(
            wpTile,
            LinearLayout.LayoutParams(0, dp(ctx, 52), 1f)
        )
        panel.addView(personalTwoCol, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })

        // 2. Priority Second: Mood Presets as visual preview cards.
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "MOOD PRESETS")
        val moodRow = MoodPresets.buildPresetCards(ctx, settings, onLiveRefresh)
        panel.addView(moodRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })

        // 3. Priority Third: Single Clock Widget Settings button opening dedicated panel with live preview.
        panel.addView(
            PanelSurfaceKit.makeActionRow(
                ctx, settings, "🕰️", "Clock Widget Settings", "Styles, location & live preview"
            ) {
                onOpenClockSettings()
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) }
        )

        // 4. Visual & Layout Options: Preview on focus, set permanently on click/OK.
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

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "GLOBAL MOTION STYLE")
        val motionContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val renderMotionGrid: () -> Unit = {
            val focusedKey = motionContainer.findFocus()?.tag as? String
            motionContainer.removeAllViews()
            AnimationController.STYLES.chunked(4).forEach { rowChunk ->
                val r = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                    clipToPadding = false
                }
                rowChunk.forEachIndexed { idx, spec ->
                    val btnTag = "motion_${spec.key}"
                    val isSel = settings.motionStyle == spec.key
                    val btn = TextView(ctx).apply {
                        tag = btnTag
                        text = spec.label
                        textSize = 9.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        isFocusable = true
                        isClickable = true
                        background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, isSel, settings.tileColor)
                        setOnFocusChangeListener { v, f ->
                            AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 3f)
                            if (f) {
                                settings.motionStyle = spec.key
                                val hostPanel = (panel.parent?.parent as? View) ?: panel
                                AnimationController.playDemoPreview(hostPanel, spec.key)
                            } else if (settings.motionStyle != savedMotion) {
                                settings.motionStyle = savedMotion
                            }
                            v.background = PanelSurfaceKit.glassCardBg(
                                ctx, settings.accentColor, f || settings.motionStyle == spec.key, settings.tileColor
                            )
                        }
                        setOnClickListener { v ->
                            settings.motionStyle = spec.key
                            Toast.makeText(ctx, "${spec.label} Motion Set", Toast.LENGTH_SHORT).show()
                            v.post { if (v.isAttachedToWindow) v.requestFocus() }
                        }
                    }
                    r.addView(btn, LinearLayout.LayoutParams(0, dp(ctx, 34), 1f).apply {
                        if (idx < rowChunk.lastIndex) rightMargin = dp(ctx, 5)
                    })
                }
                motionContainer.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 5) })
            }
            if (focusedKey != null) {
                motionContainer.findViewWithTag<View>(focusedKey)?.requestFocus()
            }
        }
        renderMotionGrid()
        settings.addStyleListener(renderMotionGrid)
        panel.addView(motionContainer, LinearLayout.LayoutParams(-1, -2))

        // Natural multi-stop gradient accents with focus live-preview and click-to-set.
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "GRADIENT ACCENTS")
        val swatchContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        val renderAccentRow: () -> Unit = {
            val focusedTag = swatchContainer.findFocus()?.tag as? String
            swatchContainer.removeAllViews()
            Themes.ACCENTS.forEachIndexed { idx, spec ->
                val dotTag = "accent_${spec.key}"
                val isSelected = settings.accentColor == spec.color
                val dot = object : View(ctx) {
                    private val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
                    private val rect = RectF()

                    override fun onDraw(canvas: Canvas) {
                        val w = width.toFloat()
                        val h = height.toFloat()
                        if (w <= 0f || h <= 0f) return
                        val pad = LayoutMetrics.dpF(context, 2f)
                        rect.set(pad, pad, w - pad, h - pad)
                        gradPaint.shader = android.graphics.LinearGradient(
                            pad, pad, w - pad, h - pad,
                            spec.gradientColors, null, android.graphics.Shader.TileMode.CLAMP
                        )
                        val r = minOf(w, h) / 2f
                        canvas.drawRoundRect(rect, r, r, gradPaint)

                        val active = hasFocus() || isSelected
                        ringPaint.strokeWidth = LayoutMetrics.dpF(context, if (active) 2.5f else 1f)
                        ringPaint.color = if (active) Color.WHITE else Color.argb(110, 255, 255, 255)
                        canvas.drawRoundRect(rect, r, r, ringPaint)
                    }
                }.apply {
                    tag = dotTag
                    contentDescription = spec.label
                    isFocusable = true
                    isClickable = true
                    setOnFocusChangeListener { v, f ->
                        AnimationController.animateFocus(v, f, settings.motionStyle, 1.12f, 5f)
                        if (f) {
                            settings.accentColor = spec.color
                            settings.accentName = spec.label
                        } else if (settings.accentColor != savedAccent) {
                            settings.accentColor = savedAccent
                            settings.accentName = savedAccentName
                        }
                        v.invalidate()
                    }
                    setOnClickListener { v ->
                        settings.accentColor = spec.color
                        settings.accentName = spec.label
                        Toast.makeText(ctx, "${spec.label} Gradient Set", Toast.LENGTH_SHORT).show()
                        v.post { if (v.isAttachedToWindow) v.requestFocus() }
                    }
                }
                swatchContainer.addView(dot, LinearLayout.LayoutParams(0, dp(ctx, 32), 1f).apply {
                    if (idx < Themes.ACCENTS.lastIndex) rightMargin = dp(ctx, 6)
                })
            }
            if (focusedTag != null) {
                swatchContainer.findViewWithTag<View>(focusedTag)?.requestFocus()
            }
        }
        renderAccentRow()
        settings.addStyleListener(renderAccentRow)
        panel.addView(swatchContainer, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })

        // Dynamic Glass Palette cards with focus live-preview and click-to-set.
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "TILE GLASS PALETTE")
        val themeContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val renderThemeGrid: () -> Unit = {
            val focusedTag = themeContainer.findFocus()?.tag as? String
            themeContainer.removeAllViews()
            Themes.THEMES.chunked(2).forEach { rowChunk ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                    clipToPadding = false
                }
                rowChunk.forEachIndexed { idx, theme ->
                    val cardTag = "theme_${theme.key}"
                    val isSel = settings.tileColor == theme.key
                    val labelTv = TextView(ctx).apply {
                        text = theme.name
                        textSize = 9.5f
                        typeface = Typeface.DEFAULT_BOLD
                        isSingleLine = true
                        setTextColor(
                            if (isSel) Themes.getAdaptiveTextColor(intArrayOf(settings.accentColor))
                            else Themes.getPrimaryTextColor(settings.tileColor)
                        )
                    }
                    val card = LinearLayout(ctx).apply {
                        tag = cardTag
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
                        isFocusable = true
                        isClickable = true
                        background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, isSel, settings.tileColor)
                        setOnFocusChangeListener { v, f ->
                            AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 4f)
                            if (f) {
                                settings.tileColor = theme.key
                            } else if (settings.tileColor != savedTheme) {
                                settings.tileColor = savedTheme
                            }
                            val active = f || settings.tileColor == theme.key
                            v.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, active, settings.tileColor)
                            labelTv.setTextColor(
                                if (active) Themes.getAdaptiveTextColor(intArrayOf(settings.accentColor))
                                else Themes.getPrimaryTextColor(settings.tileColor)
                            )
                        }
                        setOnClickListener { v ->
                            settings.tileColor = theme.key
                            Toast.makeText(ctx, "${theme.name} Palette Set", Toast.LENGTH_SHORT).show()
                            v.post { if (v.isAttachedToWindow) v.requestFocus() }
                        }
                        addView(
                            PanelSurfaceKit.createThemeOrbView(ctx, theme.focusedColors, isSel),
                            LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)).apply { rightMargin = dp(ctx, 7) }
                        )
                        addView(labelTv)
                    }
                    row.addView(card, LinearLayout.LayoutParams(0, dp(ctx, 36), 1f).apply {
                        if (idx < rowChunk.lastIndex) rightMargin = dp(ctx, 6)
                    })
                }
                themeContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 5) })
            }
            if (focusedTag != null) {
                themeContainer.findViewWithTag<View>(focusedTag)?.requestFocus()
            }
        }
        renderThemeGrid()
        settings.addStyleListener(renderThemeGrid)
        panel.addView(themeContainer, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 8) })

        // Restore unconfirmed preview changes if panel detaches without clicking an option.
        panel.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
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
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 22), dp(ctx, 18), dp(ctx, 22), dp(ctx, 18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(20, 32, 62), Color.rgb(12, 18, 38))
            ).apply {
                cornerRadius = LayoutMetrics.dpF(ctx, 20f)
                setStroke(dp(ctx, 1), Color.argb(120, 255, 255, 255))
            }
        }
        box.addView(TextView(ctx).apply {
            text = "Custom Greeting Phrase"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(ctx, 10))
        })
        val input = EditText(ctx).apply {
            isSingleLine = true
            hint = "Custom phrase"
            setText(settings.greetingPhrase)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(150, 200, 220, 245))
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = GradientDrawable().apply {
                setColor(Color.argb(55, 255, 255, 255))
                cornerRadius = LayoutMetrics.dpF(ctx, 12f)
                setStroke(dp(ctx, 1), settings.accentColor)
            }
        }
        box.addView(input, LinearLayout.LayoutParams(dp(ctx, 310), -2))
        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(
            makeDialogButton(ctx, settings, "↺  Auto", false) {
                settings.greetingPhrase = ""
                onLiveRefresh()
                onRefreshPanel()
                dialog.dismiss()
            },
            LinearLayout.LayoutParams(0, dp(ctx, 40), 1f).apply { rightMargin = dp(ctx, 8) }
        )
        btnRow.addView(
            makeDialogButton(ctx, settings, "✓  Save", true) {
                settings.greetingPhrase = input.text.toString().trim()
                onLiveRefresh()
                onRefreshPanel()
                dialog.dismiss()
            },
            LinearLayout.LayoutParams(0, dp(ctx, 40), 1f)
        )
        box.addView(btnRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 12) })
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
        setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6))
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

        setOnFocusChangeListener { v, f ->
            AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 4f)
            syncColors()
        }
        setOnClickListener { v -> onClick(v) }
        addView(titleTv)
        addView(subTv)
    }

    private fun swatchBg(ctx: Context, col: Int, active: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(col)
        setStroke(dp(ctx, if (active) 3 else 1), if (active) Color.WHITE else Color.argb(110, 255, 255, 255))
    }

    private fun dp(ctx: Context, v: Int): Int = LayoutMetrics.dp(ctx, v)
}
