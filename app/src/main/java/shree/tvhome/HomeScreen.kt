// Important instructions:
// - Compute top padding relative to TopHeaderBar avatar height so the circular profile avatar never crops at the top edge.
// - Smoothly center-scroll focused app rows without layout jitter and linearly fade rows above/below center.

package shree.tvhome

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.LiveBackdrop
import shree.tvhome.design.AppsShelfDesign
import shree.tvhome.design.SpatialGridDesign
import kotlin.math.abs

class HomeScreen(
    context: Context,
    private val settings: AppSettings,
    callbacks: TopHeaderBar.NavCallbacks,
    onSortChanged: () -> Unit,
    private val onHiddenRibbonClosed: (String?) -> Unit
) : LinearLayout(context) {

    val topBar: TopHeaderBar
    val contentArea: FrameLayout
    val bodyContainer: LinearLayout
    val hiddenSection: LinearLayout
    val hiddenRow: LinearLayout
    val hiddenCount: TextView
    val favSection: LinearLayout
    val favRow: LinearLayout
    val favCount: TextView
    val appGrid: LinearLayout
    val allCount: TextView

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false

        val hPad = LayoutMetrics.screenHorizontalPadding(context)
        val vPad = LayoutMetrics.screenVerticalPadding(context)
        val barHeight = LayoutMetrics.dynamicSpace(context, ratio = 0.054f, minDp = 34, maxDp = 48, horizontal = false)
        // Reserve proportional top clearance for the oversized circular avatar so it never clips at the screen top.
        val topAvatarClearance = maxOf(vPad, (barHeight * 0.68f).toInt())
        val sectionGap = LayoutMetrics.dynamicSpace(context, ratio = 0.015f, minDp = 10, maxDp = 20, horizontal = false)

        setPadding(hPad, topAvatarClearance, hPad, vPad / 2)

        topBar = TopHeaderBar(context, settings, callbacks)
        addView(topBar, LayoutParams(-1, barHeight).apply { bottomMargin = sectionGap })

        contentArea = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        addView(contentArea, LayoutParams(-1, 0, 1f))

        bodyContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        contentArea.addView(bodyContainer, FrameLayout.LayoutParams(-1, -1))

        // Hidden apps ribbon with proportional padding and clean spacing.
        val ribbonPadH = LayoutMetrics.dynamicSpace(context, ratio = 0.012f, minDp = 10, maxDp = 18, horizontal = true)
        val ribbonPadV = LayoutMetrics.dynamicSpace(context, ratio = 0.010f, minDp = 8, maxDp = 14, horizontal = false)
        hiddenSection = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
            clipChildren = false
            clipToPadding = false
            setPadding(ribbonPadH, ribbonPadV, ribbonPadH, ribbonPadV)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(95, 255, 95, 125), Color.argb(45, 32, 24, 58))
            ).apply {
                cornerRadius = LayoutMetrics.dpF(context, 18f)
                setStroke(LayoutMetrics.dp(context, 1), Color.argb(130, 255, 130, 155))
            }
        }
        val hiddenHeader = AppsShelfDesign.createHeader(context, "👁  HIDDEN APPS", isFavoritesRow = false)
        hiddenCount = hiddenHeader.findViewWithTag("count_tv")
        val closeHiddenBtn = TextView(context).apply {
            text = "Done ✕"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            isFocusable = true
            isClickable = true
            val btnPadH = LayoutMetrics.dynamicSpace(context, ratio = 0.010f, minDp = 10, maxDp = 16, horizontal = true)
            val btnPadV = LayoutMetrics.dynamicSpace(context, ratio = 0.005f, minDp = 4, maxDp = 8, horizontal = false)
            setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
            background = GradientDrawable().apply {
                setColor(Color.argb(85, 255, 255, 255))
                cornerRadius = LayoutMetrics.dpF(context, 14f)
                setStroke(LayoutMetrics.dp(context, 1), Color.argb(140, 255, 255, 255))
            }
            setOnFocusChangeListener { v, f -> AnimationController.animateFocus(v, f, settings.motionStyle, 1.06f, 4f) }
            setOnClickListener { closeHiddenRibbon() }
        }
        hiddenHeader.addView(closeHiddenBtn, LayoutParams(-2, -2).apply { leftMargin = ribbonPadH })
        hiddenSection.addView(hiddenHeader)

        val hiddenHScroll = HorizontalScrollView(context).apply {
            isFocusable = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            clipChildren = false
            setPadding(0, ribbonPadV / 2, 0, ribbonPadV / 2)
        }
        hiddenRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        hiddenHScroll.addView(hiddenRow)
        hiddenSection.addView(hiddenHScroll)
        bodyContainer.addView(hiddenSection, LayoutParams(-1, -2).apply { bottomMargin = sectionGap })
        // Favorites horizontal shelf with balanced vertical breathing room.
        favSection = LinearLayout(context).apply {
            orientation = VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val favHeader = AppsShelfDesign.createHeader(context, "♥  FAVORITES & QUICK ACCESS", isFavoritesRow = true)
        favCount = favHeader.findViewWithTag("count_tv")
        favSection.addView(favHeader)

        val shelfPadV = LayoutMetrics.dynamicSpace(context, ratio = 0.009f, minDp = 6, maxDp = 12, horizontal = false)
        val favHScroll = HorizontalScrollView(context).apply {
            isFocusable = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            clipChildren = false
            setPadding(0, shelfPadV, 0, shelfPadV)
        }
        favRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        favHScroll.addView(favRow)
        favSection.addView(favHScroll)
        bodyContainer.addView(favSection, LayoutParams(-1, -2).apply { bottomMargin = sectionGap })

        // All apps header and smooth center-locking scroll viewport with linear row fade-out.
        val allHeader = AppsShelfDesign.createHeader(
            context, "⊞  ALL APPS", isFavoritesRow = false, settings = settings, onSortChanged = onSortChanged
        )
        allCount = allHeader.findViewWithTag("count_tv")
        bodyContainer.addView(allHeader, LayoutParams(-1, -2).apply { bottomMargin = shelfPadV })

        val fadeLength = LayoutMetrics.dynamicSpace(context, ratio = 0.065f, minDp = 32, maxDp = 60, horizontal = false)
        val scroll = object : ScrollView(context) {
            override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
                return false
            }
        }.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = FOCUS_AFTER_DESCENDANTS
            isVerticalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = true
            clipToPadding = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(fadeLength)
        }

        val scrollContent = LinearLayout(context).apply {
            orientation = VERTICAL
            clipChildren = false
            clipToPadding = false
            val edgeBuf = LayoutMetrics.dynamicSpace(context, ratio = 0.04f, minDp = 24, maxDp = 48, horizontal = false)
            setPadding(0, edgeBuf, 0, edgeBuf * 2)
        }
        appGrid = LinearLayout(context).apply {
            orientation = VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        scrollContent.addView(appGrid)
        scroll.addView(scrollContent)
        bodyContainer.addView(scroll, LayoutParams(-1, 0, 1f))

        // Linearly fade rows above and below the vertical center of the app drawer.
        val applyLinearRowFade: () -> Unit = {
            val grid = appGrid.findViewWithTag<SpatialGridDesign>("spatial_engine")
            val vpH = scroll.height.toFloat()
            if (grid != null && vpH > 0f) {
                val vpCenterY = scroll.scrollY + (vpH / 2f)
                val maxDist = (vpH * 0.56f).coerceAtLeast(1f)
                for (i in 0 until grid.childCount) {
                    val tile = grid.getChildAt(i)
                    val tileCenterY = scrollContent.paddingTop + tile.y + (tile.height / 2f)
                    val distRatio = (abs(tileCenterY - vpCenterY) / maxDist).coerceIn(0f, 1f)
                    tile.alpha = if (tile.hasFocus()) 1f else (1f - (distRatio * 0.65f)).coerceIn(0.35f, 1f)
                }
            }
        }

        scroll.viewTreeObserver.addOnScrollChangedListener {
            LiveBackdrop.onScrollParallax(scroll.scrollY)
            applyLinearRowFade()
        }

        // Smoothly scroll focused app row to the vertical center of the app drawer without layout jitter.
        scroll.viewTreeObserver.addOnGlobalFocusChangeListener(
            ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
                if (newFocus == null || scroll.height <= 0) return@OnGlobalFocusChangeListener
                if (!isDescendantOf(newFocus, appGrid)) {
                    applyLinearRowFade()
                    return@OnGlobalFocusChangeListener
                }

                val tileCenterY = scrollContent.paddingTop + newFocus.y + (newFocus.height / 2f)
                val viewportCenterY = scroll.height / 2f
                val maxScroll = maxOf(0, scrollContent.height - scroll.height)
                val targetScrollY = (tileCenterY - viewportCenterY).toInt().coerceIn(0, maxScroll)

                scroll.smoothScrollTo(0, targetScrollY)
                applyLinearRowFade()
            }
        )
    }

    private fun isDescendantOf(targetView: View, container: View): Boolean {
        var parent = targetView.parent
        while (parent != null) {
            if (parent === container) return true
            parent = parent.parent
        }
        return false
    }

    fun setRightStageInset(rightStageWidthPx: Int) {
        val gap = LayoutMetrics.dynamicSpace(context, ratio = 0.018f, minDp = 12, maxDp = 30, horizontal = true)
        bodyContainer.setPadding(0, 0, rightStageWidthPx + gap, 0)
    }

    fun isHiddenRibbonVisible(): Boolean = hiddenSection.visibility == View.VISIBLE

    fun openHiddenRibbon(onUnhideApp: (String, String) -> Unit) {
        AppsShelfDesign.populateHiddenRibbon(context, settings, hiddenSection, hiddenRow, hiddenCount, onUnhideApp)
        if (hiddenSection.visibility == View.VISIBLE) {
            hiddenSection.alpha = 0f
            hiddenSection.scaleY = 0.90f
            hiddenSection.animate()
                .alpha(1f).scaleY(1f)
                .setDuration(300L)
                .setInterpolator(LayoutMetrics.FLUID_SPRING)
                .start()
        }
    }

    fun closeHiddenRibbon(focusPkg: String? = null) {
        if (hiddenSection.visibility != View.VISIBLE) return
        hiddenSection.animate()
            .alpha(0f).scaleY(0.90f)
            .setDuration(220L)
            .setInterpolator(LayoutMetrics.SILKY_EASE)
            .withEndAction {
                hiddenSection.visibility = View.GONE
                hiddenSection.alpha = 1f
                hiddenSection.scaleY = 1f
                onHiddenRibbonClosed(focusPkg)
            }
            .start()
    }

    private fun dp(value: Int): Int = LayoutMetrics.dp(context, value)
}
