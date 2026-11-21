package shree.tvhome.panels

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.core.DynamicSectionBuilder
import shree.tvhome.core.GlassSurfaceSpec
import shree.tvhome.source.LauncherPreferences
import shree.tvhome.source.LauncherUtils
import shree.tvhome.source.MotionEngine
import shree.tvhome.source.TileThemePalette
import java.util.Locale

object CustomizeMenuBuilder {

    private var activeSubMenuRow: LinearLayout? = null
    private var activeAnchorButton: View? = null

    fun isSubMenuOpen(): Boolean = activeSubMenuRow != null

    fun closeSubMenuWithAnimation() {
        val row = activeSubMenuRow ?: return
        val anchor = activeAnchorButton
        activeSubMenuRow = null
        activeAnchorButton = null
        row.animate().alpha(0f).scaleY(0.85f).setDuration(180L).withEndAction {
            (row.parent as? LinearLayout)?.removeView(row)
            anchor?.requestFocus()
        }.start()
    }

    fun closeSubMenuAndNavigate(moveDown: Boolean) {
        val row = activeSubMenuRow ?: return
        val anchor = activeAnchorButton
        activeSubMenuRow = null
        activeAnchorButton = null
        (row.parent as? LinearLayout)?.removeView(row)
        anchor?.requestFocus()
    }

    fun build(
        ctx: Context,
        panel: LinearLayout,
        lp: LauncherPreferences,
        onLiveRefresh: () -> Unit,
        onRefreshPanel: () -> Unit,
        onEditProfile: () -> Unit,
        onOpenWallpaper: () -> Unit,
        onOpenHidden: () -> Unit,
        onCloseDrawer: () -> Unit
    ) {
        activeSubMenuRow = null
        activeAnchorButton = null

        val liveSyncListeners = ArrayList<() -> Unit>()
        val triggerInstantSync = {
            onLiveRefresh()
            liveSyncListeners.forEach { it.invoke() }
        }

        val actionHeightPx = dp(ctx, 52)
        val chipHeightPx = dp(ctx, 34)
        val swatchHeightPx = dp(ctx, 32)
        val gapPx = dp(ctx, 6)

        addHeader(ctx, panel, "ACCOUNT & VISIBILITY")
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }

        val profileTile = makeActionTile(ctx, lp, "👤  ", lp.userName.ifBlank { "Shree" }, "Edit Profile & DP") {
            onEditProfile()
        }
        val hiddenCount = lp.getHiddenApps().size
        val hiddenTile = makeActionTile(ctx, lp, "👁  ", "Hidden Apps", "$hiddenCount hidden") {
            onCloseDrawer()
            onOpenHidden()
        }

