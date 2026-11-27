// Important instructions:
// - Assemble lightweight app tiles delegating visual silhouette/aura to FocusAppGlowDesign and context actions to AppMenuDialogDesign.
// - Extract and cache a 16x16 dominant icon color and app label once per package to keep RAM and CPU load near zero.
package shree.tvhome.design

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes
import java.util.concurrent.ConcurrentHashMap

object AppTileDesign {
    interface TileActions {
        fun onToggleFavorite(pkg: String, label: String)
        fun onMoveFavorite(pkg: String, delta: Int)
        fun onHideApp(pkg: String, label: String)
    }
    private val labelCache = ConcurrentHashMap<String, String>()
    private val dominantColorCache = ConcurrentHashMap<String, Int>()
    // Releases cached labels and dominant icon colors to keep RAM clean.
    fun clearAssetCache() {
        labelCache.clear()
        dominantColorCache.clear()
    }
    // Returns cached app display label without repeated PackageManager IPC calls.
    fun getCachedLabel(pm: PackageManager, info: ResolveInfo): String {
        val pkg = info.activityInfo.packageName
        return labelCache.getOrPut(pkg) {
            info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
        }
    }
    // Extracts a vibrant dominant color from a tiny 16x16 bitmap sample and recycles immediately.
    private fun extractDominantColor(pkg: String, iconDrawable: Drawable?, fallbackColor: Int): Int {
        dominantColorCache[pkg]?.let { return it }
        if (iconDrawable == null) return fallbackColor
        var sampleBmp: Bitmap? = null
        return try {
            val size = 16
            sampleBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sampleBmp)
            iconDrawable.setBounds(0, 0, size, size)
            iconDrawable.draw(canvas)
            var bestColor = fallbackColor
            var bestScore = -1f
            val hsv = FloatArray(3)
            for (y in 2 until size - 2) {
                for (x in 2 until size - 2) {
                    val px = sampleBmp.getPixel(x, y)
                    if (Color.alpha(px) < 160) continue
                    Color.colorToHSV(px, hsv)
                    val sat = hsv.get(1)
                    val valBrightness = hsv.get(2)
                    if (valBrightness < 0.22f) continue
                    val score = (sat * 2.2f) + (valBrightness * 0.8f)
                    if (score > bestScore) {
                        bestScore = score
                        bestColor = px
                    }

                    if (score > bestScore) {
                        bestScore = score
                        bestColor = px
                    }
                }
            }
            dominantColorCache[pkg] = bestColor
            bestColor
        } catch (_: Exception) {
            fallbackColor
        } finally {
            sampleBmp?.let { if (!it.isRecycled) it.recycle() }
        }
    }
    // Builds a focusable glass app tile using FocusAppGlowDesign and AppMenuDialogDesign.
    fun create(
        ctx: Context,
        pm: PackageManager,
        settings: AppSettings,
        info: ResolveInfo,
        isFavRow: Boolean,
        isFav: Boolean,
        canMoveLeft: Boolean,
        canMoveRight: Boolean,
        w: Int,
        h: Int,
        actions: TileActions,
        isHiddenMode: Boolean = false
    ): View {
        val pkg = info.activityInfo.packageName
        val label = getCachedLabel(pm, info)
        val prefix = if (isHiddenMode) "hidden_" else if (isFavRow) "fav_" else "all_"
        val iconDrawable = try { info.loadIcon(pm) } catch (_: Exception) { null }
        val dominantCol = extractDominantColor(pkg, iconDrawable, settings.accentColor)
        val surface = FocusAppGlowDesign(ctx, settings, dominantCol).apply {
            tag = "$prefix$pkg"
            isFocusable = true
            isClickable = true
            isLongClickable = true
        }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val padH = LayoutMetrics.dynamicSpace(ctx, 0.008f, 6, 12, true)
            val padV = LayoutMetrics.dynamicSpace(ctx, 0.006f, 4, 10, false)
            setPadding(padH, padV, padH, padV)
        }
        val iconBox = FrameLayout(ctx)
        val iconSize = (minOf(w, h) * 0.44f).toInt().coerceAtLeast(LayoutMetrics.dp(ctx, 26))
        val iconView = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (iconDrawable != null) setImageDrawable(iconDrawable)
        }
        iconBox.addView(iconView, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
        val badgeStr = settings.iconBadge
        if (badgeStr.isNotEmpty()) {
            val badgeTv = TextView(ctx).apply {
                text = badgeStr
                textSize = 9.5f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(215, 22, 28, 46))
                    setStroke(LayoutMetrics.dp(ctx, 1), Color.argb(120, 255, 255, 255))
                }
            }
            val bSize = LayoutMetrics.dp(ctx, 18)
            iconBox.addView(badgeTv, FrameLayout.LayoutParams(bSize, bSize, Gravity.BOTTOM or Gravity.END))
        }
        val labelTv = TextView(ctx).apply {
            tag = "tile_label"
            text = label
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            visibility = when (settings.nameplateMode) {
                "hidden" -> View.GONE
                "focus", "focus_only" -> View.INVISIBLE
                else -> View.VISIBLE
            }
            setTextColor(Themes.getPrimaryTextColor(settings.tileColor))
        }
        inner.addView(iconBox, LinearLayout.LayoutParams(-2, -2))
        inner.addView(labelTv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = LayoutMetrics.dp(ctx, 5) })
        surface.addView(inner, FrameLayout.LayoutParams(-1, -1))
        if (isFav && !isHiddenMode) {
            val starTv = TextView(ctx).apply {
                text = "★"
                textSize = 10.5f
                setTextColor(Color.rgb(251, 191, 36))
            }
            val m = LayoutMetrics.dp(ctx, 7)
            surface.addView(starTv, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = m; rightMargin = m
            })
        }
        val openContextMenu = {
            AppMenuDialogDesign.show(
                ctx = ctx,
                pm = pm,
                settings = settings,
                pkg = pkg,
                label = label,
                isFavRow = isFavRow,
                isFav = isFav,
                canMoveLeft = canMoveLeft,
                canMoveRight = canMoveRight,
                isHiddenMode = isHiddenMode,
                onLaunch = { launchApp(ctx, pm, pkg) },
                onToggleFav = { actions.onToggleFavorite(pkg, label) },
                onMoveFav = { delta -> actions.onMoveFavorite(pkg, delta) },
                onHideOrUnhide = { actions.onHideApp(pkg, label) }
            )
        }
        surface.setOnFocusChangeListener { v, hasFocus ->
            AnimationController.animateFocus(v, hasFocus, settings.motionStyle, 1.07f, 10f)
            surface.setFocusVisualActive(hasFocus)
            if (settings.nameplateMode == "focus" || settings.nameplateMode == "focus_only") {
                labelTv.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
            }
        }
        surface.setOnClickListener { v ->
            if (isHiddenMode) {
                openContextMenu()
            } else {
                v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90L).withEndAction {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(110L).start()
                    launchApp(ctx, pm, pkg)
                }.start()
            }
        }
        surface.setOnLongClickListener {
            openContextMenu()
            true
        }
        surface.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                openContextMenu()
                true
            } else false
        }
        return surface
    }
    // Updates label color and nameplate visibility in-place when settings change without rebuilding views.
    fun updateTileAppearance(tile: View, settings: AppSettings) {
        val labelTv = tile.findViewWithTag<TextView>("tile_label")
        labelTv?.setTextColor(Themes.getPrimaryTextColor(settings.tileColor))
        labelTv?.visibility = when (settings.nameplateMode) {
            "hidden" -> View.GONE
            "focus", "focus_only" -> if (tile.hasFocus()) View.VISIBLE else View.INVISIBLE
            else -> View.VISIBLE
        }
        tile.invalidate()
    }
    // Launches the target package using Leanback intent first and standard launcher intent as fallback.
    private fun launchApp(ctx: Context, pm: PackageManager, pkg: String) {
        val launchIntent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { ctx.startActivity(launchIntent) } catch (_: Exception) {}
        }
    }
}
