// Important instructions:
// - Keep top navigation clean with zero favorite badges or hardcoded dimension multipliers.
// - Update live RAM and Wi-Fi indicators cleanly and release receivers/animations on detach.

package shree.tvhome

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
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
import shree.tvhome.core.Themes
import shree.tvhome.design.GreetingCardDesign
import shree.tvhome.design.VectorGlyphDesign
import kotlin.math.roundToInt

class TopHeaderBar(
    context: Context,
    private val settings: AppSettings,
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

    private data class NavActionSpec(
        val kind: String,
        val icon: String,
        val label: String?,
        val tint: Int,
        val tag: String,
        val onClick: () -> Unit
    )

    private lateinit var avatarImageView: ImageView
    private lateinit var avatarInitialTv: TextView
    private lateinit var greetingCard: GreetingCardDesign
    private lateinit var nudgeOverlayBubble: TextView
    private lateinit var ramTv: TextView
    private lateinit var wifiPill: LinearLayout
    private lateinit var wifiIconView: VectorGlyphDesign
    private lateinit var wifiLabelTv: TextView
    private lateinit var wifiBubbleTv: TextView

    private var receiverRegistered = false
    private val handler = Handler(Looper.getMainLooper())
    private var swingAnim: ValueAnimator? = null
    private var breatheAnim: ValueAnimator? = null
    private var wifiStateMode = -1
    private var showConnectingAlt = false
    private var lastShownSsid = ""

    // Refreshes time, RAM, and Wi-Fi status whenever system time or network intents arrive.
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            syncLiveStatus(refreshProfile = false)
        }
    }

    // Cycles status text/bubbles smoothly while Wi-Fi is enabling or disconnected.
    private val cycleRunnable = object : Runnable {
        override fun run() {
            if (!::wifiLabelTv.isInitialized) return
            if (wifiStateMode == 1) {
                showConnectingAlt = !showConnectingAlt
                wifiLabelTv.animate().alpha(0f).setDuration(180L).withEndAction {
                    wifiLabelTv.text = if (showConnectingAlt) "Connecting..." else "WI-FI"
                    wifiLabelTv.animate().alpha(1f).setDuration(220L).start()
                }.start()
                handler.postDelayed(this, 1600L)
            } else if (wifiStateMode == 0) {
                triggerMistBubbleCycle()
                handler.postDelayed(this, 10_000L)
            }
        }
    }

    // Initializes the profile greeting hero, action buttons, and dynamic cutout alignment.
    init {
        clipChildren = false
        clipToPadding = false

        val screenH = context.resources.displayMetrics.heightPixels
        val barHeight = maxOf(dp(36), (screenH * 0.052f).toInt())
        val avatarSize = (barHeight * 2.25f).toInt()

        val rightControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val navItems = listOf(
            NavActionSpec("pill", "mic", "Voice Search", Color.rgb(120, 195, 255), "nav_voice") { callbacks.onVoiceSearch() },
            NavActionSpec("ram", "bolt", "RAM --%", Color.rgb(255, 215, 95), "nav_ram") {
                System.gc()
                updateRamInfo()
                Toast.makeText(context, "Memory refreshed • ${ramTv.text}", Toast.LENGTH_SHORT).show()
            },
            NavActionSpec("wifi", "wifi", "WI-FI", Color.WHITE, "nav_wifi") { callbacks.onWifiClick() },
            NavActionSpec("circle", "bell", null, Color.rgb(255, 210, 95), "nav_alerts") { callbacks.onAlertsClick() },
            NavActionSpec("circle", "sparkle", null, Color.rgb(175, 145, 255), "nav_customize") { callbacks.onCustomizeClick() },
            NavActionSpec("circle", "gear", null, Color.WHITE, "nav_quick") { callbacks.onQuickSettingsClick() },
            NavActionSpec("circle", "power", null, Color.rgb(255, 115, 130), "nav_power") { callbacks.onPowerClick() }
        )

        navItems.forEachIndexed { index, spec ->
            val leftPad = if (index == 0) 0 else dp(8)
            val view = when (spec.kind) {
                "ram" -> buildRamPill(spec)
                "wifi" -> buildWifiWidget(spec, barHeight)
                "circle" -> makeVectorCircleButton(spec.icon, spec.tint, spec.tag, spec.onClick)
                else -> makeVectorPill(spec.icon, spec.label.orEmpty(), spec.tint, spec.tag, spec.onClick)
            }
            val lpSpec = if (spec.kind == "circle") {
                LinearLayout.LayoutParams(barHeight, barHeight)
            } else {
                LinearLayout.LayoutParams(-2, barHeight)
            }.apply { leftMargin = leftPad }
            rightControls.addView(view, lpSpec)
        }

        val profileHeroGroup = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        val avatarRadius = avatarSize / 2f
        val seamGap = LayoutMetrics.dpF(context, 4f)

        greetingCard = GreetingCardDesign(context, settings.userName, settings.greetingPhrase, settings.accentColor).apply {
            setPadding((avatarRadius + seamGap + dp(10)).toInt(), dp(7), dp(16), dp(7))
        }
        profileHeroGroup.addView(
            greetingCard,
            LayoutParams(-2, dp(40), Gravity.START or Gravity.CENTER_VERTICAL).apply {
                leftMargin = avatarRadius.toInt()
            }
        )

        val avatarFrame = FrameLayout(context).apply {
            tag = "nav_profile"
            isFocusable = true
            isClickable = true
            outlineProvider = null
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(settings.accentColor)
                setStroke(dp(2), Color.WHITE)
            }
            setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.08f, 12f)
                v.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(settings.accentColor)
                    setStroke(dp(if (f) 3 else 2), if (f) settings.accentColor else Color.WHITE)
                }
            }
            setOnClickListener { callbacks.onProfileClick() }
        }
        avatarInitialTv = TextView(context).apply {
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        avatarImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        avatarFrame.addView(avatarInitialTv, LayoutParams(-1, -1))
        avatarFrame.addView(avatarImageView, LayoutParams(-1, -1))

        profileHeroGroup.addView(
            avatarFrame,
            LayoutParams(avatarSize, avatarSize, Gravity.START or Gravity.CENTER_VERTICAL)
        )

        profileHeroGroup.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val relCenterX = (avatarFrame.left + avatarFrame.width / 2f) - greetingCard.left
            val relCenterY = (avatarFrame.top + avatarFrame.height / 2f) - greetingCard.top
            val cutoutR = (avatarFrame.width / 2f) + seamGap
            greetingCard.setAvatarCutout(relCenterX, relCenterY, cutoutR)
        }

        nudgeOverlayBubble = TextView(context).apply {
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.argb(250, 255, 246, 225))
            setPadding(dp(12), dp(4), dp(14), dp(4))
            isSingleLine = true
            elevation = LayoutMetrics.dpF(context, 10f)
            visibility = View.GONE
        }
        greetingCard.attachOverlayBubble(nudgeOverlayBubble)

        addView(rightControls, LayoutParams(-2, barHeight, Gravity.END or Gravity.CENTER_VERTICAL))
        addView(profileHeroGroup, LayoutParams(-2, barHeight, Gravity.START or Gravity.CENTER_VERTICAL))

        post {
            val availableForLeft = width - rightControls.width - dp(16)
            if (availableForLeft > avatarSize + dp(40)) {
                greetingCard.layoutParams.width = minOf(greetingCard.measuredWidth, availableForLeft - dp(20))
                greetingCard.requestLayout()
            }
        }

        syncLiveStatus(refreshProfile = true)
        settings.addStyleListener { greetingCard.setAccentColor(settings.accentColor) }
    }

    // Builds the RAM usage pill in the top navigation bar.
    private fun buildRamPill(spec: NavActionSpec): View {
        ramTv = makeText(spec.label.orEmpty(), 12.5f, Color.WHITE, true)
        return LinearLayout(context).apply {
            tag = spec.tag
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(13), 0, dp(13), 0)
            isFocusable = true
            isClickable = true
            addView(VectorGlyphDesign(context, spec.icon, spec.tint), LinearLayout.LayoutParams(dp(15), dp(15)).apply { rightMargin = dp(6) })
            addView(ramTv)
            background = pillBg(false, settings.accentColor)
            setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.06f, 6f)
                v.background = pillBg(f, settings.accentColor)
            }
            setOnClickListener { spec.onClick() }
        }
    }

    // Builds the Wi-Fi indicator pill and status bubble below it.
    private fun buildWifiWidget(spec: NavActionSpec, barHeight: Int): View {
        val wifiWrapper = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        wifiIconView = VectorGlyphDesign(context, spec.icon, spec.tint)
        wifiLabelTv = makeText(spec.label.orEmpty(), 13f, Color.WHITE, true)
        wifiPill = LinearLayout(context).apply {
            tag = spec.tag
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            isFocusable = true
            isClickable = true
            addView(wifiIconView, LinearLayout.LayoutParams(dp(16), dp(16)).apply { rightMargin = dp(6) })
            addView(wifiLabelTv)
            background = pillBg(false, settings.accentColor)
            setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.07f, 8f)
                if (wifiStateMode == 2) {
                    val green = Color.rgb(46, 232, 138)
                    v.background = GradientDrawable().apply {
                        setColor(if (f) Color.argb(125, 34, 130, 86) else Color.argb(75, 24, 98, 64))
                        setStroke(dp(if (f) 2 else 1), if (f) Color.WHITE else green)
                        cornerRadius = LayoutMetrics.dpF(context, 22f)
                    }
                } else {
                    v.background = pillBg(f, settings.accentColor)
                }
            }
            setOnClickListener { spec.onClick() }
        }

        wifiWrapper.addView(wifiPill, LayoutParams(-2, barHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        wifiBubbleTv = TextView(context).apply {
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(9), dp(2), dp(9), dp(2))
            maxWidth = dp(135)
            ellipsize = TextUtils.TruncateAt.END
            isSingleLine = true
            visibility = View.GONE
        }
        wifiWrapper.addView(
            wifiBubbleTv,
            LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(41) }
        )
        return wifiWrapper
    }

    // Exposes the floating nudge text view for placement on the root layout.
    fun createFloatingNudgeView(): TextView = nudgeOverlayBubble

    // Refreshes profile avatar bitmap or initial fallback from saved preferences.
    fun refreshProfileAvatar() {
        val cleanName = settings.userName.ifBlank { "TV" }
        val initial = cleanName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "T"
        avatarInitialTv.text = initial

        val picUriStr = settings.profilePicUri
        if (!picUriStr.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(picUriStr)
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val rawBmp = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                if (rawBmp != null) {
                    avatarImageView.setImageBitmap(makeCircularBitmap(rawBmp))
                    avatarImageView.scaleX = 1.0f
                    avatarImageView.scaleY = 1.0f
                    avatarImageView.visibility = View.VISIBLE
                    avatarInitialTv.visibility = View.GONE
                    return
                }
            } catch (_: Exception) {}
        }
        avatarImageView.visibility = View.GONE
        avatarInitialTv.visibility = View.VISIBLE
    }

    // Crops and transforms the selected profile image into a circular avatar bitmap.
    private fun makeCircularBitmap(src: Bitmap): Bitmap {
        val outSize = 240
        val output = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val cx = outSize / 2f
        val cy = outSize / 2f
        val r = outSize / 2f

        val clipPath = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        canvas.clipPath(clipPath)

        val zoom = settings.profilePicZoom
        val panX = settings.profilePicPanX
        val panY = settings.profilePicPanY

        val baseScale = maxOf(outSize.toFloat() / src.width, outSize.toFloat() / src.height)
        val finalScale = baseScale * zoom

        val matrix = Matrix().apply {
            postTranslate(-src.width / 2f, -src.height / 2f)
            postScale(finalScale, finalScale)
            postTranslate(cx + (panX * r), cy + (panY * r))
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, matrix, paint)
        return output
    }

    // Synchronizes profile avatar, time-based greeting, RAM percentage, and Wi-Fi state.
    private fun syncLiveStatus(refreshProfile: Boolean) {
        if (refreshProfile) refreshProfileAvatar()
        refreshGreeting()
        updateRamInfo()
        updateWifiVisualState()
    }

    // Reads system memory info and updates the RAM usage label.
    private fun updateRamInfo() {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val total = memInfo.totalMem.coerceAtLeast(1L)
            val used = (total - memInfo.availMem).coerceAtLeast(0L)
            val pct = ((used.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(1, 99)
            ramTv.text = "RAM $pct%"
        } catch (_: Exception) {
            ramTv.text = "RAM"
        }
    }

    // Updates Wi-Fi pill color, SSID bubble, and subtle status animations when Wi-Fi state changes.
    @Suppress("DEPRECATION")
    private fun updateWifiVisualState() {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val connectedSsid = Themes.getConnectedSsid(context).takeIf { it != "Wi-Fi" }
        val isWifiEnabling = try { wm?.wifiState == WifiManager.WIFI_STATE_ENABLING } catch (_: Exception) { false }

        val newMode = when {
            connectedSsid != null -> 2
            isWifiEnabling -> 1
            else -> 0
        }

        if (newMode == wifiStateMode && (newMode != 2 || connectedSsid == lastShownSsid)) return
        wifiStateMode = newMode
        lastShownSsid = connectedSsid ?: ""

        handler.removeCallbacks(cycleRunnable)
        swingAnim?.cancel()
        breatheAnim?.cancel()
        wifiPill.scaleX = 1f
        wifiPill.scaleY = 1f
        wifiPill.alpha = 1f

        when (newMode) {
            2 -> {
                val green = Color.rgb(46, 232, 138)
                wifiIconView.setIcon("wifi", green)
                wifiLabelTv.text = "WI-FI"
                wifiPill.background = GradientDrawable().apply {
                    setColor(Color.argb(75, 24, 98, 64))
                    setStroke(dp(2), green)
                    cornerRadius = LayoutMetrics.dpF(context, 22f)
                }
                wifiBubbleTv.text = " $connectedSsid"
                wifiBubbleTv.alpha = 1f
                wifiBubbleTv.translationY = 0f
                wifiBubbleTv.scaleX = 1f
                wifiBubbleTv.scaleY = 1f
                wifiBubbleTv.background = GradientDrawable().apply {
                    setColor(Color.argb(205, 18, 72, 48))
                    setStroke(dp(1), green)
                    cornerRadius = LayoutMetrics.dpF(context, 12f)
                }
                wifiBubbleTv.visibility = View.VISIBLE
                startPendulumSwing()
            }
            1 -> {
                wifiIconView.setIcon("wifi", Color.rgb(92, 184, 255))
                wifiBubbleTv.visibility = View.GONE
                wifiPill.background = pillBg(true, Color.rgb(92, 184, 255))
                breatheAnim = ValueAnimator.ofFloat(0.96f, 1.04f).apply {
                    duration = 950L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener {
                        val s = it.animatedValue as Float
                        wifiPill.scaleX = s
                        wifiPill.scaleY = s
                    }
                    start()
                }
                handler.post(cycleRunnable)
            }
            else -> {
                wifiIconView.setIcon("wifi", Color.WHITE)
                wifiLabelTv.text = "WI-FI"
                wifiPill.background = pillBg(false, settings.accentColor)
                wifiBubbleTv.rotation = 0f
                handler.post(cycleRunnable)
            }
        }
    }

    // Runs a gentle pendulum swing on the connected SSID indicator badge.
    private fun startPendulumSwing() {
        wifiBubbleTv.post {
            wifiBubbleTv.pivotX = wifiBubbleTv.width / 2f
            wifiBubbleTv.pivotY = 0f
            swingAnim = ValueAnimator.ofFloat(-4f, 4f).apply {
                duration = 2100L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { wifiBubbleTv.rotation = it.animatedValue as Float }
                start()
            }
        }
    }

    // Briefly shows a subtle disconnected indicator bubble when offline.
    private fun triggerMistBubbleCycle() {
        wifiBubbleTv.animate().cancel()
        wifiBubbleTv.text = "○ No Internet"
        wifiBubbleTv.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(165, 95, 145, 210), Color.argb(120, 55, 90, 150))
        ).apply {
            setStroke(dp(1), Color.argb(160, 185, 225, 255))
            cornerRadius = LayoutMetrics.dpF(context, 12f)
        }

        wifiBubbleTv.visibility = View.VISIBLE
        wifiBubbleTv.alpha = 0f
        wifiBubbleTv.translationY = -dp(10).toFloat()
        wifiBubbleTv.scaleX = 0.88f
        wifiBubbleTv.scaleY = 0.88f
        wifiBubbleTv.animate()
            .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(420L)
            .setInterpolator(LayoutMetrics.FLUID_SPRING)
            .start()

        handler.postDelayed({
            if (wifiStateMode == 0 && wifiBubbleTv.visibility == View.VISIBLE) {
                wifiBubbleTv.animate()
                    .alpha(0f)
                    .translationY(-dp(12).toFloat())
                    .scaleX(1.16f)
                    .scaleY(0.80f)
                    .setDuration(650L)
                    .setInterpolator(LayoutMetrics.SILKY_EASE)
                    .withEndAction { wifiBubbleTv.visibility = View.GONE }
                    .start()
            }
        }, 4200L)
    }

    // Updates greeting text and accent color on the greeting chip.
    fun refreshGreeting() {
        greetingCard.updateGreeting(settings.userName, settings.greetingPhrase)
        greetingCard.setAccentColor(settings.accentColor)
    }

    // Registers time and Wi-Fi receivers when the bar attaches to a window.
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(statusReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(statusReceiver, filter)
                }
                receiverRegistered = true
            } catch (_: Exception) {}
            syncLiveStatus(refreshProfile = true)
        }
    }

    // Cancels pending handlers/animations and unregisters receivers when detached.
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
        swingAnim?.cancel()
        breatheAnim?.cancel()
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(statusReceiver)
            } catch (_: Exception) {}
            receiverRegistered = false
        }
    }

    // Creates a labeled vector-icon pill button for top navigation actions.
    private fun makeVectorPill(icon: String, label: String, iconTint: Int, tagStr: String, onClick: () -> Unit) = LinearLayout(context).apply {
        tag = tagStr
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(15), 0)
        isFocusable = true
        isClickable = true
        addView(VectorGlyphDesign(context, icon, iconTint), LinearLayout.LayoutParams(dp(16), dp(16)).apply { rightMargin = dp(7) })
        addView(makeText(label, 13f, Color.WHITE, true))
        background = pillBg(false, settings.accentColor)
        setOnFocusChangeListener { v, f ->
            AnimationController.animateFocus(v, f, settings.motionStyle, 1.07f, 8f)
            v.background = pillBg(f, settings.accentColor)
        }
        setOnClickListener { onClick() }
    }

    // Creates a circular icon button with focus micro-rotation for alerts, customize, quick, and power.
    private fun makeVectorCircleButton(icon: String, tint: Int, tagStr: String, onClick: () -> Unit) = FrameLayout(context).apply {
        tag = tagStr
        isFocusable = true
        isClickable = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(62, 22, 32, 68))
            setStroke(dp(1), Color.argb(105, 255, 255, 255))
        }
        val iv = VectorGlyphDesign(context, icon, tint)
        addView(iv, LayoutParams(dp(18), dp(18), Gravity.CENTER))
        setOnClickListener { onClick() }
        setOnFocusChangeListener { v, f ->
            AnimationController.animateFocus(v, f, settings.motionStyle, 1.15f, 8f)
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (f) Color.argb(120, 255, 255, 255) else Color.argb(62, 22, 32, 68))
                setStroke(dp(if (f) 2 else 1), if (f) settings.accentColor else Color.argb(105, 255, 255, 255))
            }
            when (icon) {
                "gear" -> iv.animate().rotation(if (f) 60f else 0f).setDuration(240).start()
                "bell" -> iv.animate().rotation(if (f) 18f else 0f).setDuration(200).start()
                "sparkle" -> iv.animate().rotation(if (f) 45f else 0f).setDuration(240).start()
            }
        }
    }

    // Builds pill background styling for unfocused and focused states.
    private fun pillBg(focused: Boolean, accent: Int) = GradientDrawable().apply {
        setColor(if (focused) Color.argb(110, 255, 255, 255) else Color.argb(54, 22, 32, 68))
        setStroke(dp(if (focused) 2 else 1), if (focused) accent else Color.argb(95, 255, 255, 255))
        cornerRadius = LayoutMetrics.dpF(context, 22f)
    }

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(value: Int): Int = LayoutMetrics.dp(context, value)
}
