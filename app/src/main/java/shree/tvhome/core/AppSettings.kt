// Important instructions:
// - Act as the single source of truth for persisted launcher preferences and style change listeners.
// - Automatically derive default device identity from OS settings without hardcoded owner names.

package shree.tvhome.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import java.util.concurrent.CopyOnWriteArrayList

class AppSettings(context: Context) {

    // Stores active listeners that repaint surfaces in-place when visual tokens change.
    private val styleListeners = CopyOnWriteArrayList<() -> Unit>()

    // Registers a style listener once so panels, widgets, and tiles react automatically.
    fun addStyleListener(listener: () -> Unit) {
        if (!styleListeners.contains(listener)) styleListeners.add(listener)
    }

    // Removes a previously registered style listener.
    fun removeStyleListener(listener: () -> Unit) {
        styleListeners.remove(listener)
    }

    // Notifies all registered style listeners after a theme or accent mutation.
    private fun notifyStyleChanged() {
        for (listener in styleListeners) listener()
    }

    companion object {
        const val PREFS = "launcher_preferences"
        const val PREF_ACCENT = "accent_color"
        const val PREF_ACCENT_NAME = "accent_name"
        const val PREF_TILE_SIZE = "tile_size"
        const val PREF_TILE_SHAPE = "tile_shape"
        const val PREF_TILE_COLOR = "tile_color"
        const val PREF_COLUMNS = "grid_columns"
        const val PREF_PANEL_WIDTH = "panel_width_pct"
        const val PREF_WALLPAPER = "custom_wallpaper_uri"
        const val PREF_FAVORITES = "favorites"
        const val PREF_FAVORITES_ORDER = "favorites_ordered_list"
        const val PREF_HIDDEN_APPS = "hidden_apps_set"
        const val PREF_USER_NAME = "launcher_user_name"
        const val PREF_GREETING_PHRASE = "launcher_greeting_phrase"
        const val PREF_SORT_ORDER = "sort_order"
        const val PREF_LIVE_WP_MODE = "live_wp_mode"
        const val PREF_PHOTO_WEATHER_AURA = "photo_weather_aura"
        const val PREF_CACHED_WEATHER_CODE = "cached_weather_code"
        const val PREF_MOTION_STYLE = "motion_style"
        const val PREF_WEATHER_CITY = "weather_city"

        // Profile avatar and crop transformation keys.
        const val PREF_PROFILE_PIC_URI = "profile_pic_uri"
        const val PREF_PROFILE_PIC_ZOOM = "profile_pic_zoom"
        const val PREF_PROFILE_PIC_PAN_X = "profile_pic_pan_x"
        const val PREF_PROFILE_PIC_PAN_Y = "profile_pic_pan_y"

        // Wallpaper scaling and pan offset keys.
        const val PREF_WP_SCALE_MODE = "wp_scale_mode"
        const val PREF_WP_ZOOM = "wp_zoom"
        const val PREF_WP_OFFSET_X = "wp_offset_x"
        const val PREF_WP_OFFSET_Y = "wp_offset_y"

        // Cached weather and location keys.
        const val PREF_CACHED_TEMP = "cached_temp"
        const val PREF_CACHED_COND = "cached_cond"
        const val PREF_CACHED_CITY = "cached_city"
        const val PREF_SAVED_LAT = "saved_lat"
        const val PREF_SAVED_LON = "saved_lon"

        // Display picture mode and custom calibration keys.
        const val PREF_PICTURE_MODE = "picture_mode"
        const val PREF_CUSTOM_BRIGHTNESS = "custom_brightness_pct"
        const val PREF_CUSTOM_WARMTH = "custom_warmth_pct"

        // Nameplate visibility, Do Not Disturb, and sleep timer keys.
        const val PREF_NAMEPLATE_MODE = "nameplate_mode"
        const val PREF_DND_ENABLED = "dnd_enabled"
        const val PREF_SLEEP_TIMER_END_MS = "sleep_timer_end_ms"
    }

