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
        val chipHeightPx = 34
        val swatchHeightDp = 34
        val gapDp = 6
        val gapPx = dp(ctx, gapDp)

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

        if (TileThemePalette.TILE_SIZES.isNotEmpty()) {
            DynamicSectionBuilder.buildChipSection(
                ctx = ctx,
                panel = panel,
                lp = lp,
                headerTitle = "TILE SIZE",
                items = TileThemePalette.TILE_SIZES.map { it.key to it.label },
                maxCols = 4,
                chipHeightDp = chipHeightPx,
                gapDp = gapDp,
                syncListeners = liveSyncListeners,
                getSelectedKey = { lp.tileSize },
                onSelectKey = { key ->
                    lp.tileSize = key
                    triggerInstantSync()
                }
            )
        }

        if (TileThemePalette.PANEL_WIDTH_OPTIONS.isNotEmpty()) {
            DynamicSectionBuilder.buildChipSection(
                ctx = ctx,
                panel = panel,
                lp = lp,
                headerTitle = "STAGE & PANEL WIDTH",
                items = TileThemePalette.PANEL_WIDTH_OPTIONS.map { pct -> pct.toString() to "$pct%" },
                maxCols = 4,
                chipHeightDp = chipHeightPx,
                gapDp = gapDp,
                syncListeners = liveSyncListeners,
                getSelectedKey = { lp.panelWidthPct.toString() },
                onSelectKey = { key ->
                    lp.panelWidthPct = key.toIntOrNull() ?: TileThemePalette.PANEL_WIDTH_OPTIONS.first()
                    triggerInstantSync()
                }
            )
        }

        if (TileThemePalette.TILE_SHAPES.isNotEmpty()) {
            DynamicSectionBuilder.buildChipSection(
                ctx = ctx,
                panel = panel,
                lp = lp,
                headerTitle = "ICON SHAPE",
                items = TileThemePalette.TILE_SHAPES.map { it.key to it.label },
                maxCols = 4,
                chipHeightDp = chipHeightPx,
                gapDp = gapDp,
                syncListeners = liveSyncListeners,
                getSelectedKey = { lp.tileShape },
                onSelectKey = { key ->
                    lp.tileShape = key
                    triggerInstantSync()
                }
            )
        }

        if (MotionEngine.STYLES.isNotEmpty()) {
            DynamicSectionBuilder.buildChipSection(
                ctx = ctx,
                panel = panel,
                lp = lp,
                headerTitle = "GLOBAL MOTION STYLE (TAP TO PREVIEW)",
                items = MotionEngine.STYLES.map { it.key to it.label },
                maxCols = 4,
                chipHeightDp = chipHeightPx,
                gapDp = gapDp,
                syncListeners = liveSyncListeners,
                getSelectedKey = { lp.motionStyle },
                onSelectKey = { key ->
                    lp.motionStyle = key
                    val hostPanel = (panel.parent?.parent as? View) ?: panel
                    MotionEngine.playDemoPreview(hostPanel, key)
                    triggerInstantSync()
                }
            )
        }

        if (TileThemePalette.ACCENTS.isNotEmpty()) {
            DynamicSectionBuilder.buildSwatchSection(
                ctx = ctx,
                panel = panel,
                lp = lp,
                headerTitle = "ACCENT AURA",
                swatches = TileThemePalette.ACCENTS.map { it.color to it.label },
                maxCols = 4,
                swatchHeightDp = swatchHeightDp,
                gapDp = gapDp,
                syncListeners = liveSyncListeners,
                isSelectedColor = { col -> lp.accentColor == col },
                onSelectSwatch = { col, label ->
                    lp.accentColor = col
                    lp.accentName = label
                    triggerInstantSync()
                }
            )
        }

        if (TileThemePalette.THEMES.isNotEmpty()) {
            DynamicSectionBuilder.buildChipSection(
                ctx = ctx,
                panel = panel,
                lp = lp,
                headerTitle = "TILE GLASS PALETTE",
                items = TileThemePalette.THEMES.map { spec -> spec.key to spec.name.substringBefore(" ") },
                maxCols = 4,
                chipHeightDp = chipHeightPx,
                gapDp = gapDp,
                syncListeners = liveSyncListeners,
                getSelectedKey = { lp.tileColor },
                onSelectKey = { key ->
                    lp.tileColor = key
                    triggerInstantSync()
                }
            )
        }

        if (TileThemePalette.BADGE_MODES.isNotEmpty()) {
            DynamicSectionBuilder.buildChipSection(
                ctx = ctx,
                panel = panel,
                lp = lp,
                headerTitle = "FAVORITE BADGE",
                items = TileThemePalette.BADGE_MODES.map { it.key to it.label },
                maxCols = 4,
                chipHeightDp = chipHeightPx,
                gapDp = gapDp,
                syncListeners = liveSyncListeners,
                getSelectedKey = { lp.badgeMode },
                onSelectKey = { key ->
                    lp.badgeMode = key
                    triggerInstantSync()
                }
            )
        }
    }

    private fun makeActionTile(
        ctx: Context,
        lp: LauncherPreferences,
        icon: String,
        title: String,
        sub: String,
        onClick: () -> Unit
    ) = LinearLayout(ctx).apply {
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

    private fun addActionCard(
        ctx: Context,
        panel: LinearLayout,
        lp: LauncherPreferences,
        icon: String,
        title: String,
        sub: String,
        onClick: () -> Unit
    ) {
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
        mid.addView(TextView(ctx).apply {
            text = title
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        mid.addView(TextView(ctx).apply {
            text = sub
            textSize = 8.5f
            setTextColor(Color.argb(185, 210, 230, 245))
        })
        card.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(TextView(ctx).apply {
            text = "›"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
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

    private fun addHeader(ctx: Context, panel: LinearLayout, label: String) {
        panel.addView(TextView(ctx).apply {
            text = label.uppercase(Locale.ROOT)
            textSize = 8.8f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.argb(210, 195, 220, 255))
            letterSpacing = 0.08f
        }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(ctx, 8)
            bottomMargin = dp(ctx, 4)
        })
    }

    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
