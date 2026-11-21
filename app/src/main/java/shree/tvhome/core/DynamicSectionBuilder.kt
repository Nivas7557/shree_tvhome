package shree.tvhome.core

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.source.LauncherPreferences
import shree.tvhome.source.LauncherUtils
import shree.tvhome.source.MotionEngine
import shree.tvhome.source.TileThemePalette
import java.util.Locale

object DynamicSectionBuilder {

    fun computeOptimalColumns(totalCount: Int, maxCols: Int = 4): Int {
        if (totalCount <= 0) return 1
        val cap = maxOf(1, maxCols)
        return minOf(totalCount, cap)
    }

    fun computeDynamicItemHeight(
        ctx: Context,
        lp: LauncherPreferences,
        rowWidthPx: Int,
        itemsInRow: Int,
        gapPx: Int,
        baseHeightDp: Int,
        aspectRatio: Float = 0.75f
    ): Int {
        val count = itemsInRow.coerceAtLeast(1)
        val minH = LauncherUtils.dp(ctx, (baseHeightDp * 0.8f).toInt().coerceAtLeast(28))
        val maxH = LauncherUtils.dp(ctx, (baseHeightDp * 1.35f).toInt().coerceAtLeast(44))
        val paletteScale = TileThemePalette.getTileSizeSpec(lp.tileSize).desiredGridDp / 140f

        val usableW = (rowWidthPx - gapPx * (count - 1)).coerceAtLeast(0)
        if (usableW <= 0) {
            return (LauncherUtils.dp(ctx, baseHeightDp) * paletteScale.coerceIn(0.85f, 1.2f))
                .toInt()
                .coerceIn(minH, maxH)
        }

        val cellW = usableW / count
        return (cellW * aspectRatio).toInt().coerceIn(minH, maxH)
    }

    fun applyDynamicRowMetrics(
        ctx: Context,
        lp: LauncherPreferences,
        row: LinearLayout,
        itemsInRow: Int,
        gapPx: Int,
        baseHeightDp: Int,
        aspectRatio: Float
    ) {
        row.post {
            val h = computeDynamicItemHeight(
                ctx = ctx,
                lp = lp,
                rowWidthPx = row.width,
                itemsInRow = itemsInRow,
                gapPx = gapPx,
                baseHeightDp = baseHeightDp,
                aspectRatio = aspectRatio
            )
            for (i in 0 until row.childCount) {
                val child = row.getChildAt(i)
                val p = child.layoutParams as? LinearLayout.LayoutParams ?: continue
                if (p.height != h) {
                    p.height = h
                    child.requestLayout()
                }
            }
        }
    }

