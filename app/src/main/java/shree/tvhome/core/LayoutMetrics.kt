// Important instructions:
// - Derive all scaling, dynamic spacing, and paddings from display metrics so no TV screen uses fixed constants.
// - Keep shared interpolators, app discovery, and color blending in one central place.

package shree.tvhome.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.roundToInt

object LayoutMetrics {
    val FLUID_SPRING = OvershootInterpolator(1.45f)
    val SILKY_EASE = DecelerateInterpolator(1.8f)

    // Queries launchable activities for a category while excluding the launcher package itself.
    fun addApps(
        pm: PackageManager,
        category: String,
        out: MutableMap<String, ResolveInfo>,
        selfPackageName: String = ""
    ) {
        val query = Intent(Intent.ACTION_MAIN).apply { addCategory(category) }
        val list = try { pm.queryIntentActivities(query, 0) } catch (_: Exception) { emptyList() }
        for (app in list) {
            val pkg = app.activityInfo?.packageName ?: continue
            val name = app.activityInfo?.name ?: continue
            if (selfPackageName.isNotEmpty() && pkg == selfPackageName) continue
            out["$pkg/$name"] = app
        }
    }

    // Computes an adaptive scale factor from display width in dp across 720p, 1080p, and 4K screens.
    fun calculateAdaptiveScale(context: Context): Float {
        val dm = context.resources.displayMetrics
        val maxSidePx = maxOf(dm.widthPixels, dm.heightPixels)
        val widthDp = maxSidePx / dm.density.coerceAtLeast(0.5f)
        return (widthDp / 960f).coerceIn(0.82f, 1.18f)
    }

    // Computes a screen-proportional spacing value (horizontal or vertical) bounded dynamically.
    fun dynamicSpace(context: Context, ratio: Float, minDp: Int, maxDp: Int, horizontal: Boolean = true): Int {
        val dm = context.resources.displayMetrics
        val basePx = if (horizontal) dm.widthPixels else dm.heightPixels
        val scaled = (basePx * ratio).roundToInt()
        return scaled.coerceIn(dp(context, minDp), dp(context, maxDp))
    }

    // Computes responsive horizontal screen margin/padding from display width.
    fun screenHorizontalPadding(context: Context): Int =
        dynamicSpace(context, ratio = 0.028f, minDp = 16, maxDp = 48, horizontal = true)

    // Computes responsive vertical screen margin/padding from display height.
    fun screenVerticalPadding(context: Context): Int =
        dynamicSpace(context, ratio = 0.032f, minDp = 14, maxDp = 36, horizontal = false)

    // Converts a base dp unit into density- and screen-scaled pixels.
    fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density * calculateAdaptiveScale(context)).roundToInt()
    }

    // Converts a base dp unit into density- and screen-scaled floating-point pixels.
    fun dpF(context: Context, value: Float): Float {
        return value * context.resources.displayMetrics.density * calculateAdaptiveScale(context)
    }

    // Creates a rounded glass GradientDrawable with optional border stroke.
    fun createGlassDrawable(
        ctx: Context,
        bgColor: Int,
        strokeColor: Int,
        radiusDp: Float,
        strokeDp: Float = 1f
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dpF(ctx, radiusDp)
            setStroke(dpF(ctx, strokeDp).roundToInt(), strokeColor)
        }
    }

    // Applies focus animation using the active motion style from AppSettings.
    fun applyFocusSpring(view: View, focused: Boolean, scale: Float = 1.06f, elevationDp: Float = 8f) {
        val style = AppSettings(view.context).motionStyle
        AnimationController.animateFocus(view, focused, style, scale, elevationDp)
    }

    // Returns a copy of color with alpha clamped to 0..255.
    fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    // Blends baseColor toward accentColor by ratio and applies the target alpha.
    fun blendWithAccent(baseColor: Int, accentColor: Int, ratio: Float, alpha: Int): Int {
        val t = ratio.coerceIn(0f, 1f)
        val r = (Color.red(baseColor) + (Color.red(accentColor) - Color.red(baseColor)) * t).toInt()
        val g = (Color.green(baseColor) + (Color.green(accentColor) - Color.green(baseColor)) * t).toInt()
        val b = (Color.blue(baseColor) + (Color.blue(accentColor) - Color.blue(baseColor)) * t).toInt()
        return Color.argb(alpha.coerceIn(0, 255), r, g, b)
    }
}
