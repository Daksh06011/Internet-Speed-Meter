package com.netspeedtest.speedtest

/** Phases a speed test moves through, in order. */
enum class TestPhase { Idle, Preparing, Ping, Download, Upload, Completed, Failed, Cancelled }

/** Why a test could not finish. Mapped to human friendly copy in the UI layer. */
enum class TestError {
    NoInternet,
    AirplaneMode,
    Timeout,
    ServerUnavailable,
    /** The server is rate-limiting (HTTP 429/503) — common behind carrier-grade NAT, where many users share one IP. */
    ServerBusy,
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
    val isEmpty: Boolean get() = serverLocation == null && clientIp == null && isp == null && city == null

    /** Fills gaps in this info from [other]; values already present win. */
    fun mergedWith(other: ConnectionInfo?): ConnectionInfo = if (other == null) this else ConnectionInfo(
        serverLocation ?: other.serverLocation, clientIp ?: other.clientIp, isp ?: other.isp, city ?: other.city,
    )

    companion object {
        /**
         * Reads flat JSON (`"key":"value"` or `"key":123`) or `key=value` lines. Only flat
         * values are needed, so a tiny parser keeps the engine free of JSON dependencies.
         */
        fun parse(body: String, server: SpeedTestServer): ConnectionInfo = from(server) { key ->
            val json = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|(-?[0-9.]+))").find(body)
            json?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                ?: body.lineSequence().map { it.split('=', limit = 2) }.firstOrNull { it.size == 2 && it[0].trim() == key }?.get(1)
        }

        /** Builds info from any key lookup (JSON body, key=value body or response headers). */
        fun from(server: SpeedTestServer, lookup: (String) -> String?): ConnectionInfo {
            fun first(keys: List<String>): String? = keys.firstNotNullOfOrNull { key ->
                lookup(key)?.replace("\\/", "/")?.replace("\\\"", "\"")?.trim()?.takeIf { it.isNotEmpty() }?.take(64)
            }
            val asn = first(server.asnKeys)?.takeIf { it.all(Char::isDigit) }
            return ConnectionInfo(
                serverLocation = first(server.locationKeys),
                clientIp = first(server.ipKeys),
                isp = first(server.ispKeys) ?: asn?.let { "AS$it" },
                city = first(server.cityKeys),
            )
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
