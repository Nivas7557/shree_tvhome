package shree.tvhome.design

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.core.GlassSurfaceSpec
import shree.tvhome.source.LauncherPreferences
import shree.tvhome.source.LauncherUtils
import shree.tvhome.source.MotionEngine
import shree.tvhome.source.TileThemePalette
import java.util.concurrent.ConcurrentHashMap

object AppTileFactory {

    interface TileActions {
        fun onToggleFavorite(pkg: String, label: String)
        fun onMoveFavorite(pkg: String, delta: Int)
        fun onHideApp(pkg: String, label: String)
    }

    data class TileMetrics(
        val widthPx: Int,
        val heightPx: Int,
        val iconSizePx: Int,
        val labelTextSizeSp: Float,
        val cornerRadiusDp: Float
    )

    private val labelCache = ConcurrentHashMap<String, String>()
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    fun clearAssetCache() {
        labelCache.clear()
        iconCache.clear()
    }

    fun getCachedLabel(pm: PackageManager, info: ResolveInfo): String {
        val pkg = info.activityInfo.packageName
        return labelCache.getOrPut(pkg) {
            info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
        }
    }

    private fun getCachedIcon(pm: PackageManager, info: ResolveInfo): Drawable {
        val pkg = info.activityInfo.packageName
        return iconCache.getOrPut(pkg) {
            info.loadIcon(pm)
        }
    }

    fun resolveMetrics(ctx: Context, lp: LauncherPreferences, customW: Int = 0, customH: Int = 0): TileMetrics {
        val sizeSpec = TileThemePalette.getTileSizeSpec(lp.tileSize)
        val baseW = if (customW > 0) customW else LauncherUtils.dp(ctx, sizeSpec.desiredGridDp)
        val baseH = if (customH > 0) customH else (baseW * 0.9f).toInt().coerceAtLeast(LauncherUtils.dp(ctx, 88))
        val baseIcon = (minOf(baseW, baseH) * 0.44f).toInt().coerceAtLeast(LauncherUtils.dp(ctx, 28))
        val labelSp = sizeSpec.labelSp

        val radiusDp = when (lp.tileShape) {
            "circle", "pill" -> (minOf(LauncherUtils.pxToDp(ctx, baseW), LauncherUtils.pxToDp(ctx, baseH)) * 0.45f).coerceAtLeast(16f)
            "square" -> 10f
            "rounded" -> 18f
            else -> 16f
        }

        return TileMetrics(
            widthPx = baseW,
            heightPx = baseH,
            iconSizePx = baseIcon,
            labelTextSizeSp = labelSp,
            cornerRadiusDp = radiusDp
        )
    }

    private fun resolveTileBackground(
        ctx: Context,
        lp: LauncherPreferences,
        cornerRadiusDp: Float,
        focused: Boolean
    ): GradientDrawable {
        val themeSpec = TileThemePalette.THEMES.find { it.key == lp.tileColor }
            ?: TileThemePalette.THEMES.firstOrNull()

        val accent = lp.accentColor
        val strokePx = LauncherUtils.dp(ctx, if (focused) 2 else 1).coerceAtLeast(1)

        if (themeSpec != null) {
            val topCol = if (focused) {
                LauncherUtils.withAlpha(accent, 150)
            } else {
                LauncherUtils.withAlpha(themeSpec.startColor, 125)
            }
            val bottomCol = if (focused) {
                LauncherUtils.withAlpha(accent, 180)
            } else {
                LauncherUtils.withAlpha(themeSpec.endColor, 100)
            }
            val borderCol = if (focused) {
                accent
            } else {
                LauncherUtils.withAlpha(accent, 95)
            }

            return GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(topCol, bottomCol)
            ).apply {
                cornerRadius = LauncherUtils.dpF(ctx, cornerRadiusDp)
                setStroke(strokePx, borderCol)
            }
        }

