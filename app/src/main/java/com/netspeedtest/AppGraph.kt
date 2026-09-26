package com.netspeedtest

import android.app.Application
import android.os.SystemClock
import com.netspeedtest.data.HistoryRepository
import com.netspeedtest.data.SettingsRepository
import com.netspeedtest.device.BatteryInfoProvider
import com.netspeedtest.device.MemoryInfoProvider
import com.netspeedtest.device.NetworkInfoProvider
import com.netspeedtest.device.ThermalInfoProvider
import com.netspeedtest.device.TrafficMonitor
import com.netspeedtest.speedtest.HttpClient
import com.netspeedtest.speedtest.SpeedTestEngine
import com.netspeedtest.state.SpeedTestController
import com.netspeedtest.ui.nav.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Hand-wired dependency graph. Objects are created lazily on first use and hold only
 * the application context, so nothing can leak an Activity.
 *
 * [scope] is application-lifetime but idle: it only ever runs work the user started
 * (a speed test, a history write) and never schedules anything on its own.
 */
class AppGraph(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings by lazy { SettingsRepository(app) }
    val history by lazy { HistoryRepository(File(app.filesDir, "history.json"), scope) }
    val network by lazy { NetworkInfoProvider(app) }
    val battery by lazy { BatteryInfoProvider(app) }
    val thermal by lazy { ThermalInfoProvider(app) }
    val memory by lazy { MemoryInfoProvider(app) }
    val traffic by lazy { TrafficMonitor() }
    val navigator by lazy { Navigator() }

    val speedTest by lazy {
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "1"
        SpeedTestController(
            engine = SpeedTestEngine(
                http = HttpClient(userAgent = "NetSpeedTest/$version (Android)"),
                clock = { SystemClock.elapsedRealtimeNanos() },
            ),
            network = network,
            history = history,
            scope = scope,
        )
    }
}
