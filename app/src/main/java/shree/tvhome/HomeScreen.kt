// Important instructions:
// - Structure the home screen into proportional bands: TopHeader (~12%), Favorites (~24%), and Glossy App Shelf (~64%).
// - Render a thick, shining rounded glass container for the App Shelf with a top control ribbon and center-locked fading rows.

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
        // Reserve proportional top clearance so the circular avatar never clips at the top screen edge.
        val topClearance = maxOf(vPad, (barHeight * 0.66f).toInt())
        val bandGap = LayoutMetrics.dynamicSpace(context, ratio = 0.014f, minDp = 8, maxDp = 18, horizontal = false)

        setPadding(hPad, topClearance, hPad, vPad / 2)

        // Band 1 (~12%): Top navigation and profile header bar.
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

        // Optional Hidden Apps management ribbon.
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

        // Band 2 (~24%): Favorites & Quick Access ribbon.
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
        // Band 3 (~64%): Glossy outer glass App Shelf with thick shining rounded edges & center-locked rows.
        val shelfPadH = LayoutMetrics.dynamicSpace(context, ratio = 0.012f, minDp = 10, maxDp = 18, horizontal = true)
        val shelfPadV = LayoutMetrics.dynamicSpace(context, ratio = 0.010f, minDp = 8, maxDp = 14, horizontal = false)

        val glossyShelfBox = object : LinearLayout(context) {
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val rimOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            private val rimInnerSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            private val boxRect = RectF()
            private val innerRect = RectF()

            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0f || h <= 0f) return
                val strokeW = LayoutMetrics.dpF(context, 2.2f)
                val halfStroke = strokeW / 2f
                val cornerR = LayoutMetrics.dpF(context, 22f)

                boxRect.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)
                val glassCols = Themes.getGlassPanelColors(settings.tileColor, settings.accentColor, elevated = false)
                fillPaint.shader = LinearGradient(
                    0f, 0f, w * 0.45f, h,
                    intArrayOf(
                        LayoutMetrics.withAlpha(glassCols.first(), 168),
                        LayoutMetrics.withAlpha(glassCols.last(), 196)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(boxRect, cornerR, cornerR, fillPaint)

                // Thick shining outer edge gradient blending accent glow with crisp specular white.
                rimOuterPaint.strokeWidth = strokeW
                rimOuterPaint.shader = LinearGradient(
                    0f, 0f, w, h,
                    intArrayOf(
                        Color.argb(215, 255, 255, 255),
                        LayoutMetrics.withAlpha(settings.accentColor, 210),
                        Color.argb(110, 255, 255, 255),
                        LayoutMetrics.withAlpha(settings.accentColor, 225)
                    ),
                    floatArrayOf(0f, 0.35f, 0.70f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(boxRect, cornerR, cornerR, rimOuterPaint)

                // Subtle inner glossy bevel highlight just inside the thick outer edge.
                val inset = strokeW + LayoutMetrics.dpF(context, 1.2f)
                innerRect.set(inset, inset, w - inset, h - inset)
                rimInnerSheenPaint.strokeWidth = LayoutMetrics.dpF(context, 1f)
                rimInnerSheenPaint.color = Color.argb(72, 255, 255, 255)
                canvas.drawRoundRect(innerRect, (cornerR - inset).coerceAtLeast(4f), (cornerR - inset).coerceAtLeast(4f), rimInnerSheenPaint)

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

        // Top control ribbon inside the glossy shelf (~5% screen band: All Apps, A-Z Order, App Count).
        val allHeader = AppsShelfDesign.createHeader(
            context, "⊞  ALL APPS", isFavoritesRow = false, settings = settings, onSortChanged = onSortChanged
        )
        allCount = allHeader.findViewWithTag("count_tv")
        glossyShelfBox.addView(allHeader, LayoutParams(-1, -2))

        // Subtle luminous divider separating the top control ribbon from the 3-band app viewport.
        val divider = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.TRANSPARENT, Color.argb(95, 255, 255, 255), Color.TRANSPARENT)
            )
        }
        glossyShelfBox.addView(divider, LayoutParams(-1, LayoutMetrics.dp(context, 1)).apply {
            topMargin = shelfPadV / 3
            bottomMargin = shelfPadV / 2
        })

        // Non-focusable center-locking viewport showing upper faded row, center focus row, and lower faded row.
        val fadeEdgeLen = LayoutMetrics.dynamicSpace(context, ratio = 0.055f, minDp = 28, maxDp = 52, horizontal = false)
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

        // Apply linear fade and depth scale to rows above and below the center focus band.
        val updateRowBandVisuals: () -> Unit = {
            val grid = appGrid.findViewWithTag<SpatialGridDesign>("spatial_engine")
            val vpH = scroll.height.toFloat()
            if (grid != null && vpH > 0f) {
                val vpCenterY = scroll.scrollY + (vpH / 2f)
                val bandRadius = (vpH * 0.46f).coerceAtLeast(1f)
                for (i in 0 until grid.childCount) {
                    val tile = grid.getChildAt(i)
                    val tileCenterY = scrollContent.paddingTop + tile.y + (tile.height / 2f)
                    val distRatio = (abs(tileCenterY - vpCenterY) / bandRadius).coerceIn(0f, 1f)
                    if (tile.hasFocus() || distRatio < 0.18f) {
                        tile.alpha = 1f
                        tile.scaleX = 1f
                        tile.scaleY = 1f
                    } else {
                        tile.alpha = (1f - (distRatio * 0.68f)).coerceIn(0.32f, 0.92f)
                        val depthScale = (1f - (distRatio * 0.06f)).coerceIn(0.94f, 1f)
                        tile.scaleX = depthScale
                        tile.scaleY = depthScale
                    }
                }
            }
        }

        scroll.viewTreeObserver.addOnScrollChangedListener {
            LiveBackdrop.onScrollParallax(scroll.scrollY)
            updateRowBandVisuals()
        }

        // Keep top/bottom scroll buffers proportional so Row 0 and the last row center cleanly inside the shelf.
        scroll.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val vpH = bottom - top
            val grid = appGrid.findViewWithTag<SpatialGridDesign>("spatial_engine")
            val rowH = grid?.tileH ?: LayoutMetrics.dp(context, 96)
            if (vpH > 0 && rowH > 0) {
                val centerBuf = maxOf(shelfPadV, ((vpH - rowH) / 2f).toInt())
                if (scrollContent.paddingTop != centerBuf || scrollContent.paddingBottom != centerBuf) {
                    scrollContent.setPadding(0, centerBuf, 0, centerBuf)
                }
                updateRowBandVisuals()
            }
        }

        // Glide the focused row into the exact center 25% band on every D-Pad row change.
        scroll.viewTreeObserver.addOnGlobalFocusChangeListener(
            ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
                if (newFocus == null || scroll.height <= 0) return@OnGlobalFocusChangeListener
                if (!isDescendantOf(newFocus, appGrid)) {
                    updateRowBandVisuals()
                    return@OnGlobalFocusChangeListener
                }

                val tileCenterY = scrollContent.paddingTop + newFocus.y + (newFocus.height / 2f)
                val viewportCenterY = scroll.height / 2f
                val maxScroll = maxOf(0, scrollContent.height - scroll.height)
                val targetScrollY = (tileCenterY - viewportCenterY).toInt().coerceIn(0, maxScroll)

                if (abs(scroll.scrollY - targetScrollY) > dp(2)) {
                    centerScrollAnim?.cancel()
                    centerScrollAnim = ObjectAnimator.ofInt(scroll, "scrollY", scroll.scrollY, targetScrollY).apply {
                        duration = 280L
                        interpolator = LayoutMetrics.SILKY_EASE
                        start()
                    }
                }
                updateRowBandVisuals()
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
