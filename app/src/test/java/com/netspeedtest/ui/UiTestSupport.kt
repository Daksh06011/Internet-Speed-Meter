package com.netspeedtest.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.BatteryManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.netspeedtest.graph
import com.netspeedtest.ui.nav.Screen
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

/** Helpers shared by the Robolectric UI tests. */
object UiTestSupport {
    fun idle(ms: Long = 1_500) { // covers the 1.2 s launch intro
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    fun allViews(root: View): List<View> {
        val out = ArrayList<View>()
        fun walk(v: View) {
            out += v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return out
    }

    fun texts(root: View): List<String> = allViews(root).filterIsInstance<TextView>()
        .filter { it.isShown }.map { it.text.toString() }

    fun hasText(root: View, text: String) = texts(root).any { it == text }

    fun byDescription(root: View, description: String): View =
        allViews(root).firstOrNull { it.contentDescription?.toString() == description && it.isShown }
            ?: error("No view with description '$description'. Descriptions: ${allViews(root).mapNotNull { it.contentDescription }.take(40)}")

    fun byText(root: View, text: String): View =
        allViews(root).firstOrNull { it is TextView && it.text.toString() == text && it.isShown }
            ?: error("No text '$text'. Visible: ${texts(root)}")

    /** Clicks the nearest clickable ancestor of [view] (tiles/cards wrap their labels). */
    fun click(view: View) {
        var v: View? = view
        while (v != null && !v.isClickable) v = v.parent as? View
        (v ?: view).performClick()
        idle()
    }

    fun currentScreen(activity: Activity): Screen =
        allViews(activity.window.decorView).filterIsInstance<Screen>().last()

    /** Renders the full scrollable content of the visible screen to a PNG. */
    fun screenshot(activity: Activity, name: String) {
        val dir = System.getProperty("screenshots.dir") ?: return
        val screen = currentScreen(activity)
        val scroll = allViews(screen).filterIsInstance<ScrollView>().first()
        val content = scroll.getChildAt(0)
        val height = maxOf(content.height, screen.height)
        val bitmap = Bitmap.createBitmap(screen.width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(activity.graph.let { com.netspeedtest.ui.theme.Palette.resolve(activity, it.settings.settings.value.theme).background })
        content.draw(canvas)
        File(dir).mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    fun sendBattery(
        context: Context,
        level: Int = 82,
        plugged: Int = BatteryManager.BATTERY_PLUGGED_USB,
        status: Int = BatteryManager.BATTERY_STATUS_CHARGING,
        temperatureTenths: Int = 342,
        voltageMv: Int = 4123,
    ) {
        @Suppress("DEPRECATION")
        context.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, level)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_PLUGGED, plugged)
                .putExtra(BatteryManager.EXTRA_STATUS, status)
                .putExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
                .putExtra(BatteryManager.EXTRA_TEMPERATURE, temperatureTenths)
                .putExtra(BatteryManager.EXTRA_VOLTAGE, voltageMv)
                .putExtra(BatteryManager.EXTRA_TECHNOLOGY, "Li-poly")
                .putExtra(BatteryManager.EXTRA_CYCLE_COUNT, 214),
        )
    }
}
