package com.netspeedtest.ui.components

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.netspeedtest.ui.theme.TextStyle
import com.netspeedtest.ui.theme.Ui

/**
 * Tiny line chart for live series (charging current, network rate). Holds a fixed ring
 * of samples in a primitive array and redraws only when a sample is added.
 */
class Sparkline(private val ui: Ui, private val capacity: Int = 90, color: Int) : View(ui.context) {
    private val values = FloatArray(capacity)
    private var count = 0
    private var head = 0
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ui.dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }
    private val baseline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = ui.dp(1f)
        this.color = ui.palette.divider
    }
    private val path = Path()

    /** When true the vertical range always includes zero (signed series such as current). */
    var includeZero = true

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setColor(color: Int) {
        line.color = color
        invalidate()
    }

    fun add(value: Float) {
        values[head] = value
        head = (head + 1) % capacity
        if (count < capacity) count++
        invalidate()
    }

    fun clear() {
        count = 0
        head = 0
        invalidate()
    }

    private fun at(i: Int) = values[(head - count + i + capacity) % capacity]

    override fun onDraw(canvas: Canvas) {
        val pad = line.strokeWidth
        val h = height - pad * 2
        var min = if (includeZero) 0f else Float.MAX_VALUE
        var max = if (includeZero) 0f else -Float.MAX_VALUE
        for (i in 0 until count) {
            val v = at(i)
            if (v < min) min = v
            if (v > max) max = v
        }
        if (count == 0) { min = 0f; max = 1f }
        if (max - min < 1e-3f) { max += 1f; min -= 1f }
        fun y(v: Float) = pad + h - (v - min) / (max - min) * h
        if (includeZero && min < 0f && max > 0f) canvas.drawLine(0f, y(0f), width.toFloat(), y(0f), baseline)
        else canvas.drawLine(0f, height - pad, width.toFloat(), height - pad, baseline)
        if (count < 2) return
        val stepX = width.toFloat() / (capacity - 1)
        val startX = width - stepX * (count - 1)
        path.reset()
        path.moveTo(startX, y(at(0)))
        for (i in 1 until count) path.lineTo(startX + stepX * i, y(at(i)))
        canvas.drawPath(path, line)
    }
}

/** Horizontal level indicator: an inset well with a solid fill. */
class LevelBar(private val ui: Ui, color: Int) : View(ui.context) {
    private val well = NeuDrawable(ui.palette, ui.dp(8f), NeuDrawable.Style.Inset, ui.density, elevation = 0.5f)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val rect = RectF()
    private var fraction = 0f
    private var animator: ValueAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setColor(color: Int) {
        fill.color = color
        invalidate()
    }

    fun setFraction(value: Float, animate: Boolean = true) {
        val target = value.coerceIn(0f, 1f)
        animator?.cancel()
        if (!animate || ui.reduceMotion) {
            fraction = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(fraction, target).apply {
            duration = Motion.SLOW
            interpolator = Motion.EmphasizedDecelerate
            addUpdateListener { fraction = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), ui.dp(16))
    }

    override fun onDraw(canvas: Canvas) {
        well.setBounds(0, 0, width, height)
        well.draw(canvas)
        if (fraction <= 0f) return
        val inset = ui.dp(3f)
        val w = (width - inset * 2) * fraction
        rect.set(inset, inset, inset + maxOf(w, height - inset * 2), height - inset)
        val r = rect.height() / 2f
        canvas.drawRoundRect(rect, r, r, fill)
    }
}

/**
 * Segmented selector: an inset track with a raised thumb that slides to the selection.
 */
class SegmentedControl(
    private val ui: Ui,
    private val options: List<String>,
    selected: Int,
    private val onSelect: (Int) -> Unit,
) : FrameLayout(ui.context) {
    private val thumb = NeuCard(ui, radiusDp = 14f, elevation = 0.45f)
    private val labels = LinearLayout(ui.context)
    private var index = selected

    init {
        background = NeuDrawable(ui.palette, ui.dp(18f), NeuDrawable.Style.Inset, ui.density, elevation = 0.6f)
        unclip()
        setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
        addView(thumb, LayoutParams(0, LayoutParams.MATCH_PARENT))
        addView(labels, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        options.forEachIndexed { i, option ->
            val label = ui.text(TextStyle.BodyMedium, option).apply {
                gravity = Gravity.CENTER
                minHeight = ui.dp(44)
                setOnClickListener { select(i, fromUser = true) }
                accessibilityDelegate = object : AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.isCheckable = true
                        info.isChecked = i == index
                        info.className = "android.widget.RadioButton"
                    }
                }
            }
            labels.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        refreshLabels()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val segment = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight) / options.size
        (thumb.layoutParams as LayoutParams).width = segment
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) thumb.translationX = (thumb.layoutParams.width * index).toFloat()
    }

    fun select(i: Int, fromUser: Boolean) {
        if (i == index) return
        index = i
        val segment = (width - paddingLeft - paddingRight) / options.size
        if (ui.reduceMotion) thumb.translationX = (segment * i).toFloat()
        else thumb.animate().translationX((segment * i).toFloat()).setDuration(Motion.SLOW).setInterpolator(Motion.Spring).start()
        refreshLabels()
        if (fromUser) {
            Haptics.tap(this)
            onSelect(i)
        }
    }

    private fun refreshLabels() {
        for (i in 0 until labels.childCount) {
            (labels.getChildAt(i) as TextView).setTextColor(if (i == index) ui.palette.textPrimary else ui.palette.textSecondary)
        }
    }
}

