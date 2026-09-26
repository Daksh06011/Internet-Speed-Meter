package com.netspeedtest.speedtest

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Limits that keep a single test fast and bounded in data usage. */
data class TransferPlan(
    val streams: Int,
    val maxDurationMs: Long,
    /** A test may stop early once throughput is stable and at least this long has passed. */
    val minDurationMs: Long,
    /** Hard cap on data moved in this phase (protects metered connections). */
    val maxBytes: Long,
    /** A phase with no progress for this long is treated as a timeout. */
    val stallTimeoutMs: Long = 8_000,
)

data class TransferSample(val currentMbps: Double, val fraction: Float, val bytes: Long)

data class TransferResult(val mbps: Double, val bytes: Long, val durationMs: Long)

/**
 * Shared machinery for download and upload measurements.
 *
 * [TransferPlan.streams] parallel HTTP streams move data while a sampler records the
 * cumulative byte count against monotonic time every [SAMPLE_INTERVAL_MS]. Payload data
 * is streamed through a small reusable buffer and discarded immediately; nothing is
 * kept in memory or written to storage.
 */
abstract class TransferTester(
    protected val http: HttpClient,
    private val clock: MonotonicClock,
) {
    /** Moves one request worth of data, adding every transferred byte to [counter]. */
    protected abstract fun transferOnce(connection: HttpURLConnection, requestBytes: Long, counter: AtomicLong, buffer: ByteArray)

    /** Size of the next request for a stream, given the current aggregate throughput. */
    protected abstract fun nextRequestBytes(currentMbps: Double, streams: Int): Long

    suspend fun run(
        server: SpeedTestServer,
        plan: TransferPlan,
        onSample: (TransferSample) -> Unit,
    ): TransferResult = coroutineScope {
        val counter = AtomicLong()
        val meter = ThroughputMeter()
        val liveMbps = AtomicReference(0.0)
        val failure = AtomicReference<Throwable?>(null)
        val activeStreams = AtomicInteger(plan.streams)
        val origin = clock.nanos()
        meter.reset(origin)

        val workers = List(plan.streams) {
            launch(Dispatchers.IO) {
                val buffer = ByteArray(BUFFER_SIZE)
                var consecutiveErrors = 0
                try {
                    while (isActive) {
                        try {
                            val size = nextRequestBytes(liveMbps.get(), plan.streams)
                            val connection = openFor(server, size)
                            connection.runCancellable { transferOnce(it, size, counter, buffer) }
                            consecutiveErrors = 0
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            val busy = e is SpeedTestException && e.error == TestError.ServerBusy
                            if ((e is SpeedTestException && !busy) || ++consecutiveErrors >= MAX_STREAM_RETRIES) {
                                failure.compareAndSet(null, e)
                                break
                            }
                            delay((if (busy) BUSY_BACKOFF_MS else RETRY_BACKOFF_MS) * consecutiveErrors)
                        }
                    }
                } finally {
                    activeStreams.decrementAndGet()
                }
            }
        }

        var lastProgressNanos = origin
        var lastBytes = 0L
        try {
            while (true) {
                delay(SAMPLE_INTERVAL_MS)
                val now = clock.nanos()
                val total = counter.get()
                meter.add(now, total)
                val current = meter.currentMbps()
                liveMbps.set(current)
                val elapsedMs = (now - origin) / NANOS_PER_MS
                onSample(TransferSample(current, (elapsedMs / plan.maxDurationMs).toFloat().coerceIn(0f, 1f), total))

                if (total > lastBytes) {
                    lastBytes = total
                    lastProgressNanos = now
                }
                val stalledMs = (now - lastProgressNanos) / NANOS_PER_MS
                when {
                    activeStreams.get() == 0 -> throw failure.get() ?: SpeedTestException(TestError.ConnectionLost)
                    stalledMs > plan.stallTimeoutMs -> throw failure.get() ?: SpeedTestException(TestError.Timeout)
                    elapsedMs >= plan.maxDurationMs -> break
                    total >= plan.maxBytes -> break
                    elapsedMs >= plan.minDurationMs && meter.isStable(STABLE_SPAN_NANOS, STABLE_TOLERANCE) -> break
                }
            }
        } finally {
            workers.forEach { it.cancel() }
        }
        val result = TransferResult(
            mbps = meter.steadyMbps(),
            bytes = meter.totalBytes,
            durationMs = (meter.elapsedNanos / NANOS_PER_MS).toLong(),
        )
        onSample(TransferSample(result.mbps, 1f, result.bytes))
        result
    }

    protected abstract fun openFor(server: SpeedTestServer, requestBytes: Long): HttpURLConnection

    companion object {
        const val SAMPLE_INTERVAL_MS = 100L
        const val BUFFER_SIZE = 64 * 1024
        private const val MAX_STREAM_RETRIES = 3
        private const val RETRY_BACKOFF_MS = 250L
        private const val BUSY_BACKOFF_MS = 700L
        private const val STABLE_SPAN_NANOS = 2_000_000_000L
        private const val STABLE_TOLERANCE = 0.04

        /** Bytes that one stream should move in roughly [seconds] at [mbps] total. */
        internal fun bytesFor(mbps: Double, streams: Int, seconds: Double): Long =
            (mbps * 1_000_000 / 8 * seconds / streams).toLong()
    }
}

