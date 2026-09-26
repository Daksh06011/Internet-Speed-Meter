package com.netspeedtest.state

import com.netspeedtest.data.HistoryRepository
import com.netspeedtest.device.ConnectionKind
import com.netspeedtest.device.NetworkInfoProvider
import com.netspeedtest.speedtest.EngineConfig
import com.netspeedtest.speedtest.EngineUpdate
import com.netspeedtest.speedtest.SpeedTestEngine
import com.netspeedtest.speedtest.SpeedTestException
import com.netspeedtest.speedtest.SpeedTestResult
import com.netspeedtest.speedtest.SpeedTestServer
import com.netspeedtest.speedtest.SpeedTestServers
import com.netspeedtest.speedtest.TestError
import com.netspeedtest.speedtest.TestPhase
import com.netspeedtest.speedtest.toTestError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Immutable UI state of the speed test. */
data class TestUiState(
    val phase: TestPhase = TestPhase.Idle,
    val phaseProgress: Float = 0f,
    val liveValue: Double? = null,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val serverLocation: String? = null,
    val connectionInfo: com.netspeedtest.speedtest.ConnectionInfo? = null,
    val error: TestError? = null,
    val result: SpeedTestResult? = null,
) {
    val isRunning: Boolean get() = phase in RUNNING

    /** 0..1 across the whole test, weighting phases roughly by their duration. */
    val overallProgress: Float
        get() = when (phase) {
            TestPhase.Preparing -> 0.02f
            TestPhase.Ping -> 0.02f + 0.08f * phaseProgress
            TestPhase.Download -> 0.10f + 0.50f * phaseProgress
            TestPhase.Upload -> 0.60f + 0.40f * phaseProgress
            TestPhase.Completed -> 1f
            else -> 0f
        }

    private companion object {
        val RUNNING = setOf(TestPhase.Preparing, TestPhase.Ping, TestPhase.Download, TestPhase.Upload)
    }
}

/**
 * Owns the one speed test that may run at a time. Lives in the application graph so a
 * rotation or theme change does not interrupt a test; the activity cancels it when the
 * user leaves the app, so nothing ever runs unseen.
 */
class SpeedTestController(
    private val engine: SpeedTestEngine,
    private val network: NetworkInfoProvider,
    private val history: HistoryRepository,
    private val scope: CoroutineScope,
    private val server: SpeedTestServer = SpeedTestServers.Default,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val singleConnection: () -> Boolean = { false },
) {
    private val state = MutableStateFlow(TestUiState())
    val uiState: StateFlow<TestUiState> = state.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (state.value.isRunning) return // guards against double taps

        val connection = network.current()
        if (!connection.isConnected) {
            val error = if (network.isAirplaneModeOn()) TestError.AirplaneMode else TestError.NoInternet
            state.value = TestUiState(phase = TestPhase.Failed, error = error)
            return
        }
        val base = if (connection.metered) EngineConfig.Metered else EngineConfig.Unmetered
        val config = if (singleConnection()) base.singleStream() else base
        state.value = TestUiState(phase = TestPhase.Preparing)

        job = scope.launch {
            try {
                val outcome = coroutineScope {
                    // Abort instead of silently mixing measurements from two different networks.
                    val watcher = launch {
                        val changed = network.observe().first { it.networkHandle != connection.networkHandle }
                        throw SpeedTestException(
                            if (changed.kind == ConnectionKind.None) TestError.ConnectionLost else TestError.NetworkChanged,
                        )
                    }
                    withContext(Dispatchers.Default) {
                        engine.run(server, config, ::onEngineUpdate)
                    }.also { watcher.cancel() }
                }
                val location = outcome.serverLocation?.let { "${server.displayName} · $it" } ?: server.displayName
                val result = SpeedTestResult(
                    timestampMillis = wallClock(),
                    downloadMbps = outcome.downloadMbps,
                    uploadMbps = outcome.uploadMbps,
                    pingMs = outcome.pingMs,
                    jitterMs = outcome.jitterMs,
                    loadedLatencyDownMs = outcome.loadedLatencyDownMs,
                    loadedLatencyUpMs = outcome.loadedLatencyUpMs,
                    connectionType = connection.kind.label,
                    networkDetail = connection.detail,
                    serverName = location,
                    bytesUsed = outcome.bytesUsed,
                    isp = outcome.connectionInfo?.isp,
                    clientIp = outcome.connectionInfo?.clientIp,
                )
                history.add(result)
                state.update {
                    it.copy(phase = TestPhase.Completed, phaseProgress = 1f, liveValue = result.downloadMbps, result = result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                state.update { it.copy(phase = TestPhase.Failed, error = e.toTestError(), liveValue = null) }
            }
        }
    }

    private fun onEngineUpdate(update: EngineUpdate) {
        state.update { current ->
            if (!current.isRunning) {
                current // a late update after cancellation must not resurrect the test
            } else {
                current.copy(
                    phase = update.phase.takeIf { it != TestPhase.Completed } ?: current.phase,
                    phaseProgress = update.phaseProgress,
                    liveValue = update.liveValue,
                    pingMs = update.pingMs,
                    jitterMs = update.jitterMs,
                    downloadMbps = update.downloadMbps,
                    uploadMbps = update.uploadMbps,
                    serverLocation = update.serverLocation,
                    connectionInfo = update.connectionInfo,
                )
            }
        }
    }

    /** Stops the running test immediately. [reason] is shown when it is not a plain user cancel. */
    fun cancel(reason: TestError? = null) {
        val running = job?.isActive == true && state.value.isRunning
        job?.cancel()
        job = null
        if (running) state.update { it.copy(phase = TestPhase.Cancelled, error = reason, liveValue = null) }
    }

    /** Returns the idle screen after a result, failure or cancellation has been seen. */
    fun reset() {
        if (!state.value.isRunning) state.value = TestUiState()
    }
}
