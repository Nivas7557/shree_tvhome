// Important instructions:
// - Display live system/app notifications from AlertsListener and toggle Do Not Disturb cleanly.
// - Provide ambient screensaver, home standby, and safe soft reload actions without force-killing processes.

package shree.tvhome.panels

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.text.TextUtils
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.MainActivity
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AlertsListener
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics

object AlertsPowerPanel {

    // Builds the live notifications panel with DND toggle, dismissal, and app launch support.
    fun buildAlertsSubPanel(
        ctx: Context,
        panel: LinearLayout,
        settings: AppSettings,
        onRefreshPanel: () -> Unit
    ) {
        if (!AlertsListener.isPermissionGranted(ctx)) {
            PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "PERMISSION REQUIRED")
            panel.addView(
                PanelSurfaceKit.makeActionRow(
                    ctx, settings, "🔓",
                    "Enable Notification Access",
                    "Allow launcher to show real system & app alerts"
                ) {
                    try { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {}
                },
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 8) }
            )
            return
        }

        val dndState = settings.dndEnabled
        val dndRow = PanelSurfaceKit.makeActionRow(
            ctx, settings,
            if (dndState) "🔕" else "🔔",
            if (dndState) "Do Not Disturb: ON" else "Do Not Disturb: OFF",
            if (dndState) "Sleep nudges muted" else "Tap to mute sleep nudges",
            if (dndState) "ON" else "OFF"
        ) {
            settings.dndEnabled = !settings.dndEnabled
            onRefreshPanel()
        }
        panel.addView(dndRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 8) })

        val alerts = AlertsListener.getAlerts()
        val topBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val headerCountTv = TextView(ctx).apply {
            text = "LIVE SYSTEM ALERTS (${alerts.size})"
            textSize = 9f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(settings.accentColor)
        }
        topBar.addView(headerCountTv)
        topBar.addView(LinearLayout(ctx), LinearLayout.LayoutParams(0, 1, 1f))

        val listHolder = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        if (alerts.isNotEmpty()) {
            topBar.addView(TextView(ctx).apply {
                text = "Clear All"
                textSize = 9f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(dp(ctx, 8), dp(ctx, 3), dp(ctx, 8), dp(ctx, 3))
                isFocusable = true
                background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, false, settings.tileColor)
                setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.03f, 3f)
                    v.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, f, settings.tileColor)
                }
                setOnClickListener {
                    AlertsListener.clearAllAlerts()
                    listHolder.removeAllViews()
                    headerCountTv.text = "LIVE SYSTEM ALERTS (0)"
                    listHolder.addView(emptyAlertView(ctx))
                }
            })
        }

        panel.addView(topBar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 8) })
        panel.addView(listHolder, LinearLayout.LayoutParams(-1, -2))

        if (alerts.isEmpty()) {
            listHolder.addView(emptyAlertView(ctx))
            return
        }

        for (item in alerts) {
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(ctx, 11), dp(ctx, 9), dp(ctx, 11), dp(ctx, 9))
                isFocusable = true
                background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, false, settings.tileColor)
                setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.02f, 4f)
                    v.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, f, settings.tileColor)
                }
                setOnClickListener { v ->
                    item.packageName?.let { pkg ->
                        try {
                            ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it) }
                        } catch (_: Exception) {}
                    }
                    AlertsListener.dismissAlert(item.key)
                    listHolder.removeView(v)
                    val rem = AlertsListener.getAlerts().size
                    headerCountTv.text = "LIVE SYSTEM ALERTS ($rem)"
                    if (rem == 0) listHolder.addView(emptyAlertView(ctx))
                }
            }
            val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            row1.addView(TextView(ctx).apply {
                text = "🔔  ${item.appName}"
                textSize = 9f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(settings.accentColor)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row1.addView(TextView(ctx).apply {
                text = "${item.timeStr}  ›"
                textSize = 8.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.argb(180, 255, 255, 255))
            })
            card.addView(row1)
            card.addView(TextView(ctx).apply {
                text = item.title
                textSize = 10.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(ctx, 3), 0, dp(ctx, 1))
            })
            card.addView(TextView(ctx).apply {
                text = item.text
                textSize = 9f
                setTextColor(Color.argb(195, 215, 232, 250))
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
            listHolder.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 7) })
        }
    }

    // Builds device power and launcher reload actions inside the power panel.
    fun build(
        ctx: Context,
        panel: LinearLayout,
        settings: AppSettings,
        onCloseDrawer: () -> Unit,
        onRestartLauncher: () -> Unit
    ) {
        PanelSurfaceKit.addSectionHeader(ctx, panel, settings, "DEVICE POWER & LAUNCHER")
        panel.addView(
            PanelSurfaceKit.makeActionRow(
                ctx, settings, "🕰️", "Ambient Clock Screensaver", "Enter full-screen clock & weather view"
            ) {
                onCloseDrawer()
                ctx.sendBroadcast(Intent(MainActivity.ACTION_ENTER_AMBIENT).setPackage(ctx.packageName))
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) }
        )
        panel.addView(
            PanelSurfaceKit.makeActionRow(
                ctx, settings, "🌙", "Home Standby", "Return to system home intent"
            ) {
                try { ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) } catch (_: Exception) {}
                onCloseDrawer()
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) }
        )
        panel.addView(
            PanelSurfaceKit.makeActionRow(
                ctx, settings, "🔄", "Refresh Launcher", "Reload apps, icons & visual shaders"
            ) {
                onRestartLauncher()
                onCloseDrawer()
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) }
        )
    }

    private fun emptyAlertView(ctx: Context): TextView = TextView(ctx).apply {
        text = "No active notifications right now"
        textSize = 10.5f
        setTextColor(Color.argb(180, 210, 230, 250))
        gravity = android.view.Gravity.CENTER
        setPadding(0, dp(ctx, 28), 0, dp(ctx, 28))
    }

    private fun dp(ctx: Context, v: Int): Int = LayoutMetrics.dp(ctx, v)
}
