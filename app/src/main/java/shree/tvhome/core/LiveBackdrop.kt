// Important instructions:
// - Render 6 rich, multi-layered atmospheric TV backdrops (Aurora ribbons, Nebula dust, Sunset horizon, Deep caustics, Monsoon mist, Royal Eclipse).
// - Release previous wallpaper bitmaps on replacement and keep memory lightweight on low-RAM TVs.

package shree.tvhome.core

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object LiveBackdrop {
    const val MODE_SMART = "smart"
    const val MODE_AUTO_TIME = "auto"
    const val MODE_MORNING = "morning"
    const val MODE_PASTEL = "pastel"
    const val MODE_SUNSET = "sunset"
    const val MODE_AQUA = "aqua"
    const val MODE_RAIN = "rain"
    const val MODE_COSMOS = "cosmos"

    data class SmartModeSpec(
        val key: String,
        val icon: String,
        val name: String,
        val subtitle: String,
        val swatch: IntArray
    )

    data class SceneSpec(
        val key: String,
        val icon: String,
        val name: String,
        val swatch: IntArray,
        val renderStyle: String,
        val skyColors: IntArray,
        val primaryGlowColors: IntArray,
        val secondaryGlowColors: IntArray
    )

    // Smart automatic wallpaper modes.
    val SMART_MODES = listOf(
        SmartModeSpec(
            key = MODE_SMART,
            icon = "✨",
            name = "Smart Auto",
            subtitle = "Time + Weather",
            swatch = intArrayOf(Color.rgb(42, 118, 215), Color.rgb(138, 56, 175))
        ),
        SmartModeSpec(
            key = MODE_AUTO_TIME,
            icon = "🕒",
            name = "Time Flow",
            subtitle = "Day to Night",
            swatch = intArrayOf(Color.rgb(218, 118, 58), Color.rgb(28, 46, 112))
        )
    )

    // Six cinematic animated background scenes with rich depth and atmospheric shaders.
    val SCENES = listOf(
        SceneSpec(
            key = MODE_MORNING,
            icon = "🌌",
            name = "Aurora Borealis",
            swatch = intArrayOf(Color.rgb(12, 128, 118), Color.rgb(68, 52, 165)),
            renderStyle = "aurora",
            skyColors = intArrayOf(Color.rgb(6, 18, 36), Color.rgb(12, 48, 68), Color.rgb(22, 34, 78)),
            primaryGlowColors = intArrayOf(Color.argb(175, 48, 245, 188), Color.argb(65, 28, 178, 215), Color.TRANSPARENT),
            secondaryGlowColors = intArrayOf(Color.argb(135, 138, 78, 245), Color.TRANSPARENT)
        ),
        SceneSpec(
            key = MODE_PASTEL,
            icon = "🔮",
            name = "Nebula Cosmos",
            swatch = intArrayOf(Color.rgb(108, 38, 168), Color.rgb(28, 128, 198)),
            renderStyle = "nebula",
            skyColors = intArrayOf(Color.rgb(14, 10, 34), Color.rgb(48, 18, 86), Color.rgb(16, 46, 98)),
            primaryGlowColors = intArrayOf(Color.argb(175, 218, 68, 178), Color.argb(65, 128, 54, 225), Color.TRANSPARENT),
            secondaryGlowColors = intArrayOf(Color.argb(145, 44, 195, 238), Color.TRANSPARENT)
        ),
        SceneSpec(
            key = MODE_SUNSET,
            icon = "🌇",
            name = "Golden Horizon",
            swatch = intArrayOf(Color.rgb(165, 42, 82), Color.rgb(235, 118, 48)),
            renderStyle = "horizon",
            skyColors = intArrayOf(Color.rgb(24, 14, 48), Color.rgb(122, 34, 72), Color.rgb(218, 96, 44)),
            primaryGlowColors = intArrayOf(Color.argb(210, 255, 188, 78), Color.argb(85, 238, 74, 88), Color.TRANSPARENT),
            secondaryGlowColors = intArrayOf(Color.argb(120, 145, 44, 148), Color.TRANSPARENT)
        ),
        SceneSpec(
            key = MODE_AQUA,
            icon = "🌊",
            name = "Abyssal Rays",
            swatch = intArrayOf(Color.rgb(10, 78, 148), Color.rgb(22, 178, 162)),
            renderStyle = "abyssal",
            skyColors = intArrayOf(Color.rgb(6, 28, 62), Color.rgb(12, 88, 134), Color.rgb(8, 42, 78)),
            primaryGlowColors = intArrayOf(Color.argb(165, 58, 235, 215), Color.argb(60, 28, 148, 228), Color.TRANSPARENT),
            secondaryGlowColors = intArrayOf(Color.argb(125, 48, 118, 235), Color.TRANSPARENT)
        ),
        SceneSpec(
            key = MODE_RAIN,
            icon = "🌧️",
            name = "Monsoon Mist",
            swatch = intArrayOf(Color.rgb(18, 46, 76), Color.rgb(52, 118, 158)),
            renderStyle = "rain",
            skyColors = intArrayOf(Color.rgb(12, 26, 46), Color.rgb(22, 54, 78), Color.rgb(10, 20, 36)),
            primaryGlowColors = intArrayOf(Color.argb(115, 84, 185, 235), Color.TRANSPARENT),
            secondaryGlowColors = intArrayOf(Color.argb(85, 65, 145, 225), Color.TRANSPARENT)
        ),
        SceneSpec(
            key = MODE_COSMOS,
            icon = "🌙",
            name = "Royal Eclipse",
            swatch = intArrayOf(Color.rgb(12, 18, 44), Color.rgb(42, 56, 112)),
            renderStyle = "eclipse",
            skyColors = intArrayOf(Color.rgb(7, 11, 26), Color.rgb(14, 22, 46), Color.rgb(6, 9, 20)),
            primaryGlowColors = intArrayOf(Color.argb(215, 255, 244, 192), Color.argb(85, 255, 215, 125), Color.argb(24, 255, 195, 95), Color.TRANSPARENT),
            secondaryGlowColors = intArrayOf(Color.argb(80, 84, 172, 245), Color.TRANSPARENT)
        )
    )

    private val sceneMap by lazy { SCENES.associateBy { it.key } }

    fun getScene(key: String): SceneSpec = sceneMap[key] ?: SCENES.last()

    private var activeWallpaperView: ImageView? = null
    private var activeWallpaperBitmap: Bitmap? = null
    private var activePhotoAuraView: PhotoWeatherAuraView? = null
    private var activeCanvasView: DynamicCanvasView? = null
    private var baseZoom = 1.08f
    private var baseOffsetY = 0

    // Detaches and recycles any previously loaded wallpaper bitmap before applying a new backdrop.
    private fun releasePreviousWallpaper() {
        activeWallpaperView?.setImageDrawable(null)
        activeWallpaperView = null
        activeWallpaperBitmap?.let { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        activeWallpaperBitmap = null
    }

    // Applies either a sampled user gallery image or the live animated canvas backdrop.
    fun apply(ctx: Context, root: FrameLayout, settings: AppSettings) {
        releasePreviousWallpaper()
        activePhotoAuraView = null
        activeCanvasView = null

        val wallpaper = settings.prefs.getString(AppSettings.PREF_WALLPAPER, null)
        if (wallpaper != null) {
            root.background = null
            val image = ImageView(ctx).apply {
                scaleType = when (settings.wpScaleMode) {
                    "width" -> ImageView.ScaleType.FIT_XY
                    "height" -> ImageView.ScaleType.FIT_CENTER
                    else -> ImageView.ScaleType.CENTER_CROP
                }
                baseZoom = maxOf(1.08f, settings.wpZoom)
                baseOffsetY = settings.wpOffsetY
                scaleX = baseZoom
                scaleY = baseZoom
                translationX = settings.wpOffsetX.toFloat()
                translationY = baseOffsetY.toFloat()
                val bmp = decodeSampledBitmapFromUri(ctx, Uri.parse(wallpaper))
                if (bmp != null) {
                    activeWallpaperBitmap = bmp
                    setImageBitmap(bmp)
                } else {
                    try { setImageURI(Uri.parse(wallpaper)) } catch (_: Exception) {}
                }
            }
            root.addView(image, 0, FrameLayout.LayoutParams(-1, -1))
            activeWallpaperView = image

            if (settings.photoWeatherAura) {
                val aura = PhotoWeatherAuraView(ctx, settings)
                root.addView(aura, 1, FrameLayout.LayoutParams(-1, -1))
                activePhotoAuraView = aura
            }
        } else {
            root.background = null
            val canvasView = DynamicCanvasView(ctx, settings)
            root.addView(canvasView, 0, FrameLayout.LayoutParams(-1, -1))
            activeCanvasView = canvasView
        }
    }
        // Decodes a sampled RGB_565 bitmap scaled to display size to keep memory usage light.
    private fun decodeSampledBitmapFromUri(ctx: Context, uri: Uri): Bitmap? {
        return try {
            val dm = ctx.resources.displayMetrics
            val reqW = maxOf(1280, dm.widthPixels)
            val reqH = maxOf(720, dm.heightPixels)

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

            var inSampleSize = 1
            if (options.outHeight > reqH || options.outWidth > reqW) {
                val halfH = options.outHeight / 2
                val halfW = options.outWidth / 2
                while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                    inSampleSize *= 2
                }
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        } catch (_: Exception) {
            null
        }
    }

    fun updateThemeAndAccent(root: FrameLayout, settings: AppSettings) {
        if (settings.prefs.getString(AppSettings.PREF_WALLPAPER, null) == null) {
            activeCanvasView?.updateConfig()
        } else {
            activePhotoAuraView?.invalidate()
        }
    }

    fun onScrollParallax(scrollY: Int) {
        val shift = minOf(22f, scrollY * 0.07f)
        activeWallpaperView?.translationY = baseOffsetY - shift
        activeCanvasView?.setParallaxShift(shift)
    }

    private fun resolveEffectiveMode(settings: AppSettings): String {
        val mode = settings.liveWallpaperMode
        if (mode == MODE_SMART) {
            val wCode = settings.cachedWeatherCode
            if (wCode in 51..67 || wCode in 80..99) return MODE_RAIN
        }
        if (mode == MODE_SMART || mode == MODE_AUTO_TIME) {
            val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when (hr) {
                in 6..10 -> MODE_MORNING
                in 11..15 -> MODE_PASTEL
                in 16..18 -> MODE_SUNSET
                else -> MODE_COSMOS
            }
        }
        return if (sceneMap.containsKey(mode)) mode else SCENES.last().key
    }

    private fun blendColor(c1: Int, c2: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        val a = (Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * t).toInt()
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun tintWithAccent(colors: IntArray, accent: Int): IntArray {
        return colors.mapIndexed { idx, col ->
            if (idx == 0) blendColor(col, accent, 0.24f) else col
        }.toIntArray()
    }

    // Draws subtle weather-aware atmospheric glow over custom gallery photos.
    private class PhotoWeatherAuraView(ctx: Context, private val settings: AppSettings) : View(ctx) {
        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 200, 235, 255)
            strokeCap = Paint.Cap.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val wCode = settings.cachedWeatherCode
            val isRain = wCode in 51..67 || wCode in 80..99
            val isDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 6..17

            val glowCol = when {
                isRain -> Color.argb(52, 65, 165, 225)
                isDay -> Color.argb(48, 255, 210, 110)
                else -> Color.argb(45, 110, 145, 245)
            }
            edgePaint.shader = RadialGradient(
                w * 0.88f, h * 0.16f, w * 0.42f,
                intArrayOf(glowCol, Color.TRANSPARENT), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, edgePaint)

            if (isRain) {
                rainPaint.strokeWidth = LayoutMetrics.dpF(context, 1.2f)
                for (i in 0 until 14) {
                    val rx = w * (0.72f + (i % 5) * 0.06f)
                    val ry = h * (0.06f + (i * 0.06f))
                    canvas.drawLine(rx, ry, rx - 8f, ry + 18f, rainPaint)
                }
            }
        }
    }

    // Multi-layered atmospheric canvas rendering aurora curtains, nebula dust, sun reflections, underwater rays, rain & eclipse.
    private class DynamicCanvasView(
        ctx: Context,
        private val settings: AppSettings
    ) : View(ctx) {

        private val skyPaint = Paint(Paint.DITHER_FLAG)
        private val orb1Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val orb2Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val prevSkyPaint = Paint(Paint.DITHER_FLAG)
        private val featurePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
        }
        private val moonDiscPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val moonHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(32, 165, 185, 215) }
        private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val wavePath = Path()
        private val starPath = Path()
        private var flowAnim: ValueAnimator? = null
        private var transitionAnim: ValueAnimator? = null
        private var transitionAlpha = 0f
        private var phase = 0f
        private var parallaxY = 0f
        private var activeEffectiveMode = resolveEffectiveMode(settings)
        private var lastCalculatedMinute = -1

        private data class Particle(
            val xRatio: Float,
            val yRatio: Float,
            val sizePx: Float,
            val speed: Float,
            val offset: Float
        )

        private val particles = List(48) { i ->
            val rnd = Random(i * 997 + 17)
            val d = resources.displayMetrics.density
            Particle(
                0.02f + rnd.nextFloat() * 0.96f,
                0.02f + rnd.nextFloat() * 0.92f,
                (1.6f + rnd.nextFloat() * 3.6f) * d,
                0.8f + rnd.nextFloat() * 2.5f,
                rnd.nextFloat() * 12f
            )
        }
        fun updateConfig() {
            val nextEffective = resolveEffectiveMode(settings)
            val modeChanged = nextEffective != activeEffectiveMode
            lastCalculatedMinute = -1

            if (modeChanged && width > 0 && height > 0) {
                prevSkyPaint.shader = skyPaint.shader
                activeEffectiveMode = nextEffective
                buildShaders(width, height)
                startSmoothCrossFade()
            } else {
                activeEffectiveMode = nextEffective
                buildShaders(width, height)
                invalidate()
            }
        }

        private fun startSmoothCrossFade() {
            transitionAnim?.cancel()
            transitionAlpha = 1f
            transitionAnim = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 1400L
                addUpdateListener {
                    transitionAlpha = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        fun setParallaxShift(shift: Float) {
            if (parallaxY != shift) {
                parallaxY = shift
                invalidate()
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return
            lastCalculatedMinute = -1
            activeEffectiveMode = resolveEffectiveMode(settings)
            buildShaders(w, h)
            flowAnim?.cancel()
            flowAnim = ValueAnimator.ofFloat(0f, 6.28318f).apply {
                duration = 22000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    val mode = settings.liveWallpaperMode
                    if (mode == MODE_SMART || mode == MODE_AUTO_TIME) {
                        val curMin = Calendar.getInstance().get(Calendar.MINUTE)
                        if (curMin != lastCalculatedMinute) {
                            lastCalculatedMinute = curMin
                            activeEffectiveMode = resolveEffectiveMode(settings)
                            buildShaders(width, height)
                        }
                    }
                    invalidate()
                }
                start()
            }
        }

        private fun getAutoSkyPalette(): IntArray {
            val cal = Calendar.getInstance()
            val totalMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            lastCalculatedMinute = cal.get(Calendar.MINUTE)

            val morning = getScene(MODE_MORNING).skyColors
            val pastel = getScene(MODE_PASTEL).skyColors
            val sunset = getScene(MODE_SUNSET).skyColors
            val night = getScene(MODE_COSMOS).skyColors

            val (from, to, ratio) = when {
                totalMins in 300..419 -> Triple(night, morning, (totalMins - 300) / 120f)
                totalMins in 420..659 -> Triple(morning, pastel, (totalMins - 420) / 240f)
                totalMins in 660..959 -> Triple(pastel, sunset, (totalMins - 660) / 300f)
                totalMins in 960..1139 -> Triple(sunset, night, (totalMins - 960) / 180f)
                else -> Triple(night, night, 0f)
            }
            return intArrayOf(
                blendColor(from, to, ratio),
                blendColor(from, to, ratio),
                blendColor(from, to, ratio)
            )
        }

        private fun buildShaders(w: Int, h: Int) {
            if (w <= 0 || h <= 0) return
            val wf = w.toFloat()
            val hf = h.toFloat()
            val accentColor = settings.accentColor
            val isAuto = settings.liveWallpaperMode == MODE_SMART || settings.liveWallpaperMode == MODE_AUTO_TIME
            val spec = getScene(activeEffectiveMode)

            val activeSky = if (isAuto && spec.renderStyle != "rain" && spec.renderStyle != "abyssal") {
                getAutoSkyPalette()
            } else {
                spec.skyColors
            }
            skyPaint.shader = LinearGradient(
                0f, 0f, wf * 0.4f, hf, activeSky,
                floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP
            )

            val orb1X = if (spec.renderStyle == "eclipse") wf * 0.52f else wf * 0.28f
            val orb1Y = if (spec.renderStyle == "eclipse") hf * 0.16f else hf * 0.26f
            orb1Paint.shader = RadialGradient(
                orb1X, orb1Y, wf * 0.62f,
                tintWithAccent(spec.primaryGlowColors, accentColor), null, Shader.TileMode.CLAMP
            )
            orb2Paint.shader = RadialGradient(
                wf * 0.78f, hf * 0.76f, wf * 0.58f,
                tintWithAccent(spec.secondaryGlowColors, accentColor), null, Shader.TileMode.CLAMP
            )

            vignettePaint.shader = RadialGradient(
                wf * 0.5f, hf * 0.5f, wf * 0.78f,
                intArrayOf(Color.TRANSPARENT, Color.argb(95, 4, 8, 18)),
                floatArrayOf(0.55f, 1f),
                Shader.TileMode.CLAMP
            )

            if (spec.renderStyle == "eclipse") {
                val moonX = wf * 0.52f
                val moonY = hf * 0.16f
                val moonR = 26f * resources.displayMetrics.density
                moonDiscPaint.shader = RadialGradient(
                    moonX - moonR * 0.2f, moonY - moonR * 0.2f, moonR * 1.25f,
                    intArrayOf(Color.rgb(255, 254, 245), Color.rgb(218, 232, 255)),
                    null, Shader.TileMode.CLAMP
                )
            }
        }

        // Draws multi-layered atmospheric visuals tailored to each of the 6 scenes.
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            canvas.drawRect(0f, 0f, w, h, skyPaint)
            if (transitionAlpha > 0.01f && prevSkyPaint.shader != null) {
                prevSkyPaint.alpha = (transitionAlpha * 255).toInt()
                canvas.drawRect(0f, 0f, w, h, prevSkyPaint)
            }

            val driftX = sin(phase) * w * 0.035f
            val driftY = cos(phase * 0.8f) * h * 0.025f
            canvas.save()
            canvas.translate(driftX, driftY - parallaxY * 0.3f)
            canvas.drawRect(-w * 0.1f, -h * 0.1f, w * 1.1f, h * 1.1f, orb1Paint)
            canvas.restore()

            canvas.save()
            canvas.translate(-driftX * 0.8f, -driftY * 0.8f)
            canvas.drawRect(-w * 0.1f, -h * 0.1f, w * 1.1f, h * 1.1f, orb2Paint)
            canvas.restore()

            val spec = getScene(activeEffectiveMode)
            when (spec.renderStyle) {
                "aurora" -> drawAuroraCurtains(canvas, w, h)
                "nebula" -> drawNebulaClouds(canvas, w, h)
                "horizon" -> drawGoldenHorizon(canvas, w, h)
                "abyssal" -> drawAbyssalLightRays(canvas, w, h)
                "eclipse" -> drawRoyalEclipse(canvas, w, h)
                "rain" -> drawMonsoonMist(canvas, w, h)
            }

            // Ambient micro-particles (stars, rising sea mist, or diagonal rain streaks).
            for (s in particles) {
                val wave = sin(phase * s.speed + s.offset) * cos(phase * s.speed * 0.6f + s.offset)
                val pulse = 0.28f + 0.72f * abs(wave)
                val sx = s.xRatio * w
                val sy = s.yRatio * h - parallaxY * 0.25f
                val r = s.sizePx * (0.5f + 0.5f * pulse)

                when (spec.renderStyle) {
                    "eclipse", "aurora", "nebula" -> {
                        particlePaint.color = LayoutMetrics.withAlpha(Color.WHITE, (pulse * 220).toInt())
                        starPath.reset()
                        val inner = r * 0.20f
                        starPath.moveTo(sx, sy - r)
                        starPath.lineTo(sx + inner, sy - inner)
                        starPath.lineTo(sx + r, sy)
                        starPath.lineTo(sx + inner, sy + inner)
                        starPath.lineTo(sx, sy + r)
                        starPath.lineTo(sx - inner, sy + inner)
                        starPath.lineTo(sx - r, sy)
                        starPath.lineTo(sx - inner, sy - inner)
                        starPath.close()
                        canvas.drawPath(starPath, particlePaint)
                    }
                    "rain" -> {
                        val dropY = ((s.yRatio + (phase * 0.16f * s.speed)) % 1.0f) * h
                        particlePaint.strokeWidth = LayoutMetrics.dpF(context, 1.3f)
                        particlePaint.color = Color.argb(95, 195, 235, 255)
                        canvas.drawLine(sx, dropY, sx - 6f, dropY + 18f, particlePaint)
                    }
                    "abyssal" -> {
                        val riseY = ((1f - s.yRatio + (phase * 0.04f * s.speed)) % 1.0f) * h
                        particlePaint.color = Color.argb((pulse * 85).toInt(), 120, 245, 235)
                        canvas.drawCircle(sx + sin(phase + s.offset) * 8f, riseY, r * 0.75f, particlePaint)
                    }
                    else -> {
                        particlePaint.color = Color.argb((pulse * 75).toInt(), 255, 225, 185)
                        canvas.drawCircle(sx, sy, r * 0.65f, particlePaint)
                    }
                }
            }

            // Subtle cinema vignette around screen edges so foreground glass tiles pop effortlessly.
            canvas.drawRect(0f, 0f, w, h, vignettePaint)
        }

        // Flowing multi-layered northern lights ribbons across the upper sky.
        private fun drawAuroraCurtains(canvas: Canvas, w: Float, h: Float) {
            val ribbonColors = listOf(
        // Flowing multi-layered northern lights ribbons across the upper sky.
        private fun drawAuroraCurtains(canvas: Canvas, w: Float, h: Float) {
            val ribbonColors = listOf(
                intArrayOf(Color.TRANSPARENT, Color.argb(95, 46, 245, 185), Color.argb(28, 88, 145, 255), Color.TRANSPARENT),
                intArrayOf(Color.TRANSPARENT, Color.argb(72, 138, 88, 255), Color.argb(24, 46, 235, 215), Color.TRANSPARENT)
            )
            ribbonColors.forEachIndexed { idx, cols ->
                val baseY = h * (0.22f + idx * 0.14f) - parallaxY * 0.25f
                wavePath.reset()
                wavePath.moveTo(0f, baseY)
                var x = 0f
                val step = w / 16f
                while (x <= w + step) {
                    val yOff = sin((x / w) * 6.28f + phase * (1.2f + idx * 0.4f) + idx) * h * 0.065f
                    wavePath.lineTo(x, baseY + yOff)
                    x += step
                }
                wavePath.lineTo(w, baseY + h * 0.28f)
                wavePath.lineTo(0f, baseY + h * 0.28f)
                wavePath.close()

                featurePaint.shader = LinearGradient(0f, baseY - h * 0.08f, 0f, baseY + h * 0.28f, cols, null, Shader.TileMode.CLAMP)
                canvas.drawPath(wavePath, featurePaint)
            }
        }

        // Layered cosmic nebula dust bands across diagonal space.
        private fun drawNebulaClouds(canvas: Canvas, w: Float, h: Float) {
            featurePaint.shader = LinearGradient(
                0f, h * 0.15f, w, h * 0.85f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(75, 215, 72, 185),
                    Color.argb(85, 68, 165, 245),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.05f, 0.38f, 0.68f, 0.96f),
                Shader.TileMode.CLAMP
            )
            canvas.save()
            canvas.translate(sin(phase * 0.7f) * 24f, cos(phase * 0.5f) * 16f)
            canvas.drawRect(0f, 0f, w, h, featurePaint)
            canvas.restore()
        }

        // Warm low-sun horizon with shimmering water reflection ripples below.
        private fun drawGoldenHorizon(canvas: Canvas, w: Float, h: Float) {
            val horizonY = h * 0.64f - parallaxY * 0.2f
            val sunX = w * 0.58f
            featurePaint.shader = RadialGradient(
                sunX, horizonY, w * 0.28f,
                intArrayOf(Color.argb(185, 255, 218, 118), Color.argb(65, 245, 92, 82), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(sunX, horizonY, w * 0.28f, featurePaint)

            featurePaint.shader = LinearGradient(
                0f, horizonY, 0f, h,
                intArrayOf(Color.argb(95, 245, 118, 58), Color.argb(145, 28, 16, 48)),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, horizonY, w, h, featurePaint)
        }

        // Diagonal underwater volumetric light beams descending from the ocean surface.
        private fun drawAbyssalLightRays(canvas: Canvas, w: Float, h: Float) {
            for (i in 0 until 4) {
                val topX = w * (0.18f + i * 0.20f) + sin(phase + i) * 22f
                wavePath.reset()
                wavePath.moveTo(topX - w * 0.03f, 0f)
                wavePath.lineTo(topX + w * 0.04f, 0f)
                wavePath.lineTo(topX + w * 0.16f, h)
                wavePath.lineTo(topX - w * 0.06f, h)
                wavePath.close()

                featurePaint.shader = LinearGradient(
                    topX, 0f, topX + w * 0.05f, h * 0.85f,
                    intArrayOf(Color.argb(54, 92, 245, 228), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawPath(wavePath, featurePaint)
            }
        }

        // Luminous moon with atmospheric halo ring and drifting night mist.
        private fun drawRoyalEclipse(canvas: Canvas, w: Float, h: Float) {
            val moonX = w * 0.52f
            val moonY = h * 0.16f - parallaxY * 0.35f
            val moonR = 26f * resources.displayMetrics.density

            moonHaloPaint.strokeWidth = LayoutMetrics.dpF(context, 1.4f)
            moonHaloPaint.color = Color.argb(42, 210, 230, 255)
            canvas.drawCircle(moonX, moonY, moonR * 1.65f + sin(phase) * 3f, moonHaloPaint)

            canvas.drawCircle(moonX, moonY, moonR, moonDiscPaint)
            canvas.drawCircle(moonX - moonR * 0.32f, moonY - moonR * 0.18f, moonR * 0.22f, craterPaint)
            canvas.drawCircle(moonX + moonR * 0.28f, moonY + moonR * 0.24f, moonR * 0.16f, craterPaint)
        }

        // Soft distant thunderhead glow behind rain streaks.
        private fun drawMonsoonMist(canvas: Canvas, w: Float, h: Float) {
            val flashPulse = sin(phase * 3.0f)
            if (flashPulse > 0.92f) {
                val alpha = (((flashPulse - 0.92f) / 0.08f) * 38).toInt()
                featurePaint.shader = RadialGradient(
                    w * 0.65f, h * 0.18f, w * 0.45f,
                    intArrayOf(Color.argb(alpha, 185, 225, 255), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, featurePaint)
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            flowAnim?.cancel()
            transitionAnim?.cancel()
        }
    }
}
