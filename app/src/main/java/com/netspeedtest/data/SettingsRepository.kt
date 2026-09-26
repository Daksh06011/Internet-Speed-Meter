package com.netspeedtest.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }

enum class SpeedUnit(val label: String) { Mbps("Mbps"), MBps("MB/s") }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.Dark,
    val unit: SpeedUnit = SpeedUnit.Mbps,
    val keepScreenOn: Boolean = false,
    val haptics: Boolean = true,
)

/** A handful of preferences in [android.content.SharedPreferences]; read once, cached in memory. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = state.asStateFlow()

    private fun load() = AppSettings(
        theme = enumOrDefault(prefs.getString(KEY_THEME, null), ThemeMode.Dark),
        unit = enumOrDefault(prefs.getString(KEY_UNIT, null), SpeedUnit.Mbps),
        keepScreenOn = prefs.getBoolean(KEY_SCREEN_ON, false),
        haptics = prefs.getBoolean(KEY_HAPTICS, true),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        if (next == state.value) return
        state.value = next
        prefs.edit()
            .putString(KEY_THEME, next.theme.name)
            .putString(KEY_UNIT, next.unit.name)
            .putBoolean(KEY_SCREEN_ON, next.keepScreenOn)
            .putBoolean(KEY_HAPTICS, next.haptics)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_UNIT = "unit"
        const val KEY_SCREEN_ON = "keep_screen_on"
        const val KEY_HAPTICS = "haptics"
    }
}
