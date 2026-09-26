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
     * Robolectric jumps animated vectors straight to their end state, so this verifies the
     * part that matters for polish: the animation settles exactly on the launcher icon,
     * so there is no visual jump when the splash hands over to the app.
     */
    @Test
    fun animationSettlesExactlyOnTheLauncherIcon() {
        val context = RuntimeEnvironment.getApplication()
        fun render(name: String, start: Boolean): Bitmap {
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            val drawable = context.getDrawable(id)!!
            drawable.setBounds(0, 0, 320, 320)
            if (start) {
                (drawable as AnimatedVectorDrawable).start()
                // Like a real screen, the animation advances once it has been drawn.
                val scratch = Canvas(Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888))
                repeat(40) {
                    drawable.draw(scratch)
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
                }
            }
            return Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888).also {
                Canvas(it).apply { drawColor(0xFF161719.toInt()); drawable.draw(this) }
            }
        }
        val end = render("ic_splash_animated", start = true)
        val icon = render("ic_launcher_foreground", start = false)
        // Allow anti-aliasing differences along the edges (trimmed vs. static path), nothing more.
        var different = 0
        for (y in 0 until 320) for (x in 0 until 320) {
            val a = end.getPixel(x, y)
            val b = icon.getPixel(x, y)
            val delta = maxOf(kotlin.math.abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)), kotlin.math.abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)), kotlin.math.abs((a and 0xFF) - (b and 0xFF)))
            if (delta > 40) different++
        }
        assertTrue("final frame differs from launcher icon in $different pixels", different < 320 * 320 * 3 / 1000)
        assertTrue("arc is drawn", countAccentPixels(end) > 500)
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
