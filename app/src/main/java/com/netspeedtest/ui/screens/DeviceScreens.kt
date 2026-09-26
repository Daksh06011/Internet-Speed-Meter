package com.netspeedtest.ui.screens

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.netspeedtest.device.BatterySnapshot
import com.netspeedtest.device.ChargeStatus
import com.netspeedtest.device.ConnectionKind
import com.netspeedtest.device.MemorySnapshot
import com.netspeedtest.device.NetworkSnapshot
import com.netspeedtest.device.TrafficRate
import com.netspeedtest.ui.Copy
import com.netspeedtest.ui.Formats
import com.netspeedtest.ui.components.Glyph
import com.netspeedtest.ui.components.IconView
import com.netspeedtest.ui.components.InfoRow
import com.netspeedtest.ui.components.LevelBar
import com.netspeedtest.ui.components.NeuCard
import com.netspeedtest.ui.components.NeuDrawable
import com.netspeedtest.ui.components.Sparkline
import com.netspeedtest.ui.components.noteCard
import com.netspeedtest.ui.components.rowsCard
import com.netspeedtest.ui.components.staggerIn
import com.netspeedtest.ui.components.unclip
import com.netspeedtest.ui.components.update
import com.netspeedtest.ui.nav.Route
import com.netspeedtest.ui.nav.Screen
import com.netspeedtest.ui.nav.ScreenEnv
import com.netspeedtest.ui.theme.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/** Shared layout for the device detail screens: a hero card with one big reading. */
abstract class DetailScreen(env: ScreenEnv, title: String) : Screen(env) {
    protected val palette = ui.palette
    protected val hero = NeuCard(ui, radiusDp = 32f).apply { setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24)) }
    protected val heroLabel: TextView = ui.text(TextStyle.Label, "", palette.textTertiary)
    protected val heroValue: TextView = ui.text(TextStyle.Display, "—").apply { textSize = 52f }
    protected val heroCaption: TextView = ui.text(TextStyle.BodyMedium, "", palette.textSecondary)

    init {
        header(title)
        hero.addView(heroLabel)
        hero.addView(heroValue, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(12)
        })
        hero.addView(heroCaption, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(8)
        })
        heroValue.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
        hero.isFocusable = true
        addBlock(hero)
    }

    protected fun setHero(label: String, value: String?, caption: String, valueColor: Int = palette.textPrimary) {
        heroLabel.update(label)
        heroValue.update(value ?: Copy.UNAVAILABLE_DEVICE)
        heroValue.textSize = if (value == null) 24f else 52f
        heroValue.setTextColor(if (value == null) palette.textTertiary else valueColor)
        heroCaption.update(caption)
        heroCaption.visibility = if (caption.isEmpty()) View.GONE else View.VISIBLE
        hero.contentDescription = "$label: ${value ?: Copy.UNAVAILABLE_DEVICE}. $caption"
    }

    protected fun enter() = staggerIn(ui, (0 until column.childCount).map(column::getChildAt).drop(1), step = 40)
}

class BatteryScreen(env: ScreenEnv) : DetailScreen(env, "Battery") {
    private val bar = LevelBar(ui, palette.textPrimary)
    private val status = InfoRow(ui, "Status")
    private val source = InfoRow(ui, "Power source")
    private val health = InfoRow(ui, "Health")
    private val temperature = InfoRow(ui, "Temperature")
    private val voltage = InfoRow(ui, "Voltage")
    private val current = InfoRow(ui, "Current")
    private val power = InfoRow(ui, "Power")
    private val technology = InfoRow(ui, "Technology")
    private val cycles = InfoRow(ui, "Charge cycles")
    private val capacity = InfoRow(ui, "Capacity")

