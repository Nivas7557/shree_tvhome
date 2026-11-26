// Important instructions:
// - Never force requestFocus() calls; allow Android's native D-Pad focus search to move naturally.
// - Allow horizontal D-Pad key handling on sliders (SeekBar or pill_slider) without focus traps.

package shree.tvhome

import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar

object MoodPresets {

    // Finds the first visible, enabled, non-touch-only focusable view in a subtree without requesting focus.
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

    // Routes D-Pad keys naturally when a drawer panel is open without forcing focus traps.
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

        // Allow slider views to process left/right D-Pad events directly.
        if (inDrawer && current != null && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (current is SeekBar || current.tag == "pill_slider") {
                return false
            }
        }

        if (event.action != KeyEvent.ACTION_DOWN) return false

        // Let FocusFinder resolve natural sibling or container focus without forced focus jumps.
        val container = when {
            inDrawer -> drawerLayout
            code == KeyEvent.KEYCODE_DPAD_DOWN -> activePanel ?: drawerLayout
            else -> topNavBar
        }

        if (current != null) {
            val dir = when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
                else -> View.FOCUS_DOWN
            }
            val next = FocusFinder.getInstance().findNextFocus(container, current, dir)
            if (next != null && next !== current) {
                return false
            }
            if (inDrawer && code == KeyEvent.KEYCODE_DPAD_UP && activeParentTag.isNotEmpty()) {
                return false
            }
        }

        return false
    }
}
