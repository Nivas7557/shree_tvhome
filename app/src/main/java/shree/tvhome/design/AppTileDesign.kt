// Important instructions:
// - Render full-bleed Android TV banners when available, or clean adaptive TV banner cards without glass border frames.
// - Bound bitmap memory via LruCache and keep tile layout responsive across all TV resolutions.

package shree.tvhome.design

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import java.util.concurrent.ConcurrentHashMap

object AppTileDesign {

    // Bound bitmap memory using ~1/8th of max JVM memory (in KB) via LruCache.
    private val cacheKb = ((Runtime.getRuntime().maxMemory() / 1024L) / 8L).toInt().coerceIn(2048, 16384)
    private val bannerBitmapCache = object : LruCache<String, Bitmap>(cacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val labelCache = ConcurrentHashMap<String, String>()
    private val colorCache = ConcurrentHashMap<String, Int>()
    private var lastFocusedX = -1f
    private var lastFocusedY = -1f

    // Evicts cached banners, labels, and dominant color entries.
    fun clearAssetCache() {
        bannerBitmapCache.evictAll()
        labelCache.clear()
        colorCache.clear()
    }

    interface TileActions {
        fun onToggleFavorite(pkg: String, label: String)
        fun onMoveFavorite(pkg: String, delta: Int)
        fun onHideApp(pkg: String, label: String)
    }

    // Returns and caches the display label for a launchable activity.
    fun getCachedLabel(pm: PackageManager, info: ResolveInfo): String {
        val pkg = info.activityInfo.packageName
        return labelCache.getOrPut(pkg) { info.loadLabel(pm).toString() }
    }

    // Loads native Android TV banner if present, or synthesizes a clean 16:10 TV banner card from the app icon.
    private fun getTvBannerBitmap(pm: PackageManager, info: ResolveInfo): Bitmap {
        val pkg = info.activityInfo.packageName
        bannerBitmapCache.get(pkg)?.let { return it }

        val nativeBanner: Drawable? = try {
            info.activityInfo.loadBanner(pm) ?: pm.getApplicationBanner(pkg)
        } catch (_: Exception) {
            null
        }

        val rendered = if (nativeBanner != null) {
            renderNativeTvBanner(nativeBanner)
        } else {
            renderSynthesizedTvBanner(info.loadIcon(pm))
        }
        bannerBitmapCache.put(pkg, rendered)
        return rendered
    }

    // Draws an official 320x180 Android TV banner onto a compact 16:10 bitmap.
    private fun renderNativeTvBanner(banner: Drawable): Bitmap {
        val outW = 256
        val outH = 160
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        banner.setBounds(0, 0, outW, outH)
        banner.draw(canvas)
        return out
    }

    // Synthesizes a clean full-bleed 16:10 TV card using the app's dominant palette and centered icon.
    private fun renderSynthesizedTvBanner(iconDrawable: Drawable): Bitmap {
        val iconSample = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val sampleCanvas = Canvas(iconSample)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && iconDrawable is AdaptiveIconDrawable) {
            iconDrawable.background?.apply { setBounds(-16, -16, 80, 80); draw(sampleCanvas) }
            iconDrawable.foreground?.apply { setBounds(-12, -12, 76, 76); draw(sampleCanvas) }
        } else {
            iconDrawable.setBounds(0, 0, 64, 64)
            iconDrawable.draw(sampleCanvas)
        }

        val primaryCol = extractVibrantColor(iconSample, Color.rgb(42, 82, 168))
        val hsv = FloatArray(3)
        Color.colorToHSV(primaryCol, hsv)
        val topCol = Color.HSVToColor(255, floatArrayOf(hsv[0], (hsv[1] * 0.85f).coerceIn(0.25f, 0.90f), (hsv[2] * 0.62f).coerceIn(0.22f, 0.72f)))
        val botCol = Color.HSVToColor(255, floatArrayOf((hsv[0] + 18f) % 360f, (hsv[1] * 0.92f).coerceIn(0.30f, 0.95f), (hsv[2] * 0.36f).coerceIn(0.14f, 0.48f)))

        val outW = 256
        val outH = 160
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, outW.toFloat(), outH.toFloat(), intArrayOf(topCol, botCol), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), bgPaint)

        val iconSize = 84
        val left = (outW - iconSize) / 2
        val top = (outH - iconSize) / 2
        canvas.drawBitmap(iconSample, null, android.graphics.Rect(left, top, left + iconSize, top + iconSize), Paint(Paint.FILTER_BITMAP_FLAG))
        if (!iconSample.isRecycled) iconSample.recycle()
        return out
    }

    // Extracts and caches a vibrant dominant color from the rendered banner bitmap.
    private fun getAppDominantColor(pkg: String, bmp: Bitmap, fallback: Int): Int {
        return colorCache.getOrPut(pkg) {
            extractVibrantColor(bmp, fallback)
        }
    }

    // Samples pixels across the bitmap to find a lively accent color.
    private fun extractVibrantColor(bmp: Bitmap, fallback: Int): Int {
        return try {
            var bestColor = fallback
            var maxScore = -1f
            val stepX = maxOf(1, bmp.width / 16)
            val stepY = maxOf(1, bmp.height / 16)

            for (x in stepX until bmp.width - stepX step stepX) {
                for (y in stepY until bmp.height - stepY step stepY) {
                    val px = bmp.getPixel(x, y)
                    if (Color.alpha(px) < 180) continue
                    val hsv = FloatArray(3)
                    Color.colorToHSV(px, hsv)
                    val sat = hsv[1]
                    val valBright = hsv[2]
                    if (valBright < 0.22f || (sat < 0.16f && valBright > 0.85f)) continue
                    val score = (sat * 2.2f) + valBright
                    if (score > maxScore) {
                        maxScore = score
                        bestColor = px
                    }
                }
            }
            val outHsv = FloatArray(3)
            Color.colorToHSV(bestColor, outHsv)
            outHsv[1] = maxOf(outHsv[1], 0.55f)
            outHsv[2] = maxOf(outHsv[2], 0.88f)
            Color.HSVToColor(255, outHsv)
        } catch (_: Exception) {
            fallback
        }
    }
    // Constructs a full-width TV banner tile with FocusAppGlowDesign aura, nameplate, and D-Pad/touch actions.
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
        actions: TileActions
    ): View {
        val pkg = info.activityInfo.packageName
        val label = getCachedLabel(pm, info)
        val bannerBmp = getTvBannerBitmap(pm, info)
        val appColor = getAppDominantColor(pkg, bannerBmp, settings.accentColor)

        var breathingAnim: ValueAnimator? = null
        var styleListener: (() -> Unit)? = null

        val wrapper = object : FrameLayout(ctx) {
            override fun onDetachedFromWindow() {
                super.onDetachedFromWindow()
                breathingAnim?.cancel()
                breathingAnim = null
                styleListener?.let { settings.removeStyleListener(it) }
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(w, h)
            clipChildren = false
            clipToPadding = false
            isFocusable = true
            isClickable = true
            tag = if (isFavRow) "fav_$pkg" else "all_$pkg"
        }

        val unitContainer = LinearLayout(ctx).apply {
            tag = "unit_container"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }

        val innerCard = FocusAppGlowDesign(ctx).apply {
            tag = "glow_card"
            isFocusable = false
            isClickable = false
            clipChildren = false
            setStyle(settings.accentColor, settings.tileShape, settings.tileColor, appColor)
        }

        val listener: () -> Unit = {
            if (wrapper.isAttachedToWindow) {
                innerCard.setStyle(settings.accentColor, settings.tileShape, settings.tileColor, appColor)
            }
        }
        styleListener = listener
        settings.addStyleListener(listener)

        val iv = ImageView(ctx).apply {
            tag = "tile_icon"
            setImageBitmap(bannerBmp)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        innerCard.addView(iv, FrameLayout.LayoutParams(-1, -1))
        unitContainer.addView(innerCard, LinearLayout.LayoutParams(-1, 0, 1f))

        val namePlate = FrameLayout(ctx).apply {
            tag = "name_plate"
            outlineProvider = null
            setPadding(dp(ctx, 8), dp(ctx, 2), dp(ctx, 8), dp(ctx, 2))
            background = nameplateBg(ctx, false, appColor)
        }
        val tv = TextView(ctx).apply {
            tag = "tile_label"
            text = label
            textSize = if (settings.columns > 6) 8.8f else 9.8f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }
        namePlate.addView(tv, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        namePlate.visibility = when (settings.nameplateMode) {
            "hidden" -> View.GONE
            "focus" -> View.INVISIBLE
            else -> View.VISIBLE
        }
        unitContainer.addView(namePlate, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(ctx, 4)
            rightMargin = dp(ctx, 4)
            topMargin = dp(ctx, 4)
        })

        wrapper.addView(unitContainer, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        // Animate glow, scale, motion style, and nameplate state on focus changes.
        wrapper.setOnFocusChangeListener { v, hasFocus ->
            val dx = if (lastFocusedX >= 0f) (v.x - lastFocusedX).coerceIn(-1f, 1f) else 0f
            val dy = if (lastFocusedY >= 0f) (v.y - lastFocusedY).coerceIn(-1f, 1f) else 0f
            if (hasFocus) {
                lastFocusedX = v.x
                lastFocusedY = v.y
            }

            innerCard.setFocusState(hasFocus)
            namePlate.background = nameplateBg(ctx, hasFocus, appColor)
            if (settings.nameplateMode == "focus") {
                namePlate.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
            }
            AnimationController.animateFocus(unitContainer, hasFocus, settings.motionStyle, 1.09f, 12f, dx, dy)

            if (hasFocus) {
                tv.isSelected = true
                breathingAnim?.cancel()
                breathingAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1250L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { anim ->
                        val f = anim.animatedValue as Float
                        innerCard.updateGlowPulse(f)
                    }
                    start()
                }
            } else {
                tv.isSelected = false
                breathingAnim?.cancel()
                breathingAnim = null
            }
        }

        val openDialog = {
            AppMenuDialogDesign.show(
                ctx = ctx,
                pkg = pkg,
                appName = label,
                isFav = isFav,
                canMoveLeft = isFavRow && canMoveLeft,
                canMoveRight = isFavRow && canMoveRight,
                accent = settings.accentColor,
                isHiddenMode = false,
                onToggleFav = { actions.onToggleFavorite(pkg, label) },
                onMoveFav = { delta -> actions.onMoveFavorite(pkg, delta) },
                onHideOrUnhideApp = { actions.onHideApp(pkg, label) }
            )
        }

        wrapper.setOnClickListener {
            try {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) ctx.startActivity(launchIntent)
                else Toast.makeText(ctx, "App cannot be launched", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(ctx, "App not found", Toast.LENGTH_SHORT).show()
            }
        }

        wrapper.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_MENU || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.isLongPress))
            ) {
                openDialog()
                true
            } else false
        }

        wrapper.setOnLongClickListener {
            openDialog()
            true
        }

        return wrapper
    }

    // Updates banner card style, label size, and nameplate visibility in-place.
    fun updateTileAppearance(ctx: Context, wrapper: View, settings: AppSettings) {
        val rawTag = (wrapper.tag as? String)?.removePrefix("fav_")?.removePrefix("all_") ?: ""
        val appCol = colorCache[rawTag] ?: settings.accentColor
        wrapper.findViewWithTag<FocusAppGlowDesign>("glow_card")?.let { card ->
            card.setStyle(settings.accentColor, settings.tileShape, settings.tileColor, appCol)
        }
        wrapper.findViewWithTag<TextView>("tile_label")?.textSize = if (settings.columns > 6) 8.8f else 9.8f
        wrapper.findViewWithTag<View>("name_plate")?.visibility = when (settings.nameplateMode) {
            "hidden" -> View.GONE
            "focus" -> if (wrapper.hasFocus()) View.VISIBLE else View.INVISIBLE
            else -> View.VISIBLE
        }
    }

    private fun nameplateBg(ctx: Context, focused: Boolean, accentColor: Int) = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        if (focused) intArrayOf(LayoutMetrics.withAlpha(accentColor, 225), LayoutMetrics.withAlpha(accentColor, 145))
        else intArrayOf(LayoutMetrics.withAlpha(accentColor, 95), Color.argb(115, 18, 26, 54))
    ).apply {
        cornerRadius = LayoutMetrics.dpF(ctx, 8f)
        setStroke(dp(ctx, 1), if (focused) Color.WHITE else LayoutMetrics.withAlpha(accentColor, 150))
    }

    private fun dp(ctx: Context, v: Int): Int = LayoutMetrics.dp(ctx, v)
}
