package com.netspeedtest.ui.screens

import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.netspeedtest.data.SpeedUnit
import com.netspeedtest.data.ThemeMode
import com.netspeedtest.ui.components.Glyph
import com.netspeedtest.ui.components.IconView
import com.netspeedtest.ui.components.InfoRow
import com.netspeedtest.ui.components.NeuCard
import com.netspeedtest.ui.components.NeuSwitch
import com.netspeedtest.ui.components.SegmentedControl
import com.netspeedtest.ui.components.divider
import com.netspeedtest.ui.components.noteCard
import com.netspeedtest.ui.components.rowsCard
import com.netspeedtest.ui.nav.Screen
import com.netspeedtest.ui.nav.ScreenEnv
import com.netspeedtest.ui.theme.TextStyle
import kotlinx.coroutines.CoroutineScope

class SettingsScreen(env: ScreenEnv) : Screen(env) {
    private val repo = env.graph.settings

    init {
        header("Settings")
        val s = repo.settings.value

        sectionLabel("Appearance", topMargin = 0)
        addBlock(
            SegmentedControl(ui, listOf("System", "Light", "Dark"), ThemeMode.entries.indexOf(s.theme)) { i ->
                repo.update { it.copy(theme = ThemeMode.entries[i]) }
            },
        )

        sectionLabel("Speed unit")
        addBlock(
            SegmentedControl(ui, listOf("Mbps", "MB/s"), SpeedUnit.entries.indexOf(s.unit)) { i ->
                repo.update { it.copy(unit = SpeedUnit.entries[i]) }
            },
        )

        sectionLabel("Connections")
        addBlock(
            SegmentedControl(ui, listOf("Multi", "Single"), if (s.singleConnection) 1 else 0) { i ->
                repo.update { it.copy(singleConnection = i == 1) }
            },
        )
        addBlock(
            ui.text(TextStyle.Caption, "Multi uses parallel connections to find your full capacity. Single shows what one download or upload gets.", ui.palette.textSecondary).apply {
                setPadding(ui.dp(4), ui.dp(10), ui.dp(4), 0)
                setLineSpacing(0f, 1.2f)
            },
        )

        sectionLabel("Behaviour")
        val toggles = NeuCard(ui, radiusDp = 26f).apply { setPadding(ui.dp(20), ui.dp(4), ui.dp(12), ui.dp(4)) }
        toggles.addView(toggleRow("Keep screen on during tests", "Prevents the display sleeping mid-test", s.keepScreenOn) { on ->
            repo.update { it.copy(keepScreenOn = on) }
        })
        toggles.addView(ui.divider())
        toggles.addView(toggleRow("Haptic feedback", "Subtle taps on start, cancel and completion", s.haptics) { on ->
            repo.update { it.copy(haptics = on) }
        })
        addBlock(toggles)

        sectionLabel("Data")
        val clear = NeuCard(ui, radiusDp = 26f).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18))
            addView(IconView(ui, Glyph.Trash, ui.palette.danger), LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)))
            addView(ui.text(TextStyle.BodyMedium, "Clear test history", ui.palette.danger), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = ui.dp(14)
            })
            onClick("Clear test history") {
                env.sheets.confirm(
                    title = "Clear history?",
                    message = "All saved results on this device will be deleted. This can't be undone.",
                    confirmLabel = "Clear all",
                    destructive = true,
                ) { env.graph.history.clear() }
            }
        }
        addBlock(clear)

        sectionLabel("About")
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        addBlock(
            ui.rowsCard(
                listOf(
                    InfoRow(ui, "Version", version ?: "—"),
                    InfoRow(ui, "Test server", "Cloudflare (nearest)"),
                    InfoRow(ui, "Permissions", "Internet, network state"),
                ),
            ),
        )
        addBlock(
            ui.noteCard(
                "Privacy",
                "No accounts, ads, analytics or tracking. Speed tests only exchange test data with the server. " +
                    "History, battery and temperature readings never leave your phone. Tests run only when you start them " +
                    "and stop when you leave the app.",
            ),
            topMargin = 16,
        )
    }

    private fun toggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(12), 0, ui.dp(12))
            val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(ui.text(TextStyle.BodyMedium, title))
            texts.addView(ui.text(TextStyle.Caption, subtitle, ui.palette.textSecondary).apply { setPadding(0, ui.dp(4), 0, 0) })
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val switch = NeuSwitch(ui, checked, onChange).apply { contentDescription = title }
            addView(switch)
            setOnClickListener { switch.toggle() }
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    override fun onActive(scope: CoroutineScope) = Unit
}