    val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Active accent color; notifies style listeners automatically on change.
    var accentColor: Int
        get() = prefs.getInt(PREF_ACCENT, Themes.ACCENTS.first().color)
        set(value) {
            prefs.edit().putInt(PREF_ACCENT, value).apply()
            notifyStyleChanged()
        }

    // Human-readable label for the currently selected accent color.
    var accentName: String
        get() = prefs.getString(PREF_ACCENT_NAME, Themes.ACCENTS.first().label) ?: Themes.ACCENTS.first().label
        set(value) = prefs.edit().putString(PREF_ACCENT_NAME, value).apply()

    // Selected app tile size key ("small", "medium", or "large").
    var tileSize: String
        get() = prefs.getString(PREF_TILE_SIZE, Themes.getTileSizeSpec("medium").key)
            ?: Themes.getTileSizeSpec("medium").key
        set(value) = prefs.edit().putString(PREF_TILE_SIZE, value).apply()

    // Selected tile icon silhouette ("squircle", "circle", "heart", or "classic").
    var tileShape: String
        get() = prefs.getString(PREF_TILE_SHAPE, Themes.TILE_SHAPES.first().key)
            ?: Themes.TILE_SHAPES.first().key
        set(value) = prefs.edit().putString(PREF_TILE_SHAPE, value).apply()

    // Selected glass palette key; notifies style listeners automatically on change.
    var tileColor: String
        get() = prefs.getString(PREF_TILE_COLOR, Themes.THEMES.first().key) ?: Themes.THEMES.first().key
        set(value) {
            prefs.edit().putString(PREF_TILE_COLOR, value).apply()
            notifyStyleChanged()
        }

    // Clamped grid column count bounded by Themes.GRID_COL_RANGE.
    var columns: Int
        get() = prefs.getInt(PREF_COLUMNS, 6).coerceIn(Themes.GRID_COL_RANGE)
        set(value) = prefs.edit().putInt(PREF_COLUMNS, value.coerceIn(Themes.GRID_COL_RANGE)).apply()

    // Right-side stage/drawer width percentage bounded by available panel width options.
    var panelWidthPct: Int
        get() = prefs.getInt(
            PREF_PANEL_WIDTH,
            Themes.PANEL_WIDTH_OPTIONS.getOrElse(1) { Themes.PANEL_WIDTH_OPTIONS.first() }
        ).coerceIn(Themes.PANEL_WIDTH_OPTIONS.first(), Themes.PANEL_WIDTH_OPTIONS.last())
        set(value) = prefs.edit().putInt(
            PREF_PANEL_WIDTH,
            value.coerceIn(Themes.PANEL_WIDTH_OPTIONS.first(), Themes.PANEL_WIDTH_OPTIONS.last())
        ).apply()

    // Resolves default profile name dynamically from system device settings or manufacturer.
    private val defaultDeviceOwner: String by lazy {
        val resolver = context.contentResolver
        val globalName = try {
            Settings.Global.getString(resolver, Settings.Global.DEVICE_NAME)
        } catch (_: Exception) { null }

        val btName = try {
            Settings.Secure.getString(resolver, "bluetooth_name")
        } catch (_: Exception) { null }

        val manufacturer = Build.MANUFACTURER
            ?.replaceFirstChar { it.uppercase() }
            ?.takeIf { it.isNotBlank() }

        listOf(globalName, btName, manufacturer, "TV")
            .firstOrNull { !it.isNullOrBlank() && !it.equals("unknown", ignoreCase = true) }
            ?.trim() ?: "TV"
    }

    // User display name shown in greeting and profile controls.
    var userName: String
        get() = prefs.getString(PREF_USER_NAME, defaultDeviceOwner) ?: defaultDeviceOwner
        set(value) = prefs.edit().putString(PREF_USER_NAME, value.ifBlank { defaultDeviceOwner }).apply()

