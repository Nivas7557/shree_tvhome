package shree.tvhome.source

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import shree.tvhome.core.GlassSurfaceSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TopNavBar(
    context: Context,
    private val lp: LauncherPreferences,
    private val callbacks: NavCallbacks
) : FrameLayout(context) {

    interface NavCallbacks {
        fun onProfileClick()
        fun onVoiceSearch()
        fun onWifiClick()
        fun onAlertsClick()
        fun onCustomizeClick()
        fun onQuickSettingsClick()
        fun onPowerClick()
    }

    private data class NavPillSpec(
        val iconKind: String,
        val label: String?,
        val tint: Int,
        val tag: String,
        val onClick: () -> Unit
    )

    private lateinit var greetingCard: GreetingShineCard
    private lateinit var nudgeOverlayBubble: TextView
    private lateinit var ramTv: TextView
    private lateinit var wifiPill: LinearLayout
    private lateinit var wifiIconView: VectorIconView
    private lateinit var wifiLabelTv: TextView
    private lateinit var wifiBubbleTv: TextView

    private var receiverRegistered = false
    private val handler = Handler(Looper.getMainLooper())
    private var swingAnim: ValueAnimator? = null
    private var breatheAnim: ValueAnimator? = null
    private var wifiStateMode = -1
    private var showConnectingAlt = false
    private var lastShownSsid = ""

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            syncLiveStatus(refreshProfile = false)
        }
    }

    private val clockTicker = object : Runnable {
        override fun run() {
            refreshGreeting()
            handler.postDelayed(this, 15_000L)
        }
    }

    private val wifiPulseTicker = object : Runnable {
        override fun run() {
            if (wifiStateMode == 1) {
                showConnectingAlt = !showConnectingAlt
                applyWifiVisuals(connected = false, ssid = lastShownSsid)
                handler.postDelayed(this, 650L)
            }
        }
    }

    private val navPills = ArrayList<Pair<View, Int>>()

    init {
        clipChildren = false
        clipToPadding = false
        setPadding(dp(18), dp(12), dp(18), dp(8))
        build()
        syncLiveStatus(refreshProfile = true)
    }

    private fun build() {
        val barRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        greetingCard = GreetingShineCard(
            context = context,
            initialName = lp.userName,
            initialPhrase = lp.greetingPhrase,
            accentColor = lp.accentColor,
            avatarSizePx = dp(32)
        ).apply {
            isFocusable = true
            isClickable = true
            setOnClickListener { callbacks.onProfileClick() }
            setOnFocusChangeListener { v, hasFocus ->
                MotionEngine.animateFocus(v, hasFocus, lp.motionStyle, 1.03f, 2f)
            }
        }

        barRow.addView(
          greetingCard,
          LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT,
              dp(42),
              0f
          ).apply {
              gravity = Gravity.START or Gravity.CENTER_VERTICAL
          }
      )

        val rightControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val specs = listOf(
            NavPillSpec("mic", null, Color.WHITE, "nav_voice") { callbacks.onVoiceSearch() },
            NavPillSpec("wifi", "Wi-Fi", Color.WHITE, "nav_wifi") { callbacks.onWifiClick() },
            NavPillSpec("bell", null, Color.WHITE, "nav_alerts") { callbacks.onAlertsClick() },
            NavPillSpec("palette", null, Color.WHITE, "nav_customize") { callbacks.onCustomizeClick() },
            NavPillSpec("sliders", null, Color.WHITE, "nav_quick") { callbacks.onQuickSettingsClick() },
            NavPillSpec("power", null, Color.argb(255, 255, 110, 110), "nav_power") { callbacks.onPowerClick() }
        )

        specs.forEachIndexed { idx, spec ->
            val pill = buildNavPill(spec)
            if (spec.tag == "nav_wifi") {
                wifiPill = pill
            }
            rightControls.addView(
                pill,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(38)
                ).apply {
                    if (idx < specs.lastIndex) rightMargin = dp(8)
                }
            )
        }

        barRow.addView(
            rightControls,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(12)
            }
        )

        addView(barRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        nudgeOverlayBubble = TextView(context).apply {
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            visibility = GONE
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 20, 32, 66))
                cornerRadius = LauncherUtils.dpF(context, 12f)
                setStroke(dp(1), Color.argb(140, 255, 255, 255))
            }
        }
        addView(
            nudgeOverlayBubble,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(44)
                rightMargin = dp(8)
            }
        )

        ramTv = TextView(context).apply {
            visibility = GONE
        }
        addView(ramTv)
    }

    fun createFloatingNudgeView(): TextView = TextView(context).apply {
        textSize = 9.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        visibility = GONE
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = GradientDrawable().apply {
            setColor(Color.argb(225, 20, 32, 66))
            cornerRadius = LauncherUtils.dpF(context, 12f)
            setStroke(dp(1), Color.argb(140, 255, 255, 255))
        }
    }

    private fun buildNavPill(spec: NavPillSpec): LinearLayout {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            tag = spec.tag
            isFocusable = true
            isClickable = true
            val hPad = if (spec.label != null) dp(12) else dp(10)
            setPadding(hPad, dp(6), hPad, dp(6))
            background = pillBg(focused = false, accent = lp.accentColor)
            setOnClickListener { spec.onClick() }
        }

        val iconView = VectorIconView(context, spec.iconKind, spec.tint).apply {
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
        }
        pill.addView(iconView)

        if (spec.tag == "nav_wifi") {
            wifiIconView = iconView
            wifiLabelTv = TextView(context).apply {
                text = spec.label ?: "Wi-Fi"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { leftMargin = dp(6) }
            }
            pill.addView(wifiLabelTv)

            wifiBubbleTv = TextView(context).apply {
                visibility = GONE
            }
            pill.addView(wifiBubbleTv)
        } else if (spec.label != null) {
            pill.addView(TextView(context).apply {
                text = spec.label
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { leftMargin = dp(6) }
            })
        }

        pill.setOnFocusChangeListener { v, hasFocus ->
            MotionEngine.animateFocus(v, hasFocus, lp.motionStyle, 1.05f, 2f)
            v.background = pillBg(focused = hasFocus, accent = lp.accentColor)
        }

        navPills.add(pill to spec.tint)
        return pill
    }

    fun refreshProfileAvatar() {
        val uriStr = lp.prefs.getString("profile_pic_uri", null)
        if (uriStr.isNullOrBlank()) {
            greetingCard.setAvatarDrawable(null)
            return
        }
        try {
            val uri = Uri.parse(uriStr)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                if (bmp != null) {
                    greetingCard.setAvatarDrawable(BitmapDrawable(context.resources, bmp))
                } else {
                    greetingCard.setAvatarDrawable(null)
                }
            }
        } catch (_: Exception) {
            greetingCard.setAvatarDrawable(null)
        }
    }

    fun refreshGreeting() {
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val nowTime = timeFmt.format(Date())
        val basePhrase = lp.greetingPhrase.ifBlank { "Welcome" }
        greetingCard.updateGreeting(lp.userName, "$basePhrase • $nowTime")
    }

    fun refreshProfile(pref: LauncherPreferences = lp) {
        greetingCard.updateGreeting(pref.userName, pref.greetingPhrase)
        refreshProfileAvatar()
    }

    fun setAccent(accent: Int) {
        greetingCard.setAccentColor(accent)
        refreshNavPills(accent)
    }

    private fun refreshNavPills(accent: Int) {
        navPills.forEach { (view, _) ->
            view.background = pillBg(focused = view.hasFocus(), accent = accent)
        }
    }

    fun setAlertCount(count: Int) {
        val alertPill = navPills.find { (it.first as? View)?.tag == "nav_alerts" }?.first as? LinearLayout ?: return
        val existingBadge = alertPill.findViewWithTag<TextView>("alert_badge")
        if (count <= 0) {
            existingBadge?.let { alertPill.removeView(it) }
            return
        }
        val badge = existingBadge ?: TextView(context).apply {
            tag = "alert_badge"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(1), dp(5), dp(1))
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 225, 65, 65))
                cornerRadius = LauncherUtils.dpF(context, 9f)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(5) }
            alertPill.addView(this)
        }
        badge.text = if (count > 99) "99+" else count.toString()
    }

    fun showNightNudge(message: String) {
        if (message.isBlank()) {
            nudgeOverlayBubble.visibility = GONE
            return
        }
        nudgeOverlayBubble.text = message
        nudgeOverlayBubble.visibility = VISIBLE
    }

    fun syncLiveStatus(refreshProfile: Boolean = false) {
        if (refreshProfile) {
            refreshProfile(lp)
        }
        refreshGreeting()
        updateWifiStatus()
        startAmbientMotion()
    }

    private fun updateWifiStatus() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
        val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (isConnected) {
            wifiStateMode = 2
            handler.removeCallbacks(wifiPulseTicker)
            val ssid = lp.prefs.getString("last_wifi_ssid", null).orEmpty().ifBlank { "Connected" }
            lastShownSsid = ssid
            applyWifiVisuals(connected = true, ssid = ssid)
        } else {
            wifiStateMode = 0
            handler.removeCallbacks(wifiPulseTicker)
            applyWifiVisuals(connected = false, ssid = "Wi-Fi")
        }
    }

    private fun applyWifiVisuals(connected: Boolean, ssid: String) {
        if (!::wifiLabelTv.isInitialized) return
        wifiLabelTv.text = when {
            connected -> ssid
            wifiStateMode == 1 && showConnectingAlt -> "Connecting..."
            else -> "Wi-Fi"
        }
        if (::wifiIconView.isInitialized) {
            wifiIconView.setTint(if (connected) lp.accentColor else Color.argb(190, 225, 235, 250))
        }
    }

    private fun startAmbientMotion() {
        if (swingAnim == null) {
            swingAnim = ValueAnimator.ofFloat(-2f, 2f).apply {
                duration = 2800L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val angle = it.animatedValue as Float
                    navPills.find { p -> (p.first as? View)?.tag == "nav_alerts" }?.first?.rotation = angle
                }
                start()
            }
        }
        if (breatheAnim == null) {
            breatheAnim = ValueAnimator.ofFloat(0.98f, 1.02f).apply {
                duration = 2400L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val sc = it.animatedValue as Float
                    if (::wifiPill.isInitialized && !wifiPill.hasFocus()) {
                        wifiPill.scaleX = sc
                        wifiPill.scaleY = sc
                    }
                }
                start()
            }
        }
    }

    private fun pillBg(focused: Boolean, accent: Int) = GlassSurfaceSpec.createDrawable(
        ctx = context,
        style = GlassSurfaceSpec.PILL_NAV,
        accentColor = accent,
        focused = focused,
        active = false,
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
    )

    private fun dp(v: Int): Int = LauncherUtils.dp(context, v)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                addAction(Intent.ACTION_TIME_TICK)
            }
            try {
                context.registerReceiver(statusReceiver, filter)
                receiverRegistered = true
            } catch (_: Exception) {
            }
        }
        handler.removeCallbacks(clockTicker)
        handler.post(clockTicker)
        syncLiveStatus(refreshProfile = true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(clockTicker)
        handler.removeCallbacks(wifiPulseTicker)
        swingAnim?.cancel()
        swingAnim = null
        breatheAnim?.cancel()
        breatheAnim = null
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(statusReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
    }

    fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
