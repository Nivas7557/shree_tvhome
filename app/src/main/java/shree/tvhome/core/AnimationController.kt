// Important instructions:
// - Centralize motion presets, interpolators, durations, and panel/focus animations in one place.
// - Always cancel prior view animations before starting new ones to keep frame pacing smooth on low-RAM TVs.

package shree.tvhome.core

import android.animation.TimeInterpolator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object AnimationController {

    data class MotionStyleSpec(
        val key: String,
        val label: String,
        val focusInterpolator: TimeInterpolator,
        val previewInterpolator: TimeInterpolator,
        val focusEnterMs: Long,
        val focusExitMs: Long,
        val previewMs: Long,
        val panelEnterMs: Long,
        val panelExitMs: Long
    )

    private val OVERSHOOT = OvershootInterpolator(1.55f)
    private val JELLY_POP = OvershootInterpolator(2.1f)
    private val SILK_EASE = DecelerateInterpolator(2.2f)

    // Single source of truth for all available motion styles.
    val STYLES = listOf(
        MotionStyleSpec("elastic", "Elastic", OVERSHOOT, OVERSHOOT, 260L, 170L, 760L, 400L, 340L),
        MotionStyleSpec("jelly", "Jelly", JELLY_POP, JELLY_POP, 340L, 200L, 760L, 440L, 340L),
        MotionStyleSpec("flip3d", "3D Flip", OVERSHOOT, OVERSHOOT, 320L, 210L, 760L, 440L, 320L),
        MotionStyleSpec("book", "Book Open", OVERSHOOT, OVERSHOOT, 320L, 210L, 760L, 440L, 320L),
        MotionStyleSpec("levitate", "Levitate", SILK_EASE, SILK_EASE, 260L, 170L, 740L, 400L, 300L),
        MotionStyleSpec("silk", "Silk Glide", SILK_EASE, SILK_EASE, 280L, 190L, 720L, 400L, 340L),
        MotionStyleSpec("swing", "Swing", OVERSHOOT, OVERSHOOT, 260L, 170L, 760L, 440L, 320L)
    )

    private val styleMap by lazy { STYLES.associateBy { it.key } }

    // Resolves a motion style spec by key with a safe default fallback.
    fun getSpec(style: String): MotionStyleSpec = styleMap[style] ?: STYLES.first()

    // Returns the focus interpolator for the given motion style.
    fun getInterpolator(style: String): TimeInterpolator = getSpec(style).focusInterpolator

    // Sets perspective camera distance scaled to device density for 3D transforms.
    private fun ensureCameraDistance(view: View) {
        val d = view.resources.displayMetrics.density
        view.cameraDistance = 16000f * d
    }

    // Clears running animations and resets visual transforms back to neutral defaults.
    fun resetTransform(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.rotationX = 0f
        view.rotationY = 0f
        view.translationX = 0f
        view.translationY = 0f
    }

    // Animates focus entry or exit on focusable controls using the active motion style.
    fun animateFocus(
        view: View,
        focused: Boolean,
        style: String,
        scale: Float = 1.08f,
        elevationDp: Float = 8f,
        dirX: Float = 0f,
        dirY: Float = 0f
    ) {
        view.animate().cancel()
        ensureCameraDistance(view)
        val spec = getSpec(style)
        val interp = spec.focusInterpolator
        val dur = if (focused) spec.focusEnterMs else spec.focusExitMs
        val tz = if (focused) LayoutMetrics.dpF(view.context, elevationDp) else 0f
        val d = view.resources.displayMetrics.density

        // Add subtle directional push when focus arrives from a navigation step.
        if (focused && (dirX != 0f || dirY != 0f)) {
            view.translationX = -dirX * 10f * d
            view.translationY = -dirY * 10f * d
        }

        when (spec.key) {
            "jelly" -> {
                view.pivotX = view.width / 2f
                view.pivotY = view.height / 2f
                if (focused) {
                    view.scaleX = 0.94f
                    view.scaleY = 1.06f
                }
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .translationX(0f).translationY(0f).rotationX(0f).rotationY(0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
            "flip3d" -> {
                view.pivotX = view.width / 2f
                view.pivotY = view.height / 2f
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .rotationY(if (focused) 8f else 0f)
                    .rotationX(if (focused) -4f else 0f)
                    .translationX(0f).translationY(0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
            "book" -> {
                view.pivotX = 0f
                view.pivotY = view.height / 2f
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .rotationY(if (focused) -9f else 0f)
                    .rotationX(0f).translationX(0f).translationY(0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
            "levitate" -> {
                view.pivotX = view.width / 2f
                view.pivotY = view.height / 2f
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .translationY(if (focused) -6f * d else 0f)
                    .translationX(0f).rotationX(0f).rotationY(0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
            "swing" -> {
                view.pivotX = view.width * 0.5f
                view.pivotY = 0f
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .rotationX(if (focused) 6f else 0f)
                    .rotationY(0f).translationX(0f).translationY(0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
            else -> {
                view.pivotX = view.width / 2f
                view.pivotY = view.height / 2f
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .translationX(0f).translationY(0f).rotationX(0f).rotationY(0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
        }
    }

    // Plays a motion preview on a panel when the user picks or switches motion styles.
    fun playDemoPreview(panel: View, style: String) {
        resetTransform(panel)
        ensureCameraDistance(panel)
        val spec = getSpec(style)
        panel.translationZ = 0f
        val w = maxOf(panel.width, LayoutMetrics.dp(panel.context, Themes.PANEL_MIN_WIDTH_DP + 30)).toFloat()
        val h = maxOf(panel.height, LayoutMetrics.dp(panel.context, 420)).toFloat()
        val d = panel.resources.displayMetrics.density

        when (spec.key) {
            "jelly" -> {
                panel.pivotX = w / 2f
                panel.pivotY = h / 2f
                panel.scaleX = 0.86f
                panel.scaleY = 1.12f
                panel.animate().scaleX(1f).scaleY(1f)
                    .setDuration(spec.previewMs).setInterpolator(spec.previewInterpolator).start()
            }
            "flip3d" -> {
                panel.pivotX = w / 2f
                panel.pivotY = h / 2f
                panel.rotationY = 36f
                panel.scaleX = 0.90f
                panel.scaleY = 0.90f
                panel.animate().rotationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(spec.previewMs).setInterpolator(spec.previewInterpolator).start()
            }
            "book" -> {
                panel.pivotX = 0f
                panel.pivotY = h / 2f
                panel.rotationY = 34f
                panel.alpha = 0.72f
                panel.animate().rotationY(0f).alpha(1f)
                    .setDuration(spec.previewMs).setInterpolator(spec.previewInterpolator).start()
            }
            "levitate" -> {
                panel.translationY = 44f * d
                panel.alpha = 0.65f
                panel.animate().translationY(0f).alpha(1f)
                    .setDuration(spec.previewMs).setInterpolator(spec.previewInterpolator).start()
            }
            "swing" -> {
                panel.pivotX = w * 0.5f
                panel.pivotY = 0f
                panel.rotationX = -22f
                panel.scaleY = 0.92f
                panel.animate().rotationX(0f).scaleY(1f)
                    .setDuration(spec.previewMs).setInterpolator(spec.previewInterpolator).start()
            }
            "silk" -> {
                panel.translationX = 65f * d
                panel.alpha = 0.60f
                panel.animate().translationX(0f).alpha(1f)
                    .setDuration(spec.previewMs).setInterpolator(spec.previewInterpolator).start()
            }
            else -> {
                panel.translationX = 85f * d
                panel.scaleX = 0.94f
                panel.animate().translationX(0f).scaleX(1f)
                    .setDuration(spec.previewMs).setInterpolator(spec.previewInterpolator).start()
            }
        }
    }

    // Animates the side drawer in while hiding the ambient clock widget smoothly.
    fun animatePanelEnter(panel: View, stageWidget: View?, widthPx: Int, style: String, onDone: () -> Unit) {
        resetTransform(panel)
        ensureCameraDistance(panel)
        val spec = getSpec(style)

        // Hide the background stage widget cleanly while the drawer is open.
        stageWidget?.let {
            it.animate().cancel()
            ensureCameraDistance(it)
            it.pivotX = it.width / 2f
            it.pivotY = it.height / 2f
            it.animate()
                .alpha(0f)
                .scaleX(0.90f).scaleY(0.90f)
                .rotationY(if (spec.key == "flip3d") -24f else 0f)
                .setDuration(260L)
                .withEndAction {
                    it.visibility = View.GONE
                    resetTransform(it)
                }
                .start()
        }

        val interp = spec.focusInterpolator
        val safeW = maxOf(widthPx, LayoutMetrics.dp(panel.context, Themes.PANEL_MIN_WIDTH_DP)).toFloat()
        val safeH = maxOf(panel.height, LayoutMetrics.dp(panel.context, 420)).toFloat()
        val d = panel.resources.displayMetrics.density

        when (spec.key) {
            "jelly" -> {
                panel.pivotX = safeW / 2f
                panel.pivotY = safeH / 2f
                panel.scaleX = 0.85f
                panel.scaleY = 1.12f
                panel.alpha = 0f
                panel.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(spec.panelEnterMs).setInterpolator(interp).withEndAction(onDone).start()
            }
            "flip3d" -> {
                panel.pivotX = safeW / 2f
                panel.pivotY = safeH / 2f
                panel.rotationY = 34f
                panel.alpha = 0f
                panel.scaleX = 0.92f
                panel.scaleY = 0.92f
                panel.animate().rotationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(spec.panelEnterMs).setInterpolator(interp).withEndAction(onDone).start()
            }
            "book" -> {
                panel.pivotX = 0f
                panel.pivotY = safeH / 2f
                panel.rotationY = 32f
                panel.alpha = 0f
                panel.animate().rotationY(0f).alpha(1f)
                    .setDuration(spec.panelEnterMs).setInterpolator(interp).withEndAction(onDone).start()
            }
            "levitate" -> {
                panel.translationY = 48f * d
                panel.alpha = 0f
                panel.animate().translationY(0f).alpha(1f)
                    .setDuration(spec.panelEnterMs).setInterpolator(interp).withEndAction(onDone).start()
            }
            "swing" -> {
                panel.pivotX = safeW * 0.5f
                panel.pivotY = 0f
                panel.rotationX = -20f
                panel.alpha = 0f
                panel.animate().rotationX(0f).alpha(1f)
                    .setDuration(spec.panelEnterMs).setInterpolator(interp).withEndAction(onDone).start()
            }
            else -> {
                panel.translationX = safeW + 30f
                panel.alpha = 0f
                panel.animate().translationX(0f).alpha(1f)
                    .setDuration(spec.panelEnterMs).setInterpolator(interp).withEndAction(onDone).start()
            }
        }
    }

    // Animates the side drawer out and restores the ambient clock widget smoothly.
    fun animatePanelExit(panel: View, stageWidget: View?, widthPx: Int, style: String, onDone: () -> Unit) {
        panel.animate().cancel()
        ensureCameraDistance(panel)
        val spec = getSpec(style)

        stageWidget?.let {
            it.animate().cancel()
            resetTransform(it)
            ensureCameraDistance(it)
            it.visibility = View.VISIBLE
            it.alpha = 0f
            it.scaleX = 0.92f
            it.scaleY = 0.92f
            it.rotationY = if (spec.key == "flip3d") 24f else 0f
            it.animate().alpha(1f).scaleX(1f).scaleY(1f).rotationY(0f)
                .setDuration(380L).setInterpolator(spec.focusInterpolator).start()
        }

        val safeW = maxOf(widthPx, LayoutMetrics.dp(panel.context, Themes.PANEL_MIN_WIDTH_DP)).toFloat()
        val finish = { resetTransform(panel); onDone() }

        when (spec.key) {
            "flip3d" -> {
                panel.pivotX = safeW / 2f
                panel.animate().rotationY(-34f).alpha(0f).scaleX(0.92f).scaleY(0.92f)
                    .setDuration(spec.panelExitMs).withEndAction(finish).start()
            }
            "book" -> {
                panel.pivotX = 0f
                panel.animate().rotationY(32f).alpha(0f)
                    .setDuration(spec.panelExitMs).withEndAction(finish).start()
            }
            "levitate" -> {
                panel.animate().translationY(42f).alpha(0f)
                    .setDuration(spec.panelExitMs).withEndAction(finish).start()
            }
            "swing" -> {
                panel.pivotX = safeW * 0.5f
                panel.pivotY = 0f
                panel.animate().rotationX(-18f).alpha(0f)
                    .setDuration(spec.panelExitMs).withEndAction(finish).start()
            }
            else -> {
                panel.animate().translationX(safeW + 30f).alpha(0f)
                    .setDuration(spec.panelExitMs).setInterpolator(spec.focusInterpolator).withEndAction(finish).start()
            }
        }
    }
}