/** Downloads payloads of the requested size and discards them as they arrive. */
class DownloadTester(http: HttpClient, clock: MonotonicClock) : TransferTester(http, clock) {
    override fun openFor(server: SpeedTestServer, requestBytes: Long): HttpURLConnection =
        http.open(server.downloadUrl(requestBytes))

    override fun nextRequestBytes(currentMbps: Double, streams: Int): Long =
        bytesFor(currentMbps, streams, seconds = 2.0).coerceIn(MIN_REQUEST, MAX_REQUEST)

    override fun transferOnce(connection: HttpURLConnection, requestBytes: Long, counter: AtomicLong, buffer: ByteArray) {
        connection.requireSuccess()
        connection.inputStream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                counter.addAndGet(read.toLong())
            }
        }
    }

    private companion object {
        const val MIN_REQUEST = 1L * 1024 * 1024
        const val MAX_REQUEST = 50L * 1024 * 1024
    }
}

/**
 * Uploads request bodies of the requested size. The body is a fixed block of random
 * (incompressible) bytes written repeatedly, so memory use stays at one small buffer.
 * Bytes are counted as they are handed to the socket; the steady-state calculation
 * discards the ramp-up period in which the socket send buffer is being filled.
 */
class UploadTester(http: HttpClient, clock: MonotonicClock) : TransferTester(http, clock) {
    override fun openFor(server: SpeedTestServer, requestBytes: Long): HttpURLConnection =
        http.open(server.uploadUrl, method = "POST").apply {
            doOutput = true
            setFixedLengthStreamingMode(requestBytes)
            setRequestProperty("Content-Type", "application/octet-stream")
        }

    override fun nextRequestBytes(currentMbps: Double, streams: Int): Long =
        bytesFor(currentMbps, streams, seconds = 1.5).coerceIn(MIN_REQUEST, MAX_REQUEST)

    override fun transferOnce(connection: HttpURLConnection, requestBytes: Long, counter: AtomicLong, buffer: ByteArray) {
        val payload = PAYLOAD
        var remaining = requestBytes
        connection.outputStream.use { output ->
            while (remaining > 0) {
                val chunk = minOf(remaining, payload.size.toLong()).toInt()
                output.write(payload, 0, chunk)
                remaining -= chunk
                counter.addAndGet(chunk.toLong())
            }
        }
        connection.requireSuccess()
        connection.inputStream.use { input -> while (input.read(buffer) >= 0) Unit }
    }

    private companion object {
        const val MIN_REQUEST = 256L * 1024
        const val MAX_REQUEST = 16L * 1024 * 1024
        /** 64 KiB of random bytes shared by all upload streams (read-only). */
        val PAYLOAD: ByteArray by lazy { ByteArray(BUFFER_SIZE).also { Random(0x5EED).nextBytes(it) } }
    }
}
