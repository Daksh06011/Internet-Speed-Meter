package com.netspeedtest.ui.components

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import android.widget.TextView
import com.netspeedtest.ui.theme.Palette
import com.netspeedtest.ui.theme.TextStyle
import com.netspeedtest.ui.theme.Ui

/**
 * Soft-UI surface. A raised surface is lit from the top-left: a faint highlight shadow
 * up-left and a deeper shadow down-right. An inset surface ("well") inverts that with
 * inner shadows. Everything is drawn with solid colours and hardware-accelerated shadow
 * layers — no bitmaps, no gradients.
 *
 * Shadows extend beyond the drawable bounds, so parents of neumorphic views set
 * `clipChildren = false` (see [unclip]).
 */
class NeuDrawable(
    private val palette: Palette,
    private val radius: Float,
    private val style: Style,
    density: Float,
    /** Scales shadow distance/blur; 1 = standard card, smaller for small controls. */
    private val elevation: Float = 1f,
) : Drawable() {
    enum class Style { Raised, Inset }

    private val distance = 5f * density * elevation
    private val blur = 12f * density * elevation
    private val rect = RectF()
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 1 = fully raised, 0 = flush with the background (used for the pressed state). */
    var depth: Float = 1f
        set(value) {
            if (field != value) { field = value; invalidateSelf() }
        }

    var fillColor: Int = if (style == Style.Raised) palette.surface else palette.well
        set(value) {
            if (field != value) { field = value; invalidateSelf() }
        }

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        fill.color = fillColor
        if (style == Style.Raised) drawRaised(canvas) else drawInset(canvas)
    }

    private fun drawRaised(canvas: Canvas) {
        val d = distance * depth
        if (depth > 0.02f) {
            val b = blur * (0.4f + 0.6f * depth)
            shade.color = fillColor
            shade.setShadowLayer(b, -d, -d, palette.shadowLight)
            canvas.drawRoundRect(rect, radius, radius, shade)
            shade.setShadowLayer(b, d, d, palette.shadowDark)
            canvas.drawRoundRect(rect, radius, radius, shade)
        }
        canvas.drawRoundRect(rect, radius, radius, fill)
    }

    private fun drawInset(canvas: Canvas) {
        canvas.drawRoundRect(rect, radius, radius, fill)
        val d = distance * 0.7f
        val b = blur * 0.6f
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(rect.left - b * 3, rect.top - b * 3, rect.right + b * 3, rect.bottom + b * 3, Path.Direction.CW)
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(roundPath())
        shade.color = palette.background
        shade.setShadowLayer(b, d, d, palette.shadowDark)
        canvas.drawPath(path, shade)
        shade.setShadowLayer(b, -d, -d, palette.shadowLight)
        canvas.drawPath(path, shade)
        canvas.restoreToCount(save)
    }

    private val clip = Path()
    private fun roundPath(): Path {
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        return clip
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, radius)
        outline.alpha = 0f
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Lets neumorphic shadows render outside a view's bounds. */
fun ViewGroup.unclip(): ViewGroup = apply {
    clipChildren = false
    clipToPadding = false
}

/**
 * A raised rounded card. When [onClick] is set it behaves like a button: it presses
 * into the surface (shadow collapses, slight scale) and springs back on release.
 */
open class NeuCard(
    protected val ui: Ui,
    radiusDp: Float = 26f,
    style: NeuDrawable.Style = NeuDrawable.Style.Raised,
    elevation: Float = 1f,
) : LinearLayout(ui.context) {
    val surface = NeuDrawable(ui.palette, ui.dp(radiusDp), style, ui.density, elevation)
    private var depthAnimator: ValueAnimator? = null

    init {
        orientation = VERTICAL
        background = surface
        unclip()
    }

    fun onClick(description: CharSequence? = null, action: () -> Unit) {
        isClickable = true
        isFocusable = true
        description?.let { contentDescription = it }
        setOnClickListener {
            Haptics.tap(it)
            action()
        }
        pressScale(ui, 0.985f) { pressed ->
            animateDepth(if (pressed) 0.15f else 1f)
            surface.fillColor = if (pressed) ui.palette.surfacePressed else ui.palette.surface
        }
    }

    private fun animateDepth(target: Float) {
        depthAnimator?.cancel()
        depthAnimator = ValueAnimator.ofFloat(surface.depth, target).apply {
            duration = if (target < 1f) Motion.FAST else Motion.MEDIUM
            interpolator = Motion.Standard
            addUpdateListener { surface.depth = it.animatedValue as Float }
            start()
        }
    }
}

/** Pill-shaped button. Primary = solid accent fill; secondary = raised neutral surface. */
class NeuButton(ui: Ui, label: String, primary: Boolean) : NeuCard(ui, radiusDp = 28f, elevation = 0.8f) {
    val text: TextView = ui.text(TextStyle.Button, label, if (primary) ui.palette.onCta else ui.palette.textPrimary)

    init {
        gravity = Gravity.CENTER
        minimumHeight = ui.dp(56)
        setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(16))
        text.gravity = Gravity.CENTER
        addView(text, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        setPrimary(primary)
    }

    fun setPrimary(primary: Boolean) {
        surface.fillColor = if (primary) ui.palette.cta else ui.palette.surface
        text.setTextColor(if (primary) ui.palette.onCta else ui.palette.textPrimary)
        tag = primary
    }

    fun setLabel(value: String) {
        text.text = value
        contentDescription = value
    }
}

/** Round icon-only button with a minimum 48 dp touch target. */
class IconButton(ui: Ui, glyph: Glyph, description: String, tint: Int = ui.palette.textPrimary) :
    NeuCard(ui, radiusDp = 22f, elevation = 0.6f) {
    val icon = IconView(ui, glyph, tint)

    init {
        gravity = Gravity.CENTER
        contentDescription = description
        minimumWidth = ui.dp(48)
        minimumHeight = ui.dp(48)
        addView(icon, LayoutParams(ui.dp(22), ui.dp(22)))
    }
}

/** A straight 1 px divider in the palette's divider colour. */
fun Ui.divider(): View = View(context).apply {
    setBackgroundColor(palette.divider)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, dp(1f).toInt()))
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
}

/** Removes the default outline shadow system so only neumorphic shadows are drawn. */
val NoOutline: ViewOutlineProvider = object : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) = Unit
}
