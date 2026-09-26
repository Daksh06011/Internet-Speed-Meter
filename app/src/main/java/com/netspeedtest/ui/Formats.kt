package com.netspeedtest.ui

import com.netspeedtest.data.SpeedUnit
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** All user-visible number formatting in one place. */
object Formats {
    fun speedValue(mbps: Double, unit: SpeedUnit): String {
        val v = if (unit == SpeedUnit.MBps) mbps / 8.0 else mbps
        return when {
            v >= 1000 -> String.format(Locale.getDefault(), "%,.0f", v)
            v >= 100 -> String.format(Locale.getDefault(), "%.1f", v)
            v >= 10 -> String.format(Locale.getDefault(), "%.1f", v)
            else -> String.format(Locale.getDefault(), "%.2f", v)
        }
    }

    fun speed(mbps: Double?, unit: SpeedUnit): String = mbps?.let { "${speedValue(it, unit)} ${unit.label}" } ?: "—"

    fun ms(value: Double?): String = when {
        value == null -> "—"
        value < 10 -> String.format(Locale.getDefault(), "%.1f", value)
        else -> value.roundToInt().toString()
    }

    fun bytes(value: Long): String {
        val abs = abs(value.toDouble())
        return when {
            abs >= 1e9 -> String.format(Locale.getDefault(), "%.2f GB", value / 1e9)
            abs >= 1e6 -> String.format(Locale.getDefault(), "%.1f MB", value / 1e6)
            abs >= 1e3 -> String.format(Locale.getDefault(), "%.0f KB", value / 1e3)
            else -> "$value B"
        }
    }

    /** Network rate from bytes per second, in the user's speed unit. */
    fun rate(bytesPerSecond: Double?, unit: SpeedUnit): String {
        if (bytesPerSecond == null) return "—"
        val mbps = bytesPerSecond * 8 / 1e6
        return if (mbps < 0.1 && unit == SpeedUnit.Mbps) {
            String.format(Locale.getDefault(), "%.0f kbps", bytesPerSecond * 8 / 1e3)
        } else {
            speed(mbps, unit)
        }
    }

    fun signedMa(ma: Double): String = String.format(Locale.getDefault(), "%+,.0f mA", ma)

    fun watts(w: Double): String = String.format(Locale.getDefault(), "%.1f W", abs(w))

    fun celsius(c: Double): String = String.format(Locale.getDefault(), "%.1f°C", c)

    fun dateTime(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

    fun shortDate(millis: Long): String {
        val diff = System.currentTimeMillis() - millis
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> "${diff / 3_600_000} h ago"
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
        }
    }
}
