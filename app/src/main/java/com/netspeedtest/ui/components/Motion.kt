package com.netspeedtest.ui.components

import android.animation.TimeInterpolator
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.PathInterpolator
import com.netspeedtest.ui.theme.Ui
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Motion tokens. Durations stay within 120–350 ms; springs are used sparingly. */
object Motion {
    const val FAST = 140L
    const val MEDIUM = 240L
    const val SLOW = 340L

    /** Standard ease-out for things entering the screen. */
    val EmphasizedDecelerate: TimeInterpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    val Standard: TimeInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** A gentle, slightly under-damped spring (normalised to 0..1 over the animation). */
    val Spring: TimeInterpolator = SpringInterpolator(dampingRatio = 0.72f, stiffness = 380f)
    val SpringSoft: TimeInterpolator = SpringInterpolator(dampingRatio = 0.85f, stiffness = 260f)
}

/**
 * Closed-form damped harmonic oscillator used as an interpolator, so springs run on the
 * standard animator framework (and therefore honour the system animation scale).
 */
class SpringInterpolator(private val dampingRatio: Float, stiffness: Float) : TimeInterpolator {
    private val omega = sqrt(stiffness.toDouble())
    private val duration = 1.0 // time is normalised; stiffness is tuned for ~350 ms

    override fun getInterpolation(input: Float): Float {
        val t = input * duration * SETTLE_SECONDS
        val zeta = dampingRatio.toDouble()
        val value = if (zeta < 1) {
            val wd = omega * sqrt(1 - zeta * zeta)
            1 - exp(-zeta * omega * t) * (cos(wd * t) + zeta * omega / wd * sin(wd * t))
        } else {
            1 - exp(-omega * t) * (1 + omega * t)
        }
        return if (input >= 1f) 1f else value.toFloat()
    }

    private companion object {
        const val SETTLE_SECONDS = 0.55
    }
}

/** Gives any view a physical press response: scales down slightly and springs back. */
fun View.pressScale(ui: Ui, pressedScale: Float = 0.97f, onPressChange: ((Boolean) -> Unit)? = null) {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                onPressChange?.invoke(true)
                if (!ui.reduceMotion) v.animate().scaleX(pressedScale).scaleY(pressedScale)
                    .setDuration(Motion.FAST).setInterpolator(Motion.Standard).start()
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                onPressChange?.invoke(false)
                v.animate().scaleX(1f).scaleY(1f).setDuration(Motion.SLOW).setInterpolator(Motion.Spring).start()
            }
        }
        false // let the normal click handling continue
    }
}

/** Fades and lifts views in one after another (result cards, detail rows). */
fun staggerIn(ui: Ui, views: List<View>, startDelay: Long = 0L, step: Long = 45L) {
    if (ui.reduceMotion) return
    val lift = ui.dp(14f)
    views.forEachIndexed { index, view ->
        view.alpha = 0f
        view.translationY = lift
        view.animate().alpha(1f).translationY(0f)
            .setStartDelay(startDelay + index * step)
            .setDuration(Motion.SLOW)
            .setInterpolator(Motion.EmphasizedDecelerate)
            .start()
    }
}

/** Subtle haptics that respect both the app toggle and the system haptic setting. */
object Haptics {
    var enabled = true

    fun tap(view: View) = perform(view, HapticFeedbackConstants.VIRTUAL_KEY)

    fun confirm(view: View) = perform(
        view,
        if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
    )

    fun reject(view: View) = perform(
        view,
        if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.VIRTUAL_KEY,
    )

    private fun perform(view: View, constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }
}
