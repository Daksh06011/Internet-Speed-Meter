package com.netspeedtest.ui.theme

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView

/**
 * Typography built entirely on system fonts (no bundled font files). Numbers use
 * tabular figures so live values do not jitter horizontally as digits change.
 * Small technical labels use the system monospace face for a precise, instrument-like
 * personality.
 */
enum class TextStyle(val sizeSp: Float, val weight: Int, val mono: Boolean = false, val tracking: Float = 0f, val upper: Boolean = false) {
    Display(64f, 300, tracking = -0.03f),
    Title(28f, 600, tracking = -0.02f),
    Headline(22f, 600, tracking = -0.01f),
    Value(22f, 500, tracking = -0.01f),
    ValueSmall(17f, 500),
    Body(15f, 400),
    BodyMedium(15f, 500),
    Caption(13f, 400),
    Label(11f, 500, mono = true, tracking = 0.12f, upper = true),
    Button(16f, 600, tracking = 0.02f),
}

object Fonts {
    private val cache = HashMap<Int, Typeface>()
    private val monoCache = HashMap<Int, Typeface>()

    fun sans(weight: Int): Typeface = cache.getOrPut(weight) { Typeface.create(Typeface.SANS_SERIF, weight, false) }
    fun mono(weight: Int): Typeface = monoCache.getOrPut(weight) { Typeface.create(Typeface.MONOSPACE, weight, false) }
}

/** Everything a view needs to style itself: palette, density helpers and motion policy. */
class Ui(val context: Context, val palette: Palette) {
    private val metrics = context.resources.displayMetrics

    val density: Float = metrics.density

    /** True when the user disabled animations (Settings › Accessibility › Remove animations). */
    val reduceMotion: Boolean get() = !ValueAnimator.areAnimatorsEnabled()

    fun dp(value: Float): Float = value * density
    fun dp(value: Int): Int = (value * density + 0.5f).toInt()
    fun sp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, metrics)

    fun style(view: TextView, style: TextStyle, color: Int = palette.textPrimary) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.sizeSp)
        view.typeface = if (style.mono) Fonts.mono(style.weight) else Fonts.sans(style.weight)
        view.letterSpacing = style.tracking
        view.isAllCaps = style.upper
        view.setTextColor(color)
        view.fontFeatureSettings = "tnum"
        view.includeFontPadding = false
    }

    fun text(style: TextStyle, value: CharSequence = "", color: Int = palette.textPrimary): TextView =
        TextView(context).also {
            style(it, style, color)
            it.text = value
        }
}
