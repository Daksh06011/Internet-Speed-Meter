package com.netspeedtest.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

enum class ChargeStatus { Charging, Discharging, Full, NotCharging, Unknown }

enum class PowerSource { Battery, Ac, Usb, Wireless, Dock, Unknown }

/** One reading of every battery value Android exposes publicly. `null` = unavailable. */
data class BatterySnapshot(
    val levelPercent: Int?,
    val status: ChargeStatus,
    val source: PowerSource,
    val health: String?,
    val technology: String?,
    val temperatureC: Double?,
    val voltageV: Double?,
    /** Smoothed current in mA; positive while charging, negative while discharging. */
    val currentMa: Double?,
    /** Estimated power in W (voltage × current), signed like [currentMa]. */
    val powerW: Double?,
    val capacityEstimateMah: Double?,
    val cycleCount: Int?,
) {
    val isPluggedIn: Boolean get() = source != PowerSource.Battery && source != PowerSource.Unknown
}

/**
 * Battery data from the sticky `ACTION_BATTERY_CHANGED` broadcast plus [BatteryManager]
 * properties. Nothing is registered or polled unless the returned flow is collected, and
 * collection only happens while a screen showing battery data is visible.
 */
class BatteryInfoProvider(private val context: Context) {
    private val manager = context.getSystemService(BatteryManager::class.java)

    fun observe(pollIntervalMs: Long = 1_000L): Flow<BatterySnapshot> = callbackFlow {
        var lastIntent: Intent? = null
        var sign = 1
        val smoothing = MovingAverage(SMOOTHING_WINDOW)

        fun emit(intent: Intent) {
            trySend(snapshot(intent, smoothing) { current, plugged ->
                sign = BatteryMath.learnSign(current, plugged, sign)
                current * sign
            })
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                lastIntent = intent
                emit(intent)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        if (sticky != null && lastIntent == null) {
            lastIntent = sticky
            emit(sticky)
        }
        // The broadcast only fires on level/state changes; current flow changes constantly.
        val poller = launch {
            while (true) {
                delay(pollIntervalMs)
                lastIntent?.let(::emit)
            }
        }
        awaitClose {
            poller.cancel()
            context.unregisterReceiver(receiver)
        }
    }.conflate()

    private inline fun snapshot(
        intent: Intent,
        smoothing: MovingAverage,
        correctSign: (Double, Boolean) -> Double,
    ): BatterySnapshot {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else null
        val source = sourceOf(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1))
        val plugged = source != PowerSource.Battery && source != PowerSource.Unknown
        val voltage = BatteryMath.normalizeVoltage(intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1))

        val rawCurrent = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
        val current = BatteryMath.normalizeCurrentMa(rawCurrent)?.let { smoothing.add(correctSign(it, plugged)) }

        val counter = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE
        val cycles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 }
        } else {
            null
        }
        return BatterySnapshot(
            levelPercent = percent,
            status = statusOf(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)),
            source = source,
            health = healthOf(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)),
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
            temperatureC = BatteryMath.normalizeTemperature(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)),
            voltageV = voltage,
            currentMa = current,
            powerW = BatteryMath.powerWatts(voltage, current),
            capacityEstimateMah = percent?.let { BatteryMath.estimateCapacityMah(counter, it) },
            cycleCount = cycles,
        )
    }

    private fun statusOf(value: Int) = when (value) {
        BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.Charging
        BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeStatus.Discharging
        BatteryManager.BATTERY_STATUS_FULL -> ChargeStatus.Full
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeStatus.NotCharging
        else -> ChargeStatus.Unknown
    }

    private fun sourceOf(value: Int) = when (value) {
        0 -> PowerSource.Battery
        BatteryManager.BATTERY_PLUGGED_AC -> PowerSource.Ac
        BatteryManager.BATTERY_PLUGGED_USB -> PowerSource.Usb
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> PowerSource.Wireless
        PLUGGED_DOCK -> PowerSource.Dock
        else -> PowerSource.Unknown
    }

    private fun healthOf(value: Int) = when (value) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> null
    }

    private companion object {
        const val SMOOTHING_WINDOW = 5
        /** `BatteryManager.BATTERY_PLUGGED_DOCK`, added in API 33. */
        const val PLUGGED_DOCK = 8
    }
}
