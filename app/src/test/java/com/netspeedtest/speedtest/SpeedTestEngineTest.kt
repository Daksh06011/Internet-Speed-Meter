package com.netspeedtest.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections

/** End-to-end engine tests over real loopback sockets with throttled transfers. */
class SpeedTestEngineTest {
    private fun plan(streams: Int, ms: Long) =
        TransferPlan(streams = streams, maxDurationMs = ms, minDurationMs = ms, maxBytes = Long.MAX_VALUE)

    @Test
    fun measuresThrottledDownloadAndUpload() = runBlocking {
        // 4 × 1.5 MB/s = 48 Mbps down; 3 × 1 MB/s = 24 Mbps up.
        LocalSpeedServer(downBytesPerSec = 1_500_000, upBytesPerSec = 1_000_000).use { server ->
            val phases = Collections.synchronizedList(ArrayList<TestPhase>())
            val engine = SpeedTestEngine(HttpClient(), JvmMonotonicClock)
            val config = EngineConfig(pingSamples = 6, download = plan(4, 5_000), upload = plan(3, 5_000))
            val outcome = withTimeout(30_000) {
                engine.run(server.config, config) { u -> if (phases.lastOrNull() != u.phase) phases += u.phase }
            }
            println("download=${outcome.downloadMbps} upload=${outcome.uploadMbps} ping=${outcome.pingMs} jitter=${outcome.jitterMs} loaded=${outcome.loadedLatencyDownMs}/${outcome.loadedLatencyUpMs} bytes=${outcome.bytesUsed}")

            assertEquals(listOf(TestPhase.Preparing, TestPhase.Ping, TestPhase.Download, TestPhase.Upload, TestPhase.Completed), phases.toList())
            assertEquals(48.0, outcome.downloadMbps, 48.0 * 0.2)
            assertEquals(24.0, outcome.uploadMbps, 24.0 * 0.3)
            assertTrue("ping should be a positive loopback RTT", outcome.pingMs > 0 && outcome.pingMs < 50)
            assertTrue(outcome.jitterMs >= 0)
            assertEquals("TST", outcome.serverLocation)
            assertEquals("Test ISP \"Fibre\"", outcome.connectionInfo?.isp)
            assertEquals("203.0.113.7", outcome.connectionInfo?.clientIp)
            assertEquals("Testville", outcome.connectionInfo?.city)
            assertNotNull(outcome.loadedLatencyDownMs)
            assertTrue(outcome.bytesUsed > 10_000_000)
        }
    }

    @Test
    fun singleConnectionModeUsesOneStream() = runBlocking {
        LocalSpeedServer(downBytesPerSec = 1_500_000, upBytesPerSec = 1_000_000).use { server ->
            val config = EngineConfig(pingSamples = 3, download = plan(4, 3_000), upload = plan(3, 3_000)).singleStream()
            val outcome = withTimeout(30_000) { SpeedTestEngine().run(server.config, config) {} }
            // One throttled stream: 1.5 MB/s = 12 Mbps down, 1 MB/s = 8 Mbps up.
            assertEquals(12.0, outcome.downloadMbps, 12.0 * 0.25)
            assertEquals(8.0, outcome.uploadMbps, 8.0 * 0.35)
        }
    }

    @Test
    fun cancellationStopsAllTransfersPromptly() = runBlocking {
        LocalSpeedServer(downBytesPerSec = 2_000_000).use { server ->
            val engine = SpeedTestEngine(HttpClient(), JvmMonotonicClock)
            val config = EngineConfig(pingSamples = 3, download = plan(4, 60_000), upload = plan(3, 60_000))
            val job = async(Dispatchers.Default) { engine.run(server.config, config) {} }
            // Wait until the download is actually streaming.
            withTimeout(10_000) { while (server.activeTransfers.get() < 4) delay(20) }
            val started = System.nanoTime()
            job.cancel()
            job.join()
            val cancelMs = (System.nanoTime() - started) / 1e6
            println("cancelled in ${cancelMs}ms")
            assertTrue("cancel took $cancelMs ms", cancelMs < 1_000)
            // Server side sees the connections drop.
            withTimeout(5_000) { while (server.activeTransfers.get() > 0) delay(20) }
            val served = server.bytesServed.get()
            delay(500)
            assertEquals("no data may flow after cancellation", served, server.bytesServed.get())
        }
    }

    @Test
    fun serverErrorIsReportedAsServerUnavailable() = runBlocking {
        LocalSpeedServer(failWith = 503).use { server ->
            val error = runCatching {
                SpeedTestEngine().run(server.config, EngineConfig(download = plan(1, 1000), upload = plan(1, 1000))) {}
            }.exceptionOrNull()
            assertEquals(TestError.ServerUnavailable, error?.toTestError())
        }
    }

    @Test
    fun unreachableServerFailsFast() = runBlocking {
        val port = java.net.ServerSocket(0).use { it.localPort } // closed port: connection refused
        val config = SpeedTestServer("x", "x", "http://127.0.0.1:$port/p", "http://127.0.0.1:$port/d?b={bytes}", "http://127.0.0.1:$port/u", "http://127.0.0.1:$port/t", "colo")
        val error = runCatching {
            withTimeout(5_000) { SpeedTestEngine().run(config, EngineConfig(download = plan(1, 1000), upload = plan(1, 1000))) {} }
        }.exceptionOrNull()
        assertEquals(TestError.ServerUnavailable, error?.toTestError())
    }

    @Test
    fun stalledTransferTimesOut() = runBlocking {
        LocalSpeedServer(stallDownloads = true).use { server ->
            val engine = SpeedTestEngine(HttpClient(connectTimeoutMs = 1_000, readTimeoutMs = 1_000), JvmMonotonicClock)
            val download = TransferPlan(streams = 2, maxDurationMs = 20_000, minDurationMs = 20_000, maxBytes = Long.MAX_VALUE, stallTimeoutMs = 1_500)
            val started = System.nanoTime()
            val error = runCatching {
                withTimeout(15_000) { engine.run(server.config, EngineConfig(pingSamples = 2, download = download, upload = plan(1, 1000))) {} }
            }.exceptionOrNull()
            val tookMs = (System.nanoTime() - started) / 1e6
            if (error == null) fail("expected a timeout")
            assertEquals(TestError.Timeout, error!!.toTestError())
            assertTrue("took $tookMs ms", tookMs < 8_000)
        }
    }
}
