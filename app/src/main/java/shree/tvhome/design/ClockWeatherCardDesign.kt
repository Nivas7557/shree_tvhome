// Important instructions:
// - Render clock faces dynamically from Themes.ClockStyleSpec without hardcoded style branches or fixed paddings.
// - Handle network/geocoding safely off the main thread and support D-Pad Automatic / Manual location selection.

package shree.tvhome.design

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sin

class ClockWeatherCardDesign(
    ctx: Context,
    private val settings: AppSettings
) : LinearLayout(ctx) {

    private data class WeatherVisualSpec(val range: IntRange, val emoji: String, val label: String)
    private data class CitySuggestion(val name: String, val admin: String, val country: String, val lat: Double, val lon: Double)

    private val weatherSpecs = listOf(
        WeatherVisualSpec(0..0, "☀️", "Clear Sky"),
        WeatherVisualSpec(1..2, "🌤️", "Partly Cloudy"),
        WeatherVisualSpec(3..3, "☁️", "Overcast"),
        WeatherVisualSpec(45..48, "🌫️", "Misty Fog"),
        WeatherVisualSpec(51..67, "🌧️", "Light Rain"),
        WeatherVisualSpec(80..82, "🌦️", "Rain Showers"),
        WeatherVisualSpec(95..99, "⛈️", "Thunderstorm")
    )

    private val quickCities = listOf(
        CitySuggestion("Hyderabad", "Telangana", "India", 17.3850, 78.4867),
        CitySuggestion("Visakhapatnam", "Andhra Pradesh", "India", 17.6868, 83.2185),
        CitySuggestion("Bengaluru", "Karnataka", "India", 12.9716, 77.5946),
        CitySuggestion("Chennai", "Tamil Nadu", "India", 13.0827, 80.2707),
        CitySuggestion("Mumbai", "Maharashtra", "India", 19.0760, 72.8777),
        CitySuggestion("New Delhi", "Delhi", "India", 28.6139, 77.2090)
    )

    private val clockFaceView = DynamicClockFaceView(ctx)
    private val digitalTimeTv = TextView(ctx)
    private val amPmTv = TextView(ctx)
    private val dateTv = TextView(ctx)
    private val weatherIconTv = TextView(ctx)
    private val tempTv = TextView(ctx)
    private val condTv = TextView(ctx)
    private val cityTv = TextView(ctx)
    private val weatherCard = LinearLayout(ctx)

    private val uiHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private var lastWeatherFetchMs = 0L
    private var activeDialog: Dialog? = null
    private var pendingSearchRunnable: Runnable? = null

    private val clockTicker = object : Runnable {
        override fun run() {
            updateTimeDisplay()
            clockFaceView.invalidate()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val cardPadH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.011f, minDp = 10, maxDp = 22, horizontal = true)
        val cardPadV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.014f, minDp = 10, maxDp = 22, horizontal = false)
        setPadding(cardPadH, cardPadV, cardPadH, cardPadV)

        val dialInit = LayoutMetrics.dynamicSpace(ctx, ratio = 0.22f, minDp = 115, maxDp = 185, horizontal = false)
        addView(clockFaceView, LayoutParams(dialInit, dialInit).apply { bottomMargin = cardPadV / 2 })

        val timeRow = LinearLayout(ctx).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        }
        digitalTimeTv.apply {
            textSize = 26f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setShadowLayer(6f, 0f, 2f, Color.argb(150, 0, 0, 0))
        }
        amPmTv.apply {
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(settings.accentColor)
            setPadding(cardPadH / 2, 0, 0, cardPadV / 4)
        }
        timeRow.addView(digitalTimeTv)
        timeRow.addView(amPmTv)
        addView(timeRow)

        dateTv.apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.03f
            setTextColor(Color.rgb(225, 240, 255))
            gravity = Gravity.CENTER
            setPadding(0, cardPadV / 4, 0, cardPadV)
        }
        addView(dateTv)

        weatherCard.apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setPadding(cardPadH, (cardPadV * 0.75f).toInt(), cardPadH, (cardPadV * 0.75f).toInt())
            background = weatherCardBg(false)
            setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 6f)
                v.background = weatherCardBg(f)
            }
            setOnClickListener { showLocationSelectorDialog() }
        }

        val cachedVisual = decodeWeatherCode(settings.cachedWeatherCode)
        val wTop = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        weatherIconTv.apply { text = cachedVisual.first; textSize = 26f; setPadding(0, 0, cardPadH / 2, 0) }
        tempTv.apply {
            text = settings.prefs.getString(AppSettings.PREF_CACHED_TEMP, "28°C") ?: "28°C"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        wTop.addView(weatherIconTv)
        wTop.addView(tempTv)
        weatherCard.addView(wTop)

        condTv.apply {
            text = settings.prefs.getString(AppSettings.PREF_CACHED_COND, cachedVisual.second) ?: cachedVisual.second
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(145, 225, 255))
            gravity = Gravity.CENTER
            setPadding(0, cardPadV / 5, 0, cardPadV / 5)
        }
        weatherCard.addView(condTv)

        val savedCity = settings.prefs.getString(AppSettings.PREF_CACHED_CITY, settings.weatherCity.ifBlank { "Auto Location" })
        val modeTag = if (settings.locationMode == "manual") "Manual" else "Auto"
        cityTv.apply {
            text = "📍 $savedCity ($modeTag) ▾"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        weatherCard.addView(cityTv)
        addView(weatherCard, LayoutParams(-1, -2))

        refreshStyle()
    }

    private fun weatherCardBg(focused: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (focused) intArrayOf(Color.argb(125, 255, 255, 255), Color.argb(75, 255, 255, 255))
        else intArrayOf(Color.argb(75, 255, 255, 255), Color.argb(32, 255, 255, 255))
    ).apply {
        cornerRadius = LayoutMetrics.dpF(context, 18f)
        setStroke(LayoutMetrics.dp(context, if (focused) 2 else 1), if (focused) settings.accentColor else Color.argb(80, 255, 255, 255))
    }

    // Opens a D-Pad friendly location dialog supporting Automatic/Manual toggle, quick cities, and live search.
    fun showLocationSelectorDialog() {
        activeDialog?.dismiss()
        val dialog = Dialog(context)
        activeDialog = dialog
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val padH = LayoutMetrics.dynamicSpace(context, ratio = 0.02f, minDp = 14, maxDp = 26, horizontal = true)
        val padV = LayoutMetrics.dynamicSpace(context, ratio = 0.02f, minDp = 12, maxDp = 22, horizontal = false)
        val gapH = LayoutMetrics.dynamicSpace(context, ratio = 0.008f, minDp = 6, maxDp = 12, horizontal = true)
        val gapV = LayoutMetrics.dynamicSpace(context, ratio = 0.008f, minDp = 6, maxDp = 12, horizontal = false)

        val scrollWrapper = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
        }

        val box = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(20, 32, 62), Color.rgb(12, 18, 38))
            ).apply {
                cornerRadius = LayoutMetrics.dpF(context, 20f)
                setStroke(LayoutMetrics.dp(context, 1), Color.argb(120, 255, 255, 255))
            }
        }
        scrollWrapper.addView(box)

        box.addView(TextView(context).apply {
            text = "📍  Weather Location"
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, gapV)
        })

        val modeRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        Themes.LOCATION_MODES.forEachIndexed { idx, modeSpec ->
            val isSelected = settings.locationMode == modeSpec.key
            val modeBtn = TextView(context).apply {
                text = if (isSelected) "✓  ${modeSpec.label}" else modeSpec.label
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                setPadding(gapH, gapV, gapH, gapV)
                background = GradientDrawable().apply {
                    setColor(if (isSelected) LayoutMetrics.withAlpha(settings.accentColor, 180) else Color.argb(48, 255, 255, 255))
                    cornerRadius = LayoutMetrics.dpF(context, 12f)
                    setStroke(LayoutMetrics.dp(context, if (isSelected) 2 else 1), if (isSelected) Color.WHITE else Color.argb(80, 255, 255, 255))
                }
                setOnFocusChangeListener { v, f ->
                    v.background = GradientDrawable().apply {
                        setColor(if (f || isSelected) LayoutMetrics.withAlpha(settings.accentColor, 185) else Color.argb(48, 255, 255, 255))
                        cornerRadius = LayoutMetrics.dpF(context, 12f)
                        setStroke(LayoutMetrics.dp(context, if (f || isSelected) 2 else 1), Color.WHITE)
                    }
                }
                setOnClickListener {
                    settings.locationMode = modeSpec.key
                    if (modeSpec.key == Themes.LOCATION_MODES.first().key) {
                        settings.weatherCity = ""
                        settings.prefs.edit()
                            .remove(AppSettings.PREF_SAVED_LAT)
                            .remove(AppSettings.PREF_SAVED_LON)
                            .remove(AppSettings.PREF_CACHED_CITY)
                            .apply()
                        dialog.dismiss()
                        triggerWeatherRefresh(true)
                    } else {
                        dialog.dismiss()
                        showLocationSelectorDialog()
                    }
                }
            }
            modeRow.addView(modeBtn, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (idx == 0) rightMargin = gapH
            })
        }
        box.addView(modeRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })

        val suggestionsContainer = LinearLayout(context).apply { orientation = VERTICAL }

        val applySelectedCity: (CitySuggestion) -> Unit = { item ->
            settings.locationMode = "manual"
            settings.weatherCity = item.name
            settings.prefs.edit()
                .putString(AppSettings.PREF_CACHED_CITY, item.name)
                .putFloat(AppSettings.PREF_SAVED_LAT, item.lat.toFloat())
                .putFloat(AppSettings.PREF_SAVED_LON, item.lon.toFloat())
                .apply()
            cityTv.text = "📍 ${item.name} (Manual) ▾"
            dialog.dismiss()
            triggerWeatherRefresh(true)
        }

        val renderCityRows: (List<CitySuggestion>, String) -> Unit = { list, heading ->
            suggestionsContainer.removeAllViews()
            suggestionsContainer.addView(TextView(context).apply {
                text = heading
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(settings.accentColor)
                setPadding(0, 0, 0, gapV / 2)
            })
            list.chunked(2).forEach { pair ->
                val row = LinearLayout(context).apply { orientation = HORIZONTAL }
                pair.forEachIndexed { colIdx, item ->
                    val rowBtn = LinearLayout(context).apply {
                        orientation = VERTICAL
                        isFocusable = true
                        isClickable = true
                        setPadding(gapH, gapV, gapH, gapV)
                        background = GradientDrawable().apply {
                            setColor(Color.argb(45, 255, 255, 255))
                            cornerRadius = LayoutMetrics.dpF(context, 10f)
                            setStroke(LayoutMetrics.dp(context, 1), Color.argb(65, 255, 255, 255))
                        }
                        setOnFocusChangeListener { v, f ->
                            v.background = GradientDrawable().apply {
                                setColor(if (f) LayoutMetrics.withAlpha(settings.accentColor, 150) else Color.argb(45, 255, 255, 255))
                                cornerRadius = LayoutMetrics.dpF(context, 10f)
                                setStroke(LayoutMetrics.dp(context, if (f) 2 else 1), if (f) Color.WHITE else Color.argb(65, 255, 255, 255))
                            }
                        }
                        setOnClickListener { applySelectedCity(item) }
                        addView(TextView(context).apply {
                            text = "📍 ${item.name}"
                            textSize = 10.5f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(Color.WHITE)
                        })
                        val sub = listOf(item.admin, item.country).filter { it.isNotBlank() }.joinToString(", ")
                        if (sub.isNotEmpty()) {
                            addView(TextView(context).apply {
                                text = sub
                                textSize = 8.5f
                                setTextColor(Color.argb(195, 210, 230, 255))
                            })
                        }
                    }
                    row.addView(rowBtn, LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (colIdx == 0 && pair.size > 1) rightMargin = gapH
                    })
                }
                suggestionsContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV })
            }
        }

        val inputW = LayoutMetrics.dynamicSpace(context, ratio = 0.34f, minDp = 240, maxDp = 420, horizontal = true)
        val input = EditText(context).apply {
            isSingleLine = true
            isFocusable = true
            isFocusableInTouchMode = true
            hint = "Search city..."
            setText(if (settings.locationMode == "manual") settings.weatherCity else "")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(150, 200, 220, 245))
            setPadding(gapH, gapV, gapH, gapV)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = GradientDrawable().apply {
                setColor(Color.argb(55, 255, 255, 255))
                cornerRadius = LayoutMetrics.dpF(context, 12f)
                setStroke(LayoutMetrics.dp(context, 1), settings.accentColor)
            }
        }
        box.addView(input, LinearLayout.LayoutParams(inputW, -2).apply { bottomMargin = gapV })

        val statusTv = TextView(context).apply {
            text = "Choose a city or type 2+ characters"
            textSize = 9.5f
            setTextColor(Color.rgb(145, 215, 255))
            setPadding(0, 0, 0, gapV / 2)
        }
        box.addView(statusTv)
        box.addView(suggestionsContainer)

        renderCityRows(quickCities, "QUICK CITIES")

        dialog.setContentView(scrollWrapper)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                pendingSearchRunnable?.let { uiHandler.removeCallbacks(it) }
                if (q.length < 2) {
                    renderCityRows(quickCities, "QUICK CITIES")
                    statusTv.text = "Choose a city or type 2+ characters"
                } else {
                    statusTv.text = "Searching..."
                    val r = Runnable {
                        bgExecutor.execute {
                            val (list, errMsg) = fetchCitySuggestionsWithStatus(q)
                            uiHandler.post {
                                if (!dialog.isShowing) return@post
                                if (errMsg != null) {
                                    statusTv.text = errMsg
                                } else if (list.isEmpty()) {
                                    statusTv.text = "No results"
                                } else {
                                    statusTv.text = "Select city:"
                                    renderCityRows(list, "RESULTS")
                                }
                            }
                        }
                    }
                    pendingSearchRunnable = r
                    uiHandler.postDelayed(r, 300L)
                }
            }
        })

        dialog.setOnDismissListener {
            pendingSearchRunnable?.let { uiHandler.removeCallbacks(it) }
            pendingSearchRunnable = null
            if (activeDialog === dialog) activeDialog = null
        }

        dialog.show()
    }

    private fun fetchCitySuggestions(query: String): List<CitySuggestion> =
        fetchCitySuggestionsWithStatus(query).first

    private fun fetchCitySuggestionsWithStatus(query: String): Pair<List<CitySuggestion>, String?> {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=6&language=en&format=json"
            val rawResponse = fetchUrl(url)
            val json = JSONObject(rawResponse)
            val arr = json.optJSONArray("results")
            if (arr != null && arr.length() > 0) {
                val out = ArrayList<CitySuggestion>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    out.add(
                        CitySuggestion(
                            name = obj.optString("name"),
                            admin = obj.optString("admin1"),
                            country = obj.optString("country"),
                            lat = obj.optDouble("latitude", 0.0),
                            lon = obj.optDouble("longitude", 0.0)
                        )
                    )
                }
                return out to null
            }
        } catch (_: Exception) {}

        return try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault()).getFromLocationName(query, 6)
            if (!addresses.isNullOrEmpty()) {
                val out = addresses.mapNotNull { addr ->
                    val cName = addr.locality ?: addr.subAdminArea ?: addr.featureName ?: return@mapNotNull null
                    CitySuggestion(
                        name = cName,
                        admin = addr.adminArea ?: "",
                        country = addr.countryName ?: "",
                        lat = addr.latitude,
                        lon = addr.longitude
                    )
                }
                if (out.isNotEmpty()) out to null else emptyList<CitySuggestion>() to null
            } else {
                emptyList<CitySuggestion>() to null
            }
        } catch (e: Exception) {
            emptyList<CitySuggestion>() to (e.message ?: "Network unavailable")
        }
    }

    // Updates clock face spec, accent, glass surface, and display-proportional sizing.
    fun refreshStyle() {
        val accent = settings.accentColor
        amPmTv.setTextColor(accent)
        clockFaceView.updateSpec(Themes.getClockStyle(settings.clockStyle), accent)

        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            Themes.getGlassPanelColors(settings.tileColor, accent, elevated = false)
        ).apply {
            cornerRadius = LayoutMetrics.dpF(context, 24f)
            setStroke(LayoutMetrics.dp(context, 1), LayoutMetrics.withAlpha(accent, 145))
        }
        weatherCard.background = weatherCardBg(weatherCard.hasFocus())

        val minWidth = Themes.PANEL_WIDTH_OPTIONS.first()
        val isNarrow = settings.panelWidthPct <= minWidth + 2
        val ratio = if (isNarrow) 0.17f else 0.23f
        val dialSize = LayoutMetrics.dynamicSpace(context, ratio = ratio, minDp = 105, maxDp = 180, horizontal = false)

        clockFaceView.layoutParams?.let { lp ->
            lp.width = dialSize
            lp.height = dialSize
        }
        digitalTimeTv.textSize = if (isNarrow) 21f else 26f
        dateTv.textSize = if (isNarrow) 11.5f else 13f
        clockFaceView.requestLayout()

        val savedCity = settings.prefs.getString(AppSettings.PREF_CACHED_CITY, settings.weatherCity.ifBlank { "Auto Location" })
        val modeTag = if (settings.locationMode == "manual") "Manual" else "Auto"
        cityTv.text = "📍 $savedCity ($modeTag) ▾"
    }

    // Fetches weather safely off the UI thread using manual coordinates or automatic timezone/locale fallback.
    fun triggerWeatherRefresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastWeatherFetchMs < 20 * 60 * 1000L) return
        lastWeatherFetchMs = now

        val appCtx = context.applicationContext
        bgExecutor.execute {
            try {
                val isManual = settings.locationMode == "manual"
                var lat = if (isManual) settings.prefs.getFloat(AppSettings.PREF_SAVED_LAT, 0f).toDouble() else 0.0
                var lon = if (isManual) settings.prefs.getFloat(AppSettings.PREF_SAVED_LON, 0f).toDouble() else 0.0
                var cityName = if (isManual) settings.weatherCity.trim() else ""

                if (cityName.isNotEmpty() && (lat == 0.0 || lon == 0.0)) {
                    val found = fetchCitySuggestions(cityName).firstOrNull()
                    if (found != null) {
                        lat = found.lat
                        lon = found.lon
                        cityName = found.name
                        settings.prefs.edit()
                            .putFloat(AppSettings.PREF_SAVED_LAT, lat.toFloat())
                            .putFloat(AppSettings.PREF_SAVED_LON, lon.toFloat())
                            .apply()
                    }
                }

                if (lat == 0.0 && lon == 0.0) {
                    val tzCity = TimeZone.getDefault().id
                        .substringAfterLast('/')
                        .replace('_', ' ')
                        .takeIf { it.isNotBlank() && it != "GMT" && it != "UTC" }
                    val fallbackQuery = tzCity ?: Locale.getDefault().displayCountry
                    if (fallbackQuery.isNotBlank()) {
                        val resolved = fetchCitySuggestions(fallbackQuery).firstOrNull()
                        if (resolved != null) {
                            lat = resolved.lat
                            lon = resolved.lon
                            if (cityName.isEmpty()) cityName = resolved.name
                        }
                    }
                }

                val wUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
                val wJson = JSONObject(fetchUrl(wUrl))
                val cw = wJson.optJSONObject("current") ?: return@execute
                val temp = "${cw.optDouble("temperature_2m", 28.0).toInt()}°C"
                val code = cw.optInt("weather_code", 0)
                val (emoji, desc) = decodeWeatherCode(code)
                val modeTag = if (isManual) "Manual" else "Auto"
                val displayCity = cityName.ifBlank { appCtx.getString(android.R.string.unknownName) }

                settings.prefs.edit()
                    .putString(AppSettings.PREF_CACHED_TEMP, temp)
                    .putString(AppSettings.PREF_CACHED_COND, desc)
                    .putString(AppSettings.PREF_CACHED_CITY, displayCity)
                    .putInt(AppSettings.PREF_CACHED_WEATHER_CODE, code)
                    .apply()

                uiHandler.post {
                    weatherIconTv.text = emoji
                    tempTv.text = temp
                    condTv.text = desc
                    cityTv.text = "📍 $displayCity ($modeTag) ▾"
                }
            } catch (_: Exception) {}
        }
    }

    private fun fetchUrl(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; shree_tvhome)")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun decodeWeatherCode(code: Int): Pair<String, String> {
        val matched = weatherSpecs.find { code in it.range }
        return if (matched != null) matched.emoji to matched.label else "⛅" to "Pleasant Weather"
    }

    private fun updateTimeDisplay() {
        val now = Date()
        digitalTimeTv.text = SimpleDateFormat("hh:mm", Locale.getDefault()).format(now)
        amPmTv.text = SimpleDateFormat("a", Locale.getDefault()).format(now).uppercase(Locale.ROOT)
        dateTv.text = SimpleDateFormat("EEEE • d MMM", Locale.getDefault()).format(now)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        uiHandler.post(clockTicker)
        triggerWeatherRefresh(false)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        uiHandler.removeCallbacks(clockTicker)
        pendingSearchRunnable?.let { uiHandler.removeCallbacks(it) }
        pendingSearchRunnable = null
        activeDialog?.dismiss()
        activeDialog = null
    }

    // Data-driven clock face renderer driven purely by Themes.ClockStyleSpec properties.
    private class DynamicClockFaceView(ctx: Context) : View(ctx) {
        private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
        }
        private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val shapeRect = RectF()
        private var styleSpec: Themes.ClockStyleSpec = Themes.CLOCK_STYLES.first()
        private var accent = Themes.ACCENTS.first().color

        fun updateSpec(newSpec: Themes.ClockStyleSpec, newAccent: Int) {
            styleSpec = newSpec
            accent = newAccent
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = w / 2f
            val cy = h / 2f
            val r = minOf(cx, cy) - LayoutMetrics.dpF(context, 4f)
            if (r <= 0f) return
            val spec = styleSpec

            if (spec.showDialFace) {
                if (spec.showDigitalReadout) {
                    val pad = LayoutMetrics.dpF(context, 6f)
                    shapeRect.set(pad, pad, w - pad, h - pad)
                    dialPaint.shader = LinearGradient(
                        0f, 0f, w, h,
                        intArrayOf(Color.argb(175, 22, 32, 66), Color.argb(210, 10, 16, 36)),
                        null, Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(shapeRect, r * spec.cornerRadiusRatio, r * spec.cornerRadiusRatio, dialPaint)
                } else {
                    dialPaint.shader = RadialGradient(
                        cx, cy * 0.85f, r,
                        intArrayOf(Color.rgb(34, 48, 96), Color.rgb(16, 24, 54), Color.rgb(8, 12, 28)),
                        floatArrayOf(0f, 0.72f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(cx, cy, r, dialPaint)
                }
            }

            if (spec.showBezelRing) {
                if (spec.showDigitalReadout) {
                    val pad = LayoutMetrics.dpF(context, 6f)
                    shapeRect.set(pad, pad, w - pad, h - pad)
                    arcPaint.style = Paint.Style.STROKE
                    arcPaint.strokeWidth = LayoutMetrics.dpF(context, 2.2f)
                    arcPaint.color = accent
                    canvas.drawRoundRect(shapeRect, r * spec.cornerRadiusRatio, r * spec.cornerRadiusRatio, arcPaint)
                } else {
                    bezelPaint.strokeWidth = LayoutMetrics.dpF(context, 3.2f)
                    bezelPaint.shader = Themes.createAccentShader(w, h, accent)
                    canvas.drawCircle(cx, cy, r, bezelPaint)
                }
            }

            if (spec.dashedOuterRing) {
                innerRingPaint.style = Paint.Style.STROKE
                innerRingPaint.strokeWidth = LayoutMetrics.dpF(context, 2f)
                innerRingPaint.color = LayoutMetrics.withAlpha(accent, 140)
                innerRingPaint.pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
                canvas.drawCircle(cx, cy, r * spec.outerRingRatio, innerRingPaint)
                innerRingPaint.pathEffect = null

                innerRingPaint.strokeWidth = LayoutMetrics.dpF(context, 1.2f)
                innerRingPaint.color = Color.argb(65, 255, 255, 255)
                canvas.drawCircle(cx, cy, r * spec.innerRingRatio, innerRingPaint)
            } else if (spec.showDialFace && !spec.showDigitalReadout) {
                innerRingPaint.style = Paint.Style.STROKE
                innerRingPaint.strokeWidth = LayoutMetrics.dpF(context, 1f)
                innerRingPaint.color = Color.argb(75, 255, 255, 255)
                canvas.drawCircle(cx, cy, r * spec.outerRingRatio, innerRingPaint)
            }

            if (spec.showProgressArcs) {
                val cal = Calendar.getInstance()
                val secSweep = (cal.get(Calendar.SECOND) / 60f) * 360f
                val minSweep = (cal.get(Calendar.MINUTE) / 60f) * 360f

                arcPaint.style = Paint.Style.STROKE
                arcPaint.strokeWidth = LayoutMetrics.dpF(context, 5f)
                arcPaint.color = Color.argb(45, 255, 255, 255)
                shapeRect.set(
                    cx - r * spec.outerRingRatio, cy - r * spec.outerRingRatio,
                    cx + r * spec.outerRingRatio, cy + r * spec.outerRingRatio
                )
                canvas.drawArc(shapeRect, 0f, 360f, false, arcPaint)

                arcPaint.color = accent
                canvas.drawArc(shapeRect, -90f, secSweep, false, arcPaint)

                arcPaint.strokeWidth = LayoutMetrics.dpF(context, 3.5f)
                arcPaint.color = Color.rgb(120, 235, 195)
                shapeRect.set(
                    cx - r * spec.innerRingRatio, cy - r * spec.innerRingRatio,
                    cx + r * spec.innerRingRatio, cy + r * spec.innerRingRatio
                )
                canvas.drawArc(shapeRect, -90f, minSweep, false, arcPaint)
            }

            if (spec.showTicks) {
                for (i in 0 until 60) {
                    val ang = Math.toRadians((i * 6 - 90).toDouble())
                    val isHour = i % 5 == 0
                    tickPaint.strokeWidth = LayoutMetrics.dpF(context, if (isHour) 2.2f else 1f)
                    tickPaint.color = if (isHour) accent else Color.argb(90, 210, 230, 255)
                    val inner = r * if (isHour) 0.83f else 0.86f
                    val outer = r * 0.89f
                    canvas.drawLine(
                        (cx + cos(ang) * inner).toFloat(), (cy + sin(ang) * inner).toFloat(),
                        (cx + cos(ang) * outer).toFloat(), (cy + sin(ang) * outer).toFloat(), tickPaint
                    )
                }
            }

            if (spec.showNumerals) {
                for (hrNum in 1..12) {
                    val ang = Math.toRadians((hrNum * 30 - 90).toDouble())
                    val isCardinal = hrNum % 3 == 0
                    numPaint.typeface = Typeface.create("serif", Typeface.BOLD)
                    numPaint.textSize = r * if (isCardinal) 0.19f else 0.145f
                    numPaint.color = if (isCardinal) Color.WHITE else Color.argb(215, 205, 225, 250)
                    val numDist = r * spec.innerRingRatio
                    val nx = (cx + cos(ang) * numDist).toFloat()
                    val ny = (cy + sin(ang) * numDist).toFloat() - ((numPaint.descent() + numPaint.ascent()) / 2f)
                    canvas.drawText(hrNum.toString(), nx, ny, numPaint)
                }
            }

            if (spec.showDigitalReadout) {
                val now = Date()
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                val secStr = SimpleDateFormat(":ss", Locale.getDefault()).format(now)

                numPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                numPaint.textSize = r * 0.36f
                numPaint.color = Color.WHITE
                canvas.drawText(timeStr, cx, cy - r * 0.04f, numPaint)

                numPaint.textSize = r * 0.22f
                numPaint.color = accent
                canvas.drawText(secStr, cx, cy + r * 0.28f, numPaint)
            }

            if (spec.showHands) {
                drawHands(canvas, cx, cy, r * spec.handScaleRatio)
            }
        }

        private fun drawHands(canvas: Canvas, cx: Float, cy: Float, r: Float) {
            val cal = Calendar.getInstance()
            val sec = cal.get(Calendar.SECOND)
            val min = cal.get(Calendar.MINUTE) + sec / 60f
            val hr = (cal.get(Calendar.HOUR) % 12) + min / 60f

            drawHand(canvas, cx, cy, (hr * 30f - 90f), r * 0.45f, 4.4f, Color.WHITE)
            drawHand(canvas, cx, cy, (min * 6f - 90f), r * 0.66f, 2.8f, Color.rgb(185, 230, 255))
            drawHand(canvas, cx, cy, (sec * 6f - 90f), r * 0.76f, 1.5f, accent)

            handPaint.style = Paint.Style.FILL
            handPaint.color = accent
            canvas.drawCircle(cx, cy, LayoutMetrics.dpF(context, 5f), handPaint)
            handPaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, LayoutMetrics.dpF(context, 2f), handPaint)
        }

        private fun drawHand(canvas: Canvas, cx: Float, cy: Float, deg: Float, len: Float, strokeDp: Float, col: Int) {
            val rad = Math.toRadians(deg.toDouble())
            handPaint.style = Paint.Style.STROKE
            handPaint.strokeWidth = LayoutMetrics.dpF(context, strokeDp)
            handPaint.color = col
            canvas.drawLine(cx, cy, (cx + cos(rad) * len).toFloat(), (cy + sin(rad) * len).toFloat(), handPaint)
        }
    }
}
