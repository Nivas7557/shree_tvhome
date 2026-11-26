// Important instructions:
// - Derive screen padding and spacing proportionally from display metrics so every TV resolution looks balanced.
// - Keep homepage layout composition centralized without hardcoded layout constants.

package shree.tvhome

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
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

    // Builds the top header bar, hidden ribbon, favorites shelf, and scrollable app grid with screen-scaled spacing.
    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false

        val hPad = LayoutMetrics.screenHorizontalPadding(context)
        val vPad = LayoutMetrics.screenVerticalPadding(context)
        val bottomPad = maxOf(dp(10), vPad / 2)
        val headerGap = maxOf(dp(12), vPad / 2)
        // Derive top header bar height consistently via LayoutMetrics.dynamicSpace across all TV resolutions.
        val barHeight = LayoutMetrics.dynamicSpace(context, ratio = 0.054f, minDp = 34, maxDp = 48, horizontal = false)

        setPadding(hPad, vPad, hPad, bottomPad)

        topBar = TopHeaderBar(context, settings, callbacks)
        addView(topBar, LayoutParams(-1, barHeight).apply { bottomMargin = headerGap })

        contentArea = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        addView(contentArea, LayoutParams(-1, 0, 1f))

        bodyContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutTransition = LayoutTransition().apply {
                setDuration(300L)
                enableTransitionType(LayoutTransition.CHANGING)
                setInterpolator(LayoutTransition.CHANGING, LayoutMetrics.FLUID_SPRING)
            }
        }
        contentArea.addView(bodyContainer, FrameLayout.LayoutParams(-1, -1))

        // Hidden apps ribbon shown only while managing hidden packages.
        hiddenSection = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
            setPadding(dp(10), dp(6), dp(10), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(95, 255, 95, 125), Color.argb(45, 32, 24, 58))
            ).apply {
                cornerRadius = LayoutMetrics.dpF(context, 18f)
                setStroke(dp(1), Color.argb(130, 255, 130, 155))
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
            setPadding(dp(12), dp(4), dp(12), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.argb(85, 255, 255, 255))
                cornerRadius = LayoutMetrics.dpF(context, 14f)
                setStroke(dp(1), Color.argb(140, 255, 255, 255))
            }
            setOnFocusChangeListener { v, f -> AnimationController.animateFocus(v, f, settings.motionStyle, 1.06f, 4f) }
            setOnClickListener { closeHiddenRibbon() }
        }
        hiddenHeader.addView(closeHiddenBtn, LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        hiddenSection.addView(hiddenHeader)

        val hiddenHScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            clipChildren = false
        }
        hiddenRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            clipChildren = false
        }
        hiddenHScroll.addView(hiddenRow)
        hiddenSection.addView(hiddenHScroll)
        bodyContainer.addView(hiddenSection, LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        // Favorites horizontal shelf.
        favSection = LinearLayout(context).apply { orientation = VERTICAL }
        val favHeader = AppsShelfDesign.createHeader(context, "♥  FAVORITES & QUICK ACCESS", isFavoritesRow = false)
        favCount = favHeader.findViewWithTag("count_tv")
        favSection.addView(favHeader)

        val favHScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            clipChildren = false
        }
        favRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            clipChildren = false
        }
        favHScroll.addView(favRow)
        favSection.addView(favHScroll)
        bodyContainer.addView(favSection)

        // All apps header and vertical scroll container with background parallax hook.
        val allHeader = AppsShelfDesign.createHeader(
            context, "⊞  ALL APPS", isFavoritesRow = false, settings = settings, onSortChanged = onSortChanged
        )
        allCount = allHeader.findViewWithTag("count_tv")
        bodyContainer.addView(allHeader, LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(24))
            viewTreeObserver.addOnScrollChangedListener { LiveBackdrop.onScrollParallax(scrollY) }
        }

        val scrollContent = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutTransition = LayoutTransition().apply {
                setDuration(200L)
                disableTransitionType(LayoutTransition.APPEARING)
                disableTransitionType(LayoutTransition.DISAPPEARING)
            }
        }
        appGrid = LinearLayout(context).apply {
            orientation = VERTICAL
            clipChildren = false
        }
        scrollContent.addView(appGrid)
        scroll.addView(scrollContent)
        bodyContainer.addView(scroll, LayoutParams(-1, 0, 1f))
    }

    // Reserves right-side stage width proportionally so app tiles stay clear of the widget/drawer.
    fun setRightStageInset(rightStageWidthPx: Int) {
        val gap = (context.resources.displayMetrics.widthPixels * 0.015f).toInt().coerceIn(dp(10), dp(28))
        bodyContainer.setPadding(0, dp(4), rightStageWidthPx + gap, 0)
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

    private fun dp(value: Int): Int = LayoutMetrics.dp(context, value)
}
