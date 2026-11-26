// Important instructions:
// - Structure the home screen into proportional bands: TopHeader, Favorites, and Transparent Glossy-Rim App Shelf.
// - Render a thick luminous gradient rim with zero interior fill, a glossy top control ribbon, and center-locked rows.

package shree.tvhome

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
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
import shree.tvhome.core.Themes
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
    private var centerScrollAnim: ObjectAnimator? = null

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false

        val hPad = LayoutMetrics.screenHorizontalPadding(context)
        val vPad = LayoutMetrics.screenVerticalPadding(context)
        val barHeight = LayoutMetrics.dynamicSpace(context, ratio = 0.056f, minDp = 36, maxDp = 50, horizontal = false)
        val topClearance = maxOf(vPad, (barHeight * 0.66f).toInt())
        val bandGap = LayoutMetrics.dynamicSpace(context, ratio = 0.014f, minDp = 8, maxDp = 18, horizontal = false)

        setPadding(hPad, topClearance, hPad, vPad / 2)

        topBar = TopHeaderBar(context, settings, callbacks)
        addView(topBar, LayoutParams(-1, barHeight).apply { bottomMargin = bandGap })

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

        val ribbonPadH = LayoutMetrics.dynamicSpace(context, ratio = 0.012f, minDp = 10, maxDp = 18, horizontal = true)
        val ribbonPadV = LayoutMetrics.dynamicSpace(context, ratio = 0.008f, minDp = 6, maxDp = 12, horizontal = false)
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
            val bPadH = LayoutMetrics.dynamicSpace(context, ratio = 0.010f, minDp = 10, maxDp = 16, horizontal = true)
            val bPadV = LayoutMetrics.dynamicSpace(context, ratio = 0.005f, minDp = 4, maxDp = 8, horizontal = false)
            setPadding(bPadH, bPadV, bPadH, bPadV)
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
        }
        hiddenRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        hiddenHScroll.addView(hiddenRow)
        hiddenSection.addView(hiddenHScroll)
        bodyContainer.addView(hiddenSection, LayoutParams(-1, -2).apply { bottomMargin = bandGap })

        favSection = LinearLayout(context).apply {
            orientation = VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val favHeader = AppsShelfDesign.createHeader(context, "♥  FAVORITES & QUICK ACCESS", isFavoritesRow = true)
        favCount = favHeader.findViewWithTag("count_tv")
        favSection.addView(favHeader)

        val favHScroll = HorizontalScrollView(context).apply {
            isFocusable = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            clipChildren = false
        }
        favRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        favHScroll.addView(favRow)
        favSection.addView(favHScroll)
        bodyContainer.addView(favSection, LayoutParams(-1, -2).apply { bottomMargin = bandGap })
        val shelfPadH = LayoutMetrics.dynamicSpace(context, ratio = 0.012f, minDp = 10, maxDp = 18, horizontal = true)
        val shelfPadV = LayoutMetrics.dynamicSpace(context, ratio = 0.010f, minDp = 8, maxDp = 14, horizontal = false)

        // Transparent App Shelf with thick luminous gradient rounding rim (Google TV / YouTube style).
        val glossyShelfBox = object : LinearLayout(context) {
            private val rimOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            private val rimInnerSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            private val boxRect = RectF()
            private val innerRect = RectF()

            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0f || h <= 0f) return

                val strokeW = (minOf(w, h) * 0.012f).coerceIn(LayoutMetrics.dpF(context, 2.5f), LayoutMetrics.dpF(context, 5.5f))
                val halfStroke = strokeW / 2f
                val cornerR = LayoutMetrics.dpF(context, 24f)

                boxRect.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)
                val accentSpec = Themes.getAccentSpec(settings.accentColor, settings.accentName)
                val c1 = accentSpec.gradientColors.firstOrNull() ?: settings.accentColor
                val c2 = accentSpec.gradientColors.lastOrNull() ?: settings.accentColor

                rimOuterPaint.strokeWidth = strokeW
                rimOuterPaint.shader = LinearGradient(
                    0f, 0f, w, h,
                    intArrayOf(
                        Color.argb(230, 255, 255, 255),
                        LayoutMetrics.withAlpha(c1, 235),
                        Color.argb(145, 255, 255, 255),
                        LayoutMetrics.withAlpha(c2, 235)
                    ),
                    floatArrayOf(0f, 0.34f, 0.68f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(boxRect, cornerR, cornerR, rimOuterPaint)

                val inset = strokeW + LayoutMetrics.dpF(context, 1f)
                innerRect.set(inset, inset, w - inset, h - inset)
                rimInnerSheenPaint.strokeWidth = LayoutMetrics.dpF(context, 0.9f)
                rimInnerSheenPaint.color = Color.argb(85, 255, 255, 255)
                canvas.drawRoundRect(
                    innerRect,
                    (cornerR - inset).coerceAtLeast(4f),
                    (cornerR - inset).coerceAtLeast(4f),
                    rimInnerSheenPaint
                )

                super.onDraw(canvas)
            }
        }.apply {
            orientation = VERTICAL
            setWillNotDraw(false)
            clipChildren = true
            clipToPadding = true
            setPadding(shelfPadH, shelfPadV, shelfPadH, shelfPadV)
        }
        settings.addStyleListener { glossyShelfBox.invalidate() }

        // Top glossy filled control ribbon inside the transparent rim box.
        val allHeader = AppsShelfDesign.createHeader(
            context, "⊞  ALL APPS", isFavoritesRow = false, settings = settings, onSortChanged = onSortChanged
        ).apply {
            val rPadH = LayoutMetrics.dynamicSpace(context, ratio = 0.012f, minDp = 10, maxDp = 16, horizontal = true)
            val rPadV = LayoutMetrics.dynamicSpace(context, ratio = 0.007f, minDp = 5, maxDp = 10, horizontal = false)
            setPadding(rPadH, rPadV, rPadH, rPadV)
            val syncRibbonBg = {
                val glassCols = Themes.getGlassPanelColors(settings.tileColor, settings.accentColor, elevated = true)
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(
                        LayoutMetrics.withAlpha(glassCols.first(), 215),
                        LayoutMetrics.withAlpha(glassCols.last(), 230)
                    )
                ).apply {
                    cornerRadius = LayoutMetrics.dpF(context, 14f)
                    setStroke(LayoutMetrics.dp(context, 1), LayoutMetrics.withAlpha(settings.accentColor, 175))
                }
            }
            syncRibbonBg()
            settings.addStyleListener(syncRibbonBg)
        }
        allCount = allHeader.findViewWithTag("count_tv")
        glossyShelfBox.addView(allHeader, LayoutParams(-1, -2).apply { bottomMargin = shelfPadV / 2 })

        // Non-focusable Leanback-style center-locked viewport with active-row highlight and neighbor dimming.
        val fadeEdgeLen = LayoutMetrics.dynamicSpace(context, ratio = 0.065f, minDp = 32, maxDp = 64, horizontal = false)
        val scroll = object : ScrollView(context) {
            override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
                return false
            }
        }.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = FOCUS_AFTER_DESCENDANTS
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = true
            clipToPadding = true
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(fadeEdgeLen)
        }

        val scrollContent = LinearLayout(context).apply {
            orientation = VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        appGrid = LinearLayout(context).apply {
            orientation = VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        scrollContent.addView(appGrid)
        scroll.addView(scrollContent)
        glossyShelfBox.addView(scroll, LayoutParams(-1, 0, 1f))
        bodyContainer.addView(glossyShelfBox, LayoutParams(-1, 0, 1f))

        val applyLeanbackRowFocusVisuals: (Int) -> Unit = { activeRow ->
            val grid = appGrid.findViewWithTag<SpatialGridDesign>("spatial_engine")
            if (grid != null && grid.columns > 0) {
                for (i in 0 until grid.childCount) {
                    val tile = grid.getChildAt(i)
                    val rowIdx = i / grid.columns
                    val distRows = abs(rowIdx - activeRow)
                    val targetAlpha = when {
                        activeRow < 0 -> if (rowIdx == 0) 1f else 0.48f
                        distRows == 0 -> 1f
                        distRows == 1 -> 0.48f
                        else -> 0.24f
                    }
                    val targetScale = if (activeRow >= 0 && distRows == 0) 1f else 0.95f
                    if (abs(tile.alpha - targetAlpha) > 0.02f) {
                        tile.animate()
                            .alpha(targetAlpha)
                            .scaleX(targetScale)
                            .scaleY(targetScale)
                            .setDuration(240L)
                            .setInterpolator(LayoutMetrics.SILKY_EASE)
                            .start()
                    }
                }
            }
        }

        scroll.viewTreeObserver.addOnScrollChangedListener {
            LiveBackdrop.onScrollParallax(scroll.scrollY)
        }

        scroll.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val vpH = bottom - top
            val grid = appGrid.findViewWithTag<SpatialGridDesign>("spatial_engine")
            val rowH = grid?.tileH ?: LayoutMetrics.dp(context, 92)
            if (vpH > 0 && rowH > 0) {
                val centerAnchorPad = maxOf(shelfPadV, ((vpH - rowH) / 2f).toInt())
                if (scrollContent.paddingTop != centerAnchorPad || scrollContent.paddingBottom != centerAnchorPad) {
                    scrollContent.setPadding(0, centerAnchorPad, 0, centerAnchorPad)
                }
                val focusedChild = grid?.findFocus()
                val activeRow = if (focusedChild != null && grid.columns > 0) {
                    grid.indexOfChild(focusedChild) / grid.columns
                } else -1
                applyLeanbackRowFocusVisuals(activeRow)
            }
        }

        scroll.viewTreeObserver.addOnGlobalFocusChangeListener(
            ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
                val grid = appGrid.findViewWithTag<SpatialGridDesign>("spatial_engine")
                if (newFocus == null || grid == null || scroll.height <= 0 || !isDescendantOf(newFocus, grid)) {
                    applyLeanbackRowFocusVisuals(-1)
                    return@OnGlobalFocusChangeListener
                }

                val childIdx = grid.indexOfChild(newFocus)
                val cols = grid.columns.coerceAtLeast(1)
                val activeRow = if (childIdx >= 0) childIdx / cols else 0
                val rowPitch = grid.tileH + grid.gap
                val maxScroll = maxOf(0, scrollContent.height - scroll.height)
                val targetScrollY = (activeRow * rowPitch).coerceIn(0, maxScroll)

                if (abs(scroll.scrollY - targetScrollY) > dp(1)) {
                    centerScrollAnim?.cancel()
                    centerScrollAnim = ObjectAnimator.ofInt(scroll, "scrollY", scroll.scrollY, targetScrollY).apply {
                        duration = 260L
                        interpolator = LayoutMetrics.SILKY_EASE
                        start()
                    }
                }
                applyLeanbackRowFocusVisuals(activeRow)
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

    // Reserves right-side stage width proportionally so app tiles stay clear of the widget/drawer.
    fun setRightStageInset(rightStageWidthPx: Int) {
        val gap = LayoutMetrics.dynamicSpace(context, ratio = 0.018f, minDp = 12, maxDp = 30, horizontal = true)
        bodyContainer.setPadding(0, 0, rightStageWidthPx + gap, 0)
    }

    // Returns whether the hidden apps ribbon is currently visible.
    fun isHiddenRibbonVisible(): Boolean = hiddenSection.visibility == View.VISIBLE

    // Populates and animates open the hidden apps ribbon.
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

    // Animates closed the hidden apps ribbon and notifies the caller to refresh focus/grid.
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

    // Scales a base dp integer into display-proportional pixels.
    private fun dp(value: Int): Int = LayoutMetrics.dp(context, value)
}