    // Optional custom greeting phrase; empty falls back to automatic time-of-day greeting.
    var greetingPhrase: String
        get() = prefs.getString(PREF_GREETING_PHRASE, "") ?: ""
        set(value) = prefs.edit().putString(PREF_GREETING_PHRASE, value).apply()

    // App catalog sort order ("az" or "za").
    var sortOrder: String
        get() = prefs.getString(PREF_SORT_ORDER, "az") ?: "az"
        set(value) = prefs.edit().putString(PREF_SORT_ORDER, value).apply()

    // Active live wallpaper or smart scene mode.
    var liveWallpaperMode: String
        get() = prefs.getString(PREF_LIVE_WP_MODE, LiveBackdrop.MODE_SMART) ?: LiveBackdrop.MODE_SMART
        set(value) = prefs.edit().putString(PREF_LIVE_WP_MODE, value).apply()

    // Toggles subtle weather aura overlay on custom gallery wallpapers.
    var photoWeatherAura: Boolean
        get() = prefs.getBoolean(PREF_PHOTO_WEATHER_AURA, true)
        set(value) = prefs.edit().putBoolean(PREF_PHOTO_WEATHER_AURA, value).apply()

    // Latest WMO weather code cached from weather updates.
    var cachedWeatherCode: Int
        get() = prefs.getInt(PREF_CACHED_WEATHER_CODE, 0)
        set(value) = prefs.edit().putInt(PREF_CACHED_WEATHER_CODE, value).apply()

    // Active global motion style key managed by AnimationController.
    var motionStyle: String
        get() = prefs.getString(PREF_MOTION_STYLE, AnimationController.STYLES.first().key)
            ?: AnimationController.STYLES.first().key
        set(value) = prefs.edit().putString(PREF_MOTION_STYLE, value).apply()

    // User-selected weather city label.
    var weatherCity: String
        get() = prefs.getString(PREF_WEATHER_CITY, "") ?: ""
        set(value) = prefs.edit().putString(PREF_WEATHER_CITY, value).apply()

    // URI string for the user's custom profile avatar image.
    var profilePicUri: String?
        get() = prefs.getString(PREF_PROFILE_PIC_URI, null)
        set(value) = prefs.edit().putString(PREF_PROFILE_PIC_URI, value).apply()

    // Zoom factor applied inside the circular avatar crop preview and output.
    var profilePicZoom: Float
        get() = prefs.getFloat(PREF_PROFILE_PIC_ZOOM, 1.0f)
        set(value) = prefs.edit().putFloat(PREF_PROFILE_PIC_ZOOM, value).apply()

    // Horizontal pan ratio for avatar crop framing.
    var profilePicPanX: Float
        get() = prefs.getFloat(PREF_PROFILE_PIC_PAN_X, 0f)
        set(value) = prefs.edit().putFloat(PREF_PROFILE_PIC_PAN_X, value).apply()

    // Vertical pan ratio for avatar crop framing.
    var profilePicPanY: Float
        get() = prefs.getFloat(PREF_PROFILE_PIC_PAN_Y, 0f)
        set(value) = prefs.edit().putFloat(PREF_PROFILE_PIC_PAN_Y, value).apply()

    // Wallpaper scaling mode ("fit" or "width").
    var wpScaleMode: String
        get() = prefs.getString(PREF_WP_SCALE_MODE, "fit") ?: "fit"
        set(value) = prefs.edit().putString(PREF_WP_SCALE_MODE, value).apply()

    // Wallpaper zoom multiplier.
    var wpZoom: Float
        get() = prefs.getFloat(PREF_WP_ZOOM, 1.0f)
        set(value) = prefs.edit().putFloat(PREF_WP_ZOOM, value).apply()

    // Wallpaper horizontal offset in pixels.
    var wpOffsetX: Int
        get() = prefs.getInt(PREF_WP_OFFSET_X, 0)
        set(value) = prefs.edit().putInt(PREF_WP_OFFSET_X, value).apply()

