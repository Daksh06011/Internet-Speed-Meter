package com.netspeedtest.speedtest

import kotlin.math.abs

/** Converts a byte count transferred over [nanos] into megabits per second. */
fun megabitsPerSecond(bytes: Long, nanos: Long): Double =
    if (nanos <= 0L || bytes <= 0L) 0.0 else bytes * 8.0 / (nanos / NANOS_PER_SECOND) / 1_000_000.0

/**
 * Records cumulative byte counts at monotonic timestamps and derives two numbers:
 *  - [currentMbps]: throughput over a short sliding window (what the gauge shows live);
 *  - [steadyMbps]: throughput after the TCP ramp-up period is discarded (the result).
 *
 * The meter stores at most [capacity] samples in primitive arrays, so it allocates
 * nothing while a test is running.
 */
class ThroughputMeter(
    private val windowNanos: Long = 1_000_000_000L,
    private val capacity: Int = 512,
) {
    private val times = LongArray(capacity)
    private val bytes = LongArray(capacity)
    private var count = 0
    private var start = 0
    private var originNanos = Long.MIN_VALUE

    val sampleCount: Int get() = count

    fun reset(originNanos: Long) {
        count = 0
        start = 0
        this.originNanos = originNanos
        add(originNanos, 0L)
    }

    fun add(timeNanos: Long, totalBytes: Long) {
        if (count == capacity) { // drop oldest, keep most recent history
            start = (start + 1) % capacity
            count--
        }
        val index = (start + count) % capacity
        times[index] = timeNanos
        bytes[index] = totalBytes
        count++
    }

    private fun time(i: Int) = times[(start + i) % capacity]
    private fun bytesAt(i: Int) = bytes[(start + i) % capacity]

    val totalBytes: Long get() = if (count == 0) 0L else bytesAt(count - 1)
    val elapsedNanos: Long get() = if (count == 0) 0L else time(count - 1) - originNanos

    fun currentMbps(): Double {
        if (count < 2) return 0.0
        val lastTime = time(count - 1)
        var first = count - 2
        while (first > 0 && lastTime - time(first) < windowNanos) first--
        return megabitsPerSecond(bytesAt(count - 1) - bytesAt(first), lastTime - time(first))
    }

    /**
     * Throughput from the end of the warm-up period to the last sample. The warm-up is
     * [warmupFraction] of the elapsed time but at least [minWarmupNanos] (when the test
     * was long enough to afford it), which removes the TCP slow-start ramp.
     */
    fun steadyMbps(warmupFraction: Double = 0.25, minWarmupNanos: Long = 1_500_000_000L): Double {
        if (count < 2) return 0.0
        val elapsed = elapsedNanos
        var warmup = (elapsed * warmupFraction).toLong()
        if (elapsed > minWarmupNanos * 3) warmup = maxOf(warmup, minWarmupNanos)
        val cutoff = originNanos + warmup
        var i = 0
        while (i < count - 2 && time(i) < cutoff) i++
        val last = count - 1
        return megabitsPerSecond(bytesAt(last) - bytesAt(i), time(last) - time(i))
    }

    /** True when the last [spanNanos] of samples vary less than [tolerance] (relative). */
    fun isStable(spanNanos: Long, tolerance: Double): Boolean {
        if (count < 6) return false
        val lastTime = time(count - 1)
        if (lastTime - originNanos < spanNanos * 2) return false
        val half = spanNanos / 2
        val a = rateBetween(lastTime - spanNanos, lastTime - half)
        val b = rateBetween(lastTime - half, lastTime)
        if (a <= 0.0 || b <= 0.0) return false
        return abs(a - b) / maxOf(a, b) < tolerance
    }

    private fun rateBetween(fromNanos: Long, toNanos: Long): Double {
        var i = 0
        while (i < count - 1 && time(i) < fromNanos) i++
        var j = i
        while (j < count - 1 && time(j) < toNanos) j++
        return megabitsPerSecond(bytesAt(j) - bytesAt(i), time(j) - time(i))
    }
}

/** Latency statistics derived from a list of round-trip samples (milliseconds). */
object LatencyStats {
    fun median(samples: List<Double>): Double {
        require(samples.isNotEmpty())
        val sorted = samples.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /**
     * Jitter as the mean absolute difference between consecutive samples — the same
     * packet-delay-variation idea used by RFC 3550, applied to sequential round trips.
     */
    fun jitter(samples: List<Double>): Double {
        if (samples.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until samples.size) sum += abs(samples[i] - samples[i - 1])
        return sum / (samples.size - 1)
    }
}
