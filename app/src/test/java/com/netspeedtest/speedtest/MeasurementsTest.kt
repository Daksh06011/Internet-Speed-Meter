package com.netspeedtest.speedtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementsTest {
    private val second = 1_000_000_000L

    @Test
    fun megabitsPerSecondUsesDecimalMegabits() {
        assertEquals(8.0, megabitsPerSecond(1_000_000, second), 1e-9)
        assertEquals(100.0, megabitsPerSecond(12_500_000, second), 1e-9)
        assertEquals(0.0, megabitsPerSecond(1_000, 0), 0.0)
    }

    @Test
    fun steadyRateIgnoresSlowStart() {
        val meter = ThroughputMeter()
        meter.reset(0)
        // First 2 s crawl at 1 MB/s (slow start), then 10 MB/s for 6 s.
        var bytes = 0L
        for (i in 1..80) {
            bytes += if (i <= 20) 100_000 else 1_000_000
            meter.add(i * second / 10, bytes)
        }
        assertEquals(80.0, meter.steadyMbps(), 0.5) // 10 MB/s = 80 Mbps
        assertEquals(80.0, meter.currentMbps(), 0.5)
        assertTrue(meter.isStable(2 * second, 0.04))
    }

    @Test
    fun unstableRateIsNotStable() {
        val meter = ThroughputMeter()
        meter.reset(0)
        var bytes = 0L
        for (i in 1..60) {
            bytes += if (i < 50) 100_000 else 900_000
            meter.add(i * second / 10, bytes)
        }
        assertFalse(meter.isStable(2 * second, 0.04))
    }

    @Test
    fun ringBufferKeepsMostRecentSamples() {
        val meter = ThroughputMeter(capacity = 16)
        meter.reset(0)
        for (i in 1..100) meter.add(i * second / 10, i * 125_000L)
        assertEquals(10.0, meter.currentMbps(), 1e-6)
        assertEquals(100 * 125_000L, meter.totalBytes)
    }

    @Test
    fun latencyStats() {
        assertEquals(20.0, LatencyStats.median(listOf(30.0, 10.0, 20.0)), 0.0)
        assertEquals(15.0, LatencyStats.median(listOf(10.0, 20.0)), 0.0)
        // |20-10| + |15-20| + |25-15| = 25 over 3 gaps
        assertEquals(25.0 / 3, LatencyStats.jitter(listOf(10.0, 20.0, 15.0, 25.0)), 1e-9)
        assertEquals(0.0, LatencyStats.jitter(listOf(10.0)), 0.0)
    }

    @Test
    fun serverTimingParsing() {
        assertEquals(12.5, PingTester.parseServerTimingMs("cfRequestDuration;dur=12.5")!!, 1e-9)
        assertEquals(3.0, PingTester.parseServerTimingMs("db;dur=3, app;dur=47")!!, 1e-9)
        assertNull(PingTester.parseServerTimingMs("cache;desc=hit"))
        assertNull(PingTester.parseServerTimingMs(null))
    }

    @Test
    fun connectionInfoParsesJsonAndKeyValue() {
        val cf = SpeedTestServers.Cloudflare
        val json = ConnectionInfo.parse("""{"clientIp":"2001:db8::1","asOrganization":"Jio","colo":"BOM","city":"Mumbai"}""", cf)
        assertEquals(ConnectionInfo("BOM", "2001:db8::1", "Jio", "Mumbai"), json)
        val kv = ConnectionInfo.parse("colo=DEL\nclientIp=10.0.0.1\n", cf)
        assertEquals("DEL", kv.serverLocation)
        assertEquals("10.0.0.1", kv.clientIp)
        assertNull(kv.isp)
    }

    @Test
    fun downloadUrlTemplate() {
        assertEquals("https://speed.cloudflare.com/__down?bytes=42", SpeedTestServers.Cloudflare.downloadUrl(42))
    }
}

class QualityRatingTest {
    private fun grades(d: Double, u: Double, p: Double, j: Double) =
        QualityRating.evaluate(d, u, p, j).associate { it.activity to it.label }

    @Test
    fun fastLowLatencyConnection() {
        val g = grades(300.0, 80.0, 12.0, 2.0)
        assertEquals("Excellent", g["Browsing"])
        assertEquals("4K ready", g["Streaming"])
        assertEquals("Low latency", g["Gaming"])
        assertEquals("HD group calls", g["Video calls"])
    }

    @Test
    fun thresholdsAreNotExaggerated() {
        val g = grades(24.9, 2.9, 45.0, 12.0)
        assertEquals("HD ready", g["Streaming"]) // 24.9 < 25 Mbps is not "4K ready"
        assertEquals("Good", g["Gaming"])
        assertEquals("HD 1:1 calls", g["Video calls"]) // upload below 3 Mbps
    }

    @Test
    fun slowHighLatencyConnection() {
        val g = grades(1.0, 0.3, 350.0, 80.0)
        assertEquals("Slow", g["Browsing"])
        assertEquals("May buffer", g["Streaming"])
        assertEquals("High latency", g["Gaming"])
        assertEquals("Unreliable", g["Video calls"])
    }
}
