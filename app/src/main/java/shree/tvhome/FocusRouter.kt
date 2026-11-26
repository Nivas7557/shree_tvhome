// Important instructions:
// - Keep D-Pad routing 100% generic across any number of future top bar buttons, panels, and sub-menus.
// - Never hardcode panel names, tags, or fixed positions; discover focusable targets dynamically.

package shree.tvhome

import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import shree.tvhome.panels.InlineStyleSubMenus

object FocusRouter {

    private var lastTopBarFocusedView: View? = null

    // Finds the first visible, enabled focusable child inside any container dynamically.
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

    // Checks dynamically whether targetView resides inside container.
    private fun isDescendantOf(targetView: View?, container: ViewGroup): Boolean {
        var parent = targetView?.parent
        while (parent != null) {
            if (parent === container) return true
            parent = parent.parent
        }
        return false
    }

    // Discovers dynamically if the currently focused view sits inside any inline horizontal sub-menu.
    private fun findInlineSubMenuAncestor(view: View?): ViewGroup? {
        var parent = view?.parent
        while (parent is View) {
            if (parent.tag == "inline_sub_menu") return parent as? ViewGroup
            parent = parent.parent
        }
        return null
    }

    // Maps D-Pad directional keys to Android View focus directions.
    private fun mapKeyToDirection(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
        KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
        else -> null
    }

    // Routes D-Pad keys generically between topNavBar and any open drawerLayout without manual wiring.
    fun handleKeyEvent(
        event: KeyEvent,
        isDrawerOpen: Boolean,
        drawerLayout: ViewGroup,
        topNavBar: ViewGroup,
        activeParentTag: String = "",
        activePanel: ViewGroup? = null
    ): Boolean {
        if (!isDrawerOpen) return false

        val direction = mapKeyToDirection(event.keyCode) ?: return false
        val drawerFocus = drawerLayout.findFocus()
        val inDrawer = drawerFocus != null
        val topFocus = topNavBar.findFocus()
        if (topFocus != null) lastTopBarFocusedView = topFocus
        val current = drawerFocus ?: topFocus

        // Let horizontal sliders (SeekBar or pill_slider) consume LEFT/RIGHT adjustments directly.
        if (inDrawer && current != null && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) {
            if (current is SeekBar || current.tag == "pill_slider") return false
        }

        if (event.action != KeyEvent.ACTION_DOWN) return false

        // Entering drawer from topNavBar (or touch mode) on DPAD_DOWN works automatically for every panel.
        if (!inDrawer && direction == View.FOCUS_DOWN) {
            val target = (activePanel?.let { findFirstFocusable(it) }) ?: findFirstFocusable(drawerLayout)
            if (target != null) {
                target.requestFocus()
                return true
            }
            return false
        }

        if (current == null) return false

        // Generic horizontal-only trap for any inline sub-menu; UP/DOWN delegates to its registered exit handler.
        val inlineSubMenu = if (inDrawer) findInlineSubMenuAncestor(current) else null
        if (inlineSubMenu != null) {
            if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                val nextH = FocusFinder.getInstance().findNextFocus(inlineSubMenu, current, direction)
                if (nextH != null && nextH !== current) nextH.requestFocus()
                return true
            }
            if (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
                if (InlineStyleSubMenus.dispatchExit(inlineSubMenu, direction)) return true
            }
        }

        val ownerContainer = when {
            inDrawer && isDescendantOf(current, drawerLayout) -> drawerLayout
            !inDrawer && isDescendantOf(current, topNavBar) -> topNavBar
            else -> null
        }

        // Let FocusFinder move naturally within the active container whenever a sibling exists.
        if (ownerContainer != null) {
            val nextInRegion = FocusFinder.getInstance().findNextFocus(ownerContainer, current, direction)
            if (nextInRegion != null && nextInRegion !== current) return false
        }

        // At top boundary of any open panel, DPAD_UP automatically returns to the last focused top bar button.
        if (inDrawer && direction == View.FOCUS_UP) {
            val returnTarget = lastTopBarFocusedView?.takeIf { it.isShown && it.isFocusable }
                ?: (activeParentTag.takeIf { it.isNotEmpty() }?.let { topNavBar.findViewWithTag<View>(it) })
                ?: findFirstFocusable(topNavBar)
            if (returnTarget != null) {
                returnTarget.requestFocus()
                return true
            }
        }

        // Keep horizontal edges inside any panel from escaping into background grids.
        if (inDrawer && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) {
            return true
        }

        return false
    }
}
