// Important instructions:
// - Bypass Android's touch-mode focus lock so finger swipes, taps, and long-presses act like a real TV D-Pad remote on mobile.
// - Automatically stay completely inactive on real Android TV devices.

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
import kotlin.math.abs

class TouchDpadSimulator(private val activity: Activity) {

    // Active automatically on mobile/tablet test devices; disabled on real Android TVs.
    private val isMobileTestDevice: Boolean =
        (activity.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType !=
            Configuration.UI_MODE_TYPE_TELEVISION

    private val swipeThresholdPx: Float = ViewConfiguration.get(activity).scaledTouchSlop * 2.0f
    private var downX = 0f
    private var downY = 0f
    private var gestureConsumed = false
    private var twoFingerTap = false
    private var trackedFocus: View? = null

    private val gestureDetector = GestureDetector(
        activity,
        object : GestureDetector.SimpleOnGestureListener() {

            // Single tap triggers OK / Click on the currently focused tile or button.
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (gestureConsumed) return true
                val target = resolveCurrentFocus() ?: return false
                gestureConsumed = true
                if (!target.performClick()) {
                    dispatchKeyPair(KeyEvent.KEYCODE_DPAD_CENTER, isLongPress = false)
                }
                return true
            }

            // Long press opens the TV context menu (Add to Favorites / Hide App) on the focused tile.
            override fun onLongPress(e: MotionEvent) {
                if (gestureConsumed) return
                val target = resolveCurrentFocus() ?: return
                gestureConsumed = true
                if (!target.performLongClick()) {
                    dispatchKeyPair(KeyEvent.KEYCODE_MENU, isLongPress = false)
                }
            }
        }
    )

    // Intercepts Activity touch events to translate swipes and taps into real TV D-Pad actions.
    fun onDispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isMobileTestDevice) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                gestureConsumed = false
                twoFingerTap = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount >= 2) twoFingerTap = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!gestureConsumed && !twoFingerTap) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dx) >= swipeThresholdPx || abs(dy) >= swipeThresholdPx) {
                        gestureConsumed = true
                        val keyCode = if (abs(dx) > abs(dy)) {
                            if (dx > 0f) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                        } else {
                            if (dy > 0f) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
                        }
                        simulateDirectionalDpad(keyCode)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (twoFingerTap && !gestureConsumed) {
                    gestureConsumed = true
                    dispatchKeyPair(KeyEvent.KEYCODE_BACK, isLongPress = false)
                    return true
                }
            }
        }

        gestureDetector.onTouchEvent(ev)
        return gestureConsumed && (ev.actionMasked == MotionEvent.ACTION_MOVE || ev.actionMasked == MotionEvent.ACTION_UP)
    }

    // Moves focus in the given D-Pad direction even while Android is in Touch Mode.
    private fun simulateDirectionalDpad(keyCode: Int) {
        val current = resolveCurrentFocus()
        if (current == null) {
            val decor = activity.window?.decorView ?: return
            val first = FocusRouter.findFirstFocusable(decor) ?: return
            grantFocusInTouchMode(first)
            return
        }

        // First let Activity's dispatchKeyEvent (FocusRouter & CinemaConveyorShelf) handle custom routing.
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        val handledByCustomRouter = activity.dispatchKeyEvent(down)
        activity.dispatchKeyEvent(KeyEvent(now, now + 16L, KeyEvent.ACTION_UP, keyCode, 0))

        // Sync trackedFocus after custom router or fallback to FocusFinder if Android touch-mode blocked movement.
        val afterFocus = activity.window?.decorView?.findFocus()
        if (afterFocus != null && afterFocus !== current) {
            trackedFocus = afterFocus
            return
        }

        if (!handledByCustomRouter) {
            val dir = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
                else -> View.FOCUS_DOWN
            }
            val rootGroup = activity.window?.decorView as? ViewGroup ?: return
            val next = FocusFinder.getInstance().findNextFocus(rootGroup, current, dir)
            if (next != null && next !== current) {
                grantFocusInTouchMode(next)
            }
        }
    }

    // Returns the active focused view or restores focus to the first focusable item on screen.
    private fun resolveCurrentFocus(): View? {
        val decor = activity.window?.decorView ?: return null
        val live = decor.findFocus() ?: trackedFocus?.takeIf { it.isAttachedToWindow && it.isShown }
        if (live != null) {
            if (!live.hasFocus()) grantFocusInTouchMode(live)
            return live
        }
        val first = FocusRouter.findFirstFocusable(decor)
        if (first != null) grantFocusInTouchMode(first)
        return first
    }

    // Temporarily enables focusableInTouchMode so Android grants real focus during touchscreen simulation.
    private fun grantFocusInTouchMode(target: View) {
        val prev = target.isFocusableInTouchMode
        target.isFocusableInTouchMode = true
        if (target.requestFocus()) {
            trackedFocus = target
        }
        target.isFocusableInTouchMode = prev
    }

    // Dispatches synthetic DOWN + UP KeyEvents for OK, Menu, or Back actions.
    private fun dispatchKeyPair(keyCode: Int, isLongPress: Boolean) {
        val now = SystemClock.uptimeMillis()
        val flags = if (isLongPress) KeyEvent.FLAG_LONG_PRESS else 0
        activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, if (isLongPress) 1 else 0, 0, -1, 0, flags))
        activity.dispatchKeyEvent(KeyEvent(now, now + 20L, KeyEvent.ACTION_UP, keyCode, 0, 0, -1, 0, 0))
    }
}
