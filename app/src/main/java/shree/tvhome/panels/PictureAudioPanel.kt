// Important instructions:
// - Apply display brightness and eye-care warmth tint safely with system write checks.
// - Probe hardware capabilities (Bluetooth, TV inputs, audio outputs, storage) dynamically.

package shree.tvhome.panels

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.DeviceSpecs
import shree.tvhome.core.LayoutMetrics
import java.util.Locale
import kotlin.math.abs

object PictureAudioPanel {

    const val REQ_BLUETOOTH_CONNECT = 202
    private const val WARM_OVERLAY_TAG = "shree_warm_tint_overlay"

    data class PicturePreset(val key: String, val label: String, val brightness: Int, val warmth: Int)
    data class TimeoutOption(val millis: Int, val label: String)

    val PICTURE_MODES = listOf(
        PicturePreset("Standard", "Standard", 80, 0),
        PicturePreset("Cinema", "Cinema / Movie", 65, 18),
        PicturePreset("Sports", "Sports", 95, 0),
        PicturePreset("Vivid", "Vivid", 100, 0),
        PicturePreset("EyeCare", "Night Eye-Care", 40, 38),
        PicturePreset("Custom", "Custom (Adjust)", 80, 10)
    )

    // Builds picture mode presets, custom sliders, and screen sleep timeout options.
    fun buildPictureAndDisplayPanel(ctx: Context, panel: LinearLayout, settings: AppSettings, onBack: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(ctx)) {
            val permBtn = PanelSurfaceKit.makeActionRow(
                ctx, settings, "🔓", "Enable System Brightness Control", "Allow launcher to adjust TV backlight"
            ) {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                } catch (_: Exception) {}
            }
            panel.addView(permBtn, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 8) })
        }

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "DISPLAY PICTURE MODES")
        val customSlidersBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val rebuildCustomControls = {
            customSlidersBox.removeAllViews()
            if (settings.pictureMode == "Custom") {
                addSliderCard(ctx, customSlidersBox, settings, "☀  Custom Brightness", settings.customBrightnessPct) { p ->
                    settings.customBrightnessPct = p
                    applyBrightness(ctx, p)
                }
                addSliderCard(ctx, customSlidersBox, settings, "🌅  Warmth / Eye-Care Tint", settings.customWarmthPct) { w ->
                    settings.customWarmthPct = w
                    applyWarmTintOverlay(ctx, w)
                }
            }
            DeviceSpecs.resolveIntent(ctx, Settings.ACTION_DISPLAY_SETTINGS)?.let { dispIntent ->
                val hwBtn = PanelSurfaceKit.makeActionRow(
                    ctx, settings, "🎛", "TV Sharpness, Contrast & HDR", "Open native TV hardware picture menu"
                ) {
                    try { ctx.startActivity(dispIntent) } catch (_: Exception) {}
                }
                customSlidersBox.addView(hwBtn, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })
            }
        }

        val modeCards = ArrayList<Pair<LinearLayout, TextView>>()
        PICTURE_MODES.forEach { preset ->
            val isSel = settings.pictureMode == preset.key
            val targetBright = if (preset.key == "Custom") settings.customBrightnessPct else preset.brightness
            val titleTv = makeText(
                ctx,
                if (isSel) "✓  ${preset.label}" else "○  ${preset.label}",
                10.5f,
                if (isSel) settings.accentColor else Color.WHITE,
                true
            )
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9))
                isFocusable = true
                background = cardBg(ctx, settings.accentColor, isSel)
                setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.02f, 3f)
                    v.background = cardBg(ctx, settings.accentColor, f || settings.pictureMode == preset.key)
                }
                setOnClickListener {
                    settings.pictureMode = preset.key
                    val b = if (preset.key == "Custom") settings.customBrightnessPct else preset.brightness
                    val w = if (preset.key == "Custom") settings.customWarmthPct else preset.warmth
                    applyBrightness(ctx, b)
                    applyWarmTintOverlay(ctx, w)
                    modeCards.forEachIndexed { i, (cView, tView) ->
                        val active = PICTURE_MODES[i].key == settings.pictureMode
                        tView.text = if (active) "✓  ${PICTURE_MODES[i].label}" else "○  ${PICTURE_MODES[i].label}"
                        tView.setTextColor(if (active) settings.accentColor else Color.WHITE)
                        cView.background = cardBg(ctx, settings.accentColor, cView.hasFocus() || active)
                    }
                    rebuildCustomControls()
                }
            }
            card.addView(titleTv, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(makeText(ctx, "$targetBright%", 9f, Color.argb(190, 220, 235, 255), true))
            modeCards.add(card to titleTv)
            panel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })
        }

        panel.addView(customSlidersBox, LinearLayout.LayoutParams(-1, -2))
        rebuildCustomControls()

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "SCREEN SLEEP TIMEOUT")
        val currentTimeout = try {
            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 900_000)
        } catch (_: Exception) { 900_000 }

        val baseTimeouts = mutableListOf(
            TimeoutOption(300_000, "5 Minutes"),
            TimeoutOption(900_000, "15 Minutes"),
            TimeoutOption(1_800_000, "30 Minutes"),
            TimeoutOption(2_147_483_647, "Never Sleep")
        )
        if (baseTimeouts.none { isTimeoutMatch(currentTimeout, it.millis) } && currentTimeout > 0) {
            val mins = maxOf(1, currentTimeout / 60_000)
            baseTimeouts.add(0, TimeoutOption(currentTimeout, "$mins Minutes (TV Default)"))
        }

        var activeTimeout = currentTimeout
        val tCards = ArrayList<Pair<LinearLayout, TextView>>()
        baseTimeouts.forEach { opt ->
            val isSel = isTimeoutMatch(activeTimeout, opt.millis)
            val tv = makeText(ctx, if (isSel) "✓  ${opt.label}" else "○  ${opt.label}", 10.5f, if (isSel) settings.accentColor else Color.WHITE, true)
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9))
                isFocusable = true
                background = cardBg(ctx, settings.accentColor, isSel)
                setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.02f, 3f)
                    v.background = cardBg(ctx, settings.accentColor, f || isTimeoutMatch(activeTimeout, opt.millis))
                }
                setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(ctx)) {
                        try {
                            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, opt.millis)
                            activeTimeout = opt.millis
                            tCards.forEachIndexed { idx, (cView, tView) ->
                                val act = isTimeoutMatch(activeTimeout, baseTimeouts[idx].millis)
                                tView.text = if (act) "✓  ${baseTimeouts[idx].label}" else "○  ${baseTimeouts[idx].label}"
                                tView.setTextColor(if (act) settings.accentColor else Color.WHITE)
                                cView.background = cardBg(ctx, settings.accentColor, cView.hasFocus() || act)
                            }
                            Toast.makeText(ctx, "Sleep timer: ${opt.label}", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {}
                    } else {
                        Toast.makeText(ctx, "Enable System Brightness/Write Control above first", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            card.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
            tCards.add(card to tv)
            panel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })
        }
        addBackBtn(ctx, panel, settings, onBack)
    }

    // Builds audio output device list and mute toggle controls.
    fun buildSoundSubPanel(ctx: Context, panel: LinearLayout, settings: AppSettings, onBack: () -> Unit) {
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "ACTIVE AUDIO OUTPUTS")
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val devices = DeviceSpecs.getAudioOutputs(ctx)
        if (devices.isEmpty()) {
            panel.addView(
                PanelSurfaceKit.makeActionRow(ctx, settings, "🔊", DeviceSpecs.mapAudioDeviceType(0), "Default TV audio output", "") {},
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) }
            )
        } else {
            devices.forEach { dev ->
                panel.addView(
                    PanelSurfaceKit.makeActionRow(ctx, settings, "🎧", dev.typeName, dev.productName, "") {},
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) }
                )
            }
        }
        val initialMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am?.isStreamMute(AudioManager.STREAM_MUSIC) == true else false
        val muteCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9))
            isFocusable = true
            background = cardBg(ctx, settings.accentColor, false)
            setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.02f, 4f)
                v.background = cardBg(ctx, settings.accentColor, f)
            }
        }
        val iconTv = makeText(ctx, if (initialMuted) "🔇" else "🔈", 14f, settings.accentColor, true).apply {
            setPadding(0, 0, dp(ctx, 10), 0)
        }
        val titleTv = makeText(ctx, if (initialMuted) "Unmute TV Audio" else "Mute TV Audio", 10.5f, Color.WHITE, true)
        val mid = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleTv)
            addView(makeText(ctx, "Instant audio toggle", 8.5f, Color.argb(185, 210, 230, 245), false))
        }
        muteCard.addView(iconTv)
        muteCard.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        muteCard.addView(makeText(ctx, "›", 15f, Color.argb(180, 255, 255, 255), true))
        muteCard.setOnClickListener {
            am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            val nowMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am?.isStreamMute(AudioManager.STREAM_MUSIC) == true else !initialMuted
            iconTv.text = if (nowMuted) "🔇" else "🔈"
            titleTv.text = if (nowMuted) "Unmute TV Audio" else "Mute TV Audio"
        }
        panel.addView(muteCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })
        addBackBtn(ctx, panel, settings, onBack)
    }

    // Lists detected TV inputs and launches their setup intents when available.
    fun buildInputsSubPanel(ctx: Context, panel: LinearLayout, settings: AppSettings, onBack: () -> Unit) {
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "DETECTED TV & HDMI INPUTS")
        val inputs = DeviceSpecs.getTvInputs(ctx)
        if (inputs.isEmpty()) {
            panel.addView(makeText(ctx, "No external HDMI tuner inputs reported by this TV.", 9.5f, Color.LTGRAY, false).apply {
                setPadding(dp(ctx, 4), dp(ctx, 8), dp(ctx, 4), dp(ctx, 12))
            })
        } else {
            for (inp in inputs) {
                panel.addView(
                    PanelSurfaceKit.makeActionRow(ctx, settings, "📺", inp.label, "Tap to configure or switch source") {
                        try { inp.setupIntent?.let { ctx.startActivity(it) } } catch (_: Exception) {}
                    },
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) }
                )
            }
        }
        addBackBtn(ctx, panel, settings, onBack)
    }

    // Builds Bluetooth power toggle and paired device list with Android 12+ permission checks.
    @SuppressLint("MissingPermission")
    fun buildBluetoothPanel(ctx: Context, panel: LinearLayout, settings: AppSettings, onBack: () -> Unit) {
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            panel.addView(makeText(ctx, "Bluetooth hardware not detected on this device.", 10.5f, Color.LTGRAY, false))
            addBackBtn(ctx, panel, settings, onBack)
            return
        }
        val hasBtPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        var isBtOn = try { adapter.isEnabled } catch (_: Exception) { false }

        val statusCard = PanelSurfaceKit.makeActionRow(
            ctx, settings, "🔵",
            if (isBtOn) "Bluetooth Enabled" else "Bluetooth Turned Off",
            "Tap to toggle Bluetooth radio"
        ) {
            if (!hasBtPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx as? Activity)?.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQ_BLUETOOTH_CONNECT)
                return@makeActionRow
            }
            try {
                @Suppress("DEPRECATION")
                if (isBtOn) adapter.disable() else adapter.enable()
                isBtOn = !isBtOn
                Toast.makeText(ctx, if (isBtOn) "Bluetooth Turning On..." else "Bluetooth Turning Off...", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(ctx, "Bluetooth state controlled by TV OS", Toast.LENGTH_SHORT).show()
            }
        }
        panel.addView(statusCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 10) })

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "PAIRED REMOTES & AUDIO")
        if (!hasBtPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            panel.addView(
                PanelSurfaceKit.makeActionRow(ctx, settings, "🔓", "Allow Bluetooth Access", "Tap to view paired devices") {
                    (ctx as? Activity)?.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQ_BLUETOOTH_CONNECT)
                },
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 8) }
            )
        } else {
            val bonded = try { adapter.bondedDevices?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
            if (bonded.isEmpty()) {
                panel.addView(makeText(ctx, "No paired Bluetooth devices found.", 9.5f, Color.LTGRAY, false))
            } else {
                for (dev in bonded) {
                    val dName = try { dev.name ?: dev.address } catch (_: Exception) { "Bluetooth Device" }
                    panel.addView(
                        PanelSurfaceKit.makeActionRow(ctx, settings, "🎧", dName, "Paired • ${dev.address}", "") {},
                        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) }
                    )
                }
            }
        }
        addBackBtn(ctx, panel, settings, onBack)
    }

    // Displays internal storage usage and lists installed launcher apps for quick app settings access.
    fun buildStorageAndAppsPanel(ctx: Context, panel: LinearLayout, settings: AppSettings, onBack: () -> Unit) {
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "INTERNAL STORAGE")
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.blockSizeLong * stat.blockCountLong
        val freeBytes = stat.blockSizeLong * stat.availableBlocksLong
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
        val usedPct = ((usedBytes.toDouble() / totalBytes.coerceAtLeast(1L)) * 100).toInt()
        val totalGb = String.format(Locale.US, "%.1f GB", totalBytes / 1_073_741_824.0)
        val freeGb = String.format(Locale.US, "%.1f GB", freeBytes / 1_073_741_824.0)
        val usedGb = String.format(Locale.US, "%.1f GB", usedBytes / 1_073_741_824.0)

        val storageCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
            background = cardBg(ctx, settings.accentColor, true)
        }
        storageCard.addView(makeText(ctx, "💾  Used: $usedGb / $totalGb ($usedPct%)", 10.5f, Color.WHITE, true))
        storageCard.addView(makeText(ctx, "Available Space: $freeGb Free", 9f, Color.rgb(125, 240, 175), true))
        panel.addView(storageCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 10) })

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "INSTALLED APPS")
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase(Locale.ROOT) }

        for (info in apps) {
            val pkg = info.activityInfo.packageName
            val lbl = info.loadLabel(pm).toString()
            panel.addView(
                PanelSurfaceKit.makeActionRow(ctx, settings, "📦", lbl, pkg) {
                    try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))) } catch (_: Exception) {}
                },
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) }
            )
        }
        addBackBtn(ctx, panel, settings, onBack)
    }

    // Reads current system screen brightness as a clamped percentage (10..100).
    fun getCurrentBrightnessPct(ctx: Context): Int = try {
        val raw = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 200)
        ((raw / 255f) * 100f).toInt().coerceIn(10, 100)
    } catch (_: Exception) { 80 }

    // Applies brightness to the current window and updates system brightness when permitted.
    fun applyBrightness(ctx: Context, pct: Int) {
        val clamped = pct.coerceIn(10, 100)
        (ctx as? Activity)?.window?.let { win ->
            val attr = win.attributes
            attr.screenBrightness = clamped / 100f
            win.attributes = attr
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(ctx)) {
            try {
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, ((clamped / 100f) * 255).toInt())
            } catch (_: Exception) {}
        }
    }

    // Adds or removes a non-interactive warm tint overlay on the activity decor view.
    fun applyWarmTintOverlay(ctx: Context, warmthPct: Int) {
        val root = (ctx as? Activity)?.window?.decorView as? ViewGroup ?: return
        var overlay = root.findViewWithTag<View>(WARM_OVERLAY_TAG)
        if (warmthPct <= 0) {
            if (overlay != null) root.removeView(overlay)
            return
        }
        if (overlay == null) {
            overlay = View(ctx).apply {
                tag = WARM_OVERLAY_TAG
                isClickable = false
                isFocusable = false
            }
            root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        }
        val alpha = ((warmthPct / 100f) * 110).toInt().coerceIn(0, 95)
        overlay.background = ColorDrawable(Color.argb(alpha, 255, 145, 45))
    }

    private fun isTimeoutMatch(cur: Int, opt: Int): Boolean =
        abs(cur - opt) < 60_000 || (opt > 100_000_000 && cur > 100_000_000)

    private fun addSliderCard(
        ctx: Context,
        parent: LinearLayout,
        settings: AppSettings,
        label: String,
        initPct: Int,
        onChange: (Int) -> Unit
    ) {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9))
            background = cardBg(ctx, settings.accentColor, false)
        }
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val pct = makeText(ctx, "$initPct%", 10.5f, settings.accentColor, true)
        top.addView(makeText(ctx, label, 10.5f, Color.WHITE, true))
        top.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(pct)
        card.addView(top)
        val sb = SeekBar(ctx).apply {
            max = 100
            progress = initPct
            isFocusable = true
            setOnFocusChangeListener { _, f -> card.background = cardBg(ctx, settings.accentColor, f) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    pct.text = "$p%"
                    if (user) onChange(p)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        card.addView(sb, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 5) })
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 7) })
    }

    private fun addBackBtn(ctx: Context, panel: LinearLayout, settings: AppSettings, onBack: () -> Unit) {
        val btn = TextView(ctx).apply {
            text = "‹  Back to Quick Settings"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isFocusable = true
            background = cardBg(ctx, settings.accentColor, true)
            setOnFocusChangeListener { v, f -> AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 3f) }
            setOnClickListener { onBack() }
        }
        panel.addView(btn, LinearLayout.LayoutParams(-1, dp(ctx, 40)).apply {
            topMargin = dp(ctx, 10)
            bottomMargin = dp(ctx, 20)
        })
    }

    private fun cardBg(ctx: Context, accent: Int, active: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (active) intArrayOf(LayoutMetrics.withAlpha(accent, 115), Color.argb(65, 255, 255, 255))
        else intArrayOf(Color.argb(52, 255, 255, 255), Color.argb(26, 255, 255, 255))
    ).apply {
        cornerRadius = LayoutMetrics.dpF(ctx, 14f)
        setStroke(dp(ctx, if (active) 2 else 1), if (active) accent else Color.argb(65, 255, 255, 255))
    }

    private fun makeText(ctx: Context, v: String, sz: Float, col: Int, bld: Boolean) = TextView(ctx).apply {
        text = v
        textSize = sz
        setTextColor(col)
        if (bld) typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(ctx: Context, v: Int): Int = LayoutMetrics.dp(ctx, v)
}
