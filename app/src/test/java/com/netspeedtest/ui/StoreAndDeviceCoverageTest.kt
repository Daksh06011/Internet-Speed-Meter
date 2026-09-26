package com.netspeedtest.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.netspeedtest.MainActivity
import com.netspeedtest.data.ThemeMode
import com.netspeedtest.graph
import com.netspeedtest.speedtest.SpeedTestResult
import com.netspeedtest.ui.UiTestSupport.hasText
import com.netspeedtest.ui.UiTestSupport.idle
import com.netspeedtest.ui.UiTestSupport.sendBattery
import com.netspeedtest.ui.nav.Route
import com.netspeedtest.ui.theme.Fonts
import com.netspeedtest.ui.theme.Palette
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Android 16 (API 36) behaviour, large screens, right-to-left locales, and the Google
 * Play store graphics — rendered from the real UI so the listing matches the app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreAndDeviceCoverageTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val storeDir get() = System.getProperty("screenshots.dir")?.let { File(it).parentFile.resolve("play-store") }

    private val sample = SpeedTestResult(
        timestampMillis = 1_758_800_000_000, downloadMbps = 325.7, uploadMbps = 88.2, pingMs = 14.3, jitterMs = 2.1,
        loadedLatencyDownMs = 41.0, loadedLatencyUpMs = 63.0, connectionType = "Wi‑Fi", networkDetail = "Wi‑Fi · 5 GHz",
        serverName = "Cloudflare · FRA", bytesUsed = 512_000_000, isp = "Deutsche Telekom AG", clientIp = "203.0.113.24",
    )

    @Before
    fun seed() {
        // A validated Wi‑Fi connection, as on a typical phone.
        val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
        org.robolectric.Shadows.shadowOf(caps).apply {
            addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        org.robolectric.Shadows.shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)
        sendBattery(app)
        app.graph.history.add(sample)
        Thread.sleep(300)
    }

    private fun save(bitmap: Bitmap, name: String) {
        val dir = storeDir ?: return
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun viewport(activity: Activity, name: String) {
        val root = activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).also { it.drawColor(Palette.resolve(activity, app.graph.settings.settings.value.theme).background) }
            .let { root.draw(it) }
        save(bitmap, name)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h800dp-land-mdpi")
    fun android16TabletLandscape() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
        val root = c.get().window.decorView
        assertTrue(hasText(root, "Start test"))
        // Content is centred at a readable width instead of stretching 1280dp wide.
        val hero = UiTestSupport.allViews(root).first { it is com.netspeedtest.ui.components.SpeedGauge }
        assertTrue("gauge width ${hero.width}", hero.width < 700 * app.resources.displayMetrics.density)
        viewport(c.get(), "tablet-landscape")
        c.get().graph.navigator.push(Route.Result(sample.timestampMillis))
        idle(2_000)
        assertTrue(hasText(root, "Deutsche Telekom AG"))
        c.pause().stop().destroy()
    }

    @Test
    @Config(sdk = [36], qualifiers = "ar-rEG-ldrtl-w400dp-h860dp-xhdpi")
    fun rightToLeftLocale() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
        assertTrue(hasText(c.get().window.decorView, "Start test"))
        UiTestSupport.screenshot(c.get(), "17-rtl-arabic-locale")
        c.pause().stop().destroy()
    }

    /** Phone screenshots for the Play listing: 1080 × 1920 (9:16). */
    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
    fun playStoreScreenshots() {
        var c = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle(2_000)
        assertTrue(hasText(c.get().window.decorView, "Wi‑Fi · Connected"))
        viewport(c.get(), "phone-1-home")
        val nav = c.get().graph.navigator
        for ((route, name) in listOf(
            Route.Result(sample.timestampMillis) to "phone-2-result",
            Route.Network to "phone-3-network",
            Route.History to "phone-4-history",
            Route.Battery to "phone-5-battery",
        )) {
            nav.push(route)
            idle(2_500)
            viewport(c.get(), name)
            nav.pop()
            idle()
        }
        c.pause().stop().destroy()

        app.graph.settings.update { it.copy(theme = ThemeMode.Light) }
        c = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle(2_000)
        viewport(c.get(), "phone-6-home-light")
        c.pause().stop().destroy()
    }

    /** 512 × 512 high-res icon and 1024 × 500 feature graphic required by Google Play. */
    @Test
    @Config(sdk = [36])
    fun playStoreGraphics() {
        val p = Palette.Dark
        // Full-resolution cut-out of the icon artwork (the gauge without bezel or white background).
        val art = javaClass.classLoader!!.getResourceAsStream("brand/gauge-art.png")!!.use { android.graphics.BitmapFactory.decodeStream(it) }
        val face = 0xFF010411.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // 512 × 512 Play icon: full-bleed navy face with the gauge centred (Play applies its own rounded mask).
        val icon = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        Canvas(icon).apply {
            drawColor(face)
            val size = 512f * 1.0f
            drawBitmap(art, null, RectF(256 - size / 2, 256 - size / 2, 256 + size / 2, 256 + size / 2), paint)
        }
        save(icon, "icon-512")

        val feature = Bitmap.createBitmap(1024, 500, Bitmap.Config.ARGB_8888)
        Canvas(feature).apply {
            drawColor(face)
            drawBitmap(art, null, RectF(520f, 0f, 1020f, 500f), paint)
            val text = Paint(Paint.ANTI_ALIAS_FLAG)
            text.typeface = Fonts.sans(600); text.textSize = 68f; text.color = p.textPrimary; text.letterSpacing = -0.02f
            drawText("Net Speed Test", 72f, 220f, text)
            text.typeface = Fonts.sans(400); text.textSize = 30f; text.color = p.textSecondary; text.letterSpacing = 0f
            drawText("Download · Upload · Ping · Jitter", 72f, 280f, text)
            text.typeface = Fonts.mono(500); text.textSize = 20f; text.color = p.accent; text.letterSpacing = 0.12f
            drawText("NO ADS · NO TRACKING · NO ACCOUNT", 72f, 340f, text)
        }
        save(feature, "feature-graphic-1024x500")
        assertTrue(storeDir == null || File(storeDir, "icon-512.png").length() > 1000)
    }
}
