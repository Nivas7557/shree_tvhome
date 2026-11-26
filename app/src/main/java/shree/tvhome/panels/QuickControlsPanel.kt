// Important instructions:
// - Derive spacing and quick controls dynamically from display metrics, DeviceSpecs, and Themes.
// - Never force requestFocus(); let native D-Pad navigation and pill_slider controls handle movement cleanly.

package shree.tvhome.panels

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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

    // Builds 2-column quick cards, hardware sliders, ambient screensaver, memory boost, and system settings.
    fun build(
        ctx: Context,
        panel: LinearLayout,
        settings: AppSettings,
        onNavigate: (String) -> Unit
    ) {
        panel.clipChildren = false
        panel.clipToPadding = false

        val gapH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 4, maxDp = 12, horizontal = true)
        val gapV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 4, maxDp = 10, horizontal = false)
        val cardH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.076f, minDp = 50, maxDp = 66, horizontal = false)
        val padH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.01f, minDp = 8, maxDp = 14, horizontal = true)
        val padV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 5, maxDp = 10, horizontal = false)

        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "QUICK SETTINGS")

        val curBright = PictureAudioPanel.getCurrentBrightnessPct(ctx)
        val activeAudio = DeviceSpecs.getAudioOutputs(ctx).lastOrNull()?.typeName
            ?: DeviceSpecs.mapAudioDeviceType(0)
        val connectedWifi = Themes.getConnectedSsid(ctx)

        // Assemble quick action tiles dynamically based on available TV hardware and inputs.
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

        val chunkedTiles = dynamicTiles.chunked(2)
        chunkedTiles.forEachIndexed { rowIdx, pair ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            pair.forEachIndexed { colIdx, tile ->
                val titleTv = TextView(ctx).apply {
                    text = "${tile.icon}  ${tile.title}"
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                }
                val subTv = TextView(ctx).apply {
                    text = tile.subtitle
                    textSize = 8.2f
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, gapV / 4, 0, 0)
                }

                val card = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(padH, padV, padH, padV)
                    isFocusable = true
                    isClickable = true
                    addView(titleTv)
                    addView(subTv)
                }

                val syncTileColors = {
                    val active = card.hasFocus()
                    card.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, active, settings.tileColor)
                    titleTv.setTextColor(Themes.getPrimaryTextColor(settings.tileColor))
                    val surfaceColors = Themes.getTheme(settings.tileColor).normalColors
                    subTv.setTextColor(
                        if (Themes.isLightSurface(surfaceColors)) {
                            Themes.getAdaptiveTextColor(surfaceColors, secondary = true)
                        } else {
                            settings.accentColor
                        }
                    )
                }
                syncTileColors()
                settings.addStyleListener(syncTileColors)

                card.setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 4f)
                    syncTileColors()
                }
                card.setOnClickListener { onNavigate(tile.targetMode) }

                row.addView(
                    card,
                    LinearLayout.LayoutParams(0, cardH, 1f).apply {
                        if (colIdx < pair.lastIndex) rightMargin = gapH
                    }
                )
            }
            val bottomPad = if (rowIdx == chunkedTiles.lastIndex) gapV * 2 else gapV
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = bottomPad })
        }

        // Hardware sliders supporting left/right D-Pad adjustment directly.
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

        // Ambient screensaver and lightweight memory optimization triggers.
        panel.addView(
            PanelSurfaceKit.makeActionRow(
                ctx, settings, "🕰️", "Ambient Clock Screensaver", "Full-screen clock & live backdrop"
            ) {
                ctx.sendBroadcast(Intent(MainActivity.ACTION_ENTER_AMBIENT).setPackage(ctx.packageName))
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV }
        )

        panel.addView(
            PanelSurfaceKit.makeActionRow(
                ctx, settings, "🚀", "Boost Memory (RAM)", getRamSummary(ctx)
            ) {
                System.gc()
                Toast.makeText(ctx, "Memory optimized", Toast.LENGTH_SHORT).show()
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV * 2 }
        )

        // Discovered native system settings rows.
        val nativeSettings = DeviceSpecs.getAvailableSettings(ctx)
        if (nativeSettings.isNotEmpty()) {
            PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "TV SYSTEM SETTINGS")
            for (item in nativeSettings) {
                panel.addView(
                    PanelSurfaceKit.makeActionRow(ctx, settings, item.icon, item.title, item.subtitle) {
                        try { ctx.startActivity(item.intent) } catch (_: Exception) {}
                    },
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gapV }
                )
            }
        }
    }

    // Computes available internal storage in GB dynamically.
    private fun getFreeStorageSummary(): String = try {
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = (stat.blockSizeLong * stat.availableBlocksLong) / 1_073_741_824.0
        String.format(Locale.US, "%.1f GB Free", freeGb)
    } catch (_: Exception) { "" }

    // Computes current app heap usage dynamically.
    private fun getRamSummary(ctx: Context): String = try {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val maxMb = rt.maxMemory() / (1024 * 1024)
        "${usedMb}MB / ${maxMb}MB"
    } catch (_: Exception) { ctx.getString(android.R.string.ok) }
}
