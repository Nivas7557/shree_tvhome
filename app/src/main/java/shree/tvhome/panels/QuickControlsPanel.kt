// Important instructions:
// - Build quick setting cards dynamically based on detected TV hardware and OS settings.
// - Delegate panel navigation cleanly through PanelHub modes.

package shree.tvhome.panels

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Environment
import android.os.StatFs
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import shree.tvhome.MainActivity
import shree.tvhome.PanelHub
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.DeviceSpecs
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes
import java.util.Locale

object QuickControlsPanel {

    private data class QuickTileSpec(
        val icon: String,
        val title: String,
        val subtitle: String,
        val targetMode: String
    )

    // Builds quick tiles, hardware sliders, ambient trigger, memory info, and available OS settings.
    fun build(
        ctx: Context,
        panel: LinearLayout,
        settings: AppSettings,
        onNavigate: (String) -> Unit
    ) {
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "QUICK SETTINGS")

        val curBright = PictureAudioPanel.getCurrentBrightnessPct(ctx)
        val activeAudio = DeviceSpecs.getAudioOutputs(ctx).lastOrNull()?.typeName
            ?: DeviceSpecs.mapAudioDeviceType(0)
        val connectedWifi = Themes.getConnectedSsid(ctx)

        val dynamicTiles = buildList {
            add(QuickTileSpec("☀", "Display & Picture", "${settings.pictureMode} • $curBright%", PanelHub.MODE_SUB_PICTURE))
            add(QuickTileSpec("🔊", "Sound & Audio", activeAudio, PanelHub.MODE_SUB_SOUND))
            add(QuickTileSpec("📶", "Wi-Fi Network", connectedWifi, PanelHub.MODE_WIFI))

            if (DeviceSpecs.hasBluetoothHardware(ctx)) {
                add(QuickTileSpec("🔵", "Bluetooth", "Remotes & Audio", PanelHub.MODE_SUB_BLUETOOTH))
            }
            val inputs = DeviceSpecs.getTvInputs(ctx)
            if (inputs.isNotEmpty()) {
                add(QuickTileSpec("📺", "TV Inputs", "${inputs.size} Sources", PanelHub.MODE_SUB_INPUTS))
            }
            add(QuickTileSpec("💾", "Storage & Apps", getFreeStorageSummary(), PanelHub.MODE_SUB_STORAGE))
        }

        dynamicTiles.chunked(2).forEachIndexed { rowIdx, pair ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            pair.forEachIndexed { colIdx, tile ->
                val card = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6))
                    isFocusable = true
                    background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, false, settings.tileColor)
                    setOnFocusChangeListener { v, f ->
                        AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 4f)
                        v.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, f, settings.tileColor)
                    }
                    setOnClickListener { onNavigate(tile.targetMode) }
                    addView(TextView(ctx).apply {
                        text = "${tile.icon}  ${tile.title}"
                        textSize = 10f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                    })
                    addView(TextView(ctx).apply {
                        text = tile.subtitle
                        textSize = 8.2f
                        setTextColor(settings.accentColor)
                        isSingleLine = true
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(0, dp(ctx, 2), 0, 0)
                    })
                }
                row.addView(
                    card,
                    LinearLayout.LayoutParams(0, dp(ctx, 56), 1f).apply {
                        if (colIdx < pair.lastIndex) rightMargin = dp(ctx, 6)
                    }
                )
            }
            val bottomPad = if (rowIdx == dynamicTiles.chunked(2).lastIndex) dp(ctx, 8) else dp(ctx, 6)
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = bottomPad })
        }

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "HARDWARE CONTROLS")
        PanelSurfaceKit.addGlossyPillSlider(ctx, panel, settings, "☀", "Screen Brightness", curBright) { p ->
            settings.pictureMode = "Custom"
            settings.customBrightnessPct = p
            PictureAudioPanel.applyBrightness(ctx, p)
        }

        if (!DeviceSpecs.isVolumeFixed(ctx)) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val maxVol = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            val curVol = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 10
            val volPct = ((curVol * 100f) / maxOf(1, maxVol)).toInt()
            PanelSurfaceKit.addGlossyPillSlider(ctx, panel, settings, "🔊", "Master Volume", volPct) { p ->
                am?.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    ((p / 100f) * maxVol).toInt(),
                    AudioManager.FLAG_SHOW_UI
                )
            }
        }

        val ambientRow = PanelSurfaceKit.makeActionRow(
            ctx, settings, "🕰️", "Ambient Clock Screensaver", "Full-screen clock, weather & live scene"
        ) {
            ctx.sendBroadcast(Intent(MainActivity.ACTION_ENTER_AMBIENT).setPackage(ctx.packageName))
        }
        panel.addView(ambientRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })

        val ramRow = PanelSurfaceKit.makeActionRow(
            ctx, settings, "🚀", "Boost Memory (RAM)", getRamSummary(ctx)
        ) {
            System.gc()
            Toast.makeText(ctx, "Memory optimized", Toast.LENGTH_SHORT).show()
        }
        panel.addView(ramRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 8) })

        val nativeSettings = DeviceSpecs.getAvailableSettings(ctx)
        if (nativeSettings.isNotEmpty()) {
            PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "TV SYSTEM SETTINGS")
            for (item in nativeSettings) {
                val row = PanelSurfaceKit.makeActionRow(ctx, settings, item.icon, item.title, item.subtitle) {
                    try { ctx.startActivity(item.intent) } catch (_: Exception) {}
                }
                panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })
            }
        }
    }

    private fun getFreeStorageSummary(): String = try {
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = (stat.blockSizeLong * stat.availableBlocksLong) / 1_073_741_824.0
        String.format(Locale.US, "%.1f GB Free", freeGb)
    } catch (_: Exception) { "Space & Apps" }

    private fun getRamSummary(ctx: Context): String = try {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val maxMb = rt.maxMemory() / (1024 * 1024)
        "App Heap: ${usedMb}MB / ${maxMb}MB • Tap to optimize"
    } catch (_: Exception) { "Optimize memory" }

    private fun dp(ctx: Context, v: Int): Int = LayoutMetrics.dp(ctx, v)
}
