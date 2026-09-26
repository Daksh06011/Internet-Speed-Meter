package com.netspeedtest.speedtest

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections

/** Tunables for one full test run. Centralised so the trade-offs are visible. */
data class EngineConfig(
    val pingSamples: Int = 12,
    val download: TransferPlan,
    val upload: TransferPlan,
) {
    /** Single-connection mode (like Speedtest's "Single"): one stream each way. */
    fun singleStream(): EngineConfig = copy(download = download.copy(streams = 1), upload = upload.copy(streams = 1))

    companion object {
        /** Unmetered links (Wi-Fi, Ethernet): allow more data for accuracy at high speeds. */
        val Unmetered = EngineConfig(
            download = TransferPlan(streams = 4, maxDurationMs = 12_000, minDurationMs = 6_000, maxBytes = 600L * 1024 * 1024),
            upload = TransferPlan(streams = 3, maxDurationMs = 10_000, minDurationMs = 5_000, maxBytes = 250L * 1024 * 1024),
        )

        /** Metered links (mobile data): same method, tighter caps to protect data plans. */
        val Metered = EngineConfig(
            download = TransferPlan(streams = 4, maxDurationMs = 10_000, minDurationMs = 5_000, maxBytes = 150L * 1024 * 1024),
            upload = TransferPlan(streams = 3, maxDurationMs = 8_000, minDurationMs = 4_000, maxBytes = 60L * 1024 * 1024),
        )
    }
}

/**
 * Runs a complete speed test: server metadata, idle latency and jitter, download and
 * upload throughput, and latency under load. Pure Kotlin/JVM — no Android types — so
 * it can be exercised end-to-end against a local server in unit tests.
 *
 * All work happens in the caller's coroutine; cancelling it stops every connection.
 */
class SpeedTestEngine(
    private val http: HttpClient = HttpClient(),
    private val clock: MonotonicClock = JvmMonotonicClock,
) {
    private val ping = PingTester(http, clock)
    private val downloader = DownloadTester(http, clock)
    private val uploader = UploadTester(http, clock)

    data class Outcome(
        val downloadMbps: Double,
        val uploadMbps: Double,
        val pingMs: Double,
        val jitterMs: Double,
        val loadedLatencyDownMs: Double?,
        val loadedLatencyUpMs: Double?,
        val serverLocation: String?,
        val connectionInfo: ConnectionInfo?,
        val bytesUsed: Long,
    )

    suspend fun run(
        server: SpeedTestServer,
        config: EngineConfig,
        onUpdate: (EngineUpdate) -> Unit,
    ): Outcome {
        var state = EngineUpdate(TestPhase.Preparing)
        fun publish(next: EngineUpdate) {
            state = next
            onUpdate(next)
        }
        publish(state)

        val info = fetchConnectionInfo(server)
        val location = info?.serverLocation
        state = state.copy(serverLocation = location, connectionInfo = info)

        // Idle latency.
        publish(state.copy(phase = TestPhase.Ping, phaseProgress = 0f))
        val idle = ping.run(server, config.pingSamples) { samples, fraction ->
            publish(
                state.copy(
                    phaseProgress = fraction,
                    liveValue = samples.last(),
                    pingMs = LatencyStats.median(samples),
                    jitterMs = LatencyStats.jitter(samples),
                ),
            )
        }
        state = state.copy(pingMs = idle.medianMs, jitterMs = idle.jitterMs)

        // Download, with latency probes running alongside.
        publish(state.copy(phase = TestPhase.Download, phaseProgress = 0f, liveValue = 0.0))
        val (download, loadedDown) = withLoadedLatency(server) {
            downloader.run(server, config.download) { s ->
                publish(state.copy(phaseProgress = s.fraction, liveValue = s.currentMbps))
            }
        }
        state = state.copy(downloadMbps = download.mbps)

        // Upload, with latency probes running alongside.
        publish(state.copy(phase = TestPhase.Upload, phaseProgress = 0f, liveValue = 0.0))
        val (upload, loadedUp) = withLoadedLatency(server) {
            uploader.run(server, config.upload) { s ->
                publish(state.copy(phaseProgress = s.fraction, liveValue = s.currentMbps))
            }
        }
        state = state.copy(uploadMbps = upload.mbps)
        publish(state.copy(phase = TestPhase.Completed, phaseProgress = 1f, liveValue = download.mbps))

        return Outcome(
            downloadMbps = download.mbps,
            uploadMbps = upload.mbps,
            pingMs = idle.medianMs,
            jitterMs = idle.jitterMs,
            loadedLatencyDownMs = loadedDown,
            loadedLatencyUpMs = loadedUp,
            serverLocation = location,
            connectionInfo = info,
            bytesUsed = download.bytes + upload.bytes,
        )
    }

    /**
     * Collects connection details from every metadata source the server offers and merges
     * them. Details are optional: a missing endpoint is skipped. Only a network failure
     * (server unreachable) is reported, so the test fails fast instead of timing out later.
     */
    private suspend fun fetchConnectionInfo(server: SpeedTestServer): ConnectionInfo? {
        var info = ConnectionInfo(null, null, null, null)
        for (url in server.metadataUrls) {
            if (info.serverLocation != null && info.clientIp != null && info.isp != null && info.city != null) break
            info = info.mergedWith(readMetadata(url) { c ->
                val body = c.inputStream.bufferedReader().use { it.readText().take(MAX_METADATA_CHARS) }
                ConnectionInfo.parse(body, server)
            })
            if (url == server.metadataUrls.first()) {
                server.metadataHeaderPrefix?.let { prefix ->
                    info = info.mergedWith(readMetadata(server.pingUrl) { c ->
                        c.inputStream.use { input -> while (input.read() != -1) Unit }
                        ConnectionInfo.from(server) { key -> c.getHeaderField(prefix + key) }
                    })
                }
            }
        }
        return info.takeUnless { it.isEmpty }
    }

    private suspend fun readMetadata(url: String, read: (java.net.HttpURLConnection) -> ConnectionInfo): ConnectionInfo? = try {
        http.open(url).apply { setRequestProperty("Accept", "application/json, text/plain") }.runCancellable { c ->
            c.requireSuccess()
            read(c)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: SpeedTestException) {
        null // endpoint missing or busy: other sources may still answer
    } catch (e: java.io.IOException) {
        throw SpeedTestException(e.toTestError(), e)
    }

    /** Runs [block] while sampling latency every [LOADED_PROBE_INTERVAL_MS]. */
    private suspend fun <T> withLoadedLatency(server: SpeedTestServer, block: suspend () -> T): Pair<T, Double?> =
        coroutineScope {
            val samples = Collections.synchronizedList(ArrayList<Double>())
            val prober: Job = launch {
                delay(LOADED_PROBE_START_MS)
                while (isActive) {
                    try {
                        samples += ping.sample(server)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Probes that time out under heavy load are simply not counted.
                    }
                    delay(LOADED_PROBE_INTERVAL_MS)
                }
            }
            val result = try { block() } finally { prober.cancel() }
            val median = synchronized(samples) { if (samples.size >= 3) LatencyStats.median(samples) else null }
            result to median
        }

    private companion object {
        const val LOADED_PROBE_START_MS = 1_000L
        const val LOADED_PROBE_INTERVAL_MS = 500L
        const val MAX_METADATA_CHARS = 8_192
    }
}
