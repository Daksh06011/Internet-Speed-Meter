package com.netspeedtest.state

import android.os.Looper
import com.netspeedtest.data.HistoryRepository
import com.netspeedtest.device.NetworkInfoProvider
import com.netspeedtest.speedtest.SpeedTestEngine
import com.netspeedtest.speedtest.LocalSpeedServer
import com.netspeedtest.speedtest.TestError
import com.netspeedtest.speedtest.TestPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeedTestControllerTest {
    private val app get() = RuntimeEnvironment.getApplication()

    private fun pump(until: () -> Boolean, timeoutMs: Long = 60_000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!until()) {
            check(System.currentTimeMillis() < end) { "timed out" }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(20)
        }
    }

    private fun controller(server: LocalSpeedServer, scope: CoroutineScope, history: HistoryRepository) =
        SpeedTestController(SpeedTestEngine(), NetworkInfoProvider(app), history, scope, server.config)

    @Test
    fun runsSavesAndGuardsAgainstDoubleStart() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val history = HistoryRepository(File(app.filesDir, "h.json"), scope)
        LocalSpeedServer(downBytesPerSec = 20_000_000, upBytesPerSec = 20_000_000).use { server ->
            val c = controller(server, scope, history)
            val phases = LinkedHashSet<TestPhase>()
            c.start()
            c.start() // second tap while running must be ignored
            pump({ phases += c.uiState.value.phase; !c.uiState.value.isRunning }, timeoutMs = 90_000)
            val state = c.uiState.value
            assertEquals(state.error.toString(), TestPhase.Completed, state.phase)
            assertTrue(phases.containsAll(listOf(TestPhase.Preparing, TestPhase.Ping, TestPhase.Download, TestPhase.Upload)))
            val result = assertNotNull(state.result).let { state.result!! }
            assertTrue(result.downloadMbps > 10 && result.uploadMbps > 10)
            pump({ history.history.value?.size == 1 })
            assertEquals(result, history.history.value!!.first())
        }
        scope.cancel()
    }

    @Test
    fun cancelStopsTheTest() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val history = HistoryRepository(File(app.filesDir, "h2.json"), scope)
        LocalSpeedServer(downBytesPerSec = 1_000_000).use { server ->
            val c = controller(server, scope, history)
            c.start()
            pump({ c.uiState.value.phase == TestPhase.Download })
            c.cancel(TestError.Backgrounded)
            assertEquals(TestPhase.Cancelled, c.uiState.value.phase)
            assertEquals(TestError.Backgrounded, c.uiState.value.error)
            pump({ server.activeTransfers.get() == 0 }, timeoutMs = 5_000)
            // Late engine updates must not revive the cancelled test.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            assertEquals(TestPhase.Cancelled, c.uiState.value.phase)
        }
        scope.cancel()
    }
}
