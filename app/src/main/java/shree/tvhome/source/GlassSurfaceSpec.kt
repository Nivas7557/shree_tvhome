package shree.tvhome.source

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object GlassSurfaceSpec {

    data class SurfaceStyle(
        val radiusDp: Float,
        val strokeDp: Float,
        val bgAlphaNormal: Int,
        val bgAlphaFocused: Int,
        val strokeAlphaNormal: Int,
        val strokeAlphaFocused: Int,
        val useAccentFillWhenActive: Boolean = false
    )

    val PILL_NAV = SurfaceStyle(
        radiusDp = 22f,
        strokeDp = 1f,
        bgAlphaNormal = 54,
        bgAlphaFocused = 110,
        strokeAlphaNormal = 95,
        strokeAlphaFocused = 235
    )

    val GREETING_CARD = SurfaceStyle(
        radiusDp = 20f,
        strokeDp = 1f,
        bgAlphaNormal = 85,
        bgAlphaFocused = 115,
        strokeAlphaNormal = 115,
        strokeAlphaFocused = 180
    )

    val PANEL_CARD = SurfaceStyle(
        radiusDp = 14f,
        strokeDp = 1f,
        bgAlphaNormal = 52,
        bgAlphaFocused = 115,
        strokeAlphaNormal = 65,
        strokeAlphaFocused = 230
    )

    val PANEL_CHIP = SurfaceStyle(
        radiusDp = 12f,
        strokeDp = 1f,
        bgAlphaNormal = 52,
        bgAlphaFocused = 120,
        strokeAlphaNormal = 65,
        strokeAlphaFocused = 240,
        useAccentFillWhenActive = true
    )

    fun createDrawable(
        ctx: Context,
        style: SurfaceStyle,
        accentColor: Int,
        focused: Boolean = false,
        active: Boolean = false,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TOP_BOTTOM
    ): GradientDrawable {
        val highlighted = focused || active
        val strokeW = LauncherUtils.dp(ctx, if (highlighted) (style.strokeDp + 1f).toInt() else style.strokeDp.toInt()).coerceAtLeast(1)
        val strokeCol = if (focused) {
            accentColor
        } else if (active && style.useAccentFillWhenActive) {
            Color.WHITE
        } else {
            Color.argb(if (highlighted) style.strokeAlphaFocused else style.strokeAlphaNormal, 255, 255, 255)
        }

        val colors = if (style.useAccentFillWhenActive && active) {
            intArrayOf(
                LauncherUtils.withAlpha(accentColor, 165),
                LauncherUtils.withAlpha(accentColor, 95)
            )
        } else {
            val topAlpha = if (highlighted) style.bgAlphaFocused else style.bgAlphaNormal
            val botAlpha = (topAlpha * 0.55f).toInt().coerceIn(16, 255)
            intArrayOf(
                Color.argb(topAlpha, 22, 32, 68),
                Color.argb(botAlpha, 14, 22, 48)
            )
        }

        return GradientDrawable(orientation, colors).apply {
            cornerRadius = LauncherUtils.dpF(ctx, style.radiusDp)
            setStroke(strokeW, strokeCol)
        }
    }
}
