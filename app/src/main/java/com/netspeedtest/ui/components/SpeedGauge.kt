package com.netspeedtest.ui.components

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import com.netspeedtest.ui.theme.Fonts
import com.netspeedtest.ui.theme.Ui
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Minimal arc gauge. The value follows its target with critically damped smoothing
 * computed from real frame time, and the view only schedules frames while the value
 * is still moving — when nothing changes it costs nothing.
 */
class SpeedGauge(private val ui: Ui) : View(ui.context) {
    private val p = ui.palette
    private val track = stroke(p.well)
    private val arc = stroke(p.accentOnSurface)
    private val ring = stroke(p.divider)
    private val ringActive = stroke(p.textSecondary)
    private val celebrate = stroke(p.accentOnSurface)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.textTertiary }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = p.textPrimary
        typeface = Fonts.sans(300)
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "tnum"
        letterSpacing = -0.03f
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = p.textSecondary
        typeface = Fonts.mono(500)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
    }
    private val labelPaint = Paint(unitPaint).apply { color = p.textTertiary; letterSpacing = 0.14f }
    private val oval = RectF()
    private val ringOval = RectF()

    /** Maps a raw value to 0..1 along the arc. */
    var scale: (Double) -> Float = ::speedScale
    var formatter: (Double) -> String = { String.format("%.1f", it) }

    private var target = 0.0
    private var shown = 0.0
    private var hasValue = false
    private var progressTarget = 0f
    private var progressShown = 0f
    private var lastFrame = 0L
    private var unit = ""
    private var label = ""
    private var celebration = 0f
    private var celebrationAnimator: ValueAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        this.color = color
    }

    fun setArcColor(color: Int) {
        arc.color = color
        celebrate.color = color
        invalidate()
    }

    /** Sets what the gauge shows; `null` renders an em dash. */
    fun show(value: Double?, unit: String, label: String) {
        this.unit = unit
        this.label = label
        hasValue = value != null
        target = value ?: 0.0
        if (ui.reduceMotion) shown = target
        contentDescription = if (value == null) "$label, no value" else "$label ${formatter(value)} $unit"
        kick()
    }

    fun setProgress(fraction: Float) {
        progressTarget = fraction.coerceIn(0f, 1f)
        if (ui.reduceMotion) progressShown = progressTarget
        kick()
    }

    /** A single refined pulse when a test completes. */
    fun celebrate() {
        if (ui.reduceMotion) return
        celebrationAnimator?.cancel()
        celebrationAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 620
            interpolator = Motion.EmphasizedDecelerate
            addUpdateListener { celebration = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun kick() {
        lastFrame = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val size = min(w, ui.dp(300))
        setMeasuredDimension(w, (size * 0.86f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        step()
        val size = min(width.toFloat(), height / 0.86f)
        val stroke = size * 0.055f
        val cx = width / 2f
        val cy = size / 2f + stroke * 0.2f
        val r = size / 2f - stroke
        oval.set(cx - r, cy - r, cx + r, cy + r)

        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        canvas.drawArc(oval, START, SWEEP, false, track)

        // Scale marks sit just inside the track.
        val tickR = r - stroke * 1.25f
        val dot = stroke * 0.09f + 1f
        for (mark in MARKS) {
            val a = Math.toRadians((START + SWEEP * speedScale(mark)).toDouble())
            canvas.drawCircle(cx + (tickR * cos(a)).toFloat(), cy + (tickR * sin(a)).toFloat(), dot, tick)
        }

        val fraction = scale(shown).coerceIn(0f, 1f)
        if (hasValue && fraction > 0.002f) canvas.drawArc(oval, START, SWEEP * fraction, false, arc)

        if (celebration > 0f && celebration < 1f) {
            val grow = stroke * 1.6f * celebration
            celebrate.strokeWidth = stroke * (1f - celebration)
            celebrate.alpha = ((1f - celebration) * 140).toInt()
            ringOval.set(oval.left - grow, oval.top - grow, oval.right + grow, oval.bottom + grow)
            canvas.drawArc(ringOval, START, SWEEP * fraction, false, celebrate)
        }

        // Thin outer ring tracking overall test progress.
        val rr = r + stroke * 1.15f
        ringOval.set(cx - rr, cy - rr, cx + rr, cy + rr)
        ring.strokeWidth = ui.dp(1.5f)
        ringActive.strokeWidth = ui.dp(1.5f)
        if (progressShown > 0.001f) {
            canvas.drawArc(ringOval, START, SWEEP, false, ring)
            canvas.drawArc(ringOval, START, SWEEP * progressShown, false, ringActive)
        }

        // Centre readout.
        numberPaint.textSize = size * 0.21f
        unitPaint.textSize = maxOf(ui.sp(12f), size * 0.045f)
        labelPaint.textSize = maxOf(ui.sp(11f), size * 0.04f)
        canvas.drawText(label.uppercase(), cx, cy - size * 0.17f, labelPaint)
        canvas.drawText(if (hasValue) formatter(shown) else "—", cx, cy + size * 0.07f, numberPaint)
        canvas.drawText(unit, cx, cy + size * 0.16f, unitPaint)
    }

    /** Advances the smoothed values using elapsed frame time; schedules the next frame if needed. */
    private fun step() {
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0L, 64L) / 1000.0
        lastFrame = now
        val k = 1.0 - exp(-dt / TIME_CONSTANT)
        shown += (target - shown) * k
        progressShown += ((progressTarget - progressShown) * k).toFloat()
        val settled = abs(target - shown) < maxOf(0.01, abs(target) * 0.0005) && abs(progressTarget - progressShown) < 0.001f
        if (settled) {
            shown = target
            progressShown = progressTarget
        } else {
            postInvalidateOnAnimation()
        }
    }

    companion object {
        private const val START = 140f
        private const val SWEEP = 260f
        private const val TIME_CONSTANT = 0.14

        private val MARKS = doubleArrayOf(0.0, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0)

        /** Piecewise-linear scale over [MARKS]: equal arc length per step. */
        fun speedScale(mbps: Double): Float {
            if (mbps <= 0) return 0f
            for (i in 1 until MARKS.size) {
                if (mbps <= MARKS[i]) {
                    val within = (mbps - MARKS[i - 1]) / (MARKS[i] - MARKS[i - 1])
                    return ((i - 1 + within) / (MARKS.size - 1)).toFloat()
                }
            }
            return 1f
        }

        /** Latency scale: 0–200 ms spans the arc. */
        fun latencyScale(ms: Double): Float = (ms / 200.0).toFloat().coerceIn(0f, 1f)
    }
}
