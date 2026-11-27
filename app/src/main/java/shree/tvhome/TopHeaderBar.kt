// Important instructions:
// - Keep top navigation free of favorite badges, hardcoded dimensions, or forced focus calls.
// - Release receivers, handlers, and animators cleanly on detach to keep RAM usage minimal.

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

    // Refreshes RAM and Wi-Fi indicators on system time or Wi-Fi state broadcasts.
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            syncLiveStatus(refreshProfile = false)
        }
    }

    // Cycles status text or disconnected bubble smoothly while Wi-Fi is enabling or offline.
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

    // Builds the profile/greeting hero group and right-side navigation controls using dynamic sizing.
    init {
        clipChildren = false
        clipToPadding = false

        val barHeight = LayoutMetrics.dynamicSpace(context, ratio = 0.054f, minDp = 34, maxDp = 48, horizontal = false)
        val avatarSize = (barHeight * 2.25f).roundToInt()
        val navGap = LayoutMetrics.dynamicSpace(context, ratio = 0.008f, minDp = 5, maxDp = 12, horizontal = true)
        val seamGap = LayoutMetrics.dpF(context, 4f)
        val avatarRadius = avatarSize / 2f

        val rightControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val navItems = listOf(
            NavActionSpec("pill", "mic", "Voice Search", Color.rgb(120, 195, 255), "nav_voice") { callbacks.onVoiceSearch() },
            NavActionSpec("ram", "bolt", "RAM", Color.rgb(255, 215, 95), "nav_ram") {
                System.gc()
                updateRamInfo()
                Toast.makeText(context, ramTv.text, Toast.LENGTH_SHORT).show()
            },
            NavActionSpec("wifi", "wifi", "WI-FI", Color.WHITE, "nav_wifi") { callbacks.onWifiClick() },
            NavActionSpec("circle", "bell", null, Color.rgb(255, 210, 95), "nav_alerts") { callbacks.onAlertsClick() },
            NavActionSpec("circle", "sparkle", null, Color.rgb(175, 145, 255), "nav_customize") { callbacks.onCustomizeClick() },
            NavActionSpec("circle", "gear", null, Color.WHITE, "nav_quick") { callbacks.onQuickSettingsClick() },
            NavActionSpec("circle", "power", null, Color.rgb(255, 115, 130), "nav_power") { callbacks.onPowerClick() }
        )

        navItems.forEachIndexed { index, spec ->
            val leftPad = if (index == 0) 0 else navGap
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

        val greetingPadStart = (avatarRadius + seamGap + LayoutMetrics.dpF(context, 10f)).roundToInt()
        val greetingPadEnd = LayoutMetrics.dynamicSpace(context, ratio = 0.014f, minDp = 12, maxDp = 22, horizontal = true)
        val greetingPadV = LayoutMetrics.dynamicSpace(context, ratio = 0.01f, minDp = 5, maxDp = 10, horizontal = false)
        val greetingH = LayoutMetrics.dynamicSpace(context, ratio = 0.056f, minDp = 36, maxDp = 48, horizontal = false)

        greetingCard = GreetingCardDesign(context, settings.userName, settings.greetingPhrase, settings.accentColor).apply {
            setPadding(greetingPadStart, greetingPadV, greetingPadEnd, greetingPadV)
        }
        profileHeroGroup.addView(
            greetingCard,
            LayoutParams(-2, greetingH, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                leftMargin = avatarRadius.roundToInt()
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
                setStroke(LayoutMetrics.dp(context, 2), Color.WHITE)
            }
            setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.08f, 12f)
                v.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(settings.accentColor)
                    setStroke(LayoutMetrics.dp(context, if (f) 3 else 2), if (f) settings.accentColor else Color.WHITE)
                }
            }
            setOnClickListener { callbacks.onProfileClick() }
        }
        avatarInitialTv = TextView(context).apply {
            textSize = 28f
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
            setPadding(greetingPadEnd, greetingPadV / 2, greetingPadEnd, greetingPadV / 2)
            isSingleLine = true
            elevation = LayoutMetrics.dpF(context, 10f)
            visibility = View.GONE
        }
        greetingCard.attachOverlayBubble(nudgeOverlayBubble)

        addView(rightControls, LayoutParams(-2, barHeight, Gravity.END or Gravity.CENTER_VERTICAL))
        // Allow profileHeroGroup to wrap the full circular avatar height cleanly without vertical clipping.
        addView(profileHeroGroup, LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))


        post {
            val minGap = LayoutMetrics.dynamicSpace(context, ratio = 0.015f, minDp = 12, maxDp = 24, horizontal = true)
            val availableForLeft = width - rightControls.width - minGap
            if (availableForLeft > avatarSize + minGap * 2) {
                greetingCard.layoutParams.width = minOf(greetingCard.measuredWidth, availableForLeft - minGap)
                greetingCard.requestLayout()
            }
        }

        syncLiveStatus(refreshProfile = true)
        settings.addStyleListener { greetingCard.setAccentColor(settings.accentColor) }
    }

    private fun buildRamPill(spec: NavActionSpec): View {
        ramTv = makeText(spec.label.orEmpty(), 12.5f, Color.WHITE, true)
        val padH = LayoutMetrics.dynamicSpace(context, ratio = 0.012f, minDp = 10, maxDp = 16, horizontal = true)
        val iconSize = LayoutMetrics.dynamicSpace(context, ratio = 0.022f, minDp = 13, maxDp = 18, horizontal = false)
        return LinearLayout(context).apply {
            tag = spec.tag
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(padH, 0, padH, 0)
            isFocusable = true
            isClickable = true
            addView(VectorGlyphDesign(context, spec.icon, spec.tint), LinearLayout.LayoutParams(iconSize, iconSize).apply {
                rightMargin = padH / 2
            })
            addView(ramTv)
            background = pillBg(false, settings.accentColor)
            setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.06f, 6f)
                v.background = pillBg(f, settings.accentColor)
            }
            setOnClickListener { spec.onClick() }
        }
    }

    private fun buildWifiWidget(spec: NavActionSpec, barHeight: Int): View {
        val wifiWrapper = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        val padH = LayoutMetrics.dynamicSpace(context, ratio = 0.012f, minDp = 10, maxDp = 18, horizontal = true)
        val iconSize = LayoutMetrics.dynamicSpace(context, ratio = 0.024f, minDp = 14, maxDp = 19, horizontal = false)
        wifiIconView = VectorGlyphDesign(context, spec.icon, spec.tint)
        wifiLabelTv = makeText(spec.label.orEmpty(), 13f, Color.WHITE, true)
        wifiPill = LinearLayout(context).apply {
            tag = spec.tag
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(padH, 0, padH, 0)
            isFocusable = true
            isClickable = true
            addView(wifiIconView, LinearLayout.LayoutParams(iconSize, iconSize).apply {
                rightMargin = padH / 2
            })
            addView(wifiLabelTv)
            background = pillBg(false, settings.accentColor)
            setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.07f, 8f)
                if (wifiStateMode == 2) {
                    val green = Color.rgb(46, 232, 138)
                    v.background = GradientDrawable().apply {
                        setColor(if (f) Color.argb(125, 34, 130, 86) else Color.argb(75, 24, 98, 64))
                        setStroke(LayoutMetrics.dp(context, if (f) 2 else 1), if (f) Color.WHITE else green)
                        cornerRadius = LayoutMetrics.dpF(context, 22f)
                    }
                } else {
                    v.background = pillBg(f, settings.accentColor)
                }
            }
            setOnClickListener { spec.onClick() }
        }

        wifiWrapper.addView(wifiPill, LayoutParams(-2, barHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        val bubbleTop = barHeight + LayoutMetrics.dynamicSpace(context, ratio = 0.008f, minDp = 4, maxDp = 8, horizontal = false)
        val bubblePadH = LayoutMetrics.dynamicSpace(context, ratio = 0.008f, minDp = 7, maxDp = 12, horizontal = true)
        val bubblePadV = LayoutMetrics.dynamicSpace(context, ratio = 0.004f, minDp = 2, maxDp = 5, horizontal = false)
        val bubbleMaxW = LayoutMetrics.dynamicSpace(context, ratio = 0.13f, minDp = 110, maxDp = 175, horizontal = true)

        wifiBubbleTv = TextView(context).apply {
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(bubblePadH, bubblePadV, bubblePadH, bubblePadV)
            maxWidth = bubbleMaxW
            ellipsize = TextUtils.TruncateAt.END
            isSingleLine = true
            visibility = View.GONE
        }
        wifiWrapper.addView(
            wifiBubbleTv,
            LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = bubbleTop }
        )
        return wifiWrapper
    }

    fun createFloatingNudgeView(): TextView = nudgeOverlayBubble

    // Refreshes profile avatar bitmap or initial fallback safely.
    fun refreshProfileAvatar() {
        // Resolve initial dynamically from AppSettings device/profile name without hardcoded letter fallbacks.
        val cleanName = settings.userName.trim()
        val initial = cleanName.firstOrNull()?.uppercaseChar()?.toString() ?: "★"
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
                    val circ = makeCircularBitmap(rawBmp)
                    if (!rawBmp.isRecycled) rawBmp.recycle()
                    avatarImageView.setImageBitmap(circ)
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

    private fun syncLiveStatus(refreshProfile: Boolean) {
        if (refreshProfile) refreshProfileAvatar()
        refreshGreeting()
        updateRamInfo()
        updateWifiVisualState()
    }

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
                    setStroke(LayoutMetrics.dp(context, 2), green)
                    cornerRadius = LayoutMetrics.dpF(context, 22f)
                }
                wifiBubbleTv.text = " $connectedSsid"
                wifiBubbleTv.alpha = 1f
                wifiBubbleTv.translationY = 0f
                wifiBubbleTv.scaleX = 1f
                wifiBubbleTv.scaleY = 1f
                wifiBubbleTv.background = GradientDrawable().apply {
                    setColor(Color.argb(205, 18, 72, 48))
                    setStroke(LayoutMetrics.dp(context, 1), green)
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

    private fun triggerMistBubbleCycle() {
        wifiBubbleTv.animate().cancel()
        wifiBubbleTv.text = "○"
        wifiBubbleTv.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(165, 95, 145, 210), Color.argb(120, 55, 90, 150))
        ).apply {
            setStroke(LayoutMetrics.dp(context, 1), Color.argb(160, 185, 225, 255))
            cornerRadius = LayoutMetrics.dpF(context, 12f)
        }

        val dropY = LayoutMetrics.dynamicSpace(context, ratio = 0.015f, minDp = 8, maxDp = 14, horizontal = false).toFloat()
        wifiBubbleTv.visibility = View.VISIBLE
        wifiBubbleTv.alpha = 0f
        wifiBubbleTv.translationY = -dropY
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
                    .translationY(-dropY)
                    .scaleX(1.16f)
                    .scaleY(0.80f)
                    .setDuration(650L)
                    .setInterpolator(LayoutMetrics.SILKY_EASE)
                    .withEndAction { wifiBubbleTv.visibility = View.GONE }
                    .start()
            }
        }, 4200L)
    }

    // Refreshes greetingCard with Greeting Studio style & font family while keeping original avatar cutout & air-fog.
    fun refreshGreeting() {
        val fontSpec = Themes.GREETING_FONTS.find { it.key == settings.greetingFont }
            ?: Themes.GREETING_FONTS.first()
        greetingCard.updateGreeting(
            name = settings.userName,
            phrase = settings.greetingPhrase,
            styleKey = settings.greetingStyle,
            fontFamily = fontSpec.family
        )
    }

        val effectiveGreeting = when (settings.greetingStyle) {
            "minimal" -> celestialEmoji
            "welcome" -> "$celestialEmoji Welcome Back"
            "custom" -> "$celestialEmoji ${settings.greetingPhrase.ifBlank { "Hello" }}"
            else -> "$celestialEmoji $timeGreeting"
        }

        greetingCard.updateProfile(name, effectiveGreeting)
    }

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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
        swingAnim?.cancel()
        breatheAnim?.cancel()
        swingAnim = null
        breatheAnim = null
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(statusReceiver)
            } catch (_: Exception) {}
            receiverRegistered = false
        }
    }

    private fun makeVectorPill(icon: String, label: String, iconTint: Int, tagStr: String, onClick: () -> Unit) = LinearLayout(context).apply {
        tag = tagStr
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        val padH = LayoutMetrics.dynamicSpace(context, ratio = 0.013f, minDp = 11, maxDp = 18, horizontal = true)
        val iconSize = LayoutMetrics.dynamicSpace(context, ratio = 0.024f, minDp = 14, maxDp = 19, horizontal = false)
        setPadding(padH, 0, padH, 0)
        isFocusable = true
        isClickable = true
        addView(VectorGlyphDesign(context, icon, iconTint), LinearLayout.LayoutParams(iconSize, iconSize).apply {
            rightMargin = padH / 2
        })
        addView(makeText(label, 13f, Color.WHITE, true))
        background = pillBg(false, settings.accentColor)
        setOnFocusChangeListener { v, f ->
            AnimationController.animateFocus(v, f, settings.motionStyle, 1.07f, 8f)
            v.background = pillBg(f, settings.accentColor)
        }
        setOnClickListener { onClick() }
    }

    private fun makeVectorCircleButton(icon: String, tint: Int, tagStr: String, onClick: () -> Unit) = FrameLayout(context).apply {
        tag = tagStr
        isFocusable = true
        isClickable = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(62, 22, 32, 68))
            setStroke(LayoutMetrics.dp(context, 1), Color.argb(105, 255, 255, 255))
        }
        val iconSize = LayoutMetrics.dynamicSpace(context, ratio = 0.026f, minDp = 16, maxDp = 22, horizontal = false)
        val iv = VectorGlyphDesign(context, icon, tint)
        addView(iv, LayoutParams(iconSize, iconSize, Gravity.CENTER))
        setOnClickListener { onClick() }
        setOnFocusChangeListener { v, f ->
            AnimationController.animateFocus(v, f, settings.motionStyle, 1.15f, 8f)
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (f) Color.argb(120, 255, 255, 255) else Color.argb(62, 22, 32, 68))
                setStroke(LayoutMetrics.dp(context, if (f) 2 else 1), if (f) settings.accentColor else Color.argb(105, 255, 255, 255))
            }
            when (icon) {
                "gear" -> iv.animate().rotation(if (f) 60f else 0f).setDuration(240).start()
                "bell" -> iv.animate().rotation(if (f) 18f else 0f).setDuration(200).start()
                "sparkle" -> iv.animate().rotation(if (f) 45f else 0f).setDuration(240).start()
            }
        }
    }

    private fun pillBg(focused: Boolean, accent: Int) = GradientDrawable().apply {
        setColor(if (focused) Color.argb(110, 255, 255, 255) else Color.argb(54, 22, 32, 68))
        setStroke(LayoutMetrics.dp(context, if (focused) 2 else 1), if (focused) accent else Color.argb(95, 255, 255, 255))
        cornerRadius = LayoutMetrics.dpF(context, 22f)
    }

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
}
