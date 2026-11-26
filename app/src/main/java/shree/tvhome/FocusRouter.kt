// Important instructions:
// - Never force requestFocus() calls; rely on Android's native FocusFinder and focusSearch() traversal.
// - Support both Android TV D-Pad remote navigation and mobile touch testing seamlessly without crashes.

package shree.tvhome

import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar

object FocusRouter {

    // Finds the first visible, enabled focusable view in a subtree without calling requestFocus().
    fun findFirstFocusable(root: View): View? {
        if (root.visibility != View.VISIBLE || !root.isEnabled) return null
        if (root.isFocusable) {
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

    // Safely verifies that targetView belongs to container before invoking FocusFinder.
    private fun isDescendantOf(targetView: View?, container: ViewGroup): Boolean {
        var parent = targetView?.parent
        while (parent != null) {
            if (parent === container) return true
            parent = parent.parent
        }
        return false
    }

    // Maps D-Pad key codes to Android View focus directions, returning null for non-directional keys.
    private fun mapKeyToFocusDirection(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
        KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
        else -> null
    }

    // Routes D-Pad keys cleanly across topNavBar and drawerLayout while preserving mobile touch behavior.
    fun handleKeyEvent(
        event: KeyEvent,
        isDrawerOpen: Boolean,
        drawerLayout: ViewGroup,
        topNavBar: ViewGroup,
        activeParentTag: String = "",
        activePanel: ViewGroup? = null
    ): Boolean {
        if (!isDrawerOpen) return false

        val direction = mapKeyToFocusDirection(event.keyCode) ?: return false
        val drawerFocus = drawerLayout.findFocus()
        val inDrawer = drawerFocus != null
        val current = drawerFocus ?: topNavBar.findFocus()

        // Allow horizontal sliders (SeekBar or pill_slider) to consume LEFT/RIGHT D-Pad steps directly.
        if (inDrawer && current != null && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) {
            if (current is SeekBar || current.tag == "pill_slider") {
                return false
            }
        }

        if (event.action != KeyEvent.ACTION_DOWN) return false

        // When entering from touch mode with no active focus, let Android's native system establish initial focus.
        if (current == null) {
            return false
        }

        val ownerContainer = when {
            inDrawer && isDescendantOf(current, drawerLayout) -> drawerLayout
            !inDrawer && isDescendantOf(current, topNavBar) -> topNavBar
            else -> null
        }

        // Check if FocusFinder has a natural sibling inside the active region first.
        if (ownerContainer != null) {
            val nextInRegion = FocusFinder.getInstance().findNextFocus(ownerContainer, current, direction)
            if (nextInRegion != null && nextInRegion !== current) {
                return false
            }
        }

        // Allow natural transition down from topNavBar into the open drawer panel.
        if (!inDrawer && direction == View.FOCUS_DOWN && activePanel != null) {
            return false
        }

        // Allow natural transition up from the top edge of drawerLayout back to topNavBar.
        if (inDrawer && direction == View.FOCUS_UP && activeParentTag.isNotEmpty()) {
            return false
        }

        // Prevent horizontal D-Pad presses at drawer edges from escaping into background grids.
        if (inDrawer && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) {
            return true
        }

        return false
    }
}
