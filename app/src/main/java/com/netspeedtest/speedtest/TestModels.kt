package com.netspeedtest.speedtest

/** Phases a speed test moves through, in order. */
enum class TestPhase { Idle, Preparing, Ping, Download, Upload, Completed, Failed, Cancelled }

/** Why a test could not finish. Mapped to human friendly copy in the UI layer. */
enum class TestError {
    NoInternet,
    AirplaneMode,
    Timeout,
    ServerUnavailable,
    SecureConnectionFailed,
    ConnectionLost,
    NetworkChanged,
    Backgrounded,
}

class SpeedTestException(val error: TestError, cause: Throwable? = null) : Exception(error.name, cause)

/**
 * A live snapshot emitted by the engine while it runs. All speeds are in megabits per
 * second (10^6 bits/s), all times in milliseconds. `null` means "not measured yet".
 */
data class EngineUpdate(
    val phase: TestPhase,
    /** Progress of the current phase, 0..1. */
    val phaseProgress: Float = 0f,
    /** The value the big gauge should show right now (Mbps, or ms during ping). */
    val liveValue: Double? = null,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val serverLocation: String? = null,
    val connectionInfo: ConnectionInfo? = null,
)

/** Who the server sees: public IP, internet provider and approximate city. */
data class ConnectionInfo(val serverLocation: String?, val clientIp: String?, val isp: String?, val city: String?) {
    companion object {
        /**
         * Reads flat JSON (`"key":"value"`) or `key=value` lines. Only string values are
         * needed, so a tiny parser keeps the engine free of JSON dependencies.
         */
        fun parse(body: String, server: SpeedTestServer): ConnectionInfo {
            fun value(key: String?): String? {
                if (key == null) return null
                val json = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(body)
                val raw = json?.groupValues?.get(1)
                    ?: body.lineSequence().map { it.split('=', limit = 2) }.firstOrNull { it.size == 2 && it[0].trim() == key }?.get(1)
                return raw?.replace("\\/", "/")?.replace("\\\"", "\"")?.trim()?.takeIf { it.isNotEmpty() }?.take(64)
            }
            return ConnectionInfo(value(server.locationKey), value(server.ipKey), value(server.ispKey), value(server.cityKey))
        }
    }
}

/** Final, immutable outcome of a completed test. */
data class SpeedTestResult(
    val timestampMillis: Long,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double,
    val jitterMs: Double,
    /** Median latency measured while the download was saturating the link. */
    val loadedLatencyDownMs: Double?,
    /** Median latency measured while the upload was saturating the link. */
    val loadedLatencyUpMs: Double?,
    val connectionType: String,
    val networkDetail: String,
    val serverName: String,
    val bytesUsed: Long,
    val isp: String? = null,
    val clientIp: String? = null,
)