    init {
        hero.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(20)
        })
        sectionLabel("Readings")
        addBlock(ui.rowsCard(listOf(status, source, health, temperature, voltage, current, power, technology, cycles, capacity)))
        addBlock(linkCard("Live charging monitor", Glyph.Bolt) { env.navigator.push(Route.Charging) }, topMargin = 16)
        addBlock(
            ui.noteCard(
                "About these values",
                "All readings come from Android's public BatteryManager API. Current is smoothed over a few seconds. " +
                    "Power is an estimate (voltage × current). Capacity is estimated from the charge counter and level.",
            ),
            topMargin = 16,
        )
        enter()
    }

    override fun onActive(scope: CoroutineScope) {
        scope.launch { env.graph.battery.observe().collect(::render) }
    }

    private fun render(b: BatterySnapshot) {
        val level = b.levelPercent
        val charging = b.status == ChargeStatus.Charging
        setHero("Level", level?.let { "$it%" }, if (b.isPluggedIn) "${Copy.status(b.status)} · ${Copy.source(b.source)}" else Copy.status(b.status))
        bar.setColor(
            when {
                charging -> palette.accent
                level != null && level <= 10 -> palette.danger
                level != null && level <= 20 -> palette.warning
                else -> palette.textSecondary
            },
        )
        bar.setFraction((level ?: 0) / 100f)
        status.set(Copy.status(b.status))
        source.set(Copy.source(b.source))
        health.set(b.health)
        temperature.set(b.temperatureC?.let(Formats::celsius))
        voltage.set(b.voltageV?.let { String.format(Locale.getDefault(), "%.2f V", it) })
        current.showIf(b.currentMa != null)
        power.showIf(b.powerW != null)
        cycles.showIf(b.cycleCount != null)
        capacity.showIf(b.capacityEstimateMah != null)
        current.set(b.currentMa?.let(Formats::signedMa), b.currentMa?.let { if (it >= 0) "Into battery" else "Out of battery" })
        power.set(b.powerW?.let { "≈ ${Formats.watts(it)}" }, if (b.powerW != null) "Estimate · V × I" else null)
        technology.set(b.technology)
        cycles.set(b.cycleCount?.toString())
        capacity.set(b.capacityEstimateMah?.let { String.format(Locale.getDefault(), "≈ %,.0f mAh", it) }, if (b.capacityEstimateMah != null) "Estimate" else null)
    }

    private fun linkCard(title: String, glyph: Glyph, action: () -> Unit) = NeuCard(ui, radiusDp = 24f).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(20), ui.dp(18), ui.dp(16), ui.dp(18))
        addView(IconView(ui, glyph, palette.accentOnSurface), LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)))
        addView(ui.text(TextStyle.BodyMedium, title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = ui.dp(14)
        })
        addView(IconView(ui, Glyph.ChevronRight, palette.textTertiary), LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)))
        onClick(title, action)
    }
}

class ChargingScreen(env: ScreenEnv) : DetailScreen(env, "Charging") {
    private val chart = Sparkline(ui, capacity = 90, color = palette.accentOnSurface)
    private val chartCaption = ui.text(TextStyle.Caption, "Current over this session", palette.textTertiary)
    private val average = InfoRow(ui, "Session average")
    private val peak = InfoRow(ui, "Peak")
    private val voltage = InfoRow(ui, "Voltage")
    private val temperature = InfoRow(ui, "Battery temperature")
    private val source = InfoRow(ui, "Power source")
    private val level = InfoRow(ui, "Level")
    private var sum = 0.0
    private var samples = 0
    private var peakMa: Double? = null
    private var note: View? = null

    init {
        hero.addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(96)).apply {
            topMargin = ui.dp(24)
        })
        hero.addView(chartCaption, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(10)
        })
        sectionLabel("This session")
        addBlock(ui.rowsCard(listOf(average, peak, voltage, temperature, source, level)))
        note = addBlock(
            ui.noteCard(
                "How this is measured",
                "Current is read from BatteryManager.CURRENT_NOW once per second and averaged over 5 readings. " +
                    "Devices report it in µA or mA and some flip the sign, so values are normalised; impossible readings are discarded. " +
                    "Watts are estimated as voltage × current and are approximate. Session data is kept in memory only.",
            ),
            topMargin = 16,
        )
        enter()
    }

    override fun onActive(scope: CoroutineScope) {
        scope.launch { env.graph.battery.observe().collect(::render) }
    }

    private fun render(b: BatterySnapshot) {
        val current = b.currentMa
        val charging = current != null && current > 0
        val color = if (charging) palette.accentOnSurface else palette.textPrimary
        val label = when {
            current == null -> Copy.status(b.status)
            charging -> "Charging"
            else -> "Discharging"
        }
        setHero(
            label,
            current?.let(Formats::signedMa) ?: b.levelPercent?.let { "$it%" },
            when {
                current == null -> if (b.isPluggedIn) Copy.source(b.source) else Copy.status(b.status)
                b.powerW != null -> "≈ ${Formats.watts(b.powerW)} estimated"
                else -> ""
            },
            color,
        )
        average.showIf(current != null)
        peak.showIf(current != null)
        note?.visibility = if (current != null) View.VISIBLE else View.GONE
        chart.visibility = if (current == null) View.GONE else View.VISIBLE
        chartCaption.visibility = chart.visibility
        if (current != null) {
            chart.setColor(color)
            chart.add(current.toFloat())
            sum += current
            samples++
            if (peakMa == null || abs(current) > abs(peakMa!!)) peakMa = current
        }
        average.set(if (samples > 0) Formats.signedMa(sum / samples) else null, if (samples > 0) "$samples readings" else null)
        peak.set(peakMa?.let(Formats::signedMa))
        voltage.set(b.voltageV?.let { String.format(Locale.getDefault(), "%.2f V", it) })
        temperature.set(b.temperatureC?.let(Formats::celsius))
        source.set(Copy.source(b.source))
        level.set(b.levelPercent?.let { "$it%" })
    }
}