    // Wallpaper vertical offset in pixels.
    var wpOffsetY: Int
        get() = prefs.getInt(PREF_WP_OFFSET_Y, 0)
        set(value) = prefs.edit().putInt(PREF_WP_OFFSET_Y, value).apply()

    // Active display picture preset key.
    var pictureMode: String
        get() = prefs.getString(PREF_PICTURE_MODE, "Standard") ?: "Standard"
        set(value) = prefs.edit().putString(PREF_PICTURE_MODE, value).apply()

    // Custom screen brightness percentage (10..100).
    var customBrightnessPct: Int
        get() = prefs.getInt(PREF_CUSTOM_BRIGHTNESS, 80).coerceIn(10, 100)
        set(value) = prefs.edit().putInt(PREF_CUSTOM_BRIGHTNESS, value.coerceIn(10, 100)).apply()

    // Custom eye-care warmth tint percentage (0..75).
    var customWarmthPct: Int
        get() = prefs.getInt(PREF_CUSTOM_WARMTH, 0).coerceIn(0, 75)
        set(value) = prefs.edit().putInt(PREF_CUSTOM_WARMTH, value.coerceIn(0, 75)).apply()

    // App tile nameplate display mode ("always", "focus", or "hidden").
    var nameplateMode: String
        get() = prefs.getString(PREF_NAMEPLATE_MODE, "always") ?: "always"
        set(value) = prefs.edit().putString(PREF_NAMEPLATE_MODE, value).apply()

    // Do Not Disturb flag that mutes night nudge popups.
    var dndEnabled: Boolean
        get() = prefs.getBoolean(PREF_DND_ENABLED, false)
        set(value) = prefs.edit().putBoolean(PREF_DND_ENABLED, value).apply()

    // Optional sleep timer target epoch time in milliseconds.
    var sleepTimerEndMs: Long
        get() = prefs.getLong(PREF_SLEEP_TIMER_END_MS, 0L)
        set(value) = prefs.edit().putLong(PREF_SLEEP_TIMER_END_MS, value).apply()

    // Returns ordered favorite package names, migrating from legacy string-set storage if needed.
    fun getOrderedFavorites(): MutableList<String> {
        val raw = prefs.getString(PREF_FAVORITES_ORDER, null)
        if (!raw.isNullOrEmpty()) {
            return raw.split(",").filter { it.isNotBlank() }.toMutableList()
        }
        val legacy = prefs.getStringSet(PREF_FAVORITES, emptySet()) ?: emptySet()
        return legacy.toMutableList()
    }

    // Returns unique favorite package names as a set.
    fun getFavorites(): Set<String> = getOrderedFavorites().toSet()

    // Persists deduplicated favorite package order.
    fun setFavorites(favs: Collection<String>) {
        val list = favs.distinct()
        prefs.edit()
            .putString(PREF_FAVORITES_ORDER, list.joinToString(","))
            .putStringSet(PREF_FAVORITES, list.toSet())
            .apply()
    }

    // Shifts a favorite package left or right within the ordered favorites list.
    fun moveFavorite(pkg: String, delta: Int): Boolean {
        val list = getOrderedFavorites()
        val idx = list.indexOf(pkg)
        val target = idx + delta
        if (idx < 0 || target !in list.indices) return false
        list.removeAt(idx)
        list.add(target, pkg)
        setFavorites(list)
        return true
    }

    // Returns a mutable copy of hidden app package names.
    fun getHiddenApps(): MutableSet<String> {
        return HashSet(prefs.getStringSet(PREF_HIDDEN_APPS, emptySet()) ?: emptySet())
    }

    // Persists hidden app package names.
    fun setHiddenApps(hidden: Set<String>) {
        prefs.edit().putStringSet(PREF_HIDDEN_APPS, HashSet(hidden)).apply()
    }

    // Resolves an accent color by accent key or label.
    fun getAccentColorValue(name: String?): Int = Themes.getAccentByName(name)
}
