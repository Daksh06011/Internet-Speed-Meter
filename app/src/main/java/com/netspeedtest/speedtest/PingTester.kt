package com.netspeedtest.speedtest

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Measures latency as HTTP round-trip time on a warm (keep-alive) connection: the time
 * between sending a request for an empty body and receiving the response headers. Any
 * server processing time the server reports through `Server-Timing` is subtracted.
 */
class PingTester(
    private val http: HttpClient,
    private val clock: MonotonicClock,
) {
    data class Result(val medianMs: Double, val jitterMs: Double, val minMs: Double, val samples: List<Double>)

    /** One latency sample in milliseconds. */
    suspend fun sample(server: SpeedTestServer): Double {
        val connection = http.open(server.pingUrl)
        return connection.runCancellable { c ->
            val start = clock.nanos()
            c.requireSuccess()
            val end = clock.nanos()
            val serverMs = parseServerTimingMs(c.getHeaderField("Server-Timing"))
            c.inputStream.use { input -> while (input.read(DRAIN) != -1) Unit }
            val rtt = (end - start) / NANOS_PER_MS
            if (serverMs != null && serverMs < rtt) rtt - serverMs else rtt
        }
    }

    /**
     * Takes one warm-up sample (DNS + TCP + TLS setup, discarded) and then [count]
     * measured samples. [onProgress] receives the samples collected so far.
     */
    suspend fun run(
        server: SpeedTestServer,
        count: Int = 12,
        onProgress: (samples: List<Double>, fraction: Float) -> Unit,
    ): Result {
        // Warm-up: establishes the connection (DNS + TCP + TLS); retried if the server is busy.
        var attempt = 0
        while (true) {
            try {
                sample(server)
                break
            } catch (e: SpeedTestException) {
                if (e.error != TestError.ServerBusy || ++attempt >= WARMUP_ATTEMPTS) throw e
                delay(BUSY_BACKOFF_MS * attempt)
            }
        }
        val samples = ArrayList<Double>(count)
        var failures = 0
        while (samples.size < count) {
            coroutineContext.ensureActive()
            try {
                samples += sample(server)
                onProgress(samples, samples.size / count.toFloat())
            } catch (e: SpeedTestException) {
                // A rate-limited probe is retried after a pause; anything else is fatal.
                if (e.error != TestError.ServerBusy || ++failures > count / 2) throw e
                delay(BUSY_BACKOFF_MS)
            } catch (e: java.io.IOException) {
                // A single lost probe is tolerated; a flaky link that loses most is an error.
                if (++failures > count / 2) throw e
            }
            delay(PROBE_GAP_MS)
        }
        return Result(
            medianMs = LatencyStats.median(samples),
            jitterMs = LatencyStats.jitter(samples),
            minMs = samples.min(),
            samples = samples,
        )
    }

    companion object {
        private const val PROBE_GAP_MS = 40L
        private const val BUSY_BACKOFF_MS = 1_000L
        private const val WARMUP_ATTEMPTS = 5
        private val DRAIN = ByteArray(1024)

        /** Extracts the first `dur=` value (milliseconds) from a Server-Timing header. */
        fun parseServerTimingMs(header: String?): Double? {
            if (header.isNullOrEmpty()) return null
            val index = header.indexOf("dur=")
            if (index < 0) return null
            val end = header.indexOfAny(charArrayOf(',', ';', ' '), index + 4).let { if (it < 0) header.length else it }
            return header.substring(index + 4, end).toDoubleOrNull()?.takeIf { it >= 0 }
        }
    }
}
