// Important instructions:
// - Act as the single source of truth for palettes, rich gradient accents, tile options, 6 clock styles, weather specs, and layout ratios.
// - Keep accent gradients natural with multi-stop color blends rather than flat single colors.
package shree.tvhome.core

import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.wifi.WifiManager

object Themes {
    data class ThemeSpec(
        val key: String,
        val name: String,
        val subtitle: String,
        val normalColors: IntArray,
        val focusedColors: IntArray
    )
    // Natural multi-stop gradient accent specification for rich visual depth.
    data class AccentSpec(
        val key: String,
        val label: String,
        val color: Int,
        val gradientColors: IntArray
    )
    data class TileSizeSpec(
        val key: String,
        val label: String,
        val badgeDp: Int,
        val desiredGridDp: Int,
        val hiddenScale: Float
    )
    data class OptionSpec(
        val key: String,
        val label: String
    )
    data class GreetingStyleSpec(val key: String, val label: String, val desc: String)
    data class GreetingFontSpec(val key: String, val label: String, val family: String)
    data class WeatherVisualSpec(val range: IntRange, val emoji: String, val label: String)
    data class CitySpec(val name: String, val admin: String, val country: String, val lat: Double, val lon: Double)
    data class ClockStyleSpec(
        val key: String,
        val label: String,
        val showDialFace: Boolean = true,
        val showBezelRing: Boolean = true,
        val showTicks: Boolean = false,
        val showNumerals: Boolean = false,
        val showProgressArcs: Boolean = false,
        val dashedOuterRing: Boolean = false,
        val showDigitalReadout: Boolean = false,
        val showHands: Boolean = true,
        val showCoronaHalo: Boolean = false,
        val showActivityRings: Boolean = false,
        val outerRingRatio: Float = 0.88f,
        val innerRingRatio: Float = 0.62f,
        val handScaleRatio: Float = 1.0f,
        val cornerRadiusRatio: Float = 0.35f
    )
    // Shared proportional screen height ratios (13% Header/Avatar + 21% Favorites + 62.5% App Shelf + 3.5% Gaps).
    const val HEADER_BAR_HEIGHT_RATIO = 0.050f
    const val HEADER_BAR_MIN_DP = 32
    const val HEADER_BAR_MAX_DP = 44
    const val AVATAR_SIZE_MULTIPLIER = 2.10f
    const val AVATAR_TOP_CLEARANCE_RATIO = 0.62f
    const val AVATAR_BOTTOM_GAP_RATIO = 0.68f
    const val FAV_BAND_HEIGHT_RATIO = 0.21f
    const val SHELF_BAND_GAP_RATIO = 0.015f