        val topTiles = listOf(profileTile, hiddenTile)
        topTiles.forEachIndexed { idx, tileView ->
            topRow.addView(
                tileView,
                LinearLayout.LayoutParams(0, actionHeightPx, 1f).apply {
                    if (idx < topTiles.lastIndex) rightMargin = gapPx
                }
            )
        }
        panel.addView(topRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapPx })

        addActionCard(ctx, panel, lp, "🖼", "Wallpaper & Live Effects", "Gallery photos, blur, dim & animations") {
            onOpenWallpaper()
        }

        // 1. TILE SIZE (Purely driven from TileThemePalette.TILE_SIZES)
        if (TileThemePalette.TILE_SIZES.isNotEmpty()) {
            addHeader(ctx, panel, "TILE SIZE")
            addDynamicOptionSection(
                ctx = ctx,
                panel = panel,
                lp = lp,
                items = TileThemePalette.TILE_SIZES.map { it.key to it.label },
                chipHeightPx = chipHeightPx,
                gapPx = gapPx,
                syncListeners = liveSyncListeners,
                getSelectedKey = { lp.tileSize },
                onSelectKey = { key ->
                    lp.tileSize = key
                    triggerInstantSync()
                }
            )
        }

        // 2. STAGE & PANEL WIDTH (Purely driven from TileThemePalette.PANEL_WIDTH_OPTIONS)
        if (TileThemePalette.PANEL_WIDTH_OPTIONS.isNotEmpty()) {
            addHeader(ctx, panel, "STAGE & PANEL WIDTH")
            addDynamicOptionSection(
                ctx = ctx,
                panel = panel,
                lp = lp,
                items = TileThemePalette.PANEL_WIDTH_OPTIONS.map { pct -> pct.toString() to "$pct%" },
                chipHeightPx = chipHeightPx,
                gapPx = gapPx,
                syncListeners = liveSyncListeners,
                getSelectedKey = { lp.panelWidthPct.toString() },
                onSelectKey = { key ->
                    val parsed = key.toIntOrNull() ?: TileThemePalette.PANEL_WIDTH_OPTIONS.first()
                    lp.panelWidthPct = parsed
                    triggerInstantSync()
                }
            )
        }

        // 3. ICON SHAPE (Purely driven from TileThemePalette.TILE_SHAPES)
        if (TileThemePalette.TILE_SHAPES.isNotEmpty()) {
            addHeader(ctx, panel, "ICON SHAPE")
            addDynamicOptionSection(
                ctx = ctx,
                panel = panel,
                lp = lp,
                items = TileThemePalette.TILE_SHAPES.map { it.key to it.label },
                chipHeightPx = chipHeightPx,
                gapPx = gapPx,
                syncListeners = liveSyncListeners,
                getSelectedKey = { lp.tileShape },
                onSelectKey = { key ->
                    lp.tileShape = key
                    triggerInstantSync()
                }
            )
        }

        // 4. GLOBAL MOTION STYLE (Purely driven from MotionEngine.STYLES)
        if (MotionEngine.STYLES.isNotEmpty()) {
            addHeader(ctx, panel, "GLOBAL MOTION STYLE (TAP TO PREVIEW)")
            val motionButtons = ArrayList<Pair<String, TextView>>()
            val colsPerRow = computeDynamicColumns(MotionEngine.STYLES.size, maxCols = 4)
            MotionEngine.STYLES.chunked(colsPerRow).forEach { rowChunk ->
                val r = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                    clipToPadding = false
                }
                rowChunk.forEachIndexed { idx, spec ->
                    val btn = TextView(ctx).apply {
                        text = spec.label
                        textSize = 9.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        isFocusable = true
                        isClickable = true
                        background = chipBg(ctx, lp.accentColor, lp.motionStyle == spec.key)
                        setOnFocusChangeListener { v, f ->
                            MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 2f)
                            v.background = chipBg(ctx, lp.accentColor, f || lp.motionStyle == spec.key)
                        }
                        setOnClickListener {
                            lp.motionStyle = spec.key
                            val hostPanel = (panel.parent?.parent as? View) ?: panel
                            MotionEngine.playDemoPreview(hostPanel, spec.key)
                            triggerInstantSync()
                        }
                    }
                    motionButtons.add(spec.key to btn)
                    r.addView(
                        btn,
                        LinearLayout.LayoutParams(0, chipHeightPx, 1f).apply {
                            if (idx < rowChunk.lastIndex) rightMargin = gapPx
                        }
                    )
                }
                panel.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapPx })
            }
            liveSyncListeners.add {
                motionButtons.forEach { (k, b) ->
                    b.background = chipBg(ctx, lp.accentColor, b.hasFocus() || k == lp.motionStyle)
                }
            }
        }

        // 5. ACCENT AURA (Purely driven from TileThemePalette.ACCENTS)
        if (TileThemePalette.ACCENTS.isNotEmpty()) {
            addHeader(ctx, panel, "ACCENT AURA")
            val swatchRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            val swatchViews = ArrayList<Pair<Int, View>>()
            TileThemePalette.ACCENTS.forEachIndexed { idx, spec ->
                val col = spec.color
                val dot = View(ctx).apply {
                    isFocusable = true
                    isClickable = true
                    background = swatchBg(ctx, col, lp.accentColor == col)
                    setOnFocusChangeListener { v, f ->
                        MotionEngine.animateFocus(v, f, lp.motionStyle, 1.08f, 3f)
                        v.background = swatchBg(ctx, col, f || lp.accentColor == col)
                    }
                    setOnClickListener {
                        lp.accentColor = col
                        lp.accentName = spec.label
                        triggerInstantSync()
                    }
                }
                swatchViews.add(col to dot)
                swatchRow.addView(
                    dot,
                    LinearLayout.LayoutParams(0, swatchHeightPx, 1f).apply {
                        if (idx < TileThemePalette.ACCENTS.lastIndex) rightMargin = gapPx
                    }
                )
            }
            panel.addView(swatchRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapPx })
            liveSyncListeners.add {
                swatchViews.forEach { (c, v) ->
                    v.background = swatchBg(ctx, c, v.hasFocus() || c == lp.accentColor)
                }
            }
        }

        // 6. TILE GLASS PALETTE (Purely driven from TileThemePalette.THEMES)
        if (TileThemePalette.THEMES.isNotEmpty()) {
            addHeader(ctx, panel, "TILE GLASS PALETTE")
            val themeItems = TileThemePalette.THEMES.map { spec ->
                spec.key to spec.name.substringBefore(" ")
            }
            addDynamicOptionSection(
                ctx = ctx,
                panel = panel,
                lp = lp,
                items = themeItems,
                chipHeightPx = chipHeightPx,
                gapPx = gapPx,
                syncListeners = liveSyncListeners,
                getSelectedKey = { lp.tileColor },
                onSelectKey = { key ->
                    lp.tileColor = key
                    triggerInstantSync()
                }
            )
        }

        // 7. FAVORITE BADGE STYLE (Purely driven from TileThemePalette.BADGE_MODES)
        if (TileThemePalette.BADGE_MODES.isNotEmpty()) {
            addHeader(ctx, panel, "FAVORITE BADGE")
            addDynamicOptionSection(
                ctx = ctx,
                panel = panel,
                lp = lp,
                items = TileThemePalette.BADGE_MODES.map { it.key to it.label },
                chipHeightPx = chipHeightPx,
                gapPx = gapPx,
                syncListeners = liveSyncListeners,
                getSelectedKey = { lp.badgeMode },
                onSelectKey = { key ->
                    lp.badgeMode = key
                    triggerInstantSync()
                }
            )
        }
    }

    private fun computeDynamicColumns(totalCount: Int, maxCols: Int): Int {
        if (totalCount <= 0) return 1
        if (totalCount <= maxCols) return totalCount
        val rows = kotlin.math.ceil(totalCount.toDouble() / maxCols).toInt().coerceAtLeast(1)
        return kotlin.math.ceil(totalCount.toDouble() / rows).toInt().coerceAtLeast(1)
    }

    private fun addDynamicOptionSection(
        ctx: Context,
        panel: LinearLayout,
        lp: LauncherPreferences,
        items: List<Pair<String, String>>,
        chipHeightPx: Int,
        gapPx: Int,
        syncListeners: MutableList<() -> Unit>,
        getSelectedKey: () -> String,
        onSelectKey: (String) -> Unit
    ) {
        if (items.isEmpty()) return
        val colsPerRow = computeDynamicColumns(items.size, maxCols = 4)
        val allChips = ArrayList<Pair<String, TextView>>()

        items.chunked(colsPerRow).forEach { rowChunk ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            rowChunk.forEachIndexed { idx, (key, label) ->
                val chip = TextView(ctx).apply {
                    text = label
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    isFocusable = true
                    isClickable = true
                    background = chipBg(ctx, lp.accentColor, getSelectedKey() == key)
                    setOnFocusChangeListener { v, f ->
                        MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 2f)
                        v.background = chipBg(ctx, lp.accentColor, f || getSelectedKey() == key)
                    }
                    setOnClickListener {
                        onSelectKey(key)
                    }
                }
                allChips.add(key to chip)
                row.addView(
                    chip,
                    LinearLayout.LayoutParams(0, chipHeightPx, 1f).apply {
                        if (idx < rowChunk.lastIndex) rightMargin = gapPx
                    }
                )
            }
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapPx })
        }

        syncListeners.add {
            val activeKey = getSelectedKey()
            allChips.forEach { (k, cView) ->
                cView.background = chipBg(ctx, lp.accentColor, cView.hasFocus() || activeKey == k)
            }
        }
    }

    private fun makeActionTile(ctx: Context, lp: LauncherPreferences, icon: String, title: String, sub: String, onClick: () -> Unit) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6))
            isFocusable = true
            isClickable = true
            background = chipBg(ctx, lp.accentColor, false)
            setOnFocusChangeListener { v, f ->
                MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 2f)
                v.background = chipBg(ctx, lp.accentColor, f)
            }
            setOnClickListener { onClick() }
            addView(TextView(ctx).apply {
                text = "$icon$title"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                isSingleLine = true
            })
            addView(TextView(ctx).apply {
                text = sub
                textSize = 8.2f
                setTextColor(lp.accentColor)
                isSingleLine = true
            })
        }

    private fun addActionCard(ctx: Context, panel: LinearLayout, lp: LauncherPreferences, icon: String, title: String, sub: String, onClick: () -> Unit) {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9))
            isFocusable = true
            isClickable = true
            background = chipBg(ctx, lp.accentColor, false)
            setOnFocusChangeListener { v, f ->
                MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 2f)
                v.background = chipBg(ctx, lp.accentColor, f)
            }
            setOnClickListener { onClick() }
        }
        card.addView(TextView(ctx).apply {
            text = icon
            textSize = 14f
            setTextColor(lp.accentColor)
            setPadding(0, 0, dp(ctx, 10), 0)
        })
        val mid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(TextView(ctx).apply { text = title; textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        mid.addView(TextView(ctx).apply { text = sub; textSize = 8.5f; setTextColor(Color.argb(185, 210, 230, 245)) })
        card.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(TextView(ctx).apply { text = "›"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        panel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 4) })
    }

    private fun chipBg(ctx: Context, accent: Int, active: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (active) intArrayOf(LauncherUtils.withAlpha(accent, 165), LauncherUtils.withAlpha(accent, 95))
        else intArrayOf(Color.argb(52, 255, 255, 255), Color.argb(26, 255, 255, 255))
    ).apply {
        cornerRadius = LauncherUtils.dpF(ctx, 12f)
        setStroke(dp(ctx, if (active) 2 else 1), if (active) Color.WHITE else Color.argb(65, 255, 255, 255))
    }

    private fun swatchBg(ctx: Context, col: Int, active: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(col)
        setStroke(dp(ctx, if (active) 3 else 1), if (active) Color.WHITE else Color.argb(110, 255, 255, 255))
    }

    private fun addHeader(ctx: Context, panel: LinearLayout, label: String) {
        panel.addView(TextView(ctx).apply {
            text = label.uppercase(Locale.ROOT)
            textSize = 8.8f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.argb(210, 195, 220, 255))
            letterSpacing = 0.08f
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 8); bottomMargin = dp(ctx, 4) })
    }

    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
