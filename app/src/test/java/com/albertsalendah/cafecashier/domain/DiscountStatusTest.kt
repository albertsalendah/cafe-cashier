package com.albertsalendah.cafecashier.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscountStatusTest {

    @Test
    fun noPercentMeansNoDiscountWhateverTheDates() {
        assertEquals(DiscountStatus.None, discountStatus(0, 10L, 20L, 15L))
        assertEquals(DiscountStatus.None, discountStatus(0, null, null, 15L))
    }

    @Test
    fun beforeTheStartDateItIsScheduled() {
        assertEquals(DiscountStatus.Scheduled(20L), discountStatus(10, 20L, 30L, 19L))
    }

    @Test
    fun onTheFirstAndLastDayItIsActive() {
        assertEquals(DiscountStatus.Active(10), discountStatus(10, 20L, 30L, 20L))
        assertEquals(DiscountStatus.Active(0), discountStatus(10, 20L, 30L, 30L))
    }

    @Test
    fun withoutAnEndDateItIsActiveForever() {
        val status = discountStatus(10, 20L, null, 500L)
        assertEquals(DiscountStatus.Active(null), status)
        assertFalse((status as DiscountStatus.Active).endingSoon)
    }

    @Test
    fun theDayAfterTheEndDateItIsExpired() {
        assertEquals(DiscountStatus.Expired(30L), discountStatus(10, 20L, 30L, 31L))
        assertEquals(DiscountStatus.Expired(30L), discountStatus(10, null, 30L, 400L))
    }

    @Test
    fun endingSoonCoversTheLastThreeDaysAndTheLastDay() {
        fun soon(today: Long) = (discountStatus(10, null, 100L, today) as DiscountStatus.Active).endingSoon
        assertFalse(soon(95L)) // 5 days left
        assertFalse(soon(96L)) // 4 days left
        assertTrue(soon(97L))  // 3 days left
        assertTrue(soon(99L))  // 1 day left
        assertTrue(soon(100L)) // the last day
    }

    @Test
    fun statusIsActiveExactlyWhenTheCheckoutRuleGivesADiscount() {
        val windows = listOf(
            Triple(0, null as Long?, null as Long?),
            Triple(10, null, null),
            Triple(10, 100L, null),
            Triple(10, null, 110L),
            Triple(10, 100L, 110L),
            Triple(10, 110L, 100L) // end before start: never applies
        )
        for ((percent, start, end) in windows) {
            for (today in 90L..120L) {
                val applies = effectiveDiscountPercent(percent, start, end, today) > 0
                val active = discountStatus(percent, start, end, today) is DiscountStatus.Active
                assertEquals("percent=$percent start=$start end=$end today=$today", applies, active)
            }
        }
    }
}
