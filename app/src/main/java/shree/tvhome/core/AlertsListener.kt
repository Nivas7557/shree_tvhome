// Important instructions:
// - Capture real system/app notifications cleanly without storing self-notifications.
// - Keep alert collection lightweight and safe when notification access is toggled.

package shree.tvhome.core

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertsListener : NotificationListenerService() {

    data class RealAlert(
        val key: String,
        val packageName: String?,
        val appName: String,
        val title: String,
        val text: String,
        val timeStr: String
    )

    companion object {
        private val liveAlerts = ArrayList<RealAlert>()
        private var instance: AlertsListener? = null

        // Checks whether notification listener access is enabled for this launcher.
        fun isPermissionGranted(ctx: Context): Boolean {
            val cn = ComponentName(ctx, AlertsListener::class.java)
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
            return flat != null && flat.contains(cn.flattenToString())
        }

        // Returns a snapshot of currently tracked live notifications.
        fun getAlerts(): List<RealAlert> {
            instance?.syncActiveNotifications()
            return liveAlerts.toList()
        }

        // Dismisses a single notification in the system and removes it from the cached list.
        fun dismissAlert(key: String) {
            try {
                instance?.cancelNotification(key)
            } catch (_: Exception) {}
            liveAlerts.removeAll { it.key == key }
        }

        // Dismisses all active notifications when supported by the connected service.
        fun clearAllAlerts() {
            try {
                instance?.cancelAllNotifications()
            } catch (_: Exception) {}
            liveAlerts.clear()
        }
    }

    // Stores the active service instance and syncs initial notifications.
    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        syncActiveNotifications()
    }

    // Clears the static service reference when disconnected.
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    // Refreshes cached alerts whenever a new notification is posted.
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        syncActiveNotifications()
    }

    // Refreshes cached alerts whenever a notification is removed.
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        syncActiveNotifications()
    }

    // Reads active notifications from the system and normalizes display fields.
    fun syncActiveNotifications() {
        try {
            val list = activeNotifications ?: return
            val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
            val pm = packageManager
            val updated = ArrayList<RealAlert>()

            for (sbn in list) {
                if (sbn.packageName == packageName) continue
                val extras = sbn.notification?.extras ?: continue
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
                val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
                if (title.isEmpty() && body.isEmpty()) continue

                val appLabel = try {
                    val ai = pm.getApplicationInfo(sbn.packageName, 0)
                    pm.getApplicationLabel(ai).toString()
                } catch (_: Exception) {
                    sbn.packageName.substringAfterLast('.')
                }

                updated.add(
                    RealAlert(
                        key = sbn.key,
                        packageName = sbn.packageName,
                        appName = appLabel,
                        title = title.ifEmpty { appLabel },
                        text = body.ifEmpty { "New notification from $appLabel" },
                        timeStr = fmt.format(Date(sbn.postTime))
                    )
                )
            }
            liveAlerts.clear()
            liveAlerts.addAll(updated)
        } catch (_: Exception) {}
    }
}
