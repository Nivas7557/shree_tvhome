// Important instructions:
// - Translate finger swipes, single tap (OK), long-press (Menu), and 2-finger tap (Back) into authentic TV D-Pad KeyEvents on mobile.
// - Exit Android touch-mode cleanly before dispatching KeyEvents so the very first swipe or tap moves focus immediately.

package shree.tvhome

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

class TouchDpadSimulator(private val activity: Activity) {

    // Activate automatically on non-TV devices (phones/tablets) while staying completely dormant on real Android TVs.
    private val isMobileTestDevice: Boolean =
        (activity.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType !=
            Configuration.UI_MODE_TYPE_TELEVISION

    private val swipeDistancePx: Float = ViewConfiguration.get(activity).scaledTouchSlop * 2.2f
    private var downX = 0f
    private var downY = 0f
    private var gestureHandled = false
    private var twoFingerPointerSeen = false

    private val gestureDetector = GestureDetector(
        activity,
        object : GestureDetector.SimpleOnGestureListener() {

            // Trigger TV Remote OK (DPAD_CENTER) on single tap.
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (gestureHandled) return true
                gestureHandled = true
                dispatchRemoteKey(KeyEvent.KEYCODE_DPAD_CENTER, isLongPress = false)
                return true
            }

            // Trigger TV Remote OK Long-Press (opens app options menu on focused tile).
            override fun onLongPress(e: MotionEvent) {
                if (gestureHandled) return
                gestureHandled = true
                dispatchRemoteKey(KeyEvent.KEYCODE_DPAD_CENTER, isLongPress = true)
            }
        }
    )

    // Intercepts Activity touch events to convert swipes and taps into D-Pad navigation on mobile.
    fun onDispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isMobileTestDevice) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                gestureHandled = false
                twoFingerPointerSeen = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount >= 2) {
                    twoFingerPointerSeen = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!gestureHandled && !twoFingerPointerSeen) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dx) >= swipeDistancePx || abs(dy) >= swipeDistancePx) {
                        gestureHandled = true
                        val keyCode = if (abs(dx) > abs(dy)) {
                            if (dx > 0f) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                        } else {
                            if (dy > 0f) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
                        }
                        dispatchRemoteKey(keyCode, isLongPress = false)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (twoFingerPointerSeen && !gestureHandled) {
                    gestureHandled = true
                    dispatchRemoteKey(KeyEvent.KEYCODE_BACK, isLongPress = false)
                    return true
                }
            }
        }

        gestureDetector.onTouchEvent(ev)
        return gestureHandled && (ev.actionMasked == MotionEvent.ACTION_MOVE || ev.actionMasked == MotionEvent.ACTION_UP)
    }

    // Ensures a focus target exists (bypassing Android touch-mode swallow) and dispatches synthetic KeyEvents.
    private fun dispatchRemoteKey(keyCode: Int, isLongPress: Boolean) {
        val decor = activity.window?.decorView ?: return
        if (decor.findFocus() == null) {
            FocusRouter.findFirstFocusable(decor)?.requestFocus()
        }

        val now = SystemClock.uptimeMillis()
        val flags = if (isLongPress) KeyEvent.FLAG_LONG_PRESS else 0
        val repeatCount = if (isLongPress) 1 else 0
        val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, repeatCount, 0, -1, 0, flags)
        activity.dispatchKeyEvent(downEvent)

        val upEvent = KeyEvent(now, now + 24L, KeyEvent.ACTION_UP, keyCode, 0, 0, -1, 0, 0)
        activity.dispatchKeyEvent(upEvent)
    }
}
