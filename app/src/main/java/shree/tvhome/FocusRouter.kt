// Important instructions:
// - Route D-Pad focus naturally without forced focus traps or hardcoded tag checks.
// - Allow horizontal D-Pad adjustment on slider controls (SeekBar or pill_slider tag) without trapping focus.

package shree.tvhome

import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar

object FocusRouter {

    // Finds the first visible, enabled, D-Pad-focusable view in a view subtree.
    fun findFirstFocusable(root: View): View? {
        if (root.visibility != View.VISIBLE || !root.isEnabled) return null
        if (root.isFocusable && !root.isFocusableInTouchMode) {
            return root
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findFirstFocusable(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    // Moves focus to the first focusable control inside the active panel or drawer container.
    fun focusFirstInPanel(panel: ViewGroup?, fallbackContainer: ViewGroup? = null): Boolean {
        val target = (panel?.let { findFirstFocusable(it) })
            ?: (fallbackContainer?.let { findFirstFocusable(it) })
            ?: return false
        target.post {
            if (target.isAttachedToWindow && target.visibility == View.VISIBLE) {
                target.requestFocus()
            }
        }
        return true
    }

    // Handles directional D-Pad movement within open drawers and transitions back to the header.
    fun handleKeyEvent(
        event: KeyEvent,
        isDrawerOpen: Boolean,
        drawerLayout: ViewGroup,
        topNavBar: ViewGroup,
        activeParentTag: String = "",
        activePanel: ViewGroup? = null
    ): Boolean {
        if (!isDrawerOpen) return false

        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_DPAD_LEFT &&
            code != KeyEvent.KEYCODE_DPAD_RIGHT &&
            code != KeyEvent.KEYCODE_DPAD_UP &&
            code != KeyEvent.KEYCODE_DPAD_DOWN
        ) return false

        val drawerFocus = drawerLayout.findFocus()
        val inDrawer = drawerFocus != null
        val current = drawerFocus ?: topNavBar.findFocus()

        // Let slider controls (SeekBar or custom pill_slider) handle left/right D-Pad directly.
        if (inDrawer && current != null && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (current is SeekBar || current.tag == "pill_slider") {
                return false
            }
        }

        if (event.action != KeyEvent.ACTION_DOWN) return false

        if (inDrawer && current != null) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                val dir = if (code == KeyEvent.KEYCODE_DPAD_LEFT) View.FOCUS_LEFT else View.FOCUS_RIGHT
                val rowParent = current.parent as? ViewGroup
                val nextSibling = rowParent?.let { FocusFinder.getInstance().findNextFocus(it, current, dir) }
                if (nextSibling != null && nextSibling !== current) {
                    nextSibling.requestFocus()
                }
                return true
            }
        }

        if (code == KeyEvent.KEYCODE_DPAD_DOWN && !inDrawer && current != null) {
            focusFirstInPanel(activePanel, drawerLayout)
            return true
        }

        val container = if (inDrawer) drawerLayout else topNavBar
        if (current != null) {
            val dir = when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
                else -> View.FOCUS_DOWN
            }
            val next = FocusFinder.getInstance().findNextFocus(container, current, dir)
            if (next != null && next !== current) {
                next.requestFocus()
                return true
            }

            if (inDrawer && code == KeyEvent.KEYCODE_DPAD_UP) {
                val parentBtn = if (activeParentTag.isNotEmpty()) {
                    topNavBar.findViewWithTag<View>(activeParentTag)
                } else {
                    findFirstFocusable(topNavBar)
                }
                parentBtn?.requestFocus()
                return true
            }
            return true
        }

        return focusFirstInPanel(activePanel, drawerLayout)
    }
}
