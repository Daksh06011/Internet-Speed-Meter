package com.netspeedtest.ui.components

import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.netspeedtest.R

/**
 * The branded intro: the animated gauge on a navy disc, played every time the app is
 * opened — cold start, from memory, or from recents. It is laid out exactly like the
 * system launch screen (160 dp disc, 240 dp icon, centred on the window), so on a cold
 * start the hand-off from the system splash is seamless.
 */
class LaunchOverlay(context: Context) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val disc = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(context.getColor(R.color.icon_background))
        }
    }
    private val icon = ImageView(context)
    private val animation = context.getDrawable(R.drawable.ic_splash_animated) as AnimatedVectorDrawable
    private var finishing: Runnable? = null

    val isPlaying: Boolean get() = visibility == VISIBLE

    init {
        setBackgroundColor(context.getColor(R.color.window_background))
        icon.setImageDrawable(animation)
        addView(disc, LayoutParams(dp(160), dp(160), Gravity.CENTER))
        addView(icon, LayoutParams(dp(240), dp(240), Gravity.CENTER))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        visibility = GONE
    }

    private fun dp(value: Int) = (value * density + 0.5f).toInt()

    /** Plays the intro from the first frame, then fades away to reveal the app. */
    fun play() {
        finishing?.let(::removeCallbacks)
        animate().cancel()
        disc.animate().cancel()
        icon.animate().cancel()
        alpha = 1f
        disc.scaleX = 1f; disc.scaleY = 1f
        icon.scaleX = 1f; icon.scaleY = 1f
        visibility = VISIBLE
        animation.reset()
        animation.start()
        finishing = Runnable { fadeOut() }.also { postDelayed(it, INTRO_MS) }
    }

    private fun fadeOut() {
        disc.animate().scaleX(1.06f).scaleY(1.06f).setDuration(FADE_MS).start()
        icon.animate().scaleX(1.06f).scaleY(1.06f).setDuration(FADE_MS).start()
        animate().alpha(0f).setDuration(FADE_MS).setInterpolator(Motion.Standard)
            .withEndAction { visibility = GONE }.start()
    }

    /** While the intro is visible the app underneath can't be tapped by accident. */
    override fun onTouchEvent(event: MotionEvent): Boolean = true

    companion object {
        /** Length of ic_splash_animated. */
        const val INTRO_MS = 1_000L
        const val FADE_MS = 220L
    }
}
