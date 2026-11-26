// Important instructions:
// - Simulate authentic Android TV D-Pad remote keys on mobile touchscreens via swipes, tap (OK), and long-press.
// - Automatically stay inactive on real Android TV hardware so native D-Pad remotes remain untouched.

package shree.tvhome

import android.app.Activity
import android.os.SystemClock
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.view.ViewConfiguration
import kotlin.math.abs

class TouchDpadSimulator(private val activity: Activity) {

    // Enable touch D-Pad gesture simulation automatically on non-TV (mobile/tablet) test devices.
    private val isMobileTestDevice: Boolean =
        (activity.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType !=
            Configuration.UI_MODE_TYPE_TELEVISION
    private val swipeThresholdPx: Float = ViewConfiguration.get(activity).scaledTouchSlop * 2.4f
    private var gestureConsumed = false

    private val gestureDetector = GestureDetector(
        activity,
        object : GestureDetector.SimpleOnGestureListener() {

            // Dispatch DPAD_CENTER (OK) when tapping anywhere on screen while testing on mobile.
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val focused = activity.window?.currentFocus ?: return false
                if (focused.isAttachedToWindow) {
                    dispatchDpadKey(KeyEvent.KEYCODE_DPAD_CENTER, isLongPress = false)
                    gestureConsumed = true
                    return true
                }
                return false
            }

            // Dispatch a true TV Remote OK Long-Press so app context menus open on mobile touch.
            override fun onLongPress(e: MotionEvent) {
                dispatchDpadKey(KeyEvent.KEYCODE_DPAD_CENTER, isLongPress = true)
                gestureConsumed = true
            }

            // Convert directional swipes anywhere on screen into DPAD_LEFT/RIGHT/UP/DOWN key events.
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                val dy = e2.y - start.y
                if (abs(dx) < swipeThresholdPx && abs(dy) < swipeThresholdPx) return false

                val keyCode = if (abs(dx) > abs(dy)) {
                    if (dx > 0f) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                } else {
                    if (dy > 0f) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
                }
                dispatchDpadKey(keyCode, isLongPress = false)
                gestureConsumed = true
                return true
            }
        }
    )

    // Intercepts Activity touch events on mobile devices to drive D-Pad simulation cleanly.
    fun onDispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isMobileTestDevice) return false
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            gestureConsumed = false
        }
        gestureDetector.onTouchEvent(ev)
        return gestureConsumed && (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL)
    }

    // Dispatches synthetic DOWN + UP KeyEvents through the Activity window just like a hardware TV remote.
    private fun dispatchDpadKey(keyCode: Int, isLongPress: Boolean) {
        val now = SystemClock.uptimeMillis()
        val flags = if (isLongPress) KeyEvent.FLAG_LONG_PRESS else 0
        val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, if (isLongPress) 1 else 0, 0, -1, 0, flags)
        activity.dispatchKeyEvent(downEvent)
        if (!isLongPress) {
            val upEvent = KeyEvent(now, now + 18L, KeyEvent.ACTION_UP, keyCode, 0, 0, -1, 0, 0)
            activity.dispatchKeyEvent(upEvent)
        }
    }
}
