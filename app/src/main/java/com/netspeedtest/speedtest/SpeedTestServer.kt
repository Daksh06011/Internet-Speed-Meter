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
 *  - [metadataUrl] (optional): GET returns `key=value` lines; [locationKey] names the
 *    key holding a human-readable server location.
 */
data class SpeedTestServer(
    val id: String,
    val displayName: String,
    val pingUrl: String,
    val downloadUrlTemplate: String,
    val uploadUrl: String,
    val metadataUrl: String? = null,
    val locationKey: String? = null,
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
        metadataUrl = "https://speed.cloudflare.com/cdn-cgi/trace",
        locationKey = "colo",
    )

    val Default: SpeedTestServer = Cloudflare
}
