package com.netspeedtest.ui.theme

import android.content.Context
import android.content.res.Configuration
import com.netspeedtest.data.ThemeMode

/**
 * The complete colour system. Solid colours only — the depth of the neumorphic surfaces
 * comes from a pair of soft shadows ([shadowDark] / [shadowLight]), never gradients.
 * Text colours are chosen for ≥ 4.5:1 contrast against [background] and [surface].
 */
data class Palette(
    val isDark: Boolean,
    val background: Int,
    val surface: Int,
    val surfacePressed: Int,
    val well: Int,
    val shadowDark: Int,
    val shadowLight: Int,
    val divider: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textTertiary: Int,
    /** Primary accent: call-to-action, download, success. */
    val accent: Int,
    /** Accent used as text/strokes on [background] (identical in dark mode). */
    val accentOnSurface: Int,
    val onAccent: Int,
    val cta: Int,
    val onCta: Int,
    val upload: Int,
    val warning: Int,
    val danger: Int,
    val scrim: Int,
) {
    companion object {
        val Dark = Palette(
            isDark = true,
            background = 0xFF161719.toInt(),
            surface = 0xFF1A1B1E.toInt(),
            surfacePressed = 0xFF18191C.toInt(),
            well = 0xFF121315.toInt(),
            shadowDark = 0xE6060607.toInt(),
            shadowLight = 0x1AFFFFFF,
            divider = 0xFF26272B.toInt(),
            textPrimary = 0xFFF2F2EE.toInt(),
            textSecondary = 0xFFA6A8AF.toInt(),
            textTertiary = 0xFF84878F.toInt(),
            accent = 0xFFD4F264.toInt(),
            accentOnSurface = 0xFFD4F264.toInt(),
            onAccent = 0xFF121315.toInt(),
            cta = 0xFFD4F264.toInt(),
            onCta = 0xFF121315.toInt(),
            upload = 0xFFA3B8FF.toInt(),
            warning = 0xFFFFBE5C.toInt(),
            danger = 0xFFFF7A6B.toInt(),
            scrim = 0x99000000.toInt(),
        )

        val Light = Palette(
            isDark = false,
            background = 0xFFE7E9ED.toInt(),
            surface = 0xFFEAECF0.toInt(),
            surfacePressed = 0xFFE3E6EA.toInt(),
            well = 0xFFDEE1E6.toInt(),
            shadowDark = 0x59A3AAB8,
            shadowLight = 0xFFFFFFFF.toInt(),
            divider = 0xFFD6D9DF.toInt(),
            textPrimary = 0xFF111215.toInt(),
            textSecondary = 0xFF474B53.toInt(),
            textTertiary = 0xFF5E626B.toInt(),
            accent = 0xFF9BC53D.toInt(),
            accentOnSurface = 0xFF3F6212.toInt(),
            onAccent = 0xFF111215.toInt(),
            cta = 0xFF141518.toInt(),
            onCta = 0xFFF2F3F5.toInt(),
            upload = 0xFF3450C4.toInt(),
            warning = 0xFF8F5200.toInt(),
            danger = 0xFFB3261E.toInt(),
            scrim = 0x66000000,
        )

        fun resolve(context: Context, mode: ThemeMode): Palette = when (mode) {
            ThemeMode.Dark -> Dark
            ThemeMode.Light -> Light
            ThemeMode.System -> {
                val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (night == Configuration.UI_MODE_NIGHT_YES) Dark else Light
            }
        }
    }
}
