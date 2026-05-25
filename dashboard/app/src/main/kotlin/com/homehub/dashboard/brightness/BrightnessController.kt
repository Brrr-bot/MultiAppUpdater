package com.homehub.dashboard.brightness

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import com.homehub.dashboard.data.AppSettingsRepository
import java.util.Calendar

/**
 * Controls screen dimming via two layers:
 *  1. Window screenBrightness — lowers the actual backlight so the panel isn't
 *     glowing behind a dark overlay.
 *  2. A black overlay view — lets us go darker than the system-minimum backlight
 *     and gives a smooth animated fade.
 *
 * On stop() the window brightness is restored to the system default so other apps
 * (and the launcher) are not affected.
 *
 * Schedule:
 *  - Daytime (before 22:00) → active brightness, overlay invisible
 *  - Night (22:00+) idle    → idle brightness on both backlight and overlay
 *
 * Touch interaction briefly restores active brightness, then fades back after timeout.
 */
class BrightnessController(
    private val activity: Activity,
    private val settingsRepository: AppSettingsRepository
) {
    private val handler = Handler(Looper.getMainLooper())
    private var idleRunnable: Runnable? = null
    private var overlayAnimator: ValueAnimator? = null
    private var backlightAnimator: ValueAnimator? = null
    private var overlay: View? = null

    fun start() {
        overlay = activity.findViewById(com.homehub.dashboard.R.id.dim_overlay)
        applySchedule(animated = false)
    }

    fun onUserInteraction() {
        val settings = settingsRepository.get()
        val activeBrightness = settings.activeBrightness / 100f
        setBacklight(activeBrightness, animMs = 200)
        setOverlayAlpha(0f, animMs = 200)
        idleRunnable?.let(handler::removeCallbacks)
        idleRunnable = Runnable { applySchedule(animated = true) }
            .also { handler.postDelayed(it, settings.idleTimeoutSeconds * 1000L) }
    }

    fun stop() {
        idleRunnable?.let(handler::removeCallbacks)
        overlayAnimator?.cancel()
        backlightAnimator?.cancel()
        // Restore system-managed brightness so other apps are unaffected
        val lp = activity.window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = lp
    }

    /** Called once per minute to re-evaluate the time schedule. */
    fun tick() {
        if (idleRunnable == null) applySchedule(animated = true)
    }

    // ── Schedule ──────────────────────────────────────────────────────────────

    private fun isNighttime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 22
    }

    private fun applySchedule(animated: Boolean) {
        idleRunnable = null
        val settings = settingsRepository.get()
        val fadeMs = if (animated) settings.brightnessFadeMillis else 0
        if (isNighttime()) {
            val idlePct = settings.idleBrightness.coerceIn(0, 100)
            // Backlight: map 0-100% → 0.02-1.0
            val backlightVal = (idlePct / 100f).coerceAtLeast(0.02f)
            // Overlay: maps inversely — 0% brightness → near-black overlay, 100% → no overlay
            val overlayAlpha = (1f - idlePct / 100f) * 0.97f
            setBacklight(backlightVal, fadeMs)
            setOverlayAlpha(overlayAlpha, fadeMs)
        } else {
            val activePct = settings.activeBrightness.coerceIn(1, 100)
            setBacklight(activePct / 100f, fadeMs)
            setOverlayAlpha(0f, fadeMs)
        }
    }

    // ── Backlight ─────────────────────────────────────────────────────────────

    private fun setBacklight(target: Float, animMs: Int) {
        backlightAnimator?.cancel()
        val current = activity.window.attributes.screenBrightness
            .takeIf { it >= 0f } ?: 1f
        if (animMs <= 0) {
            applyBacklight(target)
            return
        }
        backlightAnimator = ValueAnimator.ofFloat(current, target).apply {
            duration = animMs.toLong()
            addUpdateListener { applyBacklight(it.animatedValue as Float) }
            start()
        }
    }

    private fun applyBacklight(value: Float) {
        val lp = activity.window.attributes
        lp.screenBrightness = value.coerceIn(0.02f, 1f)
        activity.window.attributes = lp
    }

    // ── Overlay ───────────────────────────────────────────────────────────────

    private fun setOverlayAlpha(target: Float, animMs: Int) {
        val view = overlay ?: return
        overlayAnimator?.cancel()
        if (animMs <= 0) {
            view.alpha = target
            return
        }
        overlayAnimator = ValueAnimator.ofFloat(view.alpha, target).apply {
            duration = animMs.toLong()
            addUpdateListener { view.alpha = it.animatedValue as Float }
            start()
        }
    }
}