    // Glass surface palettes shared across tiles, panels, and stage surfaces.
    val THEMES = listOf(
        ThemeSpec(
            key = "frost",
            name = "Royal Indigo",
            subtitle = "Deep Sapphire Glass",
            normalColors = intArrayOf(Color.argb(215, 28, 52, 118), Color.argb(195, 14, 28, 72), Color.argb(210, 44, 76, 158)),
            focusedColors = intArrayOf(Color.argb(245, 58, 108, 215), Color.argb(230, 28, 62, 145), Color.argb(245, 88, 145, 255))
        ),
        ThemeSpec(
            key = "ocean",
            name = "Ocean Breeze",
            subtitle = "Electric Cyan & Sea",
            normalColors = intArrayOf(Color.argb(215, 10, 92, 165), Color.argb(195, 6, 48, 105), Color.argb(210, 18, 132, 205)),
            focusedColors = intArrayOf(Color.argb(245, 22, 155, 245), Color.argb(230, 10, 88, 178), Color.argb(245, 48, 195, 255))
        ),
        ThemeSpec(
            key = "cyber",
            name = "Cyber Neon",
            subtitle = "Violet & Magenta Glow",
            normalColors = intArrayOf(Color.argb(215, 88, 24, 158), Color.argb(195, 42, 14, 96), Color.argb(210, 148, 32, 178)),
            focusedColors = intArrayOf(Color.argb(245, 155, 52, 245), Color.argb(230, 82, 24, 168), Color.argb(245, 225, 65, 215))
        ),
        ThemeSpec(
            key = "sunset",
            name = "Sunset Glow",
            subtitle = "Crimson & Amber",
            normalColors = intArrayOf(Color.argb(215, 155, 34, 82), Color.argb(195, 92, 18, 54), Color.argb(210, 198, 62, 78)),
            focusedColors = intArrayOf(Color.argb(245, 235, 68, 108), Color.argb(230, 165, 34, 78), Color.argb(245, 255, 128, 72))
        ),
        ThemeSpec(
            key = "emerald",
            name = "Emerald Forest",
            subtitle = "Deep Jade & Mint",
            normalColors = intArrayOf(Color.argb(215, 14, 118, 88), Color.argb(195, 8, 66, 52), Color.argb(210, 22, 148, 112)),
            focusedColors = intArrayOf(Color.argb(245, 24, 185, 138), Color.argb(230, 12, 115, 86), Color.argb(245, 52, 225, 175))
        ),
        ThemeSpec(
            key = "dark",
            name = "Slate Dark",
            subtitle = "Obsidian Graphite",
            normalColors = intArrayOf(Color.argb(225, 26, 32, 48), Color.argb(215, 14, 18, 28), Color.argb(225, 38, 46, 68)),
            focusedColors = intArrayOf(Color.argb(245, 52, 64, 92), Color.argb(235, 28, 36, 54), Color.argb(245, 74, 88, 125))
        ),
        ThemeSpec(
            key = "crystal",
            name = "Frosted Crystal",
            subtitle = "Bright Ice Glass",
            normalColors = intArrayOf(Color.argb(215, 215, 232, 252), Color.argb(195, 182, 208, 242), Color.argb(210, 228, 242, 255)),
            focusedColors = intArrayOf(Color.argb(245, 240, 248, 255), Color.argb(230, 205, 228, 255), Color.argb(245, 255, 255, 255))
        ),
        ThemeSpec(
            key = "obsidian",
            name = "Golden Obsidian",
            subtitle = "Royal Black & Gold",
            normalColors = intArrayOf(Color.argb(225, 34, 28, 18), Color.argb(215, 16, 14, 12), Color.argb(225, 58, 46, 24)),
            focusedColors = intArrayOf(Color.argb(245, 96, 76, 34), Color.argb(235, 42, 34, 20), Color.argb(245, 148, 115, 44))
        )
    )
    // Natural multi-stop gradient accents inspired by organic canvas color harmonies.
    val ACCENTS = listOf(
        AccentSpec(
            "aurora", "Aurora", Color.rgb(64, 216, 198),
            intArrayOf(Color.rgb(42, 145, 235), Color.rgb(52, 228, 182), Color.rgb(155, 245, 145))
        ),
        AccentSpec(
            "ember", "Ember", Color.rgb(255, 122, 78),
            intArrayOf(Color.rgb(232, 56, 108), Color.rgb(255, 128, 64), Color.rgb(255, 204, 82))
        ),
        AccentSpec(
            "orchid", "Orchid", Color.rgb(198, 112, 255),
            intArrayOf(Color.rgb(112, 78, 245), Color.rgb(208, 92, 235), Color.rgb(255, 134, 182))
        ),
        AccentSpec(
            "lagoon", "Lagoon", Color.rgb(72, 196, 255),
            intArrayOf(Color.rgb(24, 102, 218), Color.rgb(48, 196, 245), Color.rgb(118, 245, 225))
        ),
        AccentSpec(
            "solar", "Solar", Color.rgb(255, 192, 68),
            intArrayOf(Color.rgb(245, 136, 42), Color.rgb(255, 196, 68), Color.rgb(255, 238, 138))
        ),
        AccentSpec(
            "meadow", "Meadow", Color.rgb(92, 232, 132),
            intArrayOf(Color.rgb(28, 168, 112), Color.rgb(88, 224, 128), Color.rgb(188, 245, 108))
        )
    )
    val TILE_SIZES = listOf(
        TileSizeSpec("small", "Small", badgeDp = 64, desiredGridDp = 86, hiddenScale = 0.75f),
        TileSizeSpec("medium", "Medium", badgeDp = 78, desiredGridDp = 104, hiddenScale = 0.85f),
        TileSizeSpec("large", "Large", badgeDp = 94, desiredGridDp = 124, hiddenScale = 0.96f)
    )
    val GREETING_STYLES = listOf(
        GreetingStyleSpec("timeOfDay", "Time of Day", "Good Morning / Evening"),
        GreetingStyleSpec("welcome", "Welcome Back", "Welcome Back, Name"),
        GreetingStyleSpec("minimal", "Minimal", "Profile Name Only"),
        GreetingStyleSpec("custom", "Custom Phrase", "Personalized Prefix")
    )
    val GREETING_FONTS = listOf(
        GreetingFontSpec("jakarta", "Jakarta", "sans-serif-medium"),
        GreetingFontSpec("outfit", "Outfit", "sans-serif-light"),
        GreetingFontSpec("mono", "Cyber Mono", "monospace")
    )
    val TILE_SHAPES = listOf(
        OptionSpec("squircle", "Squircle"),
        OptionSpec("circle", "Circle"),
        OptionSpec("heart", "Heart"),
        OptionSpec("rounded", "Classic")
    )
    // WMO weather code mappings shared across ClockWeatherCardDesign and backdrop weather aura.
    val WEATHER_VISUAL_SPECS = listOf(
        WeatherVisualSpec(0..0, "☀️", "Clear Sky"),
        WeatherVisualSpec(1..2, "🌤️", "Partly Cloudy"),
        WeatherVisualSpec(3..3, "☁️", "Overcast"),
        WeatherVisualSpec(45..48, "🌫️", "Misty Fog"),
        WeatherVisualSpec(51..67, "🌧️", "Light Rain"),
        WeatherVisualSpec(80..82, "🌦️", "Rain Showers"),
        WeatherVisualSpec(95..99, "⛈️", "Thunderstorm")
    )
    // Curated quick cities for D-Pad location selector dialog.
    val QUICK_CITIES = listOf(
        CitySpec("Hyderabad", "Telangana", "India", 17.3850, 78.4867),
        CitySpec("Visakhapatnam", "Andhra Pradesh", "India", 17.6868, 83.2185),
        CitySpec("Bengaluru", "Karnataka", "India", 12.9716, 77.5946),
        CitySpec("Chennai", "Tamil Nadu", "India", 13.0827, 80.2707),
        CitySpec("Mumbai", "Maharashtra", "India", 19.0760, 72.8777),
        CitySpec("New Delhi", "Delhi", "India", 28.6139, 77.2090)
    )
    fun getCornerRadiusDp(shapeKey: String?): Float = when (shapeKey) {
        "circle" -> 999f
        "heart" -> 28f
        "rounded" -> 14f
        else -> 24f
    }
    // Single source of truth for all 6 clock widget styles; edit here to sync everywhere automatically.
    val CLOCK_STYLES = listOf(
        ClockStyleSpec(
            key = "classic",
            label = "Classic",
            showDialFace = true,
            showBezelRing = true,
            showTicks = true,
            showNumerals = true,
            showHands = true,
            outerRingRatio = 0.88f,
            innerRingRatio = 0.67f,
            handScaleRatio = 1.0f
        ),
        ClockStyleSpec(
            key = "chrono",
            label = "Chrono",
            showDialFace = true,
            showProgressArcs = true,
            showHands = true,
            outerRingRatio = 0.90f,
            innerRingRatio = 0.74f,
            handScaleRatio = 0.92f
        ),
        ClockStyleSpec(
            key = "eclipse",
            label = "Eclipse",
            showDialFace = true,
            showCoronaHalo = true,
            showTicks = true,
            showHands = true,
            outerRingRatio = 0.86f,
            innerRingRatio = 0.64f,
            handScaleRatio = 0.95f
        ),
        ClockStyleSpec(
            key = "matrix",
            label = "Pulse Rings",
            showDialFace = true,
            showActivityRings = true,
            showDigitalReadout = true,
            showHands = false,
            outerRingRatio = 0.90f,
            innerRingRatio = 0.72f
        ),
        ClockStyleSpec(
            key = "minimal",
            label = "Orbit",
            showDialFace = false,
            dashedOuterRing = true,
            showHands = true,
            outerRingRatio = 0.88f,
            innerRingRatio = 0.62f,
            handScaleRatio = 1.0f
        ),
        ClockStyleSpec(
            key = "digital",
            label = "Digital",
            showDialFace = true,
            showBezelRing = true,
            showDigitalReadout = true,
            showHands = false,
            cornerRadiusRatio = 0.35f
        )
    )
    val LOCATION_MODES = listOf(
        OptionSpec("auto", "Automatic"),
        OptionSpec("manual", "Manual")
    )
    // Single source of truth for app tile nameplate visibility modes.
    val NAMEPLATE_MODES = listOf(
        OptionSpec("always", "Always"),
        OptionSpec("focus", "On Focus"),
        OptionSpec("hidden", "Hidden")
    )
    // Proportional minimum width ratios for adaptive panel grids.
    const val MIN_SUMMARY_CARD_DP = 112
    const val MIN_TEXT_CHIP_DP = 64
    const val MIN_SWATCH_ORB_DP = 40
    val PANEL_WIDTH_OPTIONS = listOf(20, 25, 30, 35)
    const val PANEL_MIN_WIDTH_DP = 165
    val GRID_COL_RANGE = 2..10
    val CONVEYOR_NEIGHBOR_SCALE_RANGE = 0.64f..0.82f
    const val CONVEYOR_NEIGHBOR_ALPHA = 0.48f
    const val CONVEYOR_SIDE_MIN_HEIGHT_RATIO = 0.52f
    private val themeMap by lazy { THEMES.associateBy { it.key } }
    private val sizeMap by lazy { TILE_SIZES.associateBy { it.key } }
    private val accentByColorMap by lazy { ACCENTS.associateBy { it.color } }
    private val accentByKeyOrLabelMap by lazy {
        buildMap {
            ACCENTS.forEach { spec ->
                put(spec.key.lowercase(), spec)
                put(spec.label.lowercase(), spec)
            }
        }
    }
    private val clockStyleMap by lazy { CLOCK_STYLES.associateBy { it.key } }
    fun getTheme(key: String?): ThemeSpec = themeMap[key] ?: THEMES.first()
    fun getClockStyle(key: String?): ClockStyleSpec = clockStyleMap[key] ?: CLOCK_STYLES.first()
    // Resolves the full AccentSpec for a given accent color or key/label.
    fun getAccentSpec(accentColor: Int, name: String? = null): AccentSpec {
        accentByColorMap[accentColor]?.let { return it }
        name?.lowercase()?.let { key -> accentByKeyOrLabelMap[key]?.let { return it } }
        return ACCENTS.first()
    }
    // Creates a linear shader across a gradient accent's color stops.
    fun createAccentShader(width: Float, height: Float, accentColor: Int): LinearGradient {
        val spec = getAccentSpec(accentColor)
        return LinearGradient(0f, 0f, width.coerceAtLeast(1f), height.coerceAtLeast(1f), spec.gradientColors, null, Shader.TileMode.CLAMP)
    }
    private fun computeAverageLuminance(colors: IntArray): Float {
        if (colors.isEmpty()) return 0f
        var sum = 0f
        for (c in colors) {
            val r = Color.red(c) / 255f
            val g = Color.green(c) / 255f
            val b = Color.blue(c) / 255f
            sum += (0.2126f * r + 0.7152f * g + 0.0722f * b)
        }
        return sum / colors.size
    }
    fun isLightSurface(colors: IntArray): Boolean = computeAverageLuminance(colors) > 0.45f
    // Computes high-contrast text color dynamically based on surface luminance.
    fun getAdaptiveTextColor(colors: IntArray, secondary: Boolean = false): Int {
        val sample = colors.firstOrNull() ?: return Color.WHITE
        val hsv = FloatArray(3)
        Color.colorToHSV(sample, hsv)
        return if (isLightSurface(colors)) {
            hsv.set(1, (hsv.get(1) * 0.85f).coerceIn(0.25f, 0.85f))
            hsv.set(2, if (secondary) 0.32f else 0.14f)
            Color.HSVToColor(if (secondary) 215 else 255, hsv)
        } else {
            hsv.set(1, (hsv.get(1) * 0.12f).coerceIn(0f, 0.14f))
            hsv.set(2, if (secondary) 0.88f else 0.99f)
            Color.HSVToColor(if (secondary) 205 else 255, hsv)
        }
    }
    fun getPrimaryTextColor(themeKey: String?): Int =
        getAdaptiveTextColor(getTheme(themeKey).normalColors, secondary = false)
    fun getSecondaryTextColor(themeKey: String?): Int =
        getAdaptiveTextColor(getTheme(themeKey).normalColors, secondary = true)
    fun getTopBottomColors(themeKey: String?, focused: Boolean): IntArray {
        val spec = getTheme(themeKey)
        return if (focused) spec.focusedColors else spec.normalColors
    }
    fun getGlassPanelColors(themeKey: String?, accentColor: Int, elevated: Boolean = false): IntArray {
        val base = getTheme(themeKey).normalColors
        val topCol = base.firstOrNull() ?: Color.rgb(22, 34, 72)
        val botCol = base.lastOrNull() ?: topCol
        val alphaBoost = if (elevated) 242 else 220
        val c1 = LayoutMetrics.withAlpha(topCol, alphaBoost)
        val c2 = LayoutMetrics.withAlpha(botCol, (alphaBoost + 8).coerceAtMost(252))
        val c3 = LayoutMetrics.blendWithAccent(botCol, accentColor, if (elevated) 0.22f else 0.14f, alphaBoost)
        return intArrayOf(c1, c2, c3)
    }
    fun getTileSizeSpec(key: String?): TileSizeSpec = sizeMap[key] ?: sizeMap["medium"] ?: TILE_SIZES.first()
    fun getAccentByName(name: String?): Int = name?.lowercase()?.let { accentByKeyOrLabelMap[it]?.color } ?: ACCENTS.first().color
    fun getConnectedSsid(ctx: Context): String {
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val raw = wm?.connectionInfo?.ssid?.replace("\"", "") ?: ""
            if (raw.isNotEmpty() && raw != "<unknown ssid>") raw else "Wi-Fi"
        } catch (_: Exception) {
            "Wi-Fi"
        }
    }
}
