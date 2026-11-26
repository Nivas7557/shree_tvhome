// Important instructions:
// - Render a 3-band perspective conveyor shelf: Top faded row (~18%), Center fixed focus row (~28%), Bottom faded row (~18%).
// - Shift items smoothly by 1 step on Left/Right and 1 full row on Up/Down with GPU-accelerated scale, alpha, and translation.

package shree.tvhome.design

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import shree.tvhome.core.LayoutMetrics
import kotlin.math.abs

class CinemaConveyorShelf(context: Context) : ViewGroup(context) {

    var columns: Int = 5
        private set
    var tileW: Int = 0
        private set
    var tileH: Int = 0
        private set
    var gap: Int = 0
        private set

    private var activeIndex: Int = 0
    private var topBandY: Float = 0f
    private var centerBandY: Float = 0f
    private var bottomBandY: Float = 0f
    private var neighborScale: Float = 0.74f

    init {
        clipChildren = false
        clipToPadding = false
        isFocusable = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
    }

    // Updates grid column count and base tile metrics, then repositions all visible conveyor slots.
    fun updateConveyorMetrics(newCols: Int, newTileW: Int, newTileH: Int, newGap: Int, animate: Boolean) {
        columns = newCols.coerceAtLeast(1)
        tileW = newTileW.coerceAtLeast(1)
        tileH = newTileH.coerceAtLeast(1)
        gap = newGap.coerceAtLeast(0)
        if (childCount > 0) {
            activeIndex = activeIndex.coerceIn(0, childCount - 1)
        } else {
            activeIndex = 0
        }
        requestLayout()
        post { applyConveyorTransforms(animate) }
    }

    // Returns the currently active center-left item view so focus restores accurately.
    fun getActiveItemView(): View? = if (activeIndex in 0 until childCount) getChildAt(activeIndex) else null

    // Selects a specific item index (used by touch clicks or package restoration) and glides it to the focus slot.
    fun focusItemAt(index: Int, animate: Boolean = true) {
        if (childCount == 0) return
        val clamped = index.coerceIn(0, childCount - 1)
        if (activeIndex != clamped) {
            activeIndex = clamped
            applyConveyorTransforms(animate)
        }
        val target = getChildAt(activeIndex)
        if (target != null && !target.hasFocus()) {
            target.requestFocus()
        }
    }

    // Measures children at full center-focus dimensions; neighbor bands scale down smoothly via GPU scaleX/scaleY.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val cwSpec = MeasureSpec.makeMeasureSpec(tileW, MeasureSpec.EXACTLY)
        val chSpec = MeasureSpec.makeMeasureSpec(tileH, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            getChildAt(i).measure(cwSpec, chSpec)
        }
        setMeasuredDimension(w, h)
    }

    // Computes proportional Y anchors for Top (~18%), Center (~28% focus), and Bottom (~18%) bands.
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val stageH = (b - t).toFloat()
        if (stageH <= 0f || tileH <= 0) return

        // Center focus band sits squarely at the vertical midpoint of the shelf stage.
        centerBandY = (stageH - tileH) / 2f
        val verticalGap = LayoutMetrics.dynamicSpace(context, ratio = 0.012f, minDp = 6, maxDp = 16, horizontal = false).toFloat()
        val availableSideH = ((stageH - tileH) / 2f - verticalGap).coerceAtLeast(tileH * 0.52f)
        neighborScale = (availableSideH / tileH.toFloat()).coerceIn(0.64f, 0.82f)

        val scaledNeighborH = tileH * neighborScale
        val visualOffset = (tileH - scaledNeighborH) / 2f
        topBandY = (centerBandY - verticalGap - scaledNeighborH) - visualOffset
        bottomBandY = (centerBandY + tileH + verticalGap) - visualOffset

        for (i in 0 until childCount) {
            getChildAt(i).layout(0, 0, tileW, tileH)
        }
        applyConveyorTransforms(animate = false)
    }

    // Intercepts D-Pad directional keys inside the shelf to drive 1-step horizontal and 1-row vertical conveyor shifts.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && childCount > 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (activeIndex + 1 < childCount) {
                        focusItemAt(activeIndex + 1, animate = true)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (activeIndex - 1 >= 0) {
                        focusItemAt(activeIndex - 1, animate = true)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val nextRowIdx = (activeIndex + columns).coerceAtMost(childCount - 1)
                    if (nextRowIdx != activeIndex) {
                        focusItemAt(nextRowIdx, animate = true)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (activeIndex - columns >= 0) {
                        focusItemAt(activeIndex - columns, animate = true)
                        return true
                    }
                    // Allow DPAD_UP on the top-most row to exit naturally to Favorites / TopHeaderBar.
                    return super.dispatchKeyEvent(event)
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
        // Positions every item relative to activeIndex across the 3 perspective bands (Top, Center Focus, Bottom).
    private fun applyConveyorTransforms(animate: Boolean) {
        if (childCount == 0 || columns <= 0) return
        val totalGridW = (columns * tileW) + ((columns - 1) * gap)
        val startX = maxOf(0f, (width - totalGridW) / 2f)
        val pitchX = (tileW + gap).toFloat()

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val delta = i - activeIndex

            // Determine which band this item belongs to:
            // Center band: delta in 0 until columns (activeIndex is always the left-most focus anchor)
            // Top band: delta in -columns until 0 (previous items shifted up and to the right)
            // Bottom band: delta in columns until columns * 2 (upcoming items waiting below)
            val band: Int
            val colSlot: Int
            when {
                delta in 0 until columns -> {
                    band = 0
                    colSlot = delta
                }
                delta in -columns until 0 -> {
                    band = -1
                    colSlot = columns + delta
                }
                delta in columns until (columns * 2) -> {
                    band = 1
                    colSlot = delta - columns
                }
                else -> {
                    band = if (delta < 0) -2 else 2
                    colSlot = ((delta % columns) + columns) % columns
                }
            }

            val baseX = startX + (colSlot * pitchX)
            val targetScale = when (band) {
                0 -> 1f
                -1, 1 -> neighborScale
                else -> neighborScale * 0.85f
            }
            val targetAlpha = when (band) {
                0 -> 1f
                -1, 1 -> 0.46f
                else -> 0f
            }
            val targetY = when (band) {
                0 -> centerBandY
                -1 -> topBandY
                1 -> bottomBandY
                -2 -> topBandY - (tileH * 0.45f)
                else -> bottomBandY + (tileH * 0.45f)
            }

            // Center neighbors toward their column slot while keeping the active focus tile crisp at full width.
            val targetX = baseX
            val isVisibleBand = abs(band) <= 1

            child.animate().cancel()
            if (isVisibleBand && child.visibility != View.VISIBLE) {
                child.visibility = View.VISIBLE
            }

            if (animate) {
                child.animate()
                    .x(targetX)
                    .y(targetY)
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .alpha(targetAlpha)
                    .setDuration(260L)
                    .setInterpolator(LayoutMetrics.SILKY_EASE)
                    .withEndAction {
                        if (!isVisibleBand) child.visibility = View.INVISIBLE
                    }
                    .start()
            } else {
                child.x = targetX
                child.y = targetY
                child.scaleX = targetScale
                child.scaleY = targetScale
                child.alpha = targetAlpha
                child.visibility = if (isVisibleBand) View.VISIBLE else View.INVISIBLE
            }
        }
    }
}
