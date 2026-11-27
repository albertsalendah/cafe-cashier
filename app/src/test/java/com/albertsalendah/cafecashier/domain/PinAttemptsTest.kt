package com.albertsalendah.cafecashier.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinAttemptsTest {

    private class MapStore : AttemptStore {
        val map = mutableMapOf<String, AttemptState>()
        override fun read(key: String) = map[key] ?: AttemptState()
        override fun write(key: String, state: AttemptState) { map[key] = state }
        override fun clear(key: String) { map.remove(key) }
        override fun clearAll() { map.clear() }
    }

    private var now = 1_000_000L
    private val store = MapStore()
    private val attempts = PinAttempts(store) { now }

    private fun fail(key: String, times: Int): Long {
        var wait = 0L
        repeat(times) { wait = attempts.recordFailure(key) }
        return wait
    }

    @Test
    fun theFirstFourWrongTriesAreFree() {
        assertEquals(0L, fail("a", 4))
        assertEquals(0L, attempts.remainingMillis("a"))
    }

    @Test
    fun theFifthWrongTryWaitsThirtySecondsAndItCountsDown() {
        assertEquals(30_000L, fail("a", 5))
        now += 10_000
        assertEquals(20_000L, attempts.remainingMillis("a"))
        now += 20_000
        assertEquals(0L, attempts.remainingMillis("a"))
    }

    @Test
    fun theWaitsGrowWithEveryWrongTry() {
        assertEquals(30_000L, fail("a", 5))
        now += 30_000
        assertEquals(60_000L, attempts.recordFailure("a"))
        now += 60_000
        assertEquals(300_000L, attempts.recordFailure("a"))
        now += 300_000
        assertEquals(900_000L, attempts.recordFailure("a"))
        now += 900_000
        assertEquals(900_000L, attempts.recordFailure("a"))      // stays at fifteen minutes
    }

    @Test
    fun aRightPinStartsTheCountAgain() {
        fail("a", 5)
        attempts.recordSuccess("a")
        assertEquals(0L, attempts.remainingMillis("a"))
        assertEquals(0L, fail("a", 4))
    }

    @Test
    fun usersAndTheRecoveryCodeAreCountedSeparately() {
        fail(attemptKeyForUser(1), 5)
        assertEquals(30_000L, attempts.remainingMillis(attemptKeyForUser(1)))
        assertEquals(0L, attempts.remainingMillis(attemptKeyForUser(2)))
        assertEquals(0L, attempts.remainingMillis(RECOVERY_ATTEMPT_KEY))
    }

    @Test
    fun theCountSurvivesANewInstanceOnTheSameStore() {
        // What killing the app does: a new PinAttempts, the same saved state.
        fail("a", 6)
        val afterRestart = PinAttempts(store) { now }
        assertEquals(60_000L, afterRestart.remainingMillis("a"))
    }

    @Test
    fun aClockSetFarBackNeverWaitsLongerThanTheNormalWait() {
        fail("a", 5)
        now -= 10_000_000
        assertEquals(30_000L, attempts.remainingMillis("a"))
    }

    @Test
    fun clearAllForgetsEveryone() {
        fail("a", 5)
        fail("b", 6)
        attempts.clearAll()
        assertEquals(0L, attempts.remainingMillis("a"))
        assertEquals(0L, attempts.remainingMillis("b"))
        assertTrue(store.map.isEmpty())
    }

    @Test
    fun waitSecondsRoundsUp() {
        assertEquals(0L, waitSeconds(0))
        assertEquals(0L, waitSeconds(-5))
        assertEquals(1L, waitSeconds(1))
        assertEquals(1L, waitSeconds(1000))
        assertEquals(2L, waitSeconds(1001))
        assertEquals(30L, waitSeconds(30_000))
    }
}
