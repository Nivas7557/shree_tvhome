// Important instructions:
// - Render a 3-band perspective conveyor shelf with fixed Center-Left focus anchor (1 -> top row end on Right, 1 full row on Down/Up).
// - Support smooth Mobile Touch drag/fling scrolling alongside TV D-Pad navigation without recreating views or forcing focus traps.
package shree.tvhome.design

import android.animation.ValueAnimator
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.OverScroller
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes
import kotlin.math.abs
import kotlin.math.roundToInt

class CinemaConveyorShelf(context: Context) : ViewGroup(context) {
    var columns: Int = Themes.GRID_COL_RANGE.first
        private set
    var tileW: Int = 0
        private set
    var tileH: Int = 0
        private set
    var gap: Int = 0
        private set
    private var virtualPosition: Float = 0f
    private var activeIndex: Int = 0
    private var topBandY: Float = 0f
    private var centerBandY: Float = 0f
    private var bottomBandY: Float = 0f
    private var neighborScale: Float = Themes.CONVEYOR_NEIGHBOR_SCALE_RANGE.start
    private val scroller = OverScroller(context, LayoutMetrics.SILKY_EASE)
    private var glideAnimator: ValueAnimator? = null
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    init {
        clipChildren = false
        clipToPadding = false
        isFocusable = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
    }
    // Updates grid column count and base tile metrics, then repositions all visible conveyor slots smoothly.
    fun updateConveyorMetrics(newCols: Int, newTileW: Int, newTileH: Int, newGap: Int, animate: Boolean) {
        columns = newCols.coerceAtLeast(1)
        tileW = newTileW.coerceAtLeast(1)
        tileH = newTileH.coerceAtLeast(1)
        gap = newGap.coerceAtLeast(0)
        if (childCount > 0) {
            activeIndex = activeIndex.coerceIn(0, childCount - 1)
            virtualPosition = activeIndex.toFloat()
        } else {
            activeIndex = 0
            virtualPosition = 0f
        }
        requestLayout()
        post { applyConveyorTransforms() }
    }
    // Returns the currently active center-left item view so initial focus attaches naturally.
    fun getActiveItemView(): View? = if (activeIndex in 0 until childCount) getChildAt(activeIndex) else null
    // Glides the conveyor smoothly to targetIndex and transfers D-Pad focus only when requested by user navigation.
    fun moveToIndex(index: Int, animate: Boolean = true, transferFocus: Boolean = true) {
        if (childCount == 0) return
        val clamped = index.coerceIn(0, childCount - 1)
        activeIndex = clamped
        scroller.abortAnimation()
        glideAnimator?.cancel()
        if (animate && abs(virtualPosition - clamped.toFloat()) > 0.005f) {
            val startPos = virtualPosition
            val targetPos = clamped.toFloat()
            glideAnimator = ValueAnimator.ofFloat(startPos, targetPos).apply {
                duration = 250L
                interpolator = LayoutMetrics.SILKY_EASE
                addUpdateListener { anim ->
                    virtualPosition = anim.animatedValue as Float
                    applyConveyorTransforms()
                }
                start()
            }
        } else {
            virtualPosition = clamped.toFloat()
            applyConveyorTransforms()
        }
        if (transferFocus) {
            val target = getChildAt(activeIndex)
            if (target != null && !target.hasFocus()) {
                target.requestFocus()
            }
        }
    }
    // Measures children at full center-focus dimensions; neighbor bands scale down via GPU transforms.
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
        centerBandY = (stageH - tileH) / 2f
        val verticalGap = LayoutMetrics.dynamicSpace(context, ratio = 0.012f, minDp = 6, maxDp = 16, horizontal = false).toFloat()
        val availableSideH = ((stageH - tileH) / 2f - verticalGap)
            .coerceAtLeast(tileH * Themes.CONVEYOR_SIDE_MIN_HEIGHT_RATIO)
        neighborScale = (availableSideH / tileH.toFloat())
            .coerceIn(Themes.CONVEYOR_NEIGHBOR_SCALE_RANGE)
        val scaledNeighborH = tileH * neighborScale
        val visualOffset = (tileH - scaledNeighborH) / 2f
        topBandY = (centerBandY - verticalGap - scaledNeighborH) - visualOffset
        bottomBandY = (centerBandY + tileH + verticalGap) - visualOffset
        for (i in 0 until childCount) {
            getChildAt(i).layout(0, 0, tileW, tileH)
        }
        applyConveyorTransforms()
    }
    // Intercepts vertical or horizontal touch drags on mobile/touch displays while letting clean taps launch apps.
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (childCount <= 1) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.abortAnimation()
                glideAnimator?.cancel()
                lastTouchX = ev.x
                lastTouchY = ev.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - lastTouchX)
                val dy = abs(ev.y - lastTouchY)
                if (dx > touchSlop || dy > touchSlop) {
                    isDragging = true
                    return true
                }
            }
        }
        return false
    }
    // Handles touch drag and kinetic fling scrolling for mobile phones and touch-enabled TVs.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (childCount <= 1) return false
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(event)
        val pitchX = (tileW + gap).coerceAtLeast(1).toFloat()
        val pitchY = (tileH * 0.85f).coerceAtLeast(1f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.abortAnimation()
                glideAnimator?.cancel()
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y
                val deltaItems = (-dx / pitchX) + ((-dy / pitchY) * columns)
                virtualPosition = (virtualPosition + deltaItems).coerceIn(0f, (childCount - 1).toFloat())
                activeIndex = virtualPosition.roundToInt().coerceIn(0, childCount - 1)
                applyConveyorTransforms()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                val vx = velocityTracker?.xVelocity ?: 0f
                val vy = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null
                if (abs(vx) > minFlingVelocity || abs(vy) > minFlingVelocity) {
                    val startPx = (virtualPosition * 100f).roundToInt()
                    val velItemsPerSec = (-vx / pitchX) + ((-vy / pitchY) * columns)
                    val maxPx = (childCount - 1) * 100
                    scroller.fling(startPx, 0, (velItemsPerSec * 100f).roundToInt(), 0, 0, maxPx, 0, 0)
                    postInvalidateOnAnimation()
                } else {
                    moveToIndex(virtualPosition.roundToInt(), animate = true, transferFocus = false)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    // Advances touch fling inertia and snaps cleanly onto the nearest conveyor slot when settled.
    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            virtualPosition = (scroller.currX / 100f).coerceIn(0f, (childCount - 1).toFloat())
            activeIndex = virtualPosition.roundToInt().coerceIn(0, childCount - 1)
            applyConveyorTransforms()
            postInvalidateOnAnimation()
        } else if (!isDragging && abs(virtualPosition - virtualPosition.roundToInt()) > 0.005f && glideAnimator?.isRunning != true) {
            moveToIndex(virtualPosition.roundToInt(), animate = true, transferFocus = false)
        }
    }
    // Routes D-Pad directional keys so Right/Left shifts by 1 slot and Down/Up shifts by 1 full row.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && childCount > 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (activeIndex + 1 < childCount) {
                        moveToIndex(activeIndex + 1, animate = true, transferFocus = true)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (activeIndex - 1 >= 0) {
                        moveToIndex(activeIndex - 1, animate = true, transferFocus = true)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val nextRowIdx = (activeIndex + columns).coerceAtMost(childCount - 1)
                    if (nextRowIdx != activeIndex) {
                        moveToIndex(nextRowIdx, animate = true, transferFocus = true)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (activeIndex - columns >= 0) {
                        moveToIndex(activeIndex - columns, animate = true, transferFocus = true)
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
    // Interpolates continuous slot coordinates across Top, Center Focus, and Bottom bands smoothly.
    private fun applyConveyorTransforms() {
        if (childCount == 0 || columns <= 0) return
        val totalGridW = (columns * tileW) + ((columns - 1) * gap)
        val startX = maxOf(0f, (width - totalGridW) / 2f)
        val pitchX = (tileW + gap).toFloat()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val delta = i - virtualPosition
            val p0 = computeSlotPose(delta, startX, pitchX)
            val isVisibleBand = p0.alpha > 0.02f
            if (isVisibleBand) {
                if (child.visibility != View.VISIBLE) child.visibility = View.VISIBLE
                child.x = p0.x
                child.y = p0.y
                child.scaleX = p0.scale
                child.scaleY = p0.scale
                child.alpha = p0.alpha
            } else if (child.visibility != View.INVISIBLE) {
                child.visibility = View.INVISIBLE
            }
        }
    }
    private data class SlotPose(val x: Float, val y: Float, val scale: Float, val alpha: Float)
    // Computes interpolated (x, y, scale, alpha) for any continuous delta relative to virtualPosition.
    private fun computeSlotPose(delta: Float, startX: Float, pitchX: Float): SlotPose {
        val floorDelta = kotlin.math.floor(delta.toDouble()).toInt()
        val frac = delta - floorDelta
        val a = discreteSlotPose(floorDelta, startX, pitchX)
        val b = discreteSlotPose(floorDelta + 1, startX, pitchX)
        return SlotPose(
            x = a.x + (b.x - a.x) * frac,
            y = a.y + (b.y - a.y) * frac,
            scale = a.scale + (b.scale - a.scale) * frac,
            alpha = a.alpha + (b.alpha - a.alpha) * frac
        )
    }
    // Maps an integer delta into Top (-columns..-1), Center (0..columns-1), or Bottom (columns..2*columns-1) slots.
    private fun discreteSlotPose(delta: Int, startX: Float, pitchX: Float): SlotPose {
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
        val x = startX + (colSlot * pitchX)
        val scale = when (band) {
            0 -> 1f
            -1, 1 -> neighborScale
            else -> neighborScale * 0.85f
        }
        val alpha = when (band) {
            0 -> 1f
            -1, 1 -> Themes.CONVEYOR_NEIGHBOR_ALPHA
            else -> 0f
        }
        val y = when (band) {
            0 -> centerBandY
            -1 -> topBandY
            1 -> bottomBandY
            -2 -> topBandY - (tileH * 0.45f)
            else -> bottomBandY + (tileH * 0.45f)
        }
        return SlotPose(x, y, scale, alpha)
    }
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scroller.abortAnimation()
        glideAnimator?.cancel()
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