        return GlassSurfaceSpec.createDrawable(
            ctx = ctx,
            style = GlassSurfaceSpec.PANEL_CARD,
            accentColor = accent,
            focused = focused,
            active = false
        ).apply {
            cornerRadius = LauncherUtils.dpF(ctx, cornerRadiusDp)
        }
    }

    private fun resolveLabelBlockBackground(
        ctx: Context,
        lp: LauncherPreferences,
        cornerRadiusDp: Float,
        focused: Boolean
    ): GradientDrawable {
        val themeSpec = TileThemePalette.THEMES.find { it.key == lp.tileColor }
        val accent = lp.accentColor
        val baseFill = themeSpec?.endColor ?: accent
        val fillColor = if (focused) {
            LauncherUtils.withAlpha(accent, 195)
        } else {
            LauncherUtils.withAlpha(baseFill, 115)
        }
        val strokeColor = if (focused) {
            Color.WHITE
        } else {
            LauncherUtils.withAlpha(accent, 135)
        }

        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = LauncherUtils.dpF(ctx, (cornerRadiusDp * 0.65f).coerceIn(8f, 16f))
            setStroke(LauncherUtils.dp(ctx, 1).coerceAtLeast(1), strokeColor)
        }
    }

    fun updateTileAppearance(ctx: Context, tileView: View, lp: LauncherPreferences, isFav: Boolean) {
        val metrics = resolveMetrics(ctx, lp, tileView.layoutParams?.width ?: 0, tileView.layoutParams?.height ?: 0)
        val focused = tileView.hasFocus()
        tileView.background = resolveTileBackground(ctx, lp, metrics.cornerRadiusDp, focused)

        val labelBlock = tileView.findViewWithTag<TextView>("label_block")
        if (labelBlock != null) {
            labelBlock.textSize = metrics.labelTextSizeSp
            labelBlock.background = resolveLabelBlockBackground(ctx, lp, metrics.cornerRadiusDp, focused)
            labelBlock.isSelected = focused
        }

        val badgeView = tileView.findViewWithTag<TextView>("fav_badge")
        if (badgeView != null) {
            if (!isFav || lp.badgeMode == "none") {
                badgeView.visibility = View.GONE
            } else {
                badgeView.visibility = View.VISIBLE
                badgeView.text = when (lp.badgeMode) {
                    "star" -> "★"
                    "dot" -> "•"
                    else -> "♥"
                }
                badgeView.background = GradientDrawable().apply {
                    setColor(LauncherUtils.withAlpha(lp.accentColor, 220))
                    cornerRadius = LauncherUtils.dpF(ctx, 8f)
                }
            }
        }
    }

    fun create(
        ctx: Context,
        pm: PackageManager,
        lp: LauncherPreferences,
        resolveInfo: ResolveInfo,
        isFavRow: Boolean,
        isFav: Boolean,
        canMoveLeft: Boolean,
        canMoveRight: Boolean,
        w: Int,
        h: Int,
        actions: TileActions
    ): View {
        val pkg = resolveInfo.activityInfo.packageName
        val appLabel = getCachedLabel(pm, resolveInfo)
        val metrics = resolveMetrics(ctx, lp, w, h)

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            tag = if (isFavRow) "fav_$pkg" else "all_$pkg"
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            isLongClickable = true
            clipChildren = false
            clipToPadding = false
            setPadding(
                LauncherUtils.dp(ctx, 8),
                LauncherUtils.dp(ctx, 8),
                LauncherUtils.dp(ctx, 8),
                LauncherUtils.dp(ctx, 8)
            )
            layoutParams = ViewGroup.LayoutParams(metrics.widthPx, metrics.heightPx)
            background = resolveTileBackground(ctx, lp, metrics.cornerRadiusDp, focused = false)
        }

        val iconStage = FrameLayout(ctx).apply {
            clipChildren = false
            clipToPadding = false
        }

        val iconView = ImageView(ctx).apply {
            setImageDrawable(getCachedIcon(pm, resolveInfo))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        iconStage.addView(
            iconView,
            FrameLayout.LayoutParams(
                metrics.iconSizePx,
                metrics.iconSizePx,
                Gravity.CENTER
            )
        )

        val badgeView = TextView(ctx).apply {
            tag = "fav_badge"
            text = when (lp.badgeMode) {
                "star" -> "★"
                "dot" -> "•"
                else -> "♥"
            }
            textSize = 9f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = if (isFav && lp.badgeMode != "none") View.VISIBLE else View.GONE
            setPadding(
                LauncherUtils.dp(ctx, 5),
                LauncherUtils.dp(ctx, 1),
                LauncherUtils.dp(ctx, 5),
                LauncherUtils.dp(ctx, 1)
            )
            background = GradientDrawable().apply {
                setColor(LauncherUtils.withAlpha(lp.accentColor, 220))
                cornerRadius = LauncherUtils.dpF(ctx, 8f)
            }
        }
        iconStage.addView(
            badgeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
        )

        container.addView(
            iconStage,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // App name block: dynamic width (no fixed width) + accent/theme palette styling
        val labelBlock = TextView(ctx).apply {
            tag = "label_block"
            text = appLabel
            textSize = metrics.labelTextSizeSp
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = false
            setPadding(
                LauncherUtils.dp(ctx, 8),
                LauncherUtils.dp(ctx, 3),
                LauncherUtils.dp(ctx, 8),
                LauncherUtils.dp(ctx, 3)
            )
            background = resolveLabelBlockBackground(ctx, lp, metrics.cornerRadiusDp, focused = false)
        }

        container.addView(
            labelBlock,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = LauncherUtils.dp(ctx, 6)
            }
        )

        container.setOnFocusChangeListener { v, hasFocus ->
            MotionEngine.animateFocus(v, hasFocus, lp.motionStyle, 1.05f, 3f)
            updateTileAppearance(ctx, v, lp, isFav)
        }

        container.setOnClickListener {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(launchIntent)
            }
        }

        val openOptions = {
            AppActionDialog.show(
                ctx = ctx,
                pkg = pkg,
                appName = appLabel,
                isFav = isFav,
                canMoveLeft = canMoveLeft,
                canMoveRight = canMoveRight,
                accent = lp.accentColor,
                isHiddenMode = false,
                onToggleFav = { actions.onToggleFavorite(pkg, appLabel) },
                onMoveLeft = { actions.onMoveFavorite(pkg, -1) },
                onMoveRight = { actions.onMoveFavorite(pkg, 1) },
                onHideOrUnhideApp = { actions.onHideApp(pkg, appLabel) }
            )
            true
        }

        container.setOnLongClickListener { openOptions() }

        return container
    }
}
