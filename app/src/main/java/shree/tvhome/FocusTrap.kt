package shree.tvhome

import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import shree.tvhome.panels.CustomizeMenuBuilder

object FocusTrap {

    fun findFirstFocusableOption(root: View?): View? {
        if (root == null || root.visibility != View.VISIBLE || !root.isEnabled) return null
        val tagStr = root.tag as? String ?: ""
        if (root !is ViewGroup && root.isFocusable && !tagStr.startsWith("header_")) {
            return root
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val match = findFirstFocusableOption(root.getChildAt(i))
                if (match != null) return match
            }
        }
        return null
    }

    fun onPanelOpened(drawerLayout: ViewGroup?, activePanel: ViewGroup?) {
        drawerLayout?.apply {
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            isFocusable = true
            isFocusableInTouchMode = false
        }
        val targetContainer = activePanel ?: drawerLayout ?: return
        targetContainer.apply {
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            isFocusable = true
            isFocusableInTouchMode = false
        }
        targetContainer.post {
            if (!targetContainer.isAttachedToWindow || targetContainer.visibility != View.VISIBLE) return@post
            val firstOption = findFirstFocusableOption(targetContainer)
            if (firstOption != null) {
                firstOption.requestFocus()
            }
        }
    }

    fun onPanelClosed(drawerLayout: ViewGroup?, topNavBar: ViewGroup?, originNavTag: String?) {
        val root = drawerLayout?.rootView
        drawerLayout?.apply {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            clearFocus()
            visibility = View.GONE
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        val anchor = if (!originNavTag.isNullOrEmpty() && topNavBar != null) {
            topNavBar.findViewWithTag<View>(originNavTag) ?: root?.findViewWithTag<View>(originNavTag)
        } else null

        if (anchor != null) {
            anchor.post { anchor.requestFocus() }
        }
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
            val firstOption = findFirstFocusableOption(activePanel ?: drawerLayout)
            if (firstOption != null) {
                firstOption.requestFocus()
                return true
            }
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
                if (parentBtn != null) {
                    parentBtn.requestFocus()
                }
                return true
            }
            return true
        }

        return false
    }
}
