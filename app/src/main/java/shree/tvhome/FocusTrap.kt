package shree.tvhome

import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import shree.tvhome.panels.CustomizeMenuBuilder

object FocusTrap {

    fun findFirstFocusable(root: View?): View? {
        if (root == null || root.visibility != View.VISIBLE || !root.isEnabled) return null
        if (root !is ViewGroup && root.isFocusable) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findFirstFocusable(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    fun onPanelOpened(drawerLayout: ViewGroup?, activePanel: ViewGroup?) {
        val target = activePanel ?: drawerLayout ?: return
        target.post {
            if (!target.isAttachedToWindow || target.visibility != View.VISIBLE) return@post
            val first = findFirstFocusable(target)
            first?.requestFocus()
        }
    }

    fun onPanelClosed(drawerLayout: ViewGroup?, topNavBar: ViewGroup?, originNavTag: String?) {
        val root = drawerLayout?.rootView
        drawerLayout?.apply {
            clearFocus()
            visibility = View.GONE
        }
        val anchor = if (!originNavTag.isNullOrEmpty()) {
            topNavBar?.findViewWithTag<View>(originNavTag) ?: root?.findViewWithTag<View>(originNavTag)
        } else null
        anchor?.post { anchor.requestFocus() }
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
        val current = drawerFocus ?: topNavBar.findFocus() ?: return false

        if (inDrawer && current is SeekBar && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            return false
        }

        if (inDrawer) {
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

        if (!inDrawer && code == KeyEvent.KEYCODE_DPAD_DOWN) {
            val first = findFirstFocusable(activePanel ?: drawerLayout)
            first?.requestFocus()
            return true
        }

        val container = if (inDrawer) drawerLayout else topNavBar
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

        if (inDrawer && code == KeyEvent.KEYCODE_DPAD_UP && activeParentTag.isNotEmpty()) {
            topNavBar.findViewWithTag<View>(activeParentTag)?.requestFocus()
            return true
        }

        return true
    }
}
