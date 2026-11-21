package shree.tvhome.design

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
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

object AppTileFactory {

    data class TileMetrics(
        val widthPx: Int,
        val heightPx: Int,
        val iconSizePx: Int,
        val labelTextSizeSp: Float,
        val cornerRadiusDp: Float
    )

    fun resolveMetrics(ctx: Context, lp: LauncherPreferences): TileMetrics {
        val sizeSpec = TileThemePalette.TILE_SIZES.find { it.key == lp.tileSize }
            ?: TileThemePalette.TILE_SIZES.find { it.key == "medium" }
            ?: TileThemePalette.TILE_SIZES.firstOrNull()

        val baseW = sizeSpec?.widthDp ?: 132
        val baseH = sizeSpec?.heightDp ?: 112
        val baseIcon = sizeSpec?.iconDp ?: 42
        val labelSp = sizeSpec?.labelSp ?: 10.5f

        val shapeSpec = TileThemePalette.TILE_SHAPES.find { it.key == lp.tileShape }
        val radiusDp = when (shapeSpec?.key) {
            "circle", "pill" -> (minOf(baseW, baseH) * 0.45f).coerceAtLeast(16f)
            "square" -> 10f
            "rounded" -> 18f
            else -> shapeSpec?.cornerDp ?: 16f
        }

        return TileMetrics(
            widthPx = LauncherUtils.dp(ctx, baseW),
            heightPx = LauncherUtils.dp(ctx, baseH),
            iconSizePx = LauncherUtils.dp(ctx, baseIcon),
            labelTextSizeSp = labelSp,
            cornerRadiusDp = radiusDp
        )
    }

    private fun resolveTileBackground(
        ctx: Context,
        lp: LauncherPreferences,
        metrics: TileMetrics,
        focused: Boolean
    ): GradientDrawable {
        val themeSpec = TileThemePalette.THEMES.find { it.key == lp.tileColor }
            ?: TileThemePalette.THEMES.firstOrNull()

        val accent = lp.accentColor
        val strokePx = LauncherUtils.dp(ctx, if (focused) 2 else 1).coerceAtLeast(1)

        if (themeSpec != null) {
            val topCol = if (focused) {
                LauncherUtils.withAlpha(accent, 145)
            } else {
                LauncherUtils.withAlpha(themeSpec.startColor, 120)
            }
            val bottomCol = if (focused) {
                LauncherUtils.withAlpha(themeSpec.endColor, 175)
            } else {
                LauncherUtils.withAlpha(themeSpec.endColor, 95)
            }
            val borderCol = if (focused) {
                accent
            } else {
                LauncherUtils.withAlpha(accent, 90)
            }

            return GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(topCol, bottomCol)
            ).apply {
                cornerRadius = LauncherUtils.dpF(ctx, metrics.cornerRadiusDp)
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
            cornerRadius = LauncherUtils.dpF(ctx, metrics.cornerRadiusDp)
        }
    }

    private fun resolveLabelBlockBackground(
        ctx: Context,
        lp: LauncherPreferences,
        cornerRadiusDp: Float,
        focused: Boolean
    ): GradientDrawable {
        val accent = lp.accentColor
        val fillColor = if (focused) {
            LauncherUtils.withAlpha(accent, 185)
        } else {
            LauncherUtils.withAlpha(accent, 75)
        }
        val strokeColor = if (focused) {
            Color.WHITE
        } else {
            LauncherUtils.withAlpha(accent, 130)
        }

        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = LauncherUtils.dpF(ctx, (cornerRadiusDp * 0.65f).coerceIn(8f, 16f))
            setStroke(LauncherUtils.dp(ctx, 1).coerceAtLeast(1), strokeColor)
        }
    }

    fun createAppTile(
        ctx: Context,
        pm: PackageManager,
        resolveInfo: ResolveInfo,
        lp: LauncherPreferences,
        isFavorite: Boolean = false,
        onClick: (ResolveInfo) -> Unit,
        onLongClick: ((ResolveInfo) -> Boolean)? = null
    ): View {
        val metrics = resolveMetrics(ctx, lp)
        val appLabel = resolveInfo.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty {
            resolveInfo.activityInfo.packageName
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            isLongClickable = onLongClick != null
            clipChildren = false
            clipToPadding = false
            setPadding(
                LauncherUtils.dp(ctx, 8),
                LauncherUtils.dp(ctx, 8),
                LauncherUtils.dp(ctx, 8),
                LauncherUtils.dp(ctx, 8)
            )
            layoutParams = ViewGroup.LayoutParams(metrics.widthPx, metrics.heightPx)
            background = resolveTileBackground(ctx, lp, metrics, focused = false)
        }

        val iconStage = FrameLayout(ctx).apply {
            clipChildren = false
            clipToPadding = false
        }

        val iconView = ImageView(ctx).apply {
            setImageDrawable(resolveInfo.loadIcon(pm))
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

        if (isFavorite && lp.badgeMode != "none") {
            val badgeView = TextView(ctx).apply {
                text = when (lp.badgeMode) {
                    "star" -> "★"
                    "dot" -> "•"
                    else -> "♥"
                }
                textSize = 9f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
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
        }

        container.addView(
            iconStage,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // App name block: adapts naturally to tile/content width (no rigid fixed width) & uses accent/theme palette
        val labelBlock = TextView(ctx).apply {
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
            v.background = resolveTileBackground(ctx, lp, metrics, focused = hasFocus)
            labelBlock.background = resolveLabelBlockBackground(ctx, lp, metrics.cornerRadiusDp, focused = hasFocus)
            labelBlock.isSelected = hasFocus
        }

        container.setOnClickListener { onClick(resolveInfo) }
        if (onLongClick != null) {
            container.setOnLongClickListener { onLongClick(resolveInfo) }
        }

        return container
    }
}
