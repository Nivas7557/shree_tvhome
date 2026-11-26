// Important instructions:
// - Keep app shelf layout dynamic with zero favorite badges, zero forced requestFocus(), and single-source geometry math.
// - Cache installed app lists and labels cleanly, and release resources via clearCache().

package shree.tvhome.design

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes
import java.util.Locale

object AppsShelfDesign {

    private data class ShelfGeometry(
        val colCount: Int,
        val gap: Int,
        val cellW: Int,
        val tileW: Int,
        val tileH: Int,
        val sideMargin: Int
    )

    private var cachedApps: List<ResolveInfo>? = null
    private var lastAppSignature = ""
    private var lastFavSignature = ""

    // Clears cached package lists, labels, and rendered icon/color caches to keep RAM clean.
    fun clearCache() {
        cachedApps = null
        lastAppSignature = ""
        lastFavSignature = ""
        AppTileDesign.clearAssetCache()
    }

    // Computes responsive shelf column count and generous inter-tile gaps inside the glossy outer container.
    private fun computeShelfGeometry(ctx: Context, settings: AppSettings): ShelfGeometry {
        val sizeSpec = Themes.getTileSizeSpec(settings.tileSize)
        val screenW = ctx.resources.displayMetrics.widthPixels
        val sidePad = LayoutMetrics.screenHorizontalPadding(ctx) * 2
        val innerBoxPad = LayoutMetrics.dynamicSpace(ctx, ratio = 0.012f, minDp = 10, maxDp = 18, horizontal = true) * 2
        val gap = LayoutMetrics.dynamicSpace(ctx, ratio = 0.014f, minDp = 12, maxDp = 24, horizontal = true)
        val minStageW = dp(ctx, Themes.PANEL_MIN_WIDTH_DP)
        val rightStageW = maxOf(minStageW, (screenW * (settings.panelWidthPct / 100f)).toInt())
        val stageClearance = LayoutMetrics.dynamicSpace(ctx, ratio = 0.018f, minDp = 12, maxDp = 30, horizontal = true)
        val availableW = maxOf(dp(ctx, 200), screenW - sidePad - innerBoxPad - rightStageW - stageClearance)
        
        val desiredTilePx = dp(ctx, sizeSpec.desiredGridDp)
        val colRange = Themes.GRID_COL_RANGE
        val colCount = ((availableW + gap) / (desiredTilePx + gap)).coerceIn(colRange.first, colRange.last)
        val cellW = maxOf(1, (availableW - (gap * (colCount - 1))) / colCount)
        val tileW = maxOf(1, (cellW * 0.92f).toInt())
        val extraNameplateH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.032f, minDp = 22, maxDp = 32, horizontal = false)
        val tileH = maxOf(1, (tileW * 0.62f).toInt() + extraNameplateH)
        val sideMargin = maxOf(0, (cellW - tileW) / 2)

