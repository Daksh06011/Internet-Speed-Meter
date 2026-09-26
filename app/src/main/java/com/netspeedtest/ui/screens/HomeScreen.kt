package com.netspeedtest.ui.screens

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.netspeedtest.data.AppSettings
import com.netspeedtest.data.SpeedUnit
import com.netspeedtest.device.BatterySnapshot
import com.netspeedtest.device.ChargeStatus
import com.netspeedtest.device.ConnectionKind
import com.netspeedtest.device.MemorySnapshot
import com.netspeedtest.device.NetworkSnapshot
import com.netspeedtest.device.ThermalLevel
import com.netspeedtest.device.ThermalSnapshot
import com.netspeedtest.device.TrafficRate
import com.netspeedtest.speedtest.SpeedTestResult
import com.netspeedtest.speedtest.TestPhase
import com.netspeedtest.state.TestUiState
import com.netspeedtest.ui.Copy
import com.netspeedtest.ui.Formats
import com.netspeedtest.ui.components.Glyph
import com.netspeedtest.ui.components.Haptics
import com.netspeedtest.ui.components.HealthTile
import com.netspeedtest.ui.components.IconButton
import com.netspeedtest.ui.components.MetricCell
import com.netspeedtest.ui.components.Motion
import com.netspeedtest.ui.components.NeuButton
import com.netspeedtest.ui.components.NeuCard
import com.netspeedtest.ui.components.SpeedGauge
import com.netspeedtest.ui.components.StatusDot
import com.netspeedtest.ui.components.divider
import com.netspeedtest.ui.components.staggerIn
import com.netspeedtest.ui.components.unclip
import com.netspeedtest.ui.components.update
import com.netspeedtest.ui.nav.Route
import com.netspeedtest.ui.nav.Screen
import com.netspeedtest.ui.nav.ScreenEnv
import com.netspeedtest.ui.theme.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Dashboard: the speed test hero, device health tiles and the most recent result. */
class HomeScreen(env: ScreenEnv) : Screen(env) {
    private val graph = env.graph
    private val palette = ui.palette
    private var unit = SpeedUnit.Mbps
    private var lastPhase: TestPhase = graph.speedTest.uiState.value.phase

    // Header
    private val connectionDot = StatusDot(ui)
    private val connectionText = ui.text(TextStyle.Label, "Checking…", palette.textSecondary)

    // Hero
    private val serverText = ui.text(TextStyle.Label, "", palette.textTertiary)
    private val gauge = SpeedGauge(ui)
    private val statusTitle = ui.text(TextStyle.BodyMedium, "", palette.textPrimary)
    private val statusBody = ui.text(TextStyle.Caption, "", palette.textSecondary)
    private val startButton = NeuButton(ui, "Start test", primary = true)
    private val download = MetricCell(ui, Glyph.Download, "Download", palette.accentOnSurface)
    private val upload = MetricCell(ui, Glyph.Upload, "Upload", palette.upload)
    private val ping = MetricCell(ui, Glyph.Ping, "Ping", palette.textPrimary)
    private val jitter = MetricCell(ui, Glyph.Jitter, "Jitter", palette.textPrimary)

    // Device health
    private val batteryTile = HealthTile(ui, Glyph.Battery, "Battery")
    private val powerTile = HealthTile(ui, Glyph.Bolt, "Power")
    private val tempTile = HealthTile(ui, Glyph.Thermometer, "Temp")
    private val networkTile = HealthTile(ui, Glyph.Wifi, "Network")
    private val memoryTile = HealthTile(ui, Glyph.Memory, "Memory")
    private val trafficTile = HealthTile(ui, Glyph.Pulse, "Traffic")

    // Last result
    private val lastCard = NeuCard(ui, radiusDp = 26f)

