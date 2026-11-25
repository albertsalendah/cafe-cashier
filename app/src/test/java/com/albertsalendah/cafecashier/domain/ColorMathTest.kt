package com.albertsalendah.cafecashier.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun blendEndsAndMiddle() {
        assertEquals(black, blendArgb(black, white, 0f))
        assertEquals(white, blendArgb(black, white, 1f))
        assertEquals(0xFF808080.toInt(), blendArgb(black, white, 0.5f))
    }

    @Test
    fun blendIsAlwaysOpaqueAndClampsTheFraction() {
        assertEquals(white, blendArgb(0x00000000, white, 5f))
        assertEquals(black, blendArgb(black, white, -1f))
        assertEquals(0xFF, (blendArgb(0x10203040, 0x50607080, 0.3f) ushr 24) and 0xFF)
    }

    @Test
    fun luminanceOfBlackAndWhite() {
        assertEquals(0.0, luminance(black), 1e-9)
        assertEquals(1.0, luminance(white), 1e-6)
    }

    @Test
    fun textColourIsWhiteOnDarkAndBlackOnLight() {
        assertEquals(white, contrastingTextColor(0xFF1565C0.toInt()))   // dark blue
        assertEquals(white, contrastingTextColor(0xFF6A1B9A.toInt()))   // purple
        assertEquals(black, contrastingTextColor(0xFFFFEB3B.toInt()))   // yellow
        assertEquals(black, contrastingTextColor(white))
        assertEquals(white, contrastingTextColor(black))
    }

    @Test
    fun aLightTintIsLighterThanTheColourItCameFrom() {
        val accent = 0xFF00796B.toInt()
        assertTrue(luminance(blendArgb(accent, white, 0.82f)) > luminance(accent))
        assertTrue(luminance(blendArgb(accent, black, 0.45f)) < luminance(accent))
    }
}
