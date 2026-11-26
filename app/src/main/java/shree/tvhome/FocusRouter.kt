// Important instructions:
// - Route D-Pad keys reliably across TopHeaderBar and all side panels (Quick, Wi-Fi, Alerts, Power, Customize, Sub-panels).
// - Support both Android TV D-Pad remote navigation and mobile touch testing seamlessly.

package shree.tvhome

import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar

object FocusRouter {

    // Finds the first visible, enabled focusable view inside a panel or container.
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

    // Maps D-Pad key codes to Android View focus directions.
    private fun mapKeyToFocusDirection(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
        KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
        else -> null
    }

    // Bridges D-Pad navigation cleanly between topNavBar and any open drawer panel.
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

        // Allow horizontal sliders (SeekBar or pill_slider) to handle LEFT/RIGHT adjustments directly.
        if (inDrawer && current != null && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) {
            if (current is SeekBar || current.tag == "pill_slider") {
                return false
            }
        }

        if (event.action != KeyEvent.ACTION_DOWN) return false

        // If entering from touch mode with no active focus, enter the active panel's first control on DPAD_DOWN.
        if (current == null) {
            if (direction == View.FOCUS_DOWN) {
                val entryTarget = (activePanel?.let { findFirstFocusable(it) }) ?: findFirstFocusable(drawerLayout)
                if (entryTarget != null) {
                    entryTarget.requestFocus()
                    return true
                }
            }
            return false
        }

        val ownerContainer = when {
            inDrawer && isDescendantOf(current, drawerLayout) -> drawerLayout
            !inDrawer && isDescendantOf(current, topNavBar) -> topNavBar
            else -> null
        }

        // Let FocusFinder handle intra-container sibling moves when a valid next target exists inside the same region.
        if (ownerContainer != null) {
            val nextInRegion = FocusFinder.getInstance().findNextFocus(ownerContainer, current, direction)
            if (nextInRegion != null && nextInRegion !== current) {
                return false
            }
        }

        // Cross-region bridge: Pressing DPAD_DOWN from TopHeaderBar enters the active panel's first control directly.
        if (!inDrawer && direction == View.FOCUS_DOWN) {
            val panelFirst = (activePanel?.let { findFirstFocusable(it) }) ?: findFirstFocusable(drawerLayout)
            if (panelFirst != null) {
                panelFirst.requestFocus()
                return true
            }
        }

        // Cross-region bridge: Pressing DPAD_UP at the top edge of any panel returns focus to its parent nav icon.
        if (inDrawer && direction == View.FOCUS_UP) {
            val navTarget = if (activeParentTag.isNotEmpty()) {
                topNavBar.findViewWithTag<View>(activeParentTag)
            } else null
            val fallbackNav = navTarget ?: findFirstFocusable(topNavBar)
            if (fallbackNav != null) {
                fallbackNav.requestFocus()
                return true
            }
        }

        // Keep horizontal edges inside the drawer from escaping into background grids.
        if (inDrawer && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) {
            return true
        }

        return false
    }
}
