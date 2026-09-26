package com.netspeedtest.ui.screens

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.netspeedtest.data.SpeedUnit
import com.netspeedtest.speedtest.SpeedTestResult
import com.netspeedtest.ui.Formats
import com.netspeedtest.ui.components.Glyph
import com.netspeedtest.ui.components.IconButton
import com.netspeedtest.ui.components.IconView
import com.netspeedtest.ui.components.Motion
import com.netspeedtest.ui.components.NeuCard
import com.netspeedtest.ui.components.staggerIn
import com.netspeedtest.ui.components.unclip
import com.netspeedtest.ui.nav.Route
import com.netspeedtest.ui.nav.Screen
import com.netspeedtest.ui.nav.ScreenEnv
import com.netspeedtest.ui.theme.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class HistoryScreen(env: ScreenEnv) : Screen(env) {
    private val palette = ui.palette
    private val list = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        unclip()
    }
    private val clear: TextView = ui.text(TextStyle.BodyMedium, "Clear", palette.danger).apply {
        gravity = Gravity.CENTER
        minHeight = ui.dp(48)
        minWidth = ui.dp(64)
        contentDescription = "Clear all history"
        setOnClickListener { confirmClear() }
    }
    private var firstRender = true

    init {
        header("History", clear)
        addBlock(list)
    }

    override fun onActive(scope: CoroutineScope) {
        env.graph.history.load()
        scope.launch {
            env.graph.history.history.collect { render(it, env.graph.settings.settings.value.unit) }
        }
    }

    private fun confirmClear() {
        env.sheets.confirm(
            title = "Clear history?",
            message = "All saved results on this device will be deleted. This can't be undone.",
            confirmLabel = "Clear all",
            destructive = true,
        ) { env.graph.history.clear() }
    }

    private fun render(items: List<SpeedTestResult>?, unit: SpeedUnit) {
        list.removeAllViews()
        clear.visibility = if (items.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
        when {
            items == null -> list.addView(ui.text(TextStyle.Body, "Loading…", palette.textSecondary))
            items.isEmpty() -> list.addView(emptyState())
            else -> {
                val rows = items.map { row(it, unit) }
                rows.forEachIndexed { i, row ->
                    list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        if (i > 0) topMargin = ui.dp(16)
                    })
                }
                if (firstRender) staggerIn(ui, rows.take(8), step = 35)
            }
        }
        if (items != null) firstRender = false
    }

    private fun emptyState() = NeuCard(ui, radiusDp = 26f).apply {
        setPadding(ui.dp(24), ui.dp(32), ui.dp(24), ui.dp(32))
        gravity = Gravity.CENTER_HORIZONTAL
        addView(IconView(ui, Glyph.History, palette.textTertiary), LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)))
        addView(ui.text(TextStyle.BodyMedium, "No results yet").apply { setPadding(0, ui.dp(16), 0, 0) })
        addView(ui.text(TextStyle.Caption, "Run a test and it will be saved here — on this device only.", palette.textSecondary).apply {
            gravity = Gravity.CENTER
            setPadding(0, ui.dp(6), 0, 0)
        })
    }

    private fun row(r: SpeedTestResult, unit: SpeedUnit): View {
        val card = NeuCard(ui, radiusDp = 22f).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(18), ui.dp(16), ui.dp(8), ui.dp(16))
        }
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(ui.text(TextStyle.Caption, "${Formats.dateTime(r.timestampMillis)} · ${r.networkDetail}", palette.textSecondary))
        val values = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ui.dp(10), 0, 0)
        }
        values.addView(ui.text(TextStyle.ValueSmall, "↓ ${Formats.speed(r.downloadMbps, unit)}", palette.textPrimary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f))
        values.addView(ui.text(TextStyle.ValueSmall, "↑ ${Formats.speed(r.uploadMbps, unit)}", palette.textPrimary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f))
        values.addView(ui.text(TextStyle.ValueSmall, "${Formats.ms(r.pingMs)} ms", palette.textSecondary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f))
        body.addView(values)
        card.addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val delete = IconButton(ui, Glyph.Trash, "Delete result from ${Formats.dateTime(r.timestampMillis)}", palette.textSecondary)
        delete.surface.depth = 0f
        delete.onClick {
            if (ui.reduceMotion) {
                env.graph.history.delete(r.timestampMillis)
            } else {
                card.animate().alpha(0f).translationX(ui.dp(24f)).setDuration(Motion.MEDIUM)
                    .withEndAction { env.graph.history.delete(r.timestampMillis) }.start()
            }
        }
        card.addView(delete, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        card.onClick("Result from ${Formats.dateTime(r.timestampMillis)}: download ${Formats.speed(r.downloadMbps, unit)}, upload ${Formats.speed(r.uploadMbps, unit)}, ping ${Formats.ms(r.pingMs)} milliseconds") {
            env.navigator.push(Route.Result(r.timestampMillis))
        }
        return card
    }
}
