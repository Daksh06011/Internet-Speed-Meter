package com.netspeedtest.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.netspeedtest.ui.theme.Ui

/** Line icons drawn from code on a 24×24 grid — zero bitmap or icon-font weight. */
enum class Glyph { Download, Upload, Ping, Jitter, Battery, Thermometer, Bolt, Wifi, Cellular, Ethernet, Globe, Memory, History, Settings, Back, ChevronRight, Close, Trash, Check, Alert, Pulse }

class IconView(ui: Ui, glyph: Glyph, tint: Int) : View(ui.context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.8f
        color = tint
    }
    private val path = Path()
    private val oval = RectF()

    var glyph: Glyph = glyph
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    var tint: Int
        get() = paint.color
        set(value) {
            if (paint.color != value) { paint.color = value; invalidate() }
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val scale = minOf(width, height) / 24f
        canvas.save()
        canvas.translate((width - 24f * scale) / 2f, (height - 24f * scale) / 2f)
        canvas.scale(scale, scale)
        path.reset()
        build(glyph, path, oval)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    companion object {
        fun build(glyph: Glyph, p: Path, oval: RectF) {
            when (glyph) {
                Glyph.Download -> { p.moveTo(12f, 4f); p.lineTo(12f, 19f); p.moveTo(6f, 13f); p.lineTo(12f, 19f); p.lineTo(18f, 13f) }
                Glyph.Upload -> { p.moveTo(12f, 20f); p.lineTo(12f, 5f); p.moveTo(6f, 11f); p.lineTo(12f, 5f); p.lineTo(18f, 11f) }
                Glyph.Ping -> { p.addCircle(12f, 12f, 2.2f, Path.Direction.CW); p.moveTo(7.5f, 7.5f); p.quadTo(3f, 12f, 7.5f, 16.5f); p.moveTo(16.5f, 7.5f); p.quadTo(21f, 12f, 16.5f, 16.5f) }
                Glyph.Jitter -> { p.moveTo(3f, 12f); p.lineTo(6f, 12f); p.lineTo(8.5f, 6f); p.lineTo(11.5f, 18f); p.lineTo(14.5f, 8f); p.lineTo(17f, 14f); p.lineTo(18.5f, 12f); p.lineTo(21f, 12f) }
                Glyph.Pulse -> { p.moveTo(3f, 13f); p.lineTo(7f, 13f); p.lineTo(9.5f, 7f); p.lineTo(13f, 18f); p.lineTo(15.5f, 11f); p.lineTo(21f, 11f) }
                Glyph.Battery -> { oval.set(3f, 7f, 19f, 17f); p.addRoundRect(oval, 2.5f, 2.5f, Path.Direction.CW); p.moveTo(21.5f, 10.5f); p.lineTo(21.5f, 13.5f); p.moveTo(6.5f, 10.5f); p.lineTo(6.5f, 13.5f); p.moveTo(9.5f, 10.5f); p.lineTo(9.5f, 13.5f) }
                Glyph.Thermometer -> { p.moveTo(10f, 14.2f); p.lineTo(10f, 5f); p.quadTo(10f, 3f, 12f, 3f); p.quadTo(14f, 3f, 14f, 5f); p.lineTo(14f, 14.2f); oval.set(8f, 13.5f, 16f, 21.5f); p.arcTo(oval, -60f, 300f); p.close(); p.moveTo(12f, 9f); p.lineTo(12f, 17f) }
                Glyph.Bolt -> { p.moveTo(13f, 2.5f); p.lineTo(5.5f, 13.5f); p.lineTo(11.5f, 13.5f); p.lineTo(10.5f, 21.5f); p.lineTo(18.5f, 10f); p.lineTo(12.5f, 10f); p.close() }
                Glyph.Wifi -> { oval.set(2f, 5f, 22f, 25f); p.addArc(oval, 225f, 90f); oval.set(5.5f, 8.5f, 18.5f, 21.5f); p.addArc(oval, 225f, 90f); oval.set(9f, 12f, 15f, 18f); p.addArc(oval, 225f, 90f); p.addCircle(12f, 19f, 0.6f, Path.Direction.CW) }
                Glyph.Cellular -> { p.moveTo(5f, 19f); p.lineTo(5f, 16f); p.moveTo(9.5f, 19f); p.lineTo(9.5f, 12.5f); p.moveTo(14f, 19f); p.lineTo(14f, 9f); p.moveTo(18.5f, 19f); p.lineTo(18.5f, 5f) }
                Glyph.Ethernet -> { oval.set(4f, 5f, 20f, 16f); p.addRoundRect(oval, 2f, 2f, Path.Direction.CW); p.moveTo(8f, 16f); p.lineTo(8f, 19f); p.lineTo(16f, 19f); p.lineTo(16f, 16f); p.moveTo(8f, 9f); p.lineTo(8f, 11f); p.moveTo(12f, 9f); p.lineTo(12f, 11f); p.moveTo(16f, 9f); p.lineTo(16f, 11f) }
                Glyph.Globe -> { p.addCircle(12f, 12f, 9f, Path.Direction.CW); p.moveTo(3f, 12f); p.lineTo(21f, 12f); oval.set(8f, 3f, 16f, 21f); p.addOval(oval, Path.Direction.CW) }
                Glyph.Memory -> { oval.set(6f, 6f, 18f, 18f); p.addRoundRect(oval, 2f, 2f, Path.Direction.CW); oval.set(9.5f, 9.5f, 14.5f, 14.5f); p.addRect(oval, Path.Direction.CW); for (x in floatArrayOf(9.5f, 14.5f)) { p.moveTo(x, 3f); p.lineTo(x, 6f); p.moveTo(x, 18f); p.lineTo(x, 21f); p.moveTo(3f, x); p.lineTo(6f, x); p.moveTo(18f, x); p.lineTo(21f, x) } }
                Glyph.History -> { p.addCircle(12f, 12f, 8.5f, Path.Direction.CW); p.moveTo(12f, 7.5f); p.lineTo(12f, 12f); p.lineTo(15f, 14f) }
                Glyph.Settings -> { p.moveTo(4f, 7f); p.lineTo(20f, 7f); p.moveTo(4f, 17f); p.lineTo(20f, 17f); p.addCircle(9f, 7f, 2.3f, Path.Direction.CW); p.addCircle(15f, 17f, 2.3f, Path.Direction.CW) }
                Glyph.Back -> { p.moveTo(15f, 5f); p.lineTo(8f, 12f); p.lineTo(15f, 19f) }
                Glyph.ChevronRight -> { p.moveTo(9.5f, 6f); p.lineTo(15.5f, 12f); p.lineTo(9.5f, 18f) }
                Glyph.Close -> { p.moveTo(6.5f, 6.5f); p.lineTo(17.5f, 17.5f); p.moveTo(17.5f, 6.5f); p.lineTo(6.5f, 17.5f) }
                Glyph.Trash -> { p.moveTo(4.5f, 7f); p.lineTo(19.5f, 7f); p.moveTo(9.5f, 7f); p.lineTo(9.5f, 4.5f); p.lineTo(14.5f, 4.5f); p.lineTo(14.5f, 7f); p.moveTo(6.5f, 7f); p.lineTo(7.5f, 20f); p.lineTo(16.5f, 20f); p.lineTo(17.5f, 7f) }
                Glyph.Check -> { p.moveTo(5f, 12.5f); p.lineTo(10f, 17.5f); p.lineTo(19f, 7f) }
                Glyph.Alert -> { p.addCircle(12f, 12f, 9f, Path.Direction.CW); p.moveTo(12f, 7.5f); p.lineTo(12f, 13f); p.moveTo(12f, 16.4f); p.lineTo(12f, 16.6f) }
            }
        }
    }
}