        return ShelfGeometry(colCount, gap, cellW, tileW, tileH, sideMargin)
    }

    // Builds a shelf header row with optional A-Z/Z-A sort toggle and live app count badge.
    fun createHeader(
        ctx: Context,
        title: String,
        isFavoritesRow: Boolean,
        settings: AppSettings? = null,
        onSortChanged: (() -> Unit)? = null
    ): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 2), dp(ctx, 4), dp(ctx, 2), dp(ctx, 8))
            addView(
                View(ctx).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (isFavoritesRow) Color.rgb(255, 85, 130) else Color.rgb(88, 175, 255))
                    }
                },
                LinearLayout.LayoutParams(dp(ctx, 8), dp(ctx, 8)).apply { rightMargin = dp(ctx, 8) }
            )
            addView(TextView(ctx).apply {
                text = title
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                setTextColor(Color.argb(235, 225, 238, 255))
            })
            addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
            if (!isFavoritesRow && settings != null && onSortChanged != null) {
                val sortPill = TextView(ctx).apply {
                    text = if (settings.sortOrder == "za") "⇅  Z-A ORDER" else "⇅  A-Z ORDER"
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    isSingleLine = true
                    maxLines = 1
                    setPadding(dp(ctx, 12), dp(ctx, 4), dp(ctx, 12), dp(ctx, 4))
                    isFocusable = true
                    background = badgeBg(ctx, false)
                    setOnFocusChangeListener { v, f ->
                        LayoutMetrics.applyFocusSpring(v, f, 1.05f, 4f)
                        v.background = badgeBg(ctx, f)
                    }
                    setOnClickListener {
                        settings.sortOrder = if (settings.sortOrder == "za") "az" else "za"
                        text = if (settings.sortOrder == "za") "⇅  Z-A ORDER" else "⇅  A-Z ORDER"
                        onSortChanged()
                    }
                }
                addView(sortPill, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(ctx, 8) })
            }
            addView(TextView(ctx).apply {
                tag = "count_tv"
                text = "0 Apps"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.argb(220, 215, 235, 255))
                setPadding(dp(ctx, 11), dp(ctx, 4), dp(ctx, 11), dp(ctx, 4))
                background = badgeBg(ctx, false)
            })
        }
    }

    // Populates the hidden-apps horizontal ribbon when hidden packages exist.
    fun populateHiddenRibbon(
        ctx: Context,
        settings: AppSettings,
        hiddenSection: LinearLayout,
        hiddenRow: LinearLayout,
        hiddenCount: TextView,
        onUnhideApp: (String, String) -> Unit
    ) {
        val pm = ctx.packageManager
        val hiddenSet = settings.getHiddenApps()
        val hiddenApps = getInstalledApps(ctx, pm).filter { hiddenSet.contains(it.activityInfo.packageName) }

        if (hiddenApps.isEmpty()) {
            hiddenSection.visibility = View.GONE
            hiddenRow.removeAllViews()
            return
        }

        hiddenSection.visibility = View.VISIBLE
        hiddenCount.text = "${hiddenApps.size} Hidden • Select for Options"

        val geom = computeShelfGeometry(ctx, settings)
        val sizeSpec = Themes.getTileSizeSpec(settings.tileSize)
        val tileW = (geom.cellW * sizeSpec.hiddenScale).toInt()
        val tileH = (tileW * 0.85f).toInt()
        val sideMargin = maxOf(0, (geom.cellW - tileW) / 2)

        hiddenRow.setPadding(sideMargin, 0, sideMargin, 0)
        hiddenRow.removeAllViews()

        val dummyActions = object : AppTileDesign.TileActions {
            override fun onToggleFavorite(pkg: String, label: String) {}
            override fun onMoveFavorite(pkg: String, delta: Int) {}
            override fun onHideApp(pkg: String, label: String) {}
        }

        hiddenApps.forEachIndexed { idx, info ->
            val pkg = info.activityInfo.packageName
            val label = AppTileDesign.getCachedLabel(pm, info)
            val openHiddenOptions = {
                AppMenuDialogDesign.show(
                    ctx = ctx,
                    pkg = pkg,
                    appName = label,
                    isFav = false,
                    canMoveLeft = false,
                    canMoveRight = false,
                    accent = settings.accentColor,
                    isHiddenMode = true,
                    onHideOrUnhideApp = { onUnhideApp(pkg, label) }
                )
            }
            val tile = AppTileDesign.create(
                ctx = ctx,
                pm = pm,
                settings = settings,
                info = info,
                isFavRow = false,
                isFav = false,
                canMoveLeft = false,
                canMoveRight = false,
                w = tileW,
                h = tileH,
                actions = dummyActions
            ).apply {
                tag = "hidden_$pkg"
                setOnClickListener { openHiddenOptions() }
                setOnLongClickListener { openHiddenOptions(); true }
                setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN &&
                        (keyCode == KeyEvent.KEYCODE_MENU || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.isLongPress))
                    ) {
                        openHiddenOptions()
                        true
                    } else false
                }
            }
            val p = LinearLayout.LayoutParams(tileW, tileH).apply {
                if (idx < hiddenApps.lastIndex) rightMargin = geom.gap
            }
            hiddenRow.addView(tile, p)
        }
    }
        // Populates favorites and main grid tiles dynamically without forcing focus traps.
    fun populate(
        ctx: Context,
        settings: AppSettings,
        favSection: LinearLayout,
        favRow: LinearLayout,
        favCount: TextView,
        appGrid: LinearLayout,
        allCount: TextView,
        shouldRequestFocus: Boolean,
        restorePkg: String?,
        isDrawerOpen: Boolean,
        actions: AppTileDesign.TileActions
    ) {
        val pm = ctx.packageManager
        val visibleApps = getInstalledApps(ctx, pm).filter { !settings.getHiddenApps().contains(it.activityInfo.packageName) }
        val byPkg = visibleApps.associateBy { it.activityInfo.packageName }
        val favOrder = settings.getOrderedFavorites()
        val favApps = favOrder.mapNotNull { byPkg[it] }
        val sortedAll = if (settings.sortOrder == "za") {
            visibleApps.sortedByDescending { AppTileDesign.getCachedLabel(pm, it).lowercase(Locale.ROOT) }
        } else {
            visibleApps.sortedBy { AppTileDesign.getCachedLabel(pm, it).lowercase(Locale.ROOT) }
        }

        val geom = computeShelfGeometry(ctx, settings)

        // Update or rebuild the horizontal favorites shelf.
        val currentFavSig = favApps.joinToString { it.activityInfo.packageName }
        if (favApps.isEmpty()) {
            favSection.visibility = View.GONE
            lastFavSignature = ""
        } else {
            favSection.visibility = View.VISIBLE
            favCount.text = "${favApps.size} Apps"
            favRow.setPadding(geom.sideMargin, 0, geom.sideMargin, 0)
            if (currentFavSig != lastFavSignature) {
                favRow.removeAllViews()
                favApps.forEachIndexed { idx, info ->
                    val tile = AppTileDesign.create(
                        ctx = ctx,
                        pm = pm,
                        settings = settings,
                        info = info,
                        isFavRow = true,
                        isFav = true,
                        canMoveLeft = idx > 0,
                        canMoveRight = idx < favApps.lastIndex,
                        w = geom.tileW,
                        h = geom.tileH,
                        actions = actions
                    )
                    val p = LinearLayout.LayoutParams(geom.tileW, geom.tileH).apply {
                        if (idx < favApps.lastIndex) rightMargin = geom.gap
                    }
                    favRow.addView(tile, p)
                }
                lastFavSignature = currentFavSig
            } else {
                for (i in 0 until favRow.childCount) {
                    val child = favRow.getChildAt(i)
                    AppTileDesign.updateTileAppearance(ctx, child, settings)
                    animateLayoutParams(child, geom.tileW, geom.tileH, 0, if (i < favRow.childCount - 1) geom.gap else 0)
                }
            }
        }

        // Update or rebuild the main SpatialGridDesign catalog.
        allCount.text = "${sortedAll.size} Apps"
        val favSet = favOrder.toSet()
        val currentAppSig = sortedAll.joinToString { it.activityInfo.packageName } + "_${favSet.size}"
        var spatialGrid = appGrid.findViewWithTag<SpatialGridDesign>("spatial_engine")

        if (spatialGrid == null || currentAppSig != lastAppSignature) {
            appGrid.removeAllViews()
            spatialGrid = SpatialGridDesign(ctx).apply { tag = "spatial_engine" }
            appGrid.addView(
                spatialGrid,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { leftMargin = geom.sideMargin; rightMargin = geom.sideMargin }
            )
            sortedAll.forEach { info ->
                spatialGrid.addView(
                    AppTileDesign.create(
                        ctx = ctx,
                        pm = pm,
                        settings = settings,
                        info = info,
                        isFavRow = false,
                        isFav = favSet.contains(info.activityInfo.packageName),
                        canMoveLeft = false,
                        canMoveRight = false,
                        w = geom.tileW,
                        h = geom.tileH,
                        actions = actions
                    )
                )
            }
            lastAppSignature = currentAppSig
            spatialGrid.updateGridState(geom.colCount, geom.tileW, geom.tileH, geom.gap, animate = false)
        } else {
            for (i in 0 until spatialGrid.childCount) {
                val child = spatialGrid.getChildAt(i)
                AppTileDesign.updateTileAppearance(ctx, child, settings)
            }
            (spatialGrid.layoutParams as? LinearLayout.LayoutParams)?.let { p ->
                if (p.leftMargin != geom.sideMargin || p.rightMargin != geom.sideMargin) {
                    ValueAnimator.ofInt(p.leftMargin, geom.sideMargin).apply {
                        duration = 320L
                        interpolator = LayoutMetrics.FLUID_SPRING
                        addUpdateListener { anim ->
                            val m = anim.animatedValue as Int
                            p.leftMargin = m
                            p.rightMargin = m
                            spatialGrid.requestLayout()
                        }
                        start()
                    }
                }
            }
            spatialGrid.updateGridState(geom.colCount, geom.tileW, geom.tileH, geom.gap, animate = true)
        }

        // Assign default initial focus declaratively on Android O+ without forced requestFocus() calls.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isDrawerOpen) {
            val targetView = if (shouldRequestFocus && restorePkg != null) {
                favRow.findViewWithTag<View>("fav_$restorePkg")
                    ?: spatialGrid.findViewWithTag<View>("all_$restorePkg")
            } else null
            val defaultTile = targetView ?: favRow.getChildAt(0) ?: spatialGrid.getChildAt(0)
            defaultTile?.isFocusedByDefault = true
        }
    }

    // Smoothly animates tile size and horizontal margin changes when grid geometry updates.
    private fun animateLayoutParams(view: View, w: Int, h: Int, lMargin: Int, rMargin: Int) {
        val p = view.layoutParams as? LinearLayout.LayoutParams ?: return
        val startW = p.width
        val startH = p.height
        val startL = p.leftMargin
        val startR = p.rightMargin
        if (startW == w && startH == h && startL == lMargin && startR == rMargin) return
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            interpolator = LayoutMetrics.FLUID_SPRING
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                p.width = (startW + (w - startW) * f).toInt()
                p.height = (startH + (h - startH) * f).toInt()
                p.leftMargin = (startL + (lMargin - startL) * f).toInt()
                p.rightMargin = (startR + (rMargin - startR) * f).toInt()
                view.requestLayout()
            }
            start()
        }
    }

    // Queries Leanback and standard launcher activities once per cache lifecycle.
    private fun getInstalledApps(ctx: Context, pm: PackageManager): List<ResolveInfo> {
        cachedApps?.let { return it }
        val map = LinkedHashMap<String, ResolveInfo>()
        LayoutMetrics.addApps(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, map, ctx.packageName)
        LayoutMetrics.addApps(pm, Intent.CATEGORY_LAUNCHER, map, ctx.packageName)
        val result = map.values.toList()
        cachedApps = result
        return result
    }

    private fun badgeBg(ctx: Context, focused: Boolean) = GradientDrawable().apply {
        setColor(if (focused) Color.argb(95, 255, 255, 255) else Color.argb(48, 22, 34, 74))
        setStroke(dp(ctx, 1), Color.argb(if (focused) 180 else 80, 255, 255, 255))
        cornerRadius = LayoutMetrics.dpF(ctx, 14f)
    }

    private fun dp(ctx: Context, v: Int): Int = LayoutMetrics.dp(ctx, v)
}
