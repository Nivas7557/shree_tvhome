// Important instructions:
// - Act as a full-screen virtual TV remote trackpad on mobile so accidental normal touches never trigger clicks.
// - Maintain a persistent virtual focus cursor with isFocusableInTouchMode enabled so unlimited swipes work smoothly.

package shree.tvhome

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.view.FocusFinder
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EditText
import kotlin.math.abs

class TouchDpadSimulator(private val activity: Activity) {

    // Active automatically on non-TV devices (phones/tablets); completely disabled on real Android TVs.
    private val isMobileTestDevice: Boolean =
        (activity.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType !=
            Configuration.UI_MODE_TYPE_TELEVISION

    private val swipeThresholdPx: Float = ViewConfiguration.get(activity).scaledTouchSlop * 2.0f
    private var downX = 0f
    private var downY = 0f
    private var swipeFired = false
    private var multiFingerTap = false
    private var virtualFocus: View? = null

    private val gestureDetector = GestureDetector(
        activity,
        object : GestureDetector.SimpleOnGestureListener() {

            // Single tap triggers OK click on the currently highlighted TV focus item.
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (swipeFired || multiFingerTap) return true
                val target = ensureActiveFocus() ?: return true
                if (!target.performClick()) {
                    dispatchHardwareKey(KeyEvent.KEYCODE_DPAD_CENTER, isLongPress = false)
                }
                return true
            }

            // Long press triggers TV Remote Menu / Long-OK on the currently highlighted tile.
            override fun onLongPress(e: MotionEvent) {
                if (swipeFired || multiFingerTap) return
                val target = ensureActiveFocus() ?: return
                if (!target.performLongClick()) {
                    dispatchHardwareKey(KeyEvent.KEYCODE_MENU, isLongPress = false)
                }
            }
        }
    )

    // Consumes all screen touches on mobile (except text inputs) to prevent accidental touch clicks and drive D-Pad.
    fun onDispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isMobileTestDevice) return false

        // Allow direct typing if an EditText dialog (e.g., Greeting or Profile name) is open.
        if (activity.window?.currentFocus is EditText) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                swipeFired = false
                multiFingerTap = false
                ensureActiveFocus()
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
                        moveVirtualFocus(keyCode)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (multiFingerTap && !swipeFired) {
                    dispatchHardwareKey(KeyEvent.KEYCODE_BACK, isLongPress = false)
                    return true
                }
            }
        }

        gestureDetector.onTouchEvent(ev)
        // Consume every touch event on mobile so normal touch never conflicts with D-Pad simulation.
        return true
    }

    // Moves the virtual TV focus cursor in the requested D-Pad direction across shelves, panels, and sub-menus.
    private fun moveVirtualFocus(keyCode: Int) {
        val current = ensureActiveFocus() ?: return
        val now = SystemClock.uptimeMillis()

        // Step 1: Route through Activity.dispatchKeyEvent (handles FocusRouter & CinemaConveyorShelf).
        val handledByRouter = activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        activity.dispatchKeyEvent(KeyEvent(now, now + 14L, KeyEvent.ACTION_UP, keyCode, 0))

        val postRouterFocus = activity.window?.decorView?.findFocus()
        if (postRouterFocus != null && postRouterFocus !== current) {
            applyTouchModeFocus(postRouterFocus)
            return
        }

        // Step 2: If router delegated to standard spatial search, resolve next sibling via FocusFinder.
        if (!handledByRouter) {
            val dir = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
                else -> View.FOCUS_DOWN
            }
            val rootGroup = activity.window?.decorView as? ViewGroup ?: return
            val next = FocusFinder.getInstance().findNextFocus(rootGroup, current, dir)
            if (next != null && next !== current) {
                applyTouchModeFocus(next)
            }
        }
    }

    // Ensures a valid view currently holds active focus and returns it.
    private fun ensureActiveFocus(): View? {
        val decor = activity.window?.decorView ?: return null
        val live = decor.findFocus()
        if (live != null && live.isShown && live.isFocusable) {
            virtualFocus = live
            return live
        }
        val saved = virtualFocus?.takeIf { it.isAttachedToWindow && it.isShown && it.isFocusable }
        if (saved != null) {
            applyTouchModeFocus(saved)
            return saved
        }
        val first = FocusRouter.findFirstFocusable(decor)
        if (first != null) applyTouchModeFocus(first)
        return first
    }

    // Keeps isFocusableInTouchMode enabled on the active cursor so Android never drops focus between swipes.
    private fun applyTouchModeFocus(target: View) {
        if (virtualFocus !== target) {
            virtualFocus?.isFocusableInTouchMode = false
        }
        target.isFocusableInTouchMode = true
        target.requestFocus()
        virtualFocus = target
    }

    // Dispatches synthetic KeyEvents for OK, Menu, or Back actions.
    private fun dispatchHardwareKey(keyCode: Int, isLongPress: Boolean) {
        val now = SystemClock.uptimeMillis()
        val flags = if (isLongPress) KeyEvent.FLAG_LONG_PRESS else 0
        activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, if (isLongPress) 1 else 0, 0, -1, 0, flags))
        activity.dispatchKeyEvent(KeyEvent(now, now + 18L, KeyEvent.ACTION_UP, keyCode, 0, 0, -1, 0, 0))
    }
}
