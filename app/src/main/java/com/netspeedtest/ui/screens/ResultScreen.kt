package com.netspeedtest.ui.screens

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.netspeedtest.data.SpeedUnit
import com.netspeedtest.speedtest.Grade
import com.netspeedtest.speedtest.QualityRating
import com.netspeedtest.speedtest.SpeedTestResult
import com.netspeedtest.ui.Formats
import com.netspeedtest.ui.components.Haptics
import com.netspeedtest.ui.components.InfoRow
import com.netspeedtest.ui.components.NeuButton
import com.netspeedtest.ui.components.NeuCard
import com.netspeedtest.ui.components.StatusDot
import com.netspeedtest.ui.components.divider
import com.netspeedtest.ui.components.rowsCard
import com.netspeedtest.ui.components.staggerIn
import com.netspeedtest.ui.components.unclip
import com.netspeedtest.ui.nav.Screen
import com.netspeedtest.ui.nav.ScreenEnv
import com.netspeedtest.ui.theme.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Summary of one test: headline numbers, what they mean, and how they were measured. */
class ResultScreen(env: ScreenEnv, private val timestamp: Long, private val fresh: Boolean) : Screen(env) {
    private val palette = ui.palette
    private var shown = false

    init {
        header(if (fresh) "Your result" else "Result")
    }

    override fun onActive(scope: CoroutineScope) {
        if (shown) return
        scope.launch {
            val unit = env.graph.settings.settings.value.unit
            val live = env.graph.speedTest.uiState.value.result?.takeIf { it.timestampMillis == timestamp }
            env.graph.history.load()
            val result = live ?: env.graph.history.history.filterNotNull().first().firstOrNull { it.timestampMillis == timestamp }
            if (result == null) showMissing() else show(result, unit)
            shown = true
        }
    }

    private fun showMissing() {
        addBlock(ui.text(TextStyle.Body, "This result is no longer available. It may have been deleted.", palette.textSecondary))
    }

    private fun show(r: SpeedTestResult, unit: SpeedUnit) {
        val blocks = ArrayList<View>()

        val hero = NeuCard(ui, radiusDp = 32f).apply { setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(8)) }
        hero.addView(bigValue("Download", Formats.speedValue(r.downloadMbps, unit), unit.label, 56f, palette.accentOnSurface))
        hero.addView(bigValue("Upload", Formats.speedValue(r.uploadMbps, unit), unit.label, 40f, palette.upload), marginTop(22))
        hero.addView(ui.divider(), marginTop(24))
        val latency = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        latency.addView(smallValue("Ping", Formats.ms(r.pingMs), "ms"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        latency.addView(smallValue("Jitter", Formats.ms(r.jitterMs), "ms"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hero.addView(latency)
        if (r.loadedLatencyDownMs != null || r.loadedLatencyUpMs != null) {
            hero.addView(ui.divider())
            val loaded = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            loaded.addView(smallValue("Loaded ↓", Formats.ms(r.loadedLatencyDownMs), "ms"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            loaded.addView(smallValue("Loaded ↑", Formats.ms(r.loadedLatencyUpMs), "ms"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            hero.addView(loaded)
        }
        blocks += addBlock(hero)

        blocks += sectionLabel("Good for")
        val verdicts = QualityRating.evaluate(r.downloadMbps, r.uploadMbps, r.pingMs, r.jitterMs)
        val rows = verdicts.map { v ->
            InfoRow(ui, v.activity).also { row ->
                val color = when (v.grade) {
                    Grade.Excellent -> palette.accentOnSurface
                    Grade.Good -> palette.textPrimary
                    Grade.Fair -> palette.warning
                    Grade.Poor -> palette.danger
                }
                row.set(v.label, v.grade.name.takeIf { it != v.label }, color)
                val dot = StatusDot(ui).apply { set(color, hollow = v.grade == Grade.Fair || v.grade == Grade.Poor) }
                row.addView(dot, 0, LinearLayout.LayoutParams(ui.dp(8), ui.dp(8)).apply { rightMargin = ui.dp(12) })
            }
        }
        blocks += addBlock(ui.rowsCard(rows))

        blocks += sectionLabel("Details")
        blocks += addBlock(
            ui.rowsCard(
                listOf(
                    InfoRow(ui, "Connection", r.connectionType),
                    InfoRow(ui, "Network", r.networkDetail),
                    InfoRow(ui, "Server", r.serverName),
                    InfoRow(ui, "Tested", Formats.dateTime(r.timestampMillis)),
                    InfoRow(ui, "Data used", Formats.bytes(r.bytesUsed)),
                ),
            ),
        )

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            unclip()
        }
        val done = NeuButton(ui, "Done", primary = false).apply { onClick { env.navigator.pop() } }
        val again = NeuButton(ui, "Test again", primary = true).apply {
            onClick {
                Haptics.confirm(this)
                env.navigator.replaceWithHome()
                env.graph.speedTest.start()
            }
        }
        buttons.addView(done, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = ui.dp(8) })
        buttons.addView(again, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = ui.dp(8) })
        blocks += addBlock(buttons, topMargin = 28)

        staggerIn(ui, blocks, startDelay = 40, step = 55)
    }

    private fun marginTop(dp: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = ui.dp(dp)
    }

    private fun bigValue(label: String, value: String, unit: String, sizeSp: Float, accent: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(ui.text(TextStyle.Label, label, accent))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        row.addView(ui.text(TextStyle.Display, value).apply { textSize = sizeSp })
        row.addView(ui.text(TextStyle.BodyMedium, unit, palette.textSecondary).apply {
            setPadding(ui.dp(8), 0, 0, ui.dp(8))
        })
        addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(6)
        })
        isFocusable = true
        contentDescription = "$label $value $unit"
    }

    private fun smallValue(label: String, value: String, unit: String) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, ui.dp(16), 0, ui.dp(16))
        addView(ui.text(TextStyle.Label, label, palette.textTertiary))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        row.addView(ui.text(TextStyle.Value, value))
        row.addView(ui.text(TextStyle.Caption, unit, palette.textSecondary).apply { setPadding(ui.dp(5), 0, 0, ui.dp(2)) })
        addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(8)
        })
        isFocusable = true
        contentDescription = "$label $value $unit"
    }
}
