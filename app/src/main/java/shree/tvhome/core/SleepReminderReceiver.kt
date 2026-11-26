// Important instructions:
// - Respect Do Not Disturb settings automatically before showing reminder overlays or toasts.
// - Always reschedule the next alarm checkpoint so reminders continue reliably.

package shree.tvhome.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class SleepReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_SLEEP_REMINDER = "shree.tvhome.ACTION_SLEEP_REMINDER"

        // Minute-of-day checkpoints across late-night, midnight, and early-morning windows.
        private val CHECKPOINTS: List<Int> = buildList {
            for (m in 0..210 step 10) add(m)
            for (m in 240..330 step 30) add(m)
            for (m in 1350..1425 step 15) add(m)
        }

        // Schedules the next reminder alarm at the upcoming checkpoint or midnight next day.
        fun scheduleNextNudge(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val now = Calendar.getInstance()
            val curMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            val nextTarget = CHECKPOINTS.firstOrNull { it > curMins }
            val triggerCal = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (nextTarget != null) {
                    set(Calendar.HOUR_OF_DAY, nextTarget / 60)
                    set(Calendar.MINUTE, nextTarget % 60)
                } else {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                }
            }

            val intent = Intent(context, SleepReminderReceiver::class.java).apply { action = ACTION_SLEEP_REMINDER }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(context, 707, intent, flags)

            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerCal.timeInMillis, pi)
            } catch (_: Exception) {
                am.set(AlarmManager.RTC_WAKEUP, triggerCal.timeInMillis, pi)
            }
        }
    }

    private data class ReminderWindowSpec(
        val minuteRange: IntRange,
        val durationMs: Long,
        val colors: IntArray,
        val messages: List<String>
    )

    private val reminderWindows = listOf(
        ReminderWindowSpec(
            minuteRange = 1350..1439,
            durationMs = 10_000L,
            colors = intArrayOf(Color.argb(228, 55, 75, 165), Color.argb(236, 28, 38, 95)),
            messages = listOf(
                "🌙  Even the TV needs some rest now...",
                "🔋  Human battery at 10% • Time to recharge!",
                "✨  Tomorrow's plans start with tonight's sleep",
                "🛋️  One last episode... or actual sleep?",
                "💤  Winding down • Cozy blanket awaits"
            )
        ),
        ReminderWindowSpec(
            minuteRange = 0..215,
            durationMs = 10_000L,
            colors = intArrayOf(Color.argb(232, 195, 55, 95), Color.argb(238, 95, 28, 72)),
            messages = listOf(
                "🦉  Midnight Owl detected! Dreams on, TV off",
                "🚫  No TV zone! Your eyes deserve a break now",
                "🌌  The stars are asleep, why are you awake?",
                "⚡  Heads up: Morning alarm won't show mercy!",
                "🛌  Past midnight • Time to call it a night"
            )
        ),
        ReminderWindowSpec(
            minuteRange = 240..359,
            durationMs = 7_000L,
            colors = intArrayOf(Color.argb(230, 215, 135, 55), Color.argb(238, 135, 72, 32)),
            messages = listOf(
                "🌅  Got up early! Have a peaceful morning ✨",
                "☕  Up before the sun! Fresh start today",
                "✨  Quiet morning, clear mind • Great going!"
            )
        )
    )

    // Evaluates current time against reminder windows, skips if DND is active, and reschedules.
    override fun onReceive(context: Context, intent: Intent?) {
        val appCtx = context.applicationContext
        if (AppSettings(appCtx).dndEnabled) {
            scheduleNextNudge(appCtx)
            return
        }
        val cal = Calendar.getInstance()
        val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val spec = reminderWindows.find { mins in it.minuteRange }
        if (spec != null) {
            showFloatingBubble(appCtx, spec.messages.random(), spec.durationMs, spec.colors)
        }
        scheduleNextNudge(appCtx)
    }

    // Displays a non-focusable floating reminder banner or falls back to a toast if overlay is disallowed.
    private fun showFloatingBubble(ctx: Context, message: String, durationMs: Long, colors: IntArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(ctx)) {
            Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
            return
        }

        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        val bubble = TextView(ctx).apply {
            text = message
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(LayoutMetrics.dp(ctx, 20), LayoutMetrics.dp(ctx, 12), LayoutMetrics.dp(ctx, 22), LayoutMetrics.dp(ctx, 12))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
                cornerRadius = LayoutMetrics.dpF(ctx, 22f)
                setStroke(LayoutMetrics.dp(ctx, 1), Color.argb(175, 255, 255, 255))
            }
            alpha = 0f
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = LayoutMetrics.dp(ctx, 36)
            y = LayoutMetrics.dp(ctx, 28)
        }

        try {
            wm.addView(bubble, params)
            bubble.animate().alpha(1f).setDuration(350L).start()

            Handler(Looper.getMainLooper()).postDelayed({
                bubble.animate().alpha(0f).setDuration(350L).withEndAction {
                    try { wm.removeView(bubble) } catch (_: Exception) {}
                }.start()
            }, durationMs)
        } catch (_: Exception) {
            Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
        }
    }
}
