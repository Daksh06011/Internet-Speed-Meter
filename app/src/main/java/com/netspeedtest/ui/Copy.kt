package com.netspeedtest.ui

import com.netspeedtest.device.ChargeStatus
import com.netspeedtest.device.PowerSource
import com.netspeedtest.speedtest.TestError

/** Human-friendly wording for states and errors. Never exposes raw exceptions. */
object Copy {
    data class Message(val title: String, val body: String)

    fun error(error: TestError): Message = when (error) {
        TestError.NoInternet -> Message("No internet connection", "Check Wi‑Fi or mobile data and try again.")
        TestError.AirplaneMode -> Message("Airplane mode is on", "Turn off airplane mode or join a Wi‑Fi network, then try again.")
        TestError.Timeout -> Message("Connection timed out", "The server stopped responding. Your connection may be unstable — try again.")
        TestError.ServerBusy -> Message("Server is busy", "The test server is handling a lot of traffic from your network. Wait a minute and try again.")
        TestError.ServerUnavailable -> Message("Test server unavailable", "The speed test server couldn't be reached right now. Try again in a moment.")
        TestError.SecureConnectionFailed -> Message("Secure connection failed", "A secure connection couldn't be made. Wi‑Fi sign-in pages often cause this.")
        TestError.ConnectionLost -> Message("Connection lost", "The network dropped during the test. Try again once you're back online.")
        TestError.NetworkChanged -> Message("Network changed", "Your phone switched networks mid-test, so the result would be wrong. Try again.")
        TestError.Backgrounded -> Message("Test stopped", "Tests stop when you leave the app, so nothing runs in the background.")
    }

    fun status(status: ChargeStatus): String = when (status) {
        ChargeStatus.Charging -> "Charging"
        ChargeStatus.Discharging -> "Discharging"
        ChargeStatus.Full -> "Full"
        ChargeStatus.NotCharging -> "Not charging"
        ChargeStatus.Unknown -> "Unknown"
    }

    fun source(source: PowerSource): String = when (source) {
        PowerSource.Battery -> "Battery"
        PowerSource.Ac -> "AC charger"
        PowerSource.Usb -> "USB"
        PowerSource.Wireless -> "Wireless"
        PowerSource.Dock -> "Dock"
        PowerSource.Unknown -> "Unknown"
    }

    const val UNAVAILABLE_DEVICE = "Unavailable on this device"
}