    init {
        buildHeader()
        buildHero()
        buildHealth()
        sectionLabel("Last test")
        addBlock(lastCard)
        lastCard.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(20))
        startButton.onClick { onPrimaryAction() }
        staggerIn(ui, listOf(column.getChildAt(0), column.getChildAt(1)), step = 60)
    }

    private fun buildHeader() {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            unclip()
        }
        val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(ui.text(TextStyle.Title, "Net Speed Test").apply { isAccessibilityHeading = true })
        val status = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(8), 0, 0)
        }
        status.addView(connectionDot, LinearLayout.LayoutParams(ui.dp(8), ui.dp(8)))
        status.addView(connectionText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = ui.dp(8)
        })
        titles.addView(status)
        row.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val history = IconButton(ui, Glyph.History, "History").apply { onClick { env.navigator.push(Route.History) } }
        val settings = IconButton(ui, Glyph.Settings, "Settings").apply { onClick { env.navigator.push(Route.Settings) } }
        row.addView(history, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)).apply { rightMargin = ui.dp(12) })
        row.addView(settings, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        addBlock(row, topMargin = 8)
    }

    private fun buildHero() {
        val card = NeuCard(ui, radiusDp = 32f).apply { setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(8)) }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(ui.text(TextStyle.Label, "Speed test", palette.textTertiary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(serverText)
        card.addView(top)
        card.addView(gauge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(16)
        })
        statusTitle.gravity = Gravity.CENTER
        statusBody.gravity = Gravity.CENTER
        statusBody.setLineSpacing(0f, 1.2f)
        card.addView(statusTitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(4)
        })
        card.addView(statusBody, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(6)
        })
        statusTitle.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        card.addView(startButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(20)
            bottomMargin = ui.dp(20)
        })
        card.addView(ui.divider())
        card.addView(metricRow(download, upload))
        card.addView(ui.divider())
        card.addView(metricRow(ping, jitter))
        addBlock(card, topMargin = 24)
    }

    private fun metricRow(a: View, b: View) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(View(context).apply { setBackgroundColor(palette.divider) }, LinearLayout.LayoutParams(maxOf(1, ui.dp(1)), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = ui.dp(14)
            bottomMargin = ui.dp(14)
            rightMargin = ui.dp(18)
        })
        addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun buildHealth() {
        sectionLabel("Device health", topMargin = 32)
        batteryTile.onClick { env.navigator.push(Route.Battery) }
        powerTile.onClick { env.navigator.push(Route.Charging) }
        tempTile.onClick { env.navigator.push(Route.Temperature) }
        networkTile.onClick { env.navigator.push(Route.Network) }
        memoryTile.onClick { env.navigator.push(Route.Memory) }
        trafficTile.onClick { env.navigator.push(Route.Network) }
        listOf(batteryTile to powerTile, tempTile to networkTile, memoryTile to trafficTile).forEachIndexed { i, (a, b) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                unclip()
            }
            row.addView(a, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { rightMargin = ui.dp(8) })
            row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = ui.dp(8) })
            addBlock(row, topMargin = if (i == 0) 0 else 16)
        }
    }

    override fun onActive(scope: CoroutineScope) {
        graph.history.load()
        scope.launch {
            combine(graph.settings.settings, graph.speedTest.uiState) { s, t -> s to t }.collect { (s, t) -> render(s, t) }
        }
        scope.launch { graph.network.observe().collect(::renderNetwork) }
        scope.launch { graph.battery.observe(pollIntervalMs = 2_000).collect(::renderBattery) }
        scope.launch { graph.thermal.observe(headroomIntervalMs = 0).collect(::renderThermal) }
        scope.launch { graph.memory.observe(intervalMs = 5_000).collect(::renderMemory) }
        scope.launch { graph.traffic.observe().collect(::renderTraffic) }
        scope.launch { graph.history.history.collect { renderLast(it?.firstOrNull(), it == null) } }
    }

    private fun onPrimaryAction() {
        val controller = graph.speedTest
        if (controller.uiState.value.isRunning) {
            controller.cancel()
            Haptics.reject(startButton)
        } else {
            Haptics.confirm(startButton)
            controller.start()
        }
    }

    // ------------------------------------------------------------------ rendering

    private fun render(settings: AppSettings, state: TestUiState) {
        unit = settings.unit
        val justCompleted = lastPhase != TestPhase.Completed && state.phase == TestPhase.Completed
        lastPhase = state.phase

        serverText.update(state.serverLocation?.let { "Cloudflare · $it" } ?: if (state.isRunning) "Connecting" else "Cloudflare")
        val speedFormatter: (Double) -> String = { Formats.speedValue(it, unit) }

        when (state.phase) {
            TestPhase.Idle, TestPhase.Cancelled, TestPhase.Failed -> {
                gauge.formatter = speedFormatter
                gauge.scale = SpeedGauge::speedScale
                gauge.setArcColor(palette.accentOnSurface)
                gauge.show(null, unit.label, if (state.phase == TestPhase.Failed) "Error" else "Ready")
                gauge.setProgress(0f)
                startButton.setLabel(if (state.phase == TestPhase.Failed) "Try again" else "Start test")
                startButton.setPrimary(true)
                val message = state.error?.let(Copy::error)
                when {
                    message != null -> setStatus(message.title, message.body)
                    state.phase == TestPhase.Cancelled -> setStatus("Test cancelled", "Nothing was saved. Start again whenever you're ready.")
                    else -> setStatus("Ready when you are", "Real transfers to Cloudflare's nearest server.")
                }
            }
            TestPhase.Preparing -> {
                gauge.show(null, unit.label, "Connecting")
                gauge.setProgress(state.overallProgress)
                runningButton()
                setStatus("Finding the nearest server…", "")
            }
            TestPhase.Ping -> {
                gauge.formatter = { Formats.ms(it) }
                gauge.scale = SpeedGauge::latencyScale
                gauge.setArcColor(palette.textSecondary)
                gauge.show(state.liveValue, "ms", "Ping")
                gauge.setProgress(state.overallProgress)
                runningButton()
                setStatus("Measuring latency", "Timing round trips on a warm connection.")
            }
            TestPhase.Download, TestPhase.Upload -> {
                val isDown = state.phase == TestPhase.Download
                gauge.formatter = speedFormatter
                gauge.scale = SpeedGauge::speedScale
                gauge.setArcColor(if (isDown) palette.accentOnSurface else palette.upload)
                gauge.show(state.liveValue ?: 0.0, unit.label, if (isDown) "Download" else "Upload")
                gauge.setProgress(state.overallProgress)
                runningButton()
                setStatus(if (isDown) "Testing download" else "Testing upload", "Multiple parallel streams · ${(state.phaseProgress * 100).toInt()}%")
            }
            TestPhase.Completed -> {
                gauge.formatter = speedFormatter
                gauge.scale = SpeedGauge::speedScale
                gauge.setArcColor(palette.accentOnSurface)
                gauge.show(state.downloadMbps, unit.label, "Download")
                gauge.setProgress(1f)
                startButton.setLabel("Test again")
                startButton.setPrimary(true)
                setStatus("Test complete", state.result?.serverName ?: "")
            }
        }

        download.set(state.downloadMbps?.let { Formats.speedValue(it, unit) }, unit.label, state.phase == TestPhase.Download)
        upload.set(state.uploadMbps?.let { Formats.speedValue(it, unit) }, unit.label, state.phase == TestPhase.Upload)
        ping.set(state.pingMs?.let(Formats::ms), "ms", state.phase == TestPhase.Ping)
        jitter.set(state.jitterMs?.let(Formats::ms), "ms", state.phase == TestPhase.Ping)

        if (justCompleted) onCompleted(state)
    }

    private fun onCompleted(state: TestUiState) {
        gauge.celebrate()
        Haptics.confirm(gauge)
        val result = state.result ?: return
        // Let the completion pulse land, then present the full summary.
        postDelayed({
            if (isAttachedToWindow && graph.speedTest.uiState.value.result == result && env.navigator.current == Route.Home) {
                env.navigator.push(Route.Result(result.timestampMillis, fresh = true))
            }
        }, AUTO_RESULT_DELAY_MS)
    }

    private fun runningButton() {
        startButton.setLabel("Cancel")
        startButton.setPrimary(false)
    }

    private fun setStatus(title: String, body: String) {
        statusTitle.update(title)
        statusBody.update(body)
        statusBody.visibility = if (body.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun renderNetwork(n: NetworkSnapshot) {
        val (color, hollow) = when {
            !n.isConnected -> palette.danger to true
            !n.validated -> palette.warning to true
            else -> palette.accentOnSurface to false
        }
        connectionDot.set(color, hollow)
        val state = when {
            !n.isConnected -> "Offline"
            !n.validated -> "${n.detail} · No internet"
            else -> "${n.detail} · Connected"
        }
        connectionText.update(state)
        connectionText.contentDescription = "Connection: $state"
        networkTile.icon.glyph = when (n.kind) {
            ConnectionKind.Cellular -> Glyph.Cellular
            ConnectionKind.Ethernet -> Glyph.Ethernet
            ConnectionKind.WiFi -> Glyph.Wifi
            else -> Glyph.Globe
        }
        val sub = when {
            !n.isConnected -> "No connection"
            n.kind == ConnectionKind.WiFi -> listOfNotNull(n.wifiFrequencyMhz?.let(NetworkSnapshot::bandOf), n.signalDbm?.let { "$it dBm" }).joinToString(" · ").ifEmpty { "Connected" }
            n.kind == ConnectionKind.Cellular -> listOfNotNull(n.generation, if (n.metered) "Metered" else null).joinToString(" · ").ifEmpty { "Connected" }
            else -> if (n.validated) "Connected" else "No internet"
        }
        networkTile.set(if (n.kind == ConnectionKind.Cellular) "Mobile" else n.kind.label, sub, if (n.isConnected) palette.textPrimary else palette.danger)
    }

    private fun renderBattery(b: BatterySnapshot) {
        val charging = b.status == ChargeStatus.Charging || b.status == ChargeStatus.Full
        batteryTile.icon.tint = if (charging) palette.accentOnSurface else palette.textSecondary
        batteryTile.set(b.levelPercent?.let { "$it%" } ?: "—", if (b.isPluggedIn) "${Copy.status(b.status)} · ${Copy.source(b.source)}" else Copy.status(b.status))

        powerTile.label.update(if (b.isPluggedIn) "Charging" else "Power draw")
        val current = b.currentMa
        if (current == null) {
            powerTile.set("—", Copy.UNAVAILABLE_DEVICE)
        } else {
            val watts = b.powerW?.let { "≈ ${Formats.watts(it)}" }
            powerTile.set(watts ?: Formats.signedMa(current), if (watts != null) "${Formats.signedMa(current)} · estimate" else "Current")
        }
        powerTile.icon.tint = if (current != null && current > 0) palette.accentOnSurface else palette.textSecondary

        tempTile.value.tag = b.temperatureC
        renderTemperature()
    }

    private var thermal: ThermalSnapshot? = null

    private fun renderThermal(t: ThermalSnapshot) {
        thermal = t
        renderTemperature()
    }

    private fun renderTemperature() {
        val temp = tempTile.value.tag as? Double
        val level = thermal?.level
        val sub = level?.let { "Thermal: ${Copy.thermal(it)}" } ?: "Battery sensor"
        val color = when (level) {
            ThermalLevel.Hot -> palette.warning
            ThermalLevel.Severe -> palette.danger
            else -> palette.textPrimary
        }
        tempTile.set(temp?.let(Formats::celsius) ?: "—", sub, color)
    }

    private fun renderMemory(m: MemorySnapshot?) {
        if (m == null) {
            memoryTile.set("—", Copy.UNAVAILABLE_DEVICE)
            return
        }
        memoryTile.set(Formats.bytes(m.availableBytes), "free of ${Formats.bytes(m.totalBytes)}", if (m.lowMemory) palette.warning else palette.textPrimary)
    }

    private fun renderTraffic(t: TrafficRate) {
        if (t.rxBytesPerSec == null) {
            trafficTile.set("—", Copy.UNAVAILABLE_DEVICE)
            return
        }
        trafficTile.set("↓ ${Formats.rate(t.rxBytesPerSec, unit)}", "↑ ${Formats.rate(t.txBytesPerSec, unit)} · device")
    }

    private fun renderLast(result: SpeedTestResult?, loading: Boolean) {
        lastCard.removeAllViews()
        if (loading) return
        if (result == null) {
            lastCard.addView(ui.text(TextStyle.BodyMedium, "No tests yet"))
            lastCard.addView(ui.text(TextStyle.Caption, "Results stay on this device. Nothing is uploaded.", palette.textSecondary).apply {
                setPadding(0, ui.dp(6), 0, 0)
            })
            lastCard.isClickable = false
            return
        }
        val values = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun cell(label: String, value: String, color: Int) = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text(TextStyle.Label, label, palette.textTertiary))
            addView(ui.text(TextStyle.ValueSmall, value, color).apply { setPadding(0, ui.dp(8), 0, 0) })
        }
        values.addView(cell("↓ Down", Formats.speed(result.downloadMbps, unit), palette.textPrimary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
        values.addView(cell("↑ Up", Formats.speed(result.uploadMbps, unit), palette.textPrimary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
        values.addView(cell("Ping", "${Formats.ms(result.pingMs)} ms", palette.textPrimary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f))
        lastCard.addView(values)
        val meta = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(18), 0, 0)
        }
        meta.addView(ui.text(TextStyle.Caption, "${Formats.shortDate(result.timestampMillis)} · ${result.networkDetail}", palette.textSecondary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        meta.addView(ui.text(TextStyle.Caption, "All history  ›", palette.accentOnSurface).apply {
            minHeight = ui.dp(48)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(12), 0, 0, 0)
            setOnClickListener { env.navigator.push(Route.History) }
            contentDescription = "Open history"
        })
        lastCard.addView(meta)
        lastCard.onClick("Last test result. Open details") { env.navigator.push(Route.Result(result.timestampMillis)) }
    }

    private companion object {
        const val AUTO_RESULT_DELAY_MS = Motion.SLOW * 2
    }
}