/** Neumorphic on/off switch: inset track, raised knob, accent fill when on. */
class NeuSwitch(private val ui: Ui, checked: Boolean, private val onChange: (Boolean) -> Unit) : View(ui.context) {
    private val track = NeuDrawable(ui.palette, ui.dp(15f), NeuDrawable.Style.Inset, ui.density, elevation = 0.5f)
    private val knob = NeuDrawable(ui.palette, ui.dp(11f), NeuDrawable.Style.Raised, ui.density, elevation = 0.45f)
    private val onFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ui.palette.accent }
    private val rect = RectF()
    private var position = if (checked) 1f else 0f
    private var animator: ValueAnimator? = null

    var isChecked: Boolean = checked
        private set

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { toggle() }
        accessibilityDelegate = object : AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Switch"
                info.isCheckable = true
                info.isChecked = isChecked
            }
        }
    }

    fun toggle() {
        isChecked = !isChecked
        Haptics.tap(this)
        animator?.cancel()
        val target = if (isChecked) 1f else 0f
        if (ui.reduceMotion) {
            position = target
            invalidate()
        } else {
            animator = ValueAnimator.ofFloat(position, target).apply {
                duration = Motion.SLOW
                interpolator = Motion.Spring
                addUpdateListener { position = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        onChange(isChecked)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(ui.dp(56), ui.dp(48))
    }

    override fun onDraw(canvas: Canvas) {
        val h = ui.dp(30)
        val top = (height - h) / 2
        track.setBounds(0, top, width, top + h)
        track.draw(canvas)
        val inset = ui.dp(4f)
        val d = h - inset * 2
        val x = inset + (width - inset * 2 - d) * position
        if (position > 0.01f) {
            onFill.alpha = (255 * position.coerceIn(0f, 1f)).toInt()
            rect.set(inset, top + inset, x + d, top + inset + d)
            canvas.drawRoundRect(rect, d / 2, d / 2, onFill)
        }
        knob.setBounds(x.toInt(), (top + inset).toInt(), (x + d).toInt(), (top + inset + d).toInt())
        knob.draw(canvas)
    }
}

/** Label/value line used on every detail screen. */
class InfoRow(private val ui: Ui, label: String, value: String = "—", sub: String? = null) : LinearLayout(ui.context) {
    val labelView: TextView = ui.text(TextStyle.Body, label, ui.palette.textSecondary)
    val valueView: TextView = ui.text(TextStyle.ValueSmall, value)
    val subView: TextView = ui.text(TextStyle.Caption, sub ?: "", ui.palette.textTertiary)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(52)
        setPadding(0, ui.dp(12), 0, ui.dp(12))
        addView(labelView, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val right = LinearLayout(ui.context).apply {
            orientation = VERTICAL
            gravity = Gravity.END
        }
        valueView.gravity = Gravity.END
        subView.gravity = Gravity.END
        right.addView(valueView)
        right.addView(subView)
        subView.visibility = if (sub.isNullOrEmpty()) GONE else VISIBLE
        addView(right, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        isFocusable = true // TalkBack reads label and value together
    }

    /** Sets the value; `null` shows the standard "Unavailable" wording in a muted colour. */
    fun set(value: String?, sub: String? = null, color: Int = ui.palette.textPrimary) {
        valueView.text = value ?: UNAVAILABLE
        valueView.setTextColor(if (value == null) ui.palette.textTertiary else color)
        subView.text = sub ?: ""
        subView.visibility = if (sub.isNullOrEmpty()) GONE else VISIBLE
    }

    /** Hides the row (and the divider above it) when the phone doesn't provide this value. */
    fun showIf(visible: Boolean) {
        val v = if (visible) VISIBLE else GONE
        if (visibility == v) return
        visibility = v
        val parent = parent as? ViewGroup ?: return
        val index = parent.indexOfChild(this)
        if (index > 0) parent.getChildAt(index - 1).visibility = v
    }

    companion object {
        const val UNAVAILABLE = "Unavailable"
    }
}

/** Blocks touches from passing through (used for sheets and overlays). */
class TouchBlocker(ui: Ui) : FrameLayout(ui.context) {
    override fun onTouchEvent(event: MotionEvent): Boolean = true
}
