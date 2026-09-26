package com.netspeedtest.speedtest

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * A real HTTP server on the loopback interface implementing the speed test server
 * contract, with per-connection bandwidth throttling. Lets tests exercise the engine
 * end-to-end with genuine sockets and known transfer rates.
 */
class LocalSpeedServer(
    private val downBytesPerSec: Long = Long.MAX_VALUE,
    private val upBytesPerSec: Long = Long.MAX_VALUE,
    private val failWith: Int? = null,
    private val stallDownloads: Boolean = false,
    /** Reject this many /ping and this many /down requests with 429 first (rate limiting, as seen behind CGNAT). */
    busyRequests: Int = 0,
    /** Simulate a server without the JSON /meta endpoint (details must come from headers + trace). */
    private val metaMissing: Boolean = false,
) : AutoCloseable {
    private val busyLeft = mapOf("/ping" to AtomicInteger(busyRequests), "/down" to AtomicInteger(busyRequests))
    val busyRejections = AtomicInteger()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 64)
    private val pool = Executors.newCachedThreadPool()
    val activeTransfers = AtomicInteger()
    val bytesServed = AtomicLong()
    val bytesReceived = AtomicLong()

    val config: SpeedTestServer
        get() {
            val base = "http://127.0.0.1:${server.address.port}"
            return SpeedTestServer(
                id = "local",
                displayName = "Local",
                pingUrl = "$base/ping",
                downloadUrlTemplate = "$base/down?bytes=${SpeedTestServer.BYTES_TOKEN}",
                uploadUrl = "$base/up",
                metadataUrls = listOf("$base/meta", "$base/cdn-cgi/trace"),
                metadataHeaderPrefix = "cf-meta-",
                locationKeys = listOf("colo"),
                ipKeys = listOf("clientIp", "ip"),
                ispKeys = listOf("asOrganization"),
                cityKeys = listOf("city"),
                asnKeys = listOf("asn"),
            )
        }

    init {
        server.executor = pool
        server.createContext("/") { ex -> handle(ex) }
        server.start()
    }

    private fun handle(ex: HttpExchange) {
        try {
            failWith?.let {
                ex.sendResponseHeaders(it, -1)
                return
            }
            if ((busyLeft[ex.requestURI.path]?.getAndDecrement() ?: 0) > 0) {
                busyRejections.incrementAndGet()
                ex.sendResponseHeaders(429, -1)
                return
            }
            when (ex.requestURI.path) {
                "/ping" -> {
                    ex.responseHeaders.add("Server-Timing", "cfRequestDuration;dur=0.2")
                    ex.responseHeaders.add("cf-meta-city", "Headertown")
                    ex.responseHeaders.add("cf-meta-asn", "64501")
                    ex.sendResponseHeaders(200, -1)
                }
                "/meta" -> if (metaMissing) ex.sendResponseHeaders(404, -1) else {
                    val body = """{"clientIp":"203.0.113.7","asn":64500,"asOrganization":"Test ISP \"Fibre\"","colo":"TST","city":"Testville","country":"XX"}""".toByteArray()
                    ex.sendResponseHeaders(200, body.size.toLong())
                    ex.responseBody.write(body)
                }
                "/cdn-cgi/trace" -> {
                    val body = "fl=1\nh=speed.example\nip=198.51.100.9\ncolo=TRC\nloc=XX\n".toByteArray()
                    ex.sendResponseHeaders(200, body.size.toLong())
                    ex.responseBody.write(body)
                }
                "/down" -> download(ex)
                "/up" -> upload(ex)
                else -> ex.sendResponseHeaders(404, -1)
            }
        } catch (_: Exception) {
            // Client disconnects (cancellation) are expected.
        } finally {
            ex.close()
        }
    }

    private fun download(ex: HttpExchange) {
        val bytes = ex.requestURI.query.substringAfter("bytes=").toLong()
        ex.sendResponseHeaders(200, bytes)
        activeTransfers.incrementAndGet()
        try {
            if (stallDownloads) {
                ex.responseBody.write(ByteArray(1024))
                ex.responseBody.flush()
                Thread.sleep(60_000)
            }
            val chunk = ByteArray(16 * 1024)
            throttle(bytes, downBytesPerSec) { n ->
                ex.responseBody.write(chunk, 0, n)
                bytesServed.addAndGet(n.toLong())
                n
            }
        } finally {
            activeTransfers.decrementAndGet()
        }
    }

    private fun upload(ex: HttpExchange) {
        activeTransfers.incrementAndGet()
        try {
            val input = ex.requestBody
            val chunk = ByteArray(16 * 1024)
            throttle(Long.MAX_VALUE, upBytesPerSec) { n ->
                val read = input.read(chunk, 0, n)
                if (read < 0) throw EndOfBody()
                bytesReceived.addAndGet(read.toLong())
                read
            }
        } catch (_: EndOfBody) {
            ex.sendResponseHeaders(200, -1)
        } finally {
            activeTransfers.decrementAndGet()
        }
    }

    private class EndOfBody : RuntimeException()

    /** Calls [io] in 16 KiB steps, sleeping so the average rate stays at [rate] B/s. */
    private inline fun throttle(total: Long, rate: Long, io: (Int) -> Int) {
        val start = System.nanoTime()
        var done = 0L
        while (done < total) {
            val n = minOf(16 * 1024L, total - done).toInt()
            done += io(n)
            if (rate != Long.MAX_VALUE) {
                val due = start + done * 1_000_000_000L / rate
                val wait = due - System.nanoTime()
                if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
            }
        }
    }

    override fun close() {
        server.stop(0)
        pool.shutdownNow()
    }
}
