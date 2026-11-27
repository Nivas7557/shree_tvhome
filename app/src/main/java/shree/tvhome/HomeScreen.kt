// Important instructions:
// - Reserve clean top & bottom avatar clearance using shared Themes metrics so TopHeaderBar never overlaps Favorites.
// - Render the 3-band CinemaConveyorShelf inside a 3D embedded recessed glass well with proportional depth shadows and specular rim.
package shree.tvhome

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes
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
    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        val hPad = LayoutMetrics.screenHorizontalPadding(context)
        val vPad = LayoutMetrics.screenVerticalPadding(context)
        val barHeight = LayoutMetrics.dynamicSpace(
            context,
            ratio = Themes.HEADER_BAR_HEIGHT_RATIO,
            minDp = Themes.HEADER_BAR_MIN_DP,
            maxDp = Themes.HEADER_BAR_MAX_DP,
            horizontal = false
        )
        val avatarOverhangRatio = (Themes.AVATAR_SIZE_MULTIPLIER - 1f) / 2f
        val topAvatarPad = maxOf(vPad, (barHeight * (avatarOverhangRatio + 0.10f)).toInt())
        val bottomAvatarGap = (barHeight * (avatarOverhangRatio + 0.12f)).toInt()
        val bandGap = LayoutMetrics.dynamicSpace(context, ratio = 0.012f, minDp = 8, maxDp = 14, horizontal = false)
        setPadding(hPad, topAvatarPad, hPad, vPad / 2)
        topBar = TopHeaderBar(context, settings, callbacks)
        addView(topBar, LayoutParams(-1, barHeight).apply { bottomMargin = bottomAvatarGap })
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
        val favHeader = AppsShelfDesign.createHeader(context, "♥  FAVORITES & QUICK ACCESS", isFavoritesRow = true).apply {
            val fPadH = LayoutMetrics.dynamicSpace(context, ratio = 0.010f, minDp = 8, maxDp = 14, horizontal = true)
            val fPadV = LayoutMetrics.dynamicSpace(context, ratio = 0.005f, minDp = 4, maxDp = 8, horizontal = false)
            setPadding(fPadH, fPadV, fPadH, fPadV)
            val syncFavHeader = {
                val glassCols = Themes.getGlassPanelColors(settings.tileColor, settings.accentColor, elevated = false)
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(LayoutMetrics.withAlpha(glassCols.first(), 165), LayoutMetrics.withAlpha(glassCols.last(), 185))
                ).apply {
                    cornerRadius = LayoutMetrics.dpF(context, 12f)
                    setStroke(LayoutMetrics.dp(context, 1), Color.argb(85, 255, 255, 255))
                }
            }
            syncFavHeader()
            settings.addStyleListener(syncFavHeader)
        }
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
        val favBandMaxH = LayoutMetrics.dynamicSpace(context, ratio = 0.21f, minDp = 118, maxDp = 168, horizontal = false)
        bodyContainer.addView(favSection, LayoutParams(-1, favBandMaxH).apply { bottomMargin = bandGap })
        val shelfPadH = LayoutMetrics.dynamicSpace(context, ratio = 0.014f, minDp = 12, maxDp = 22, horizontal = true)
        val shelfPadV = LayoutMetrics.dynamicSpace(context, ratio = 0.012f, minDp = 10, maxDp = 18, horizontal = false)
        // 3D Embedded Recessed Glass Well with top-left depth shadow, inner bevel, and bottom-right specular rim.
        val embeddedShelfWell = object : LinearLayout(context) {
            private val cavityFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val topDepthShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val leftDepthShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val bottomHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val bevelRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            private val outerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            private val wellRect = RectF()
            private val innerRect = RectF()
            private val clipPath = Path()
            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0f || h <= 0f) return
                val cornerR = LayoutMetrics.dynamicSpace(context, 0.022f, 18, 28, false).toFloat()
                val bevelInset = LayoutMetrics.dpF(context, 2f)
                wellRect.set(bevelInset, bevelInset, w - bevelInset, h - bevelInset)
                clipPath.reset()
                clipPath.addRoundRect(wellRect, cornerR, cornerR, Path.Direction.CW)
                val themeCols = Themes.getTheme(settings.tileColor).normalColors
                val deepBase = LayoutMetrics.withAlpha(themeCols.lastOrNull() ?: Color.rgb(10, 16, 36), 135)
                val deepTop = LayoutMetrics.withAlpha(Color.rgb(6, 10, 24), 175)
                canvas.save()
                canvas.clipPath(clipPath)
                // 1. Deep recessed cavity fill.
                cavityFillPaint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(deepTop, deepBase, LayoutMetrics.blendWithAccent(deepBase, settings.accentColor, 0.12f, 145)),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(wellRect, cavityFillPaint)
                // 2. Top & Left 3D inner cast shadow giving physical recessed depth.
                val shadowDepth = LayoutMetrics.dynamicSpace(context, 0.032f, 16, 32, false).toFloat()
                topDepthShadowPaint.shader = LinearGradient(
                    0f, 0f, 0f, shadowDepth,
                    intArrayOf(Color.argb(155, 0, 0, 0), Color.argb(45, 0, 0, 0), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, shadowDepth, topDepthShadowPaint)
                leftDepthShadowPaint.shader = LinearGradient(
                    0f, 0f, shadowDepth * 0.85f, 0f,
                    intArrayOf(Color.argb(115, 0, 0, 0), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, shadowDepth * 0.85f, h, leftDepthShadowPaint)
                // 3. Bottom & Right subtle bounce light inside the recessed floor.
                val bounceH = shadowDepth * 0.75f
                bottomHighlightPaint.shader = LinearGradient(
                    0f, h - bounceH, 0f, h,
                    intArrayOf(Color.TRANSPARENT, LayoutMetrics.withAlpha(settings.accentColor, 42)),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, h - bounceH, w, h, bottomHighlightPaint)
                canvas.restore()
                // 4. Crisp 3D recessed bevel stroke (dark top-left edge transitioning to luminous accent bottom-right).
                outerRimPaint.strokeWidth = LayoutMetrics.dpF(context, 2.2f)
                outerRimPaint.shader = LinearGradient(
                    0f, 0f, w, h,
                    intArrayOf(
                        Color.argb(150, 0, 0, 0),
                        Color.argb(65, 255, 255, 255),
                        LayoutMetrics.withAlpha(settings.accentColor, 210),
                        Color.argb(215, 255, 255, 255)
                    ),
                    floatArrayOf(0f, 0.35f, 0.72f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(wellRect, cornerR, cornerR, outerRimPaint)
                val innerInset = bevelInset + LayoutMetrics.dpF(context, 2f)
                innerRect.set(innerInset, innerInset, w - innerInset, h - innerInset)
                bevelRimPaint.strokeWidth = LayoutMetrics.dpF(context, 1f)
                bevelRimPaint.color = Color.argb(48, 255, 255, 255)
                canvas.drawRoundRect(
                    innerRect,
                    (cornerR - innerInset).coerceAtLeast(6f),
                    (cornerR - innerInset).coerceAtLeast(6f),
                    bevelRimPaint
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
        settings.addStyleListener { embeddedShelfWell.invalidate() }
        // Header control bar inside the 3D recessed well.
        val allHeader = AppsShelfDesign.createHeader(
            context, "⊞  ALL APPS", isFavoritesRow = false, settings = settings, onSortChanged = onSortChanged
        ).apply {
            val rPadH = LayoutMetrics.dynamicSpace(context, ratio = 0.012f, minDp = 10, maxDp = 16, horizontal = true)
            val rPadV = LayoutMetrics.dynamicSpace(context, ratio = 0.006f, minDp = 5, maxDp = 9, horizontal = false)
            setPadding(rPadH, rPadV, rPadH, rPadV)
            val syncRibbonBg = {
                val glassCols = Themes.getGlassPanelColors(settings.tileColor, settings.accentColor, elevated = true)
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(
                        LayoutMetrics.withAlpha(glassCols.first(), 210),
                        LayoutMetrics.withAlpha(glassCols.last(), 225)
                    )
                ).apply {
                    cornerRadius = LayoutMetrics.dpF(context, 14f)
                    setStroke(LayoutMetrics.dp(context, 1), LayoutMetrics.withAlpha(settings.accentColor, 165))
                }
            }
            syncRibbonBg()
            settings.addStyleListener(syncRibbonBg)
        }
        allCount = allHeader.findViewWithTag("count_tv")
        embeddedShelfWell.addView(allHeader, LayoutParams(-1, -2).apply { bottomMargin = shelfPadV / 2 })
        appGrid = LinearLayout(context).apply {
            orientation = VERTICAL
            clipChildren = true
            clipToPadding = false
        }
        embeddedShelfWell.addView(appGrid, LayoutParams(-1, 0, 1f))
        val shelfBottomMargin = vPad / 2
        bodyContainer.addView(embeddedShelfWell, LayoutParams(-1, 0, 1f).apply { bottomMargin = shelfBottomMargin })
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
}