class TemperatureScreen(env: ScreenEnv) : DetailScreen(env, "Temperature") {
    private val battery = InfoRow(ui, "Battery temperature")
    private val health = InfoRow(ui, "Battery health")
    private val status = InfoRow(ui, "Status")

    init {
        sectionLabel("Battery")
        addBlock(ui.rowsCard(listOf(battery, health, status)))
        enter()
    }

    override fun onActive(scope: CoroutineScope) {
        scope.launch { env.graph.battery.observe(pollIntervalMs = 5_000).collect(::render) }
    }

    private fun render(b: BatterySnapshot) {
        val temp = b.temperatureC
        setHero("Battery temperature", temp?.let(Formats::celsius), b.health?.let { "Battery health: $it" } ?: "")
        battery.set(temp?.let(Formats::celsius))
        health.set(b.health)
        status.set(Copy.status(b.status))
    }
}

class NetworkScreen(env: ScreenEnv) : DetailScreen(env, "Network") {
    private val rx = ui.text(TextStyle.Value, "—")
    private val tx = ui.text(TextStyle.Value, "—")
    private val rxChart = Sparkline(ui, capacity = 60, color = palette.accentOnSurface).apply { includeZero = true }
    private val txChart = Sparkline(ui, capacity = 60, color = palette.upload).apply { includeZero = true }
    private val state = InfoRow(ui, "Internet")
    private val generation = InfoRow(ui, "Generation")
    private val band = InfoRow(ui, "Band")
    private val signal = InfoRow(ui, "Signal strength")
    private val link = InfoRow(ui, "Link speed")
    private val standard = InfoRow(ui, "Wi‑Fi standard")
    private val metered = InfoRow(ui, "Metered")
    private val vpn = InfoRow(ui, "VPN")
    private val estimate = InfoRow(ui, "Android bandwidth estimate")

