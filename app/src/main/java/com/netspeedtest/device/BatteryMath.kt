package com.netspeedtest.device

import kotlin.math.abs

/**
 * Normalisation of raw fuel-gauge readings. Android documents
 * `BATTERY_PROPERTY_CURRENT_NOW` in microamperes (positive = charging), but devices in
 * the wild report milliamperes and/or the opposite sign. These rules are pure functions
 * so they can be unit tested.
 */
object BatteryMath {
    /** Largest current a phone battery plausibly carries (≈ 20 A covers 100 W+ charging). */
    private const val MAX_PLAUSIBLE_MA = 20_000.0

    /** Readings at or above this magnitude cannot be mA on a phone, so they are µA. */
    private const val MICRO_AMP_THRESHOLD = 25_000

    /**
     * Returns the current in milliamperes, or null when the reading is missing or
     * implausible. Android returns [Int.MIN_VALUE] (and some devices 0) when unsupported.
     */
    fun normalizeCurrentMa(raw: Int): Double? {
        if (raw == Int.MIN_VALUE || raw == Int.MAX_VALUE || raw == 0) return null
        val ma = if (abs(raw) >= MICRO_AMP_THRESHOLD) raw / 1000.0 else raw.toDouble()
        return ma.takeIf { abs(it) in 1.0..MAX_PLAUSIBLE_MA }
    }

    /**
     * Some devices report the opposite sign convention. While the device runs on
     * battery (unplugged) current must flow out, so a positive reading there reveals an
     * inverted gauge. Returns the multiplier to apply to future readings.
     */
    fun learnSign(currentMa: Double, pluggedIn: Boolean, previousSign: Int): Int = when {
        !pluggedIn && currentMa > 0 -> -1
        !pluggedIn && currentMa < 0 -> 1
        else -> previousSign
    }

    /** Voltage in volts from the `EXTRA_VOLTAGE` extra (normally mV, occasionally V or µV). */
    fun normalizeVoltage(raw: Int): Double? {
        if (raw <= 0) return null
        val volts = when {
            raw < 100 -> raw.toDouble() // already volts
            raw > 100_000 -> raw / 1_000_000.0 // microvolts
            else -> raw / 1000.0
        }
        return volts.takeIf { it in 2.5..12.0 }
    }

    /** Battery temperature in °C from `EXTRA_TEMPERATURE` (tenths of a degree). */
    fun normalizeTemperature(raw: Int): Double? {
        if (raw == Int.MIN_VALUE) return null
        val c = raw / 10.0
        return c.takeIf { it > -40.0 && it < 100.0 && raw != 0 }
    }

    /** Estimated power in watts (V × I). Signed like the current. */
    fun powerWatts(volts: Double?, currentMa: Double?): Double? =
        if (volts == null || currentMa == null) null else volts * currentMa / 1000.0

    /**
     * Full-charge capacity estimated from the charge counter (µAh) and level (%).
     * Only meaningful at moderate levels; returns null outside plausible bounds.
     */
    fun estimateCapacityMah(chargeCounterUah: Int, levelPercent: Int): Double? {
        if (chargeCounterUah <= 0 || chargeCounterUah == Int.MIN_VALUE || levelPercent !in 15..100) return null
        val mah = chargeCounterUah / 1000.0 / (levelPercent / 100.0)
        return mah.takeIf { it in 500.0..30_000.0 }
    }
}

/** Fixed-size moving average used to smooth noisy fuel-gauge current readings. */
class MovingAverage(private val size: Int) {
    private val values = DoubleArray(size)
    private var count = 0
    private var next = 0

    fun add(value: Double): Double {
        values[next] = value
        next = (next + 1) % size
        if (count < size) count++
        return average()
    }

    fun average(): Double {
        var sum = 0.0
        for (i in 0 until count) sum += values[i]
        return if (count == 0) 0.0 else sum / count
    }

    fun clear() {
        count = 0
        next = 0
    }
}
