package com.netspeedtest.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryMathTest {
    @Test
    fun currentInMicroampsIsConverted() {
        assertEquals(1820.0, BatteryMath.normalizeCurrentMa(1_820_000)!!, 1e-9)
        assertEquals(-640.0, BatteryMath.normalizeCurrentMa(-640_000)!!, 1e-9)
    }

    @Test
    fun currentAlreadyInMilliampsIsKept() {
        assertEquals(1820.0, BatteryMath.normalizeCurrentMa(1820)!!, 1e-9)
        assertEquals(-450.0, BatteryMath.normalizeCurrentMa(-450)!!, 1e-9)
    }

    @Test
    fun unsupportedOrImpossibleCurrentIsUnavailable() {
        assertNull(BatteryMath.normalizeCurrentMa(Int.MIN_VALUE))
        assertNull(BatteryMath.normalizeCurrentMa(0))
        assertNull(BatteryMath.normalizeCurrentMa(90_000_000)) // 90 A is not a phone battery
    }

    @Test
    fun invertedGaugeIsDetectedWhileUnplugged() {
        assertEquals(-1, BatteryMath.learnSign(currentMa = 500.0, pluggedIn = false, previousSign = 1))
        assertEquals(1, BatteryMath.learnSign(currentMa = -500.0, pluggedIn = false, previousSign = -1))
        // While plugged in the sign is not learned (the phone may legitimately drain).
        assertEquals(-1, BatteryMath.learnSign(currentMa = -500.0, pluggedIn = true, previousSign = -1))
    }

    @Test
    fun voltageUnits() {
        assertEquals(4.123, BatteryMath.normalizeVoltage(4123)!!, 1e-9)
        assertEquals(4.0, BatteryMath.normalizeVoltage(4)!!, 1e-9)
        assertEquals(3.85, BatteryMath.normalizeVoltage(3_850_000)!!, 1e-9)
        assertNull(BatteryMath.normalizeVoltage(0))
        assertNull(BatteryMath.normalizeVoltage(-1))
    }

    @Test
    fun temperatureAndPower() {
        assertEquals(34.2, BatteryMath.normalizeTemperature(342)!!, 1e-9)
        assertNull(BatteryMath.normalizeTemperature(Int.MIN_VALUE))
        assertNull(BatteryMath.normalizeTemperature(0)) // 0 is the "not reported" default
        assertEquals(7.5, BatteryMath.powerWatts(4.0, 1875.0)!!, 1e-9)
        assertNull(BatteryMath.powerWatts(null, 100.0))
    }

    @Test
    fun capacityEstimate() {
        assertEquals(4500.0, BatteryMath.estimateCapacityMah(2_250_000, 50)!!, 1e-9)
        assertNull(BatteryMath.estimateCapacityMah(2_250_000, 5)) // too low a level to extrapolate
        assertNull(BatteryMath.estimateCapacityMah(Int.MIN_VALUE, 50))
    }

    @Test
    fun movingAverage() {
        val avg = MovingAverage(3)
        assertEquals(3.0, avg.add(3.0), 1e-9)
        avg.add(6.0)
        assertEquals(6.0, avg.add(9.0), 1e-9)
        assertEquals(9.0, avg.add(12.0), 1e-9) // oldest value dropped
    }
}
