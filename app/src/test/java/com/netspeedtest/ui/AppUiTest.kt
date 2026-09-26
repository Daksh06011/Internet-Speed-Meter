package com.netspeedtest.ui

import android.content.Context
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.provider.Settings
import com.netspeedtest.MainActivity
import com.netspeedtest.data.ThemeMode
import com.netspeedtest.graph
import com.netspeedtest.speedtest.SpeedTestResult
import com.netspeedtest.ui.UiTestSupport.byDescription
import com.netspeedtest.ui.UiTestSupport.byText
import com.netspeedtest.ui.UiTestSupport.click
import com.netspeedtest.ui.UiTestSupport.hasText
import com.netspeedtest.ui.UiTestSupport.idle
import com.netspeedtest.ui.UiTestSupport.screenshot
import com.netspeedtest.ui.UiTestSupport.sendBattery
import com.netspeedtest.ui.UiTestSupport.texts
import com.netspeedtest.ui.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBatteryManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppUiTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun deviceState() {
        sendBattery(context)
        val battery = shadowOf(context.getSystemService(BatteryManager::class.java)) as ShadowBatteryManager
        battery.setIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, 1_820_000)
        battery.setIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER, 3_690_000)
    }

    private fun launch(): ActivityController<MainActivity> {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
        return controller
    }

    private fun sampleResult(time: Long = 1_758_800_000_000) = SpeedTestResult(
        timestampMillis = time, downloadMbps = 325.7, uploadMbps = 88.2, pingMs = 14.3, jitterMs = 2.1,
        loadedLatencyDownMs = 41.0, loadedLatencyUpMs = 63.0, connectionType = "Wi‑Fi",
        networkDetail = "Wi‑Fi · 5 GHz", serverName = "Cloudflare · BOM", bytesUsed = 512_000_000,
    )

    @Test
    fun homeShowsRealDeviceReadings() {
        val controller = launch()
        val root = controller.get().window.decorView
        val visible = texts(root)
        assertTrue(visible.toString(), hasText(root, "Net Speed Test"))
        assertTrue(hasText(root, "Start test"))
        assertTrue(visible.toString(), hasText(root, "82%"))
        assertTrue(visible.toString(), hasText(root, "34.2°C"))
        assertTrue("power is V × I: 4.123 V × 1820 mA", hasText(root, "≈ 7.5 W"))
        assertTrue(hasText(root, "+1,820 mA · estimate"))
        assertTrue(hasText(root, "No tests yet"))
        assertTrue(visible.toString(), hasText(root, "Battery sensor"))
        screenshot(controller.get(), "01-home-dark")
        controller.pause().stop().destroy()
    }

    @Test
    fun startingOfflineExplainsTheProblem() {
        shadowOf(context.getSystemService(ConnectivityManager::class.java)).setActiveNetworkInfo(null)
        val controller = launch()
        val root = controller.get().window.decorView
        click(byText(root, "Start test"))
        assertTrue(texts(root).toString(), hasText(root, "No internet connection"))
        assertTrue(hasText(root, "Check Wi‑Fi or mobile data and try again."))
        assertTrue(hasText(root, "Try again"))
        screenshot(controller.get(), "02-home-offline")

        Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 1)
        click(byText(root, "Try again"))
        assertTrue(hasText(root, "Airplane mode is on"))
        controller.pause().stop().destroy()
    }

    @Test
    fun everyScreenIsReachableAndBackReturnsHome() {
        val controller = launch()
        val activity = controller.get()
        val root = activity.window.decorView
        val nav = activity.graph.navigator

        val tiles = listOf(
            "Battery" to "03-battery",
            "Charging" to "04-charging",
            "Temp" to "05-temperature",
            "Network" to "06-network",
            "Memory" to "07-memory",
        )
        for ((label, shot) in tiles) {
            click(byText(root, label))
            assertEquals(2, nav.depth)
            idle(2_500)
            screenshot(activity, shot)
            @Suppress("DEPRECATION")
            activity.onBackPressed()
            idle()
            assertEquals(Route.Home, nav.current)
        }

        click(byDescription(root, "History"))
        assertEquals(Route.History, nav.current)
        assertTrue(hasText(root, "No results yet"))
        screenshot(activity, "08-history-empty")
        click(byDescription(root, "Back"))
        click(byDescription(root, "Settings"))
        assertEquals(Route.Settings, nav.current)
        screenshot(activity, "09-settings")
        controller.pause().stop().destroy()
    }

    @Test
    fun missingBatteryCurrentIsHiddenNotFaked() {
        val battery = shadowOf(context.getSystemService(BatteryManager::class.java)) as ShadowBatteryManager
        battery.setIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, Int.MIN_VALUE)
        val controller = launch()
        val activity = controller.get()
        val root = activity.window.decorView
        // A phone without a current sensor shows plug state instead of an "unavailable" value.
        assertTrue(texts(root).toString(), hasText(root, "Plugged in"))
        assertFalse(texts(root).any { it.contains("Not exposed") || it.contains(" mA") })

        activity.graph.navigator.push(Route.Temperature)
        idle(2_500)
        assertTrue(hasText(root, "34.2°C"))
        assertFalse(texts(root).any { it.contains("CPU") || it.contains("GPU") || it.contains("hermal") })

        activity.graph.navigator.push(Route.Charging)
        idle(2_500)
        assertTrue(texts(root).toString(), hasText(root, "82%"))
        assertFalse(texts(root).any { it.contains("mA") || it == "Unavailable" })
        screenshot(activity, "10-charging-unavailable")
        controller.pause().stop().destroy()
    }

    @Test
    fun resultAndHistoryFlow() {
        val app = context.graph
        app.history.add(sampleResult())
        app.history.add(sampleResult(1_758_900_000_000).copy(downloadMbps = 18.4, uploadMbps = 2.2, pingMs = 48.0, jitterMs = 9.0, networkDetail = "Mobile · 4G", connectionType = "Mobile data"))
        Thread.sleep(300)
        val controller = launch()
        val activity = controller.get()
        val root = activity.window.decorView
        assertTrue(hasText(root, "↓ Down"))

        activity.graph.navigator.push(Route.Result(1_758_800_000_000))
        idle(2_000)
        listOf("325.7", "88.2", "4K ready", "Low latency", "HD group calls", "Cloudflare · BOM", "512.0 MB").forEach {
            assertTrue("missing $it in ${texts(root)}", hasText(root, it))
        }
        screenshot(activity, "11-result")

        activity.graph.navigator.push(Route.History)
        idle(2_000)
        screenshot(activity, "12-history")
        click(UiTestSupport.allViews(root).first { it.contentDescription?.startsWith("Delete result") == true && it.isShown })
        Thread.sleep(300)
        idle(2_000)
        assertEquals(1, app.history.history.value?.size)

        click(byText(root, "Clear"))
        assertTrue(hasText(root, "Clear history?"))
        screenshot(activity, "13-clear-sheet")
        click(byText(root, "Clear all"))
        Thread.sleep(300)
        idle(2_000)
        assertTrue(app.history.history.value!!.isEmpty())
        assertTrue(hasText(root, "No results yet"))
        controller.pause().stop().destroy()
    }

    @Test
    fun lightThemeAndLargeFontsRender() {
        context.graph.settings.update { it.copy(theme = ThemeMode.Light) }
        context.graph.history.add(sampleResult())
        Thread.sleep(300)
        var controller = launch()
        screenshot(controller.get(), "14-home-light")
        controller.get().graph.navigator.push(Route.Result(1_758_800_000_000))
        idle(2_000)
        screenshot(controller.get(), "15-result-light")
        controller.pause().stop().destroy()

        context.graph.navigator.replaceWithHome()
        context.graph.settings.update { it.copy(theme = ThemeMode.Dark) }
        RuntimeEnvironment.setFontScale(1.6f)
        controller = launch()
        val root = controller.get().window.decorView
        assertTrue(hasText(root, "Start test"))
        screenshot(controller.get(), "16-home-large-font")
        controller.pause().stop().destroy()
    }

    @Test
    fun themeChangeRecreatesButKeepsNavigation() {
        val controller = launch()
        val activity = controller.get()
        activity.graph.navigator.push(Route.Settings)
        idle()
        click(byText(activity.window.decorView, "Light"))
        assertEquals(ThemeMode.Light, activity.graph.settings.settings.value.theme)
        controller.recreate()
        idle()
        assertEquals(Route.Settings, controller.get().graph.navigator.current)
        assertTrue(hasText(controller.get().window.decorView, "Appearance"))
        controller.pause().stop().destroy()
        assertFalse(controller.get().graph.speedTest.uiState.value.isRunning)
    }
}
