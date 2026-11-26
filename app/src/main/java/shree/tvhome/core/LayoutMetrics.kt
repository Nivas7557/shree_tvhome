// Important instructions:
// - Compute adaptive UI scaling and proportional screen paddings dynamically across TV screen sizes.
// - Keep shared interpolators, app discovery, and color helpers in one single source of truth.

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
        val list = pm.queryIntentActivities(query, 0)
        for (app in list) {
            val pkg = app.activityInfo?.packageName ?: continue
            val name = app.activityInfo?.name ?: continue
            if (selfPackageName.isNotEmpty() && pkg == selfPackageName) continue
            out["$pkg/$name"] = app
        }
    }

    // Computes an adaptive scale factor so UI elements fit 720p, 1080p, and 4K TV screens proportionally.
    fun calculateAdaptiveScale(context: Context): Float {
        val dm = context.resources.displayMetrics
        val maxSidePx = maxOf(dm.widthPixels, dm.heightPixels)
        val widthDp = maxSidePx / dm.density.coerceAtLeast(0.5f)
        return (widthDp / 960f).coerceIn(0.82f, 1.18f)
    }

    // Computes responsive horizontal/vertical screen padding from display dimensions instead of fixed values.
    fun screenHorizontalPadding(context: Context): Int {
        val w = context.resources.displayMetrics.widthPixels
        return (w * 0.028f).roundToInt().coerceIn(dp(context, 16), dp(context, 48))
    }

    // Computes responsive top/bottom screen padding from display height.
    fun screenVerticalPadding(context: Context): Int {
        val h = context.resources.displayMetrics.heightPixels
        return (h * 0.032f).roundToInt().coerceIn(dp(context, 14), dp(context, 36))
    }

    // Converts a base dp value into adaptively scaled pixels.
    fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density * calculateAdaptiveScale(context)).roundToInt()
    }

    // Converts a base dp value into adaptively scaled floating-point pixels.
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

    // Applies focus animation using the active motion style stored in AppSettings.
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
