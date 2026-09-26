package com.netspeedtest.speedtest

/**
 * Monotonic time source used for every throughput and latency calculation. On Android
 * this is backed by `SystemClock.elapsedRealtimeNanos()`; tests use [System.nanoTime].
 * Wall-clock time is never used for measurements because it can jump.
 */
fun interface MonotonicClock {
    fun nanos(): Long
}

object JvmMonotonicClock : MonotonicClock {
    override fun nanos(): Long = System.nanoTime()
}

internal const val NANOS_PER_MS = 1_000_000.0
internal const val NANOS_PER_SECOND = 1_000_000_000.0
