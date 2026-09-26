package com.netspeedtest.data

import com.netspeedtest.speedtest.SpeedTestResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Test history kept in one small JSON file in app-private storage. A database would be
 * overkill for at most [MAX_ENTRIES] rows of seven numbers; a full rewrite of the file
 * is a few kilobytes. All disk access happens on [Dispatchers.IO], never on the UI thread.
 */
class HistoryRepository(
    private val file: File,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<List<SpeedTestResult>?>(null)
    private val lock = Mutex()

    /** Newest first. `null` until the file has been read for the first time. */
    val history: StateFlow<List<SpeedTestResult>?> = state.asStateFlow()

    fun load() {
        if (state.value != null) return
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                if (state.value == null) state.value = read()
            }
        }
    }

    fun add(result: SpeedTestResult) = mutate { (listOf(result) + it).take(MAX_ENTRIES) }

    fun delete(timestampMillis: Long) = mutate { list -> list.filterNot { it.timestampMillis == timestampMillis } }

    fun clear() = mutate { emptyList() }

    private fun mutate(transform: (List<SpeedTestResult>) -> List<SpeedTestResult>) {
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                val next = transform(state.value ?: read())
                state.value = next
                write(next)
            }
        }
    }

    private fun read(): List<SpeedTestResult> = try {
        if (!file.exists()) emptyList() else decode(file.readText())
    } catch (e: Exception) {
        emptyList() // A corrupt file must never crash the app; history is non-critical.
    }

    private fun write(list: List<SpeedTestResult>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(encode(list))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        const val MAX_ENTRIES = 100

        fun encode(list: List<SpeedTestResult>): String {
            val array = JSONArray()
            list.forEach { r ->
                array.put(
                    JSONObject()
                        .put("t", r.timestampMillis)
                        .put("d", r.downloadMbps)
                        .put("u", r.uploadMbps)
                        .put("p", r.pingMs)
                        .put("j", r.jitterMs)
                        .putOpt("ld", r.loadedLatencyDownMs)
                        .putOpt("lu", r.loadedLatencyUpMs)
                        .put("c", r.connectionType)
                        .put("n", r.networkDetail)
                        .put("s", r.serverName)
                        .put("b", r.bytesUsed)
                        .putOpt("i", r.isp)
                        .putOpt("ip", r.clientIp),
                )
            }
            return array.toString()
        }

        fun decode(text: String): List<SpeedTestResult> {
            val array = JSONArray(text)
            return List(array.length()) { i ->
                val o = array.getJSONObject(i)
                SpeedTestResult(
                    timestampMillis = o.getLong("t"),
                    downloadMbps = o.getDouble("d"),
                    uploadMbps = o.getDouble("u"),
                    pingMs = o.getDouble("p"),
                    jitterMs = o.getDouble("j"),
                    loadedLatencyDownMs = if (o.has("ld")) o.getDouble("ld") else null,
                    loadedLatencyUpMs = if (o.has("lu")) o.getDouble("lu") else null,
                    connectionType = o.optString("c", ""),
                    networkDetail = o.optString("n", ""),
                    serverName = o.optString("s", ""),
                    bytesUsed = o.optLong("b", 0L),
                    isp = if (o.has("i")) o.getString("i") else null,
                    clientIp = if (o.has("ip")) o.getString("ip") else null,
                )
            }
        }
    }
}
