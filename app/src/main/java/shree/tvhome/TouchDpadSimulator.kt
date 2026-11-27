// Important instructions:
// - Inject genuine system KeyEvents via Instrumentation on a worker thread so mobile touch simulates a real TV remote.
// - Never call requestFocus() or mutate focus flags; let Android OS, FocusRouter, and shelves handle focus naturally.

package shree.tvhome

import android.app.Activity
import android.app.Instrumentation
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.EditText
import java.util.concurrent.Executors
import kotlin.math.abs

class TouchDpadSimulator(private val activity: Activity) {

    // Active only on non-TV devices (phones/tablets); completely disabled on real Android TVs.
    private val isMobileTestDevice: Boolean =
        (activity.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType !=
            Configuration.UI_MODE_TYPE_TELEVISION

    private val swipeThresholdPx: Float = ViewConfiguration.get(activity).scaledTouchSlop * 2.0f
    private val keyExecutor = Executors.newSingleThreadExecutor()
    private val instrumentation = Instrumentation()

    private var downX = 0f
    private var downY = 0f
    private var swipeFired = false
    private var multiFingerTap = false

    private val gestureDetector = GestureDetector(
        activity,
        object : GestureDetector.SimpleOnGestureListener() {

            // Single tap injects a genuine DPAD_CENTER (OK) hardware key event.
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (swipeFired || multiFingerTap) return true
                injectHardwareKey(KeyEvent.KEYCODE_DPAD_CENTER, longPress = false)
                return true
            }

            // Long press injects a genuine long-held DPAD_CENTER (OK) key event for app options menus.
            override fun onLongPress(e: MotionEvent) {
                if (swipeFired || multiFingerTap) return
                injectHardwareKey(KeyEvent.KEYCODE_DPAD_CENTER, longPress = true)
            }
        }
    )

    // Consumes screen touches on mobile (outside text inputs) and maps gestures to hardware TV remote keys.
    fun onDispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isMobileTestDevice) return false
        if (activity.window?.currentFocus is EditText) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                swipeFired = false
                multiFingerTap = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount >= 2) multiFingerTap = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!swipeFired && !multiFingerTap) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dx) >= swipeThresholdPx || abs(dy) >= swipeThresholdPx) {
                        swipeFired = true
                        val keyCode = if (abs(dx) > abs(dy)) {
                            if (dx > 0f) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                        } else {
                            if (dy > 0f) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
                        }
                        injectHardwareKey(keyCode, longPress = false)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (multiFingerTap && !swipeFired) {
                    injectHardwareKey(KeyEvent.KEYCODE_BACK, longPress = false)
                    return true
                }
            }
        }

        gestureDetector.onTouchEvent(ev)
        return true
    }

    // Sends true OS-level KeyEvents asynchronously so Android exits touch-mode and routes focus natively.
    private fun injectHardwareKey(keyCode: Int, longPress: Boolean) {
        keyExecutor.execute {
            try {
                if (longPress) {
                    val downTime = SystemClock.uptimeMillis()
                    instrumentation.sendKeySync(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0))
                    Thread.sleep(520L)
                    val repeatTime = SystemClock.uptimeMillis()
                    instrumentation.sendKeySync(
                        KeyEvent(downTime, repeatTime, KeyEvent.ACTION_DOWN, keyCode, 1, 0, -1, 0, KeyEvent.FLAG_LONG_PRESS)
                    )
                    instrumentation.sendKeySync(KeyEvent(downTime, repeatTime + 20L, KeyEvent.ACTION_UP, keyCode, 0))
                } else {
                    instrumentation.sendKeyDownUpSync(keyCode)
                }
            } catch (_: Exception) {
            }
        }
    }
}
