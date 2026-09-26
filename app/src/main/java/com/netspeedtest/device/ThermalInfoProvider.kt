package com.netspeedtest.device

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Friendly thermal levels, mapped one-to-one from Android's own thermal status. */
enum class ThermalLevel { Normal, Warm, Hot, Severe }

data class ThermalSnapshot(
    /** Raw `PowerManager.THERMAL_STATUS_*` value, or null below Android 10. */
    val status: Int?,
    /**
     * `PowerManager.getThermalHeadroom`: 0 = cool, 1.0 = severe throttling threshold.
     * Null when the device does not provide a forecast.
     */
    val headroom: Float?,
) {
    val level: ThermalLevel? get() = status?.let(::levelOf)
    val statusName: String? get() = status?.let(::nameOf)

    companion object {
        fun levelOf(status: Int): ThermalLevel = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.Normal
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.Warm
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.Hot
            else -> ThermalLevel.Severe
        }

        fun nameOf(status: Int): String = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "None"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> "Unknown"
        }
    }
}

/**
 * Device thermal state from the public [PowerManager] thermal APIs. Android does not
 * give regular apps CPU/GPU/skin temperatures (that API is restricted to device owner
 * apps), so the UI states that plainly instead of guessing.
 */
class ThermalInfoProvider(context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val executor = context.mainExecutor

    /** [headroomIntervalMs] of 0 skips the headroom forecast (status updates only). */
    fun observe(headroomIntervalMs: Long = 2_000L): Flow<ThermalSnapshot> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || power == null) {
            return flowOf(ThermalSnapshot(status = null, headroom = null))
        }
        return callbackFlow {
            var status: Int? = runCatching { power.currentThermalStatus }.getOrNull()
            var headroom: Float? = null
            trySend(ThermalSnapshot(status, headroom))
            val listener = PowerManager.OnThermalStatusChangedListener {
                status = it
                trySend(ThermalSnapshot(status, headroom))
            }
            // Some builds lack a thermal service; keep the last known status instead of failing.
            val listening = runCatching { power.addThermalStatusListener(executor, listener) }.isSuccess
            val poller = if (headroomIntervalMs > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                launch {
                    while (true) {
                        // The platform rate-limits this call (≈1/s) and returns NaN when unsupported.
                        headroom = runCatching { power.getThermalHeadroom(HEADROOM_FORECAST_SECONDS) }.getOrNull()?.takeUnless { it.isNaN() }
                        trySend(ThermalSnapshot(status, headroom))
                        delay(headroomIntervalMs)
                    }
                }
            } else {
                null
            }
            awaitClose {
                poller?.cancel()
                if (listening) runCatching { power.removeThermalStatusListener(listener) }
            }
        }.conflate()
    }

    private companion object {
        const val HEADROOM_FORECAST_SECONDS = 10
    }
}
