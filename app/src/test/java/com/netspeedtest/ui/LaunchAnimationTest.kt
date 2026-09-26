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
     * The intro is built from the icon's exact geometry, so after it plays it must end
     * on the launcher icon itself — no jump when the app appears.
     */
    @Test
    fun animationEndsExactlyOnTheLauncherIcon() {
        val context = RuntimeEnvironment.getApplication()
        fun drawable(name: String) =
            context.getDrawable(context.resources.getIdentifier(name, "drawable", context.packageName))!!.apply { setBounds(0, 0, 432, 432) }
        fun snapshot(d: android.graphics.drawable.Drawable) = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(0xFF0B1222.toInt()); d.draw(this) }
        }
        val avd = drawable("ic_splash_animated") as AnimatedVectorDrawable
        avd.start()
        val scratch = Canvas(Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888))
        repeat(40) { // like a real screen, the animation advances as it is drawn
            avd.draw(scratch)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
        }
        val end = snapshot(avd)
        val icon = snapshot(drawable("ic_launcher_foreground"))
        var different = 0
        for (y in 0 until 432) for (x in 0 until 432) {
            val a = end.getPixel(x, y)
            val b = icon.getPixel(x, y)
            val delta = maxOf(kotlin.math.abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)), kotlin.math.abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)), kotlin.math.abs((a and 0xFF) - (b and 0xFF)))
            if (delta > 40) different++
        }
        assertTrue("final frame differs from launcher icon in $different pixels", different < 432 * 432 * 3 / 1000)
        assertTrue("reading arc is drawn", countAccentPixels(end) > 300)
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
