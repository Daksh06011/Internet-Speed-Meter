package com.netspeedtest.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

enum class ConnectionKind(val label: String) {
    WiFi("Wi‑Fi"), Cellular("Mobile data"), Ethernet("Ethernet"), Other("Connected"), None("Offline")
}

/** What the current default network looks like. `null` fields are unavailable. */
data class NetworkSnapshot(
    val kind: ConnectionKind,
    val networkHandle: Long?,
    val validated: Boolean,
    val metered: Boolean,
    val vpn: Boolean,
    /** "2G".."5G" for cellular, when Android reports it. */
    val generation: String?,
    val signalDbm: Int?,
    val wifiLinkMbps: Int?,
    val wifiFrequencyMhz: Int?,
    val wifiStandard: String?,
    val estimatedDownKbps: Int?,
    val estimatedUpKbps: Int?,
) {
    val isConnected: Boolean get() = kind != ConnectionKind.None

    /** Short description such as "Wi‑Fi · 5 GHz" or "Mobile data · 4G". */
    val detail: String
        get() = when (kind) {
            ConnectionKind.WiFi -> wifiFrequencyMhz?.let { "Wi‑Fi · ${bandOf(it)}" } ?: "Wi‑Fi"
            ConnectionKind.Cellular -> generation?.let { "Mobile · $it" } ?: "Mobile data"
            else -> kind.label
        }

    companion object {
        val Offline = NetworkSnapshot(ConnectionKind.None, null, false, false, false, null, null, null, null, null, null, null)

        fun bandOf(mhz: Int): String = when {
            mhz >= 5925 -> "6 GHz"
            mhz >= 4900 -> "5 GHz"
            mhz in 2400..2500 -> "2.4 GHz"
            else -> "$mhz MHz"
        }
    }
}

/**
 * Observes the system default network through [ConnectivityManager] callbacks. Needs
 * only `ACCESS_NETWORK_STATE`; no location or phone-state permission is requested, so
 * the Wi-Fi name and some cellular details are intentionally not read.
 */
class NetworkInfoProvider(private val context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun current(): NetworkSnapshot {
        val network = connectivity?.activeNetwork ?: return NetworkSnapshot.Offline
        val caps = connectivity.getNetworkCapabilities(network) ?: return NetworkSnapshot.Offline
        return snapshot(network, caps)
    }

    fun isAirplaneModeOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    fun observe(): Flow<NetworkSnapshot> = callbackFlow {
        trySend(current())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(snapshot(network, caps))
            }

            override fun onLost(network: Network) {
                trySend(NetworkSnapshot.Offline)
            }
        }
        val manager = connectivity
        if (manager == null) {
            close()
            return@callbackFlow
        }
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()

    private fun snapshot(network: Network, caps: NetworkCapabilities): NetworkSnapshot {
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionKind.WiFi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionKind.Cellular
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionKind.Ethernet
            else -> ConnectionKind.Other
        }
        val wifi = if (kind == ConnectionKind.WiFi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            caps.transportInfo as? WifiInfo
        } else {
            null
        }
        val capsSignal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps.signalStrength.takeIf { it in -140..-20 }
        } else {
            null
        }
        return NetworkSnapshot(
            kind = kind,
            networkHandle = network.networkHandle,
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            generation = if (kind == ConnectionKind.Cellular) cellularGeneration(network) else null,
            signalDbm = wifi?.rssi?.takeIf { it in -127..-1 } ?: capsSignal,
            wifiLinkMbps = wifi?.linkSpeed?.takeIf { it > 0 },
            wifiFrequencyMhz = wifi?.frequency?.takeIf { it > 0 },
            wifiStandard = wifi?.let(::wifiStandardOf),
            estimatedDownKbps = caps.linkDownstreamBandwidthKbps.takeIf { it > 0 },
            estimatedUpKbps = caps.linkUpstreamBandwidthKbps.takeIf { it > 0 },
        )
    }

    /**
     * The modern `TelephonyManager.getDataNetworkType()` requires the READ_PHONE_STATE
     * runtime permission. The legacy per-network subtype below needs none, which keeps
     * the permission list minimal. Limitation: 5G non-standalone reports as LTE.
     */
    @Suppress("DEPRECATION")
    private fun cellularGeneration(network: Network): String? =
        when (connectivity?.getNetworkInfo(network)?.subtype) {
            TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN, TelephonyManager.NETWORK_TYPE_GSM -> "2G"
            TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
            TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
            LTE_CA -> "4G+"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            else -> null
        }

    private fun wifiStandardOf(info: WifiInfo): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return when (info.wifiStandard) {
            4 -> "Wi‑Fi 4 (802.11n)"
            5 -> "Wi‑Fi 5 (802.11ac)"
            6 -> "Wi‑Fi 6 (802.11ax)"
            7 -> "Wi‑Fi 7 (802.11be)"
            1 -> "Legacy (802.11a/b/g)"
            8 -> "802.11ad"
            else -> null
        }
    }

    private companion object {
        /** `TelephonyManager.NETWORK_TYPE_LTE_CA` is hidden; its value is stable. */
        const val LTE_CA = 19
    }
}

/** Device-wide receive/transmit rate derived from [TrafficStats] byte counters. */
data class TrafficRate(val rxBytesPerSec: Double?, val txBytesPerSec: Double?)

class TrafficMonitor {
    /** Emits once per [intervalMs] while collected; stops as soon as collection stops. */
    fun observe(intervalMs: Long = 1_000L): Flow<TrafficRate> = flow {
        var lastRx = TrafficStats.getTotalRxBytes()
        var lastTx = TrafficStats.getTotalTxBytes()
        var lastTime = SystemClock.elapsedRealtimeNanos()
        val supported = lastRx != TrafficStats.UNSUPPORTED.toLong() && lastTx != TrafficStats.UNSUPPORTED.toLong()
        if (!supported) {
            emit(TrafficRate(null, null))
            return@flow
        }
        while (true) {
            delay(intervalMs)
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val now = SystemClock.elapsedRealtimeNanos()
            val seconds = (now - lastTime) / 1_000_000_000.0
            if (seconds > 0) {
                emit(TrafficRate((rx - lastRx).coerceAtLeast(0) / seconds, (tx - lastTx).coerceAtLeast(0) / seconds))
            }
            lastRx = rx
            lastTx = tx
            lastTime = now
        }
    }
}