    fun buildChipSection(
        ctx: Context,
        panel: LinearLayout,
        lp: LauncherPreferences,
        headerTitle: String,
        items: List<Pair<String, String>>,
        maxCols: Int = 4,
        chipHeightDp: Int = 34,
        gapDp: Int = 6,
        syncListeners: MutableList<() -> Unit>,
        getSelectedKey: () -> String,
        onSelectKey: (String) -> Unit
    ) {
        if (items.isEmpty()) return

        val gapPx = LauncherUtils.dp(ctx, gapDp)
        val initialH = computeDynamicItemHeight(
            ctx = ctx,
            lp = lp,
            rowWidthPx = 0,
            itemsInRow = computeOptimalColumns(items.size, maxCols),
            gapPx = gapPx,
            baseHeightDp = chipHeightDp,
            aspectRatio = 0.42f
        )

        addSectionHeader(ctx, panel, headerTitle)

        val buttons = ArrayList<Pair<String, TextView>>()
        val colsPerRow = computeOptimalColumns(items.size, maxCols)

        items.chunked(colsPerRow).forEach { rowChunk ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }

            rowChunk.forEachIndexed { idx, (key, label) ->
                val selected = getSelectedKey() == key
                val btn = TextView(ctx).apply {
                    text = label
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    maxLines = 1
                    isFocusable = true
                    isClickable = true
                    background = chipDrawable(ctx, lp.accentColor, selected)
                    setOnFocusChangeListener { v, hasFocus ->
                        MotionEngine.animateFocus(v, hasFocus, lp.motionStyle, 1.02f, 2f)
                        v.background = chipDrawable(ctx, lp.accentColor, hasFocus || getSelectedKey() == key)
                    }
                    setOnClickListener {
                        onSelectKey(key)
                    }
                }
                buttons.add(key to btn)
                row.addView(
                    btn,
                    LinearLayout.LayoutParams(0, initialH, 1f).apply {
                        if (idx < rowChunk.lastIndex) rightMargin = gapPx
                    }
                )
            }

            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapPx })
            applyDynamicRowMetrics(
                ctx = ctx,
                lp = lp,
                row = row,
                itemsInRow = rowChunk.size,
                gapPx = gapPx,
                baseHeightDp = chipHeightDp,
                aspectRatio = 0.42f
            )
        }

        syncListeners.add {
            val activeKey = getSelectedKey()
            buttons.forEach { (key, view) ->
                view.background = chipDrawable(ctx, lp.accentColor, view.hasFocus() || key == activeKey)
            }
        }
    }

    fun buildSwatchSection(
        ctx: Context,
        panel: LinearLayout,
        lp: LauncherPreferences,
        headerTitle: String,
        swatches: List<Pair<Int, String>>,
        maxCols: Int = 4,
        swatchHeightDp: Int = 34,
        gapDp: Int = 6,
        syncListeners: MutableList<() -> Unit>,
        isSelectedColor: (Int) -> Boolean,
        onSelectSwatch: (Int, String) -> Unit
    ) {
        if (swatches.isEmpty()) return

        val gapPx = LauncherUtils.dp(ctx, gapDp)
        val initialH = computeDynamicItemHeight(
            ctx = ctx,
            lp = lp,
            rowWidthPx = 0,
            itemsInRow = computeOptimalColumns(swatches.size, maxCols),
            gapPx = gapPx,
            baseHeightDp = swatchHeightDp,
            aspectRatio = 0.75f
        )

        addSectionHeader(ctx, panel, headerTitle)

        val views = ArrayList<Pair<Int, View>>()
        val colsPerRow = computeOptimalColumns(swatches.size, maxCols)

        swatches.chunked(colsPerRow).forEach { rowChunk ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }

            rowChunk.forEachIndexed { idx, (colorVal, label) ->
                val selected = isSelectedColor(colorVal)
                val dot = View(ctx).apply {
                    isFocusable = true
                    isClickable = true
                    background = swatchDrawable(ctx, colorVal, selected)
                    setOnFocusChangeListener { v, hasFocus ->
                        MotionEngine.animateFocus(v, hasFocus, lp.motionStyle, 1.04f, 2f)
                        v.background = swatchDrawable(ctx, colorVal, hasFocus || isSelectedColor(colorVal))
                    }
                    setOnClickListener {
                        onSelectSwatch(colorVal, label)
                    }
                }
                views.add(colorVal to dot)
                row.addView(
                    dot,
                    LinearLayout.LayoutParams(0, initialH, 1f).apply {
                        if (idx < rowChunk.lastIndex) rightMargin = gapPx
                    }
                )
            }

            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapPx })
            applyDynamicRowMetrics(
                ctx = ctx,
                lp = lp,
                row = row,
                itemsInRow = rowChunk.size,
                gapPx = gapPx,
                baseHeightDp = swatchHeightDp,
                aspectRatio = 0.75f
            )
        }

        syncListeners.add {
            views.forEach { (colorVal, view) ->
                view.background = swatchDrawable(ctx, colorVal, view.hasFocus() || isSelectedColor(colorVal))
            }
        }
    }

    private fun addSectionHeader(ctx: Context, panel: LinearLayout, title: String) {
        panel.addView(
            TextView(ctx).apply {
                text = title.uppercase(Locale.ROOT)
                textSize = 8.8f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.argb(210, 195, 220, 255))
                letterSpacing = 0.08f
            },
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = LauncherUtils.dp(ctx, 8)
                bottomMargin = LauncherUtils.dp(ctx, 4)
            }
        )
    }

    private fun chipDrawable(ctx: Context, accent: Int, active: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (active) {
            intArrayOf(LauncherUtils.withAlpha(accent, 165), LauncherUtils.withAlpha(accent, 95))
        } else {
            intArrayOf(Color.argb(52, 255, 255, 255), Color.argb(26, 255, 255, 255))
        }
    ).apply {
        cornerRadius = LauncherUtils.dpF(ctx, 12f)
        setStroke(
            LauncherUtils.dp(ctx, if (active) 2 else 1),
            if (active) Color.WHITE else Color.argb(65, 255, 255, 255)
        )
    }

    private fun swatchDrawable(ctx: Context, col: Int, active: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(col)
        cornerRadius = LauncherUtils.dpF(ctx, 12f)
        setStroke(
            LauncherUtils.dp(ctx, if (active) 2 else 1),
            if (active) Color.WHITE else Color.argb(120, 255, 255, 255)
        )
    }
}
