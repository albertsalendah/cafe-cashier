package com.albertsalendah.cafecashier.domain

import kotlin.math.pow
import kotlin.math.roundToInt

// Small colour helpers for the accent colour. Colours are ARGB Ints, as in Settings.accentColor.
// Pure Kotlin so they can be unit tested without Android.

private const val OPAQUE_BLACK = 0xFF000000.toInt()
private const val OPAQUE_WHITE = 0xFFFFFFFF.toInt()

// Mixes [from] towards [to]: fraction 0 gives [from], 1 gives [to]. The result is fully opaque.
fun blendArgb(from: Int, to: Int, fraction: Float): Int {
    val f = fraction.coerceIn(0f, 1f)
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * f).roundToInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

// Relative luminance (WCAG): 0.0 for black, 1.0 for white.
fun luminance(argb: Int): Double {
    fun linear(channel: Int): Double {
        val s = channel / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    val r = linear((argb shr 16) and 0xFF)
    val g = linear((argb shr 8) and 0xFF)
    val b = linear(argb and 0xFF)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

// Black or white, whichever reads better on top of [background] (the crossover is luminance 0.179,
// where both have the same contrast).
fun contrastingTextColor(background: Int): Int =
    if (luminance(background) > 0.179) OPAQUE_BLACK else OPAQUE_WHITE
