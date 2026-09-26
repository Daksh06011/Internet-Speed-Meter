package com.netspeedtest.ui

import android.view.View
import com.netspeedtest.MainActivity
import com.netspeedtest.ui.UiTestSupport.allViews
import com.netspeedtest.ui.UiTestSupport.idle
import com.netspeedtest.ui.components.LaunchOverlay
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The gauge intro must play every time the app is opened, not only on a cold start. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchIntroTest {
    private fun intro(activity: MainActivity) =
        allViews(activity.window.decorView).filterIsInstance<LaunchOverlay>().single()

    @Test
    fun playsOnLaunchAndEveryReopenButNotOnRotation() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertTrue("intro plays on a fresh launch", intro(activity).visibility == View.VISIBLE)
        idle(1_500)
        assertFalse("intro fades away after ~1.2 s", intro(activity).visibility == View.VISIBLE)

        // Home button, then open again while the app is still in memory.
        controller.pause().stop()
        controller.restart().start().resume()
        assertTrue("intro plays again when reopened from memory", intro(activity).visibility == View.VISIBLE)
        idle(1_500)
        assertFalse(intro(activity).visibility == View.VISIBLE)

        // Rotation / theme change recreates the activity but is not an "open".
        val recreated = controller.recreate().get()
        assertFalse("no intro on configuration change", intro(recreated).visibility == View.VISIBLE)
        controller.pause().stop().destroy()
    }
}
