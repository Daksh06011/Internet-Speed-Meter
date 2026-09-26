package com.netspeedtest.speedtest

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Minimal HTTP layer on top of the platform [HttpURLConnection]. No third-party stack is
 * needed for the handful of request shapes a speed test uses.
 */
class HttpClient(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 8_000,
    private val userAgent: String = "NetSpeedTest/1.0",
) {
    fun open(url: String, method: String = "GET"): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.useCaches = false
        connection.instanceFollowRedirects = true
        // Transparent gzip would make the byte count meaningless.
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("Cache-Control", "no-store")
        connection.setRequestProperty("User-Agent", userAgent)
        return connection
    }
}

/**
 * Runs blocking [block] on the IO dispatcher and makes it promptly cancellable: when the
 * calling coroutine is cancelled the connection is torn down, which unblocks any socket
 * read/write immediately. On normal completion the connection is left intact so the
 * platform can reuse it (keep-alive).
 */
internal suspend fun <T> HttpURLConnection.runCancellable(block: (HttpURLConnection) -> T): T = coroutineScope {
    var finished = false
    val watchdog = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!finished) runCatching { disconnect() }
        }
    }
    try {
        runInterruptible(Dispatchers.IO) { block(this@runCancellable) }.also { finished = true }
    } finally {
        watchdog.cancel()
    }
}

/** Throws [SpeedTestException] for non-2xx responses. */
internal fun HttpURLConnection.requireSuccess() {
    val code = responseCode
    if (code !in 200..299) {
        runCatching { errorStream?.close() }
        throw SpeedTestException(TestError.ServerUnavailable)
    }
}

/** Maps low level exceptions to a [TestError] the UI can explain. */
fun Throwable.toTestError(): TestError = when (this) {
    is SpeedTestException -> error
    is UnknownHostException, is NoRouteToHostException -> TestError.NoInternet
    is ConnectException -> TestError.ServerUnavailable
    is SocketTimeoutException -> TestError.Timeout
    is SSLException -> TestError.SecureConnectionFailed
    is InterruptedIOException -> TestError.Timeout
    is IOException -> TestError.ConnectionLost
    else -> TestError.ConnectionLost
}
