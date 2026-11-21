package shree.tvhome

import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import shree.tvhome.panels.CustomizeMenuBuilder

object FocusTrap {

    fun findFirstFocusable(root: View): View? {
        if (root.visibility != View.VISIBLE || !root.isEnabled) return null
        val isLeafFocusable = root !is ViewGroup && (root.isFocusable || root.isClickable)
        val isContainerFocusable = root is ViewGroup && root.isFocusable && !root.isFocusableInTouchMode && root.childCount == 0
        if (isLeafFocusable || isContainerFocusable) {
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

    fun focusFirstInPanel(panel: ViewGroup?, fallbackContainer: ViewGroup? = null): Boolean {
        if (panel != null && panel.findFocus() != null) return true
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

    fun handleKeyEvent(
        event: KeyEvent,
        isDrawerOpen: Boolean,
        drawerLayout: ViewGroup,
        topNavBar: ViewGroup,
        activeParentTag: String = "",
        activePanel: ViewGroup? = null
    ): Boolean {
        if (!isDrawerOpen || event.action != KeyEvent.ACTION_DOWN) return false

        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_DPAD_LEFT &&
            code != KeyEvent.KEYCODE_DPAD_RIGHT &&
            code != KeyEvent.KEYCODE_DPAD_UP &&
            code != KeyEvent.KEYCODE_DPAD_DOWN
        ) return false

        val drawerFocus = drawerLayout.findFocus()
        val inDrawer = drawerFocus != null
        val current = drawerFocus ?: topNavBar.findFocus()

        if (inDrawer && current != null) {
            if (current is SeekBar && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                return false
            }

            val tag = current.tag as? String ?: ""
            if (tag.startsWith("pill_")) {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    val subRow = current.parent as? ViewGroup ?: return true
                    val dir = if (code == KeyEvent.KEYCODE_DPAD_LEFT) View.FOCUS_LEFT else View.FOCUS_RIGHT
                    FocusFinder.getInstance().findNextFocus(subRow, current, dir)?.requestFocus()
                    return true
                }
                if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    CustomizeMenuBuilder.closeSubMenuAndNavigate(code == KeyEvent.KEYCODE_DPAD_DOWN)
                    return true
                }
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
            if (next != null) {
                next.requestFocus()
                return true
            }

            if (inDrawer && code == KeyEvent.KEYCODE_DPAD_UP) {
                val parentBtn = if (activeParentTag.isNotEmpty()) {
                    topNavBar.findViewWithTag<View>(activeParentTag)
                } else null
                val fallbackNav = topNavBar.findFocus() ?: topNavBar.getChildAt(0)
                val targetNav = parentBtn ?: fallbackNav
                targetNav?.post { targetNav.requestFocus() }
                return true
            }
            return true
        }

        return focusFirstInPanel(activePanel, drawerLayout)
    }
}