    init {
        val live = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        live.addView(rateColumn("↓ Receiving", rx, rxChart), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = ui.dp(12) })
        live.addView(rateColumn("↑ Sending", tx, txChart), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = ui.dp(12) })
        hero.addView(live, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(24)
        })
        sectionLabel("Connection")
        addBlock(ui.rowsCard(listOf(state, generation, band, signal, link, standard, metered, vpn, estimate)))
        addBlock(
            ui.noteCard(
                "Limits",
                "Live rates are device-wide totals from Android's traffic counters, sampled once per second while this " +
                    "screen is open. Without location or phone permissions Android hides the Wi‑Fi name and some cellular " +
                    "details, and 5G non-standalone connections are reported as 4G.",
            ),
            topMargin = 16,
        )
        enter()
    }

    private fun rateColumn(label: String, value: TextView, chart: Sparkline) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(ui.text(TextStyle.Label, label, palette.textTertiary))
        addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(8) })
        addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48)).apply { topMargin = ui.dp(12) })
    }

    override fun onActive(scope: CoroutineScope) {
        scope.launch { env.graph.network.observe().collect(::render) }
        scope.launch { env.graph.traffic.observe().collect(::renderTraffic) }
    }

    private fun renderTraffic(t: TrafficRate) {
        val unit = env.graph.settings.settings.value.unit
        rx.update(Formats.rate(t.rxBytesPerSec, unit))
        tx.update(Formats.rate(t.txBytesPerSec, unit))
        t.rxBytesPerSec?.let { rxChart.add(it.toFloat()) }
        t.txBytesPerSec?.let { txChart.add(it.toFloat()) }
    }

    private fun render(n: NetworkSnapshot) {
        val title = when (n.kind) {
            ConnectionKind.Cellular -> "Mobile data"
            else -> n.kind.label
        }
        setHero("Connection", title, if (!n.isConnected) "Not connected" else n.detail, if (n.isConnected) palette.textPrimary else palette.danger)
        heroValue.textSize = 40f
        state.set(
            when {
                !n.isConnected -> "Offline"
                n.validated -> "Connected"
                else -> "No internet access"
            },
            color = if (n.isConnected && n.validated) palette.accentOnSurface else palette.warning,
        )
        val cellular = n.kind == ConnectionKind.Cellular
        val wifi = n.kind == ConnectionKind.WiFi
        // Rows that don't apply to this connection (or aren't reported) are hidden, not shown as "Unavailable".
        generation.showIf(cellular && n.generation != null)
        band.showIf(wifi && n.wifiFrequencyMhz != null)
        signal.showIf(n.signalDbm != null)
        link.showIf(wifi && n.wifiLinkMbps != null)
        standard.showIf(wifi && n.wifiStandard != null)
        metered.showIf(n.isConnected)
        vpn.showIf(n.isConnected)
        estimate.showIf(n.estimatedDownKbps != null)
        generation.set(n.generation, if (n.generation == null) "Not reported" else null)
        band.set(if (wifi) n.wifiFrequencyMhz?.let { "${NetworkSnapshot.bandOf(it)} · $it MHz" } else if (cellular) "Cellular" else null)
        signal.set(n.signalDbm?.let { "$it dBm" }, n.signalDbm?.let(::signalQuality))
        link.set(if (wifi) n.wifiLinkMbps?.let { "$it Mbps" } else null, if (wifi && n.wifiLinkMbps != null) "Radio link rate, not internet speed" else null)
        standard.set(if (wifi) n.wifiStandard else null)
        metered.set(if (!n.isConnected) null else if (n.metered) "Yes" else "No")
        vpn.set(if (!n.isConnected) null else if (n.vpn) "Active" else "Off")
        estimate.set(
            n.estimatedDownKbps?.let { "↓ ${Formats.speed(it / 1000.0, env.graph.settings.settings.value.unit)}" },
            n.estimatedUpKbps?.let { "↑ ${Formats.speed(it / 1000.0, env.graph.settings.settings.value.unit)} · rough estimate" },
        )
    }

    private fun signalQuality(dbm: Int): String = when {
        dbm >= -60 -> "Excellent"
        dbm >= -70 -> "Good"
        dbm >= -80 -> "Fair"
        else -> "Weak"
    }
}

class MemoryScreen(env: ScreenEnv) : DetailScreen(env, "Memory") {
    private val bar = LevelBar(ui, palette.textSecondary)
    private val total = InfoRow(ui, "Total")
    private val used = InfoRow(ui, "In use")
    private val available = InfoRow(ui, "Available")
    private val threshold = InfoRow(ui, "Low-memory threshold")
    private val low = InfoRow(ui, "Low-memory state")
    private val app = InfoRow(ui, "This app")

    init {
        hero.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(20)
        })
        sectionLabel("Device memory")
        addBlock(ui.rowsCard(listOf(total, used, available, threshold, low, app)))
        addBlock(
            ui.noteCard(
                "About memory",
                "Android keeps recently used apps cached in RAM, so \"in use\" is normally high — that's healthy. " +
                    "The low-memory state tells you when the system is actually under pressure.",
            ),
            topMargin = 16,
        )
        enter()
    }

    override fun onActive(scope: CoroutineScope) {
        scope.launch { env.graph.memory.observe(intervalMs = 3_000, includeApp = true).collect(::render) }
    }

    private fun render(m: MemorySnapshot?) {
        if (m == null) {
            setHero("Available", null, "")
            return
        }
        setHero("Available", Formats.bytes(m.availableBytes), "of ${Formats.bytes(m.totalBytes)} total", if (m.lowMemory) palette.warning else palette.textPrimary)
        bar.setColor(if (m.lowMemory) palette.warning else palette.textSecondary)
        bar.setFraction(m.usedFraction)
        total.set(Formats.bytes(m.totalBytes))
        used.set(Formats.bytes(m.usedBytes), String.format(Locale.getDefault(), "%.0f%%", m.usedFraction * 100))
        available.set(Formats.bytes(m.availableBytes))
        threshold.set(Formats.bytes(m.thresholdBytes))
        low.set(if (m.lowMemory) "Under pressure" else "Normal", color = if (m.lowMemory) palette.warning else palette.textPrimary)
        app.set(m.appPssBytes?.let(Formats::bytes), "Proportional set size")
    }
}
