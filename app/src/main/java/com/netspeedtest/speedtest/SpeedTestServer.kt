package com.netspeedtest.speedtest

/**
 * Describes where the speed test sends its traffic. Everything the engine needs from a
 * server is expressed here, so switching provider (or self-hosting) means adding one
 * more [SpeedTestServer] instance — no engine code changes.
 *
 * Contract expected from a server:
 *  - [pingUrl]: GET returns a tiny body quickly (used for latency and jitter).
 *  - [downloadUrl]: GET returns exactly `bytes` bytes of payload.
 *  - [uploadUrl]: accepts a POST body of arbitrary size and replies quickly.
 *  - [metadataUrls] (optional): each GET returns flat JSON or `key=value` lines
 *    describing the client (IP, provider, city) and serving location. They are tried
 *    in order and merged, so one missing endpoint never blanks the details.
 *  - [metadataHeaderPrefix] (optional): the same details as response headers on
 *    [pingUrl], e.g. `cf-meta-city`.
 */
data class SpeedTestServer(
    val id: String,
    val displayName: String,
    val pingUrl: String,
    val downloadUrlTemplate: String,
    val uploadUrl: String,
    val metadataUrls: List<String> = emptyList(),
    val metadataHeaderPrefix: String? = null,
    val locationKeys: List<String> = emptyList(),
    val ipKeys: List<String> = emptyList(),
    val ispKeys: List<String> = emptyList(),
    val cityKeys: List<String> = emptyList(),
    /** Autonomous-system number, shown as "AS1234" when the provider name is unavailable. */
    val asnKeys: List<String> = emptyList(),
) {
    fun downloadUrl(bytes: Long): String = downloadUrlTemplate.replace(BYTES_TOKEN, bytes.toString())

    companion object {
        const val BYTES_TOKEN = "{bytes}"
    }
}

object SpeedTestServers {
    /**
     * Cloudflare's speed test backend (the same endpoints used by speed.cloudflare.com).
     * Served from Cloudflare's anycast edge, so the nearest data centre answers.
     */
    val Cloudflare = SpeedTestServer(
        id = "cloudflare",
        displayName = "Cloudflare",
        pingUrl = "https://speed.cloudflare.com/__down?bytes=0",
        downloadUrlTemplate = "https://speed.cloudflare.com/__down?bytes=${SpeedTestServer.BYTES_TOKEN}",
        uploadUrl = "https://speed.cloudflare.com/__up",
        metadataUrls = listOf("https://speed.cloudflare.com/meta", "https://speed.cloudflare.com/cdn-cgi/trace"),
        metadataHeaderPrefix = "cf-meta-",
        locationKeys = listOf("colo"),
        ipKeys = listOf("clientIp", "ip"),
        ispKeys = listOf("asOrganization"),
        cityKeys = listOf("city"),
        asnKeys = listOf("asn"),
    )

    val Default: SpeedTestServer = Cloudflare
}
