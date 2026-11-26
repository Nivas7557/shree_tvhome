// Important instructions:
// - Route D-Pad keys reliably across TopHeaderBar and every side panel (Quick, Wi-Fi, Alerts, Power, Customize, Sub-panels).
// - Enforce horizontal-only navigation inside inline sub-menus so Up/Down cleanly exits to previous/next parent row.

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
        if (root.isFocusable) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findFirstFocusable(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    // Safely verifies whether targetView is a descendant of container.
    private fun isDescendantOf(targetView: View?, container: ViewGroup): Boolean {
        var parent = targetView?.parent
        while (parent != null) {
            if (parent === container) return true
            parent = parent.parent
        }
        return false
    }

    // Finds an ancestor marked as an inline horizontal sub-menu if present.
    private fun findInlineSubMenuAncestor(view: View?): ViewGroup? {
        var parent = view?.parent
        while (parent is View) {
            if (parent.tag == "inline_sub_menu") return parent as? ViewGroup
            parent = parent.parent
        }
        return null
    }

    // Maps D-Pad key codes to Android View focus directions.
    private fun mapKeyToFocusDirection(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
        KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
        else -> null
    }

    // Routes D-Pad keys across topNavBar, open side panels, and horizontal inline sub-menus.
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

        // Allow horizontal sliders (SeekBar or pill_slider) to consume LEFT/RIGHT steps directly.
        if (inDrawer && current != null && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) {
            if (current is SeekBar || current.tag == "pill_slider") {
                return false
            }
        }

        if (event.action != KeyEvent.ACTION_DOWN) return false

        // Cross-region entry: Pressing DPAD_DOWN while on TopHeaderBar jumps directly into the open panel's first item.
        if (!inDrawer && direction == View.FOCUS_DOWN) {
            val panelFirst = (activePanel?.let { findFirstFocusable(it) }) ?: findFirstFocusable(drawerLayout)
            if (panelFirst != null) {
                panelFirst.requestFocus()
                return true
            }
            return false
        }

        if (current == null) return false

        // Inside an inline horizontal sub-menu: allow LEFT/RIGHT only, and delegate UP/DOWN to exit cleanly.
        val inlineSubMenu = if (inDrawer) findInlineSubMenuAncestor(current) else null
        if (inlineSubMenu != null) {
            if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                val nextH = FocusFinder.getInstance().findNextFocus(inlineSubMenu, current, direction)
                if (nextH != null && nextH !== current) {
                    nextH.requestFocus()
                }
                return true
            }
            if (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
                val handler = inlineSubMenu.getTag(R.id.tag_submenu_exit_handler) as? ((Int) -> Boolean)
                if (handler != null && handler(direction)) {
                    return true
                }
            }
        }

        val ownerContainer = when {
            inDrawer && isDescendantOf(current, drawerLayout) -> drawerLayout
            !inDrawer && isDescendantOf(current, topNavBar) -> topNavBar
            else -> null
        }

        if (ownerContainer != null) {
            val nextInRegion = FocusFinder.getInstance().findNextFocus(ownerContainer, current, direction)
            if (nextInRegion != null && nextInRegion !== current) {
                return false
            }
        }

        // Pressing DPAD_UP at the top edge of any panel returns focus cleanly to its TopHeaderBar icon.
        if (inDrawer && direction == View.FOCUS_UP) {
            val navTarget = if (activeParentTag.isNotEmpty()) topNavBar.findViewWithTag<View>(activeParentTag) else null
            val fallbackNav = navTarget ?: findFirstFocusable(topNavBar)
            if (fallbackNav != null) {
                fallbackNav.requestFocus()
                return true
            }
        }

        // Prevent horizontal edges inside the drawer from escaping into background grids.
        if (inDrawer && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) {
            return true
        }

        return false
    }
}
