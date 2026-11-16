package com.albertsalendah.cafecashier.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSizingTest {

    @Test
    fun smallImagesAreNotShrunkWhenDecoding() {
        assertEquals(1, sampleSizeFor(800, 600))
        assertEquals(1, sampleSizeFor(1024, 1024))
        assertEquals(1, sampleSizeFor(1500, 1200)) // halving would drop below 1024
    }

    @Test
    fun bigImagesAreShrunkByPowersOfTwoButStayAtLeastTheLimit() {
        assertEquals(2, sampleSizeFor(2048, 1536))
        assertEquals(2, sampleSizeFor(4000, 3000))
        assertEquals(4, sampleSizeFor(8000, 6000))
        for ((w, h) in listOf(4000 to 3000, 3000 to 4000, 12000 to 9000, 5000 to 100)) {
            val sample = sampleSizeFor(w, h)
            assertEquals("power of two for ${w}x$h", 0, sample and (sample - 1))
            assertTrue("big enough after decoding ${w}x$h", maxOf(w, h) / sample >= MAX_IMAGE_SIDE)
        }
    }

    @Test
    fun imagesThatFitKeepTheirSize() {
        assertEquals(800 to 600, fitWithin(800, 600))
        assertEquals(1024 to 1024, fitWithin(1024, 1024))
    }

    @Test
    fun biggerImagesKeepTheirProportions() {
        assertEquals(1024 to 768, fitWithin(2000, 1500))
        assertEquals(768 to 1024, fitWithin(1500, 2000))
        assertEquals(1024 to 1024, fitWithin(3000, 3000))
    }

    @Test
    fun extremeShapesNeverCollapseToZero() {
        assertEquals(1024 to 20, fitWithin(5000, 100))
        assertEquals(1024 to 1, fitWithin(10000, 1))
    }
}
