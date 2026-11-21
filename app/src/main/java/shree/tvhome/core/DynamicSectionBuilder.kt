package shree.tvhome.core

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.source.LauncherPreferences
import shree.tvhome.source.LauncherUtils
import shree.tvhome.source.MotionEngine
import java.util.Locale
import kotlin.math.ceil

object DynamicSectionBuilder {

    fun computeOptimalColumns(totalCount: Int, maxCols: Int = 4): Int {
        if (totalCount <= 0) return 1
        val cap = maxOf(1, maxCols)
        return minOf(totalCount, cap)
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
        val chipHeightPx = LauncherUtils.dp(ctx, chipHeightDp)

        panel.addView(TextView(ctx).apply {
            text = headerTitle.uppercase(Locale.ROOT)
            textSize = 8.8f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.argb(210, 195, 220, 255))
            letterSpacing = 0.08f
        }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = LauncherUtils.dp(ctx, 8)
            bottomMargin = LauncherUtils.dp(ctx, 4)
        })

        val colsPerRow = computeOptimalColumns(items.size, maxCols)
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
                    background = GlassSurfaceSpec.createDrawable(
                        ctx = ctx,
                        style = GlassSurfaceSpec.PANEL_CHIP,
                        accentColor = lp.accentColor,
                        focused = false,
                        active = getSelectedKey() == key
                    )
                    setOnFocusChangeListener { v, f ->
                        MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 2f)
                        v.background = GlassSurfaceSpec.createDrawable(
                            ctx = ctx,
                            style = GlassSurfaceSpec.PANEL_CHIP,
                            accentColor = lp.accentColor,
                            focused = f,
                            active = getSelectedKey() == key
                        )
                    }
                    setOnClickListener { onSelectKey(key) }
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
                cView.background = GlassSurfaceSpec.createDrawable(
                    ctx = ctx,
                    style = GlassSurfaceSpec.PANEL_CHIP,
                    accentColor = lp.accentColor,
                    focused = cView.hasFocus(),
                    active = activeKey == k
                )
            }
        }
    }
}
