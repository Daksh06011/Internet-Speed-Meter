package com.netspeedtest.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.netspeedtest.ui.theme.TextStyle
import com.netspeedtest.ui.theme.Ui

/** Sets text only when it actually changed, avoiding needless relayouts on live updates. */
fun TextView.update(value: CharSequence) {
    if (text.toString() != value.toString()) text = value
}

/** Tappable dashboard tile: icon + label on top, large value, secondary line. */
class HealthTile(ui: Ui, glyph: Glyph, label: String) : NeuCard(ui, radiusDp = 24f) {
    val icon = IconView(ui, glyph, ui.palette.textSecondary)
    val label: TextView = ui.text(TextStyle.Label, label, ui.palette.textTertiary)
    val value: TextView = ui.text(TextStyle.Value, "—")
    val sub: TextView = ui.text(TextStyle.Caption, "", ui.palette.textSecondary)

    init {
        setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(18))
        minimumHeight = ui.dp(128)
        val top = LinearLayout(ui.context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(icon, LayoutParams(ui.dp(18), ui.dp(18)))
        top.addView(this.label, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = ui.dp(8)
        })
        addView(top)
        addView(value, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(20)
        })
        value.maxLines = 1
        sub.maxLines = 2
        addView(sub, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(6)
        })
    }

    fun set(value: String, sub: String, valueColor: Int = ui.palette.textPrimary) {
        this.value.update(value)
        this.value.setTextColor(valueColor)
        this.sub.update(sub)
        contentDescription = "${label.text}: $value. $sub"
    }
}

/** A metric inside the hero card: small icon/label line and a large value with unit. */
class MetricCell(private val ui: Ui, glyph: Glyph, label: String, private val accent: Int) : LinearLayout(ui.context) {
    private val icon = IconView(ui, glyph, ui.palette.textTertiary)
    private val label: TextView = ui.text(TextStyle.Label, label, ui.palette.textTertiary)
    private val value: TextView = ui.text(TextStyle.Value, "—")
    private val unit: TextView = ui.text(TextStyle.Caption, "", ui.palette.textSecondary)
    private var active = false

    init {
        orientation = VERTICAL
        setPadding(0, ui.dp(14), 0, ui.dp(14))
        val top = LinearLayout(ui.context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(icon, LayoutParams(ui.dp(15), ui.dp(15)))
        top.addView(this.label, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = ui.dp(6)
        })
        addView(top)
        val bottom = LinearLayout(ui.context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        bottom.addView(value)
        bottom.addView(unit, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = ui.dp(5)
            bottomMargin = ui.dp(2)
        })
        addView(bottom, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(8)
        })
        isFocusable = true
    }

    fun set(value: String?, unit: String, isActive: Boolean) {
        val shown = value ?: "—"
        this.value.update(shown)
        this.unit.update(if (value == null) "" else unit)
        if (isActive != active) {
            active = isActive
            val color = if (isActive) accent else ui.palette.textTertiary
            icon.tint = color
            label.setTextColor(color)
            if (!ui.reduceMotion && isActive) {
                icon.animate().cancel()
                icon.scaleX = 0.6f
                icon.scaleY = 0.6f
                icon.animate().scaleX(1f).scaleY(1f).setDuration(Motion.SLOW).setInterpolator(Motion.Spring).start()
            }
        }
        contentDescription = "${label.text}: ${if (value == null) "not measured" else "$value $unit"}"
    }
}

/** Small solid status dot (connection state). Shape, not only colour, changes with state. */
class StatusDot(private val ui: Ui) : View(ui.context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var hollow = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun set(color: Int, hollow: Boolean) {
        paint.color = color
        this.hollow = hollow
        paint.style = if (hollow) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = ui.dp(1.5f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val r = minOf(width, height) / 2f - paint.strokeWidth
        canvas.drawCircle(width / 2f, height / 2f, r, paint)
    }
}
