package com.albertsalendah.cafecashier.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.domain.blendArgb
import com.albertsalendah.cafecashier.domain.contrastingTextColor

// The accent colours a café can pick in Settings (stored as ARGB in Settings.accentColor).
// "No accent" (null) keeps the system colours.
data class AccentPreset(val argb: Int, @StringRes val nameRes: Int)

val ACCENT_PRESETS = listOf(
    AccentPreset(0xFF00796B.toInt(), R.string.accent_teal),
    AccentPreset(0xFF1565C0.toInt(), R.string.accent_blue),
    AccentPreset(0xFF3949AB.toInt(), R.string.accent_indigo),
    AccentPreset(0xFF6A1B9A.toInt(), R.string.accent_purple),
    AccentPreset(0xFFC62828.toInt(), R.string.accent_red),
    AccentPreset(0xFFEF6C00.toInt(), R.string.accent_orange),
    AccentPreset(0xFF6D4C41.toInt(), R.string.accent_brown),
    AccentPreset(0xFF2E7D32.toInt(), R.string.accent_green)
)

private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()

// A colour scheme built around one accent colour: the standard Material 3 baseline with the primary
// colours (and the container colour the bottom bar's selected tab uses) replaced. In dark mode the
// accent is lightened so it stays readable on a dark surface.
fun accentColorScheme(accentArgb: Int, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val primary = if (dark) blendArgb(accentArgb, WHITE, 0.45f) else accentArgb
    val container = if (dark) blendArgb(accentArgb, BLACK, 0.45f) else blendArgb(accentArgb, WHITE, 0.82f)
    return base.copy(
        primary = Color(primary),
        onPrimary = Color(contrastingTextColor(primary)),
        primaryContainer = Color(container),
        onPrimaryContainer = Color(contrastingTextColor(container)),
        secondaryContainer = Color(container),
        onSecondaryContainer = Color(contrastingTextColor(container)),
        surfaceTint = Color(primary)
    )
}
