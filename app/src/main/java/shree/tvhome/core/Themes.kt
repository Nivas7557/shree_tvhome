// Important instructions:
// - Act as the single source of truth for palettes, Canva-inspired gradient accents, tile specs, clock styles, and location modes.
// - Any addition, edit, or removal here propagates automatically to panels, tiles, and widgets.

package shree.tvhome.core

import android.content.Context
import android.graphics.Color
import android.net.wifi.WifiManager

object Themes {

    data class ThemeSpec(
        val key: String,
        val name: String,
        val subtitle: String,
        val normalColors: IntArray,
        val focusedColors: IntArray
    )

    // Canva-inspired gradient accent spec holding primary, secondary, and tertiary gradient stops.
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

    // Data-driven specification for clock styles so renderer logic never hardcodes style names.
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
        val outerRingRatio: Float = 0.88f,
        val innerRingRatio: Float = 0.62f,
        val handScaleRatio: Float = 1.0f,
        val cornerRadiusRatio: Float = 0.35f
    )

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

    // Natural Canva-style gradient accents blending complementary tones instead of flat single colors.
    val ACCENTS = listOf(
        AccentSpec(
            "royal", "Sky & Iris", Color.rgb(92, 184, 255),
            intArrayOf(Color.rgb(72, 202, 255), Color.rgb(123, 120, 255))
        ),
        AccentSpec(
            "emerald", "Mint & Teal", Color.rgb(46, 232, 138),
            intArrayOf(Color.rgb(46, 232, 138), Color.rgb(24, 182, 198))
        ),
        AccentSpec(
            "magenta", "Coral & Rose", Color.rgb(255, 110, 145),
            intArrayOf(Color.rgb(255, 128, 112), Color.rgb(225, 76, 168))
        ),
        AccentSpec(
            "purple", "Violet & Lilac", Color.rgb(185, 135, 255),
            intArrayOf(Color.rgb(168, 98, 255), Color.rgb(232, 128, 225))
        ),
        AccentSpec(
            "sunset", "Peach & Gold", Color.rgb(255, 195, 75),
            intArrayOf(Color.rgb(255, 158, 68), Color.rgb(255, 218, 96))
        ),
        AccentSpec(
            "cyan", "Aqua & Lagoon", Color.rgb(75, 235, 245),
            intArrayOf(Color.rgb(64, 232, 218), Color.rgb(88, 158, 255))
        )
    )

    // Tile size presets defining badge dimensions, desired grid cell size, and hidden-row scale.
    val TILE_SIZES = listOf(
        TileSizeSpec("small", "Small", badgeDp = 64, desiredGridDp = 86, hiddenScale = 0.75f),
        TileSizeSpec("medium", "Medium", badgeDp = 78, desiredGridDp = 104, hiddenScale = 0.85f),
        TileSizeSpec("large", "Large", badgeDp = 94, desiredGridDp = 124, hiddenScale = 0.96f)
    )

    // Supported tile outline shapes.
    val TILE_SHAPES = listOf(
        OptionSpec("squircle", "◐ Squircle"),
        OptionSpec("circle", "● Circle"),
        OptionSpec("heart", "♥ Heart"),
        OptionSpec("classic", "■ Classic")
    )

    // Single source of truth for clock widget styles; add/remove/edit entries here to sync everywhere.
    val CLOCK_STYLES = listOf(
        ClockStyleSpec(
            key = "classic",
            label = "Classic",
            showDialFace = true,
            showBezelRing = true,
            showTicks = true,
            showNumerals = true,
            showProgressArcs = false,
            dashedOuterRing = false,
            showDigitalReadout = false,
            showHands = true,
            outerRingRatio = 0.88f,
            innerRingRatio = 0.67f,
            handScaleRatio = 1.0f
        ),
        ClockStyleSpec(
            key = "chrono",
            label = "Chrono",
            showDialFace = true,
            showBezelRing = false,
            showTicks = false,
            showNumerals = false,
            showProgressArcs = true,
            dashedOuterRing = false,
            showDigitalReadout = false,
            showHands = true,
            outerRingRatio = 0.90f,
            innerRingRatio = 0.74f,
            handScaleRatio = 0.92f
        ),
        ClockStyleSpec(
            key = "minimal",
            label = "Orbit",
            showDialFace = false,
            showBezelRing = false,
            showTicks = false,
            showNumerals = false,
            showProgressArcs = false,
            dashedOuterRing = true,
            showDigitalReadout = false,
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
            showTicks = false,
            showNumerals = false,
            showProgressArcs = false,
            dashedOuterRing = false,
            showDigitalReadout = true,
            showHands = false,
            cornerRadiusRatio = 0.35f
        )
    )

    // Weather location resolution modes ("auto" vs "manual").
    val LOCATION_MODES = listOf(
        OptionSpec("auto", "Automatic"),
        OptionSpec("manual", "Manual")
    )

    // Right-side stage/drawer width options and shared layout bounds.
    val PANEL_WIDTH_OPTIONS = listOf(20, 25, 30, 35)
    const val PANEL_MIN_WIDTH_DP = 165
    val GRID_COL_RANGE = 3..8

    private val themeMap by lazy { THEMES.associateBy { it.key } }
    private val sizeMap by lazy { TILE_SIZES.associateBy { it.key } }
    private val accentMap by lazy { ACCENTS.associateBy { it.key.lowercase() } }
    private val clockStyleMap by lazy { CLOCK_STYLES.associateBy { it.key } }

    // Looks up a theme specification by key, falling back to the default theme.
    fun getTheme(key: String?): ThemeSpec = themeMap[key] ?: THEMES.first()

    // Looks up a clock style specification by key, falling back to the first style in CLOCK_STYLES.
    fun getClockStyle(key: String?): ClockStyleSpec = clockStyleMap[key] ?: CLOCK_STYLES.first()

    // Returns gradient color stops for an accent color so UI controls render rich Canva-like gradients.
    fun getAccentGradientColors(accentColor: Int): IntArray {
        val match = ACCENTS.find { it.color == accentColor }
        if (match != null) return match.gradientColors
        val hsv = FloatArray(3)
        Color.colorToHSV(accentColor, hsv)
        val shiftedHsv = floatArrayOf((hsv[0] + 38f) % 360f, (hsv[1] * 0.92f).coerceIn(0.35f, 1f), (hsv[2] * 0.96f).coerceIn(0.4f, 1f))
        return intArrayOf(accentColor, Color.HSVToColor(shiftedHsv))
    }

    // Calculates mean relative luminance across a gradient color array.
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

    // Determines whether a surface palette is bright enough to require dark foreground text.
    fun isLightSurface(colors: IntArray): Boolean = computeAverageLuminance(colors) > 0.45f

    // Computes a readable primary or secondary text color matched to the surface's hue.
    fun getAdaptiveTextColor(colors: IntArray, secondary: Boolean = false): Int {
        val sample = colors.firstOrNull() ?: return Color.WHITE
        val hsv = FloatArray(3)
        Color.colorToHSV(sample, hsv)
        return if (isLightSurface(colors)) {
            hsv[1] = (hsv[1] * 0.85f).coerceIn(0.25f, 0.85f)
            hsv[2] = if (secondary) 0.32f else 0.14f
            Color.HSVToColor(if (secondary) 215 else 255, hsv)
        } else {
            hsv[1] = (hsv[1] * 0.12f).coerceIn(0f, 0.14f)
            hsv[2] = if (secondary) 0.88f else 0.99f
            Color.HSVToColor(if (secondary) 205 else 255, hsv)
        }
    }

    // Returns primary readable text color for a given theme key.
    fun getPrimaryTextColor(themeKey: String?): Int =
        getAdaptiveTextColor(getTheme(themeKey).normalColors, secondary = false)

    // Returns secondary readable text color for a given theme key.
    fun getSecondaryTextColor(themeKey: String?): Int =
        getAdaptiveTextColor(getTheme(themeKey).normalColors, secondary = true)

    // Returns normal or focused gradient colors for a theme key.
    fun getTopBottomColors(themeKey: String?, focused: Boolean): IntArray {
        val spec = getTheme(themeKey)
        return if (focused) spec.focusedColors else spec.normalColors
    }

    // Builds glass panel gradient colors blended with the active accent gradient.
    fun getGlassPanelColors(themeKey: String?, accentColor: Int, elevated: Boolean = false): IntArray {
        val base = getTheme(themeKey).normalColors
        val topCol = base.firstOrNull() ?: Color.rgb(22, 34, 72)
        val botCol = base.lastOrNull() ?: topCol
        val gradEnd = getAccentGradientColors(accentColor).lastOrNull() ?: accentColor
        val alphaBoost = if (elevated) 242 else 220
        val c1 = LayoutMetrics.withAlpha(topCol, alphaBoost)
        val c2 = LayoutMetrics.blendWithAccent(botCol, gradEnd, 0.12f, (alphaBoost + 6).coerceAtMost(252))
        val c3 = LayoutMetrics.blendWithAccent(botCol, accentColor, if (elevated) 0.22f else 0.14f, alphaBoost)
        return intArrayOf(c1, c2, c3)
    }

    // Looks up a tile size specification by key, defaulting to "medium".
    fun getTileSizeSpec(key: String?): TileSizeSpec = sizeMap[key] ?: sizeMap["medium"] ?: TILE_SIZES.first()

    // Resolves an accent RGB integer from a key or display label.
    fun getAccentByName(name: String?): Int = accentMap[name?.lowercase()]?.color ?: ACCENTS.first().color

    // Reads the active Wi-Fi SSID when connected, or returns "Wi-Fi" as a clean fallback.
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
