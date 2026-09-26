package com.netspeedtest.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Looper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** Plays the real launch animation drawable and renders its frames (used for the preview GIF). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LaunchAnimationTest {
    /**
     * The launch animation is a vector rendition of the icon artwork. After it has played,
     * the full teal → cyan → blue arc and the knob must be on screen, like the icon.
     */
    @Test
    fun animationEndsOnTheCompleteGauge() {
        val context = RuntimeEnvironment.getApplication()
        val id = context.resources.getIdentifier("ic_splash_animated", "drawable", context.packageName)
        val avd = context.getDrawable(id) as AnimatedVectorDrawable
        avd.setBounds(0, 0, 432, 432)
        avd.start()
        val scratch = Canvas(Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888))
        repeat(40) { // like a real screen, the animation advances as it is drawn
            avd.draw(scratch)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
        }
        val end = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        Canvas(end).apply { drawColor(0xFF010411.toInt()); avd.draw(this) }

        System.getProperty("screenshots.dir")?.let { dir ->
            java.io.File(dir).mkdirs()
            java.io.FileOutputStream(java.io.File(dir, "18-launch-animation-final-frame.png")).use { end.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun pixelAt(angleDeg: Double, radius: Float): Int {
            val a = Math.toRadians(angleDeg)
            return end.getPixel((216 + radius * 4 * Math.cos(a)).toInt(), (216 + radius * 4 * Math.sin(a)).toInt())
        }
        val top = pixelAt(270.0, 24f)       // cyan at the top of the arc
        val right = pixelAt(0.0, 24f)       // electric blue on the right
        val start = pixelAt(140.0, 24f)     // teal where the sweep begins
        val knob = end.getPixel(216, 216)   // silver knob in the centre
        assertTrue("top should be cyan: ${Integer.toHexString(top)}", (top and 0xFF) > 0xB0 && (top shr 8 and 0xFF) > 0xA0)
        assertTrue("right should be blue: ${Integer.toHexString(right)}", (right and 0xFF) > 0xC0 && (right shr 16 and 0xFF) < 0x60)
        assertTrue("start should be drawn: ${Integer.toHexString(start)}", (start and 0xFF) > 0x60)
        assertTrue("knob should be light: ${Integer.toHexString(knob)}", (knob shr 16 and 0xFF) > 0x90)
        assertTrue("arc is drawn", countAccentPixels(end) > 300)
    }

    private fun countAccentPixels(b: Bitmap): Int {
        var n = 0
        for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) {
            val c = b.getPixel(x, y)
            if ((c shr 16 and 0xFF) < 0x80 && (c shr 8 and 0xFF) > 0xB0 && (c and 0xFF) > 0xD0) n++
        }
        return n
    }
}
