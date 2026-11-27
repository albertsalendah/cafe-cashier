package com.albertsalendah.cafecashier.domain

// Remembers wrong PIN (and recovery code) tries and applies the waiting times of lockoutSeconds()
// (UserRules.kt). The state sits behind AttemptStore so the real app can keep it in SharedPreferences
// (it must survive the app being killed, or killing it would reset the lockout) while the tests use a map.
//
// The waiting time is measured with the phone's clock, so setting the clock back can shorten it. The
// wait is capped at its normal length, so a clock set far forward or back never locks anyone out for
// longer than that. Like the PIN itself this stops casual guessing, not a determined person.

data class AttemptState(val failed: Int = 0, val lastFailureAt: Long = 0L)

interface AttemptStore {
    fun read(key: String): AttemptState
    fun write(key: String, state: AttemptState)
    fun clear(key: String)
    fun clearAll()
}

/** Attempt key for one user's PIN. */
fun attemptKeyForUser(userId: Long): String = "user_$userId"

/** Attempt key for the recovery code (one counter for the whole app). */
const val RECOVERY_ATTEMPT_KEY = "recovery"

private const val MAX_COUNTED_FAILURES = 1_000_000

class PinAttempts(
    private val store: AttemptStore,
    private val clock: () -> Long
) {
    /** Milliseconds still to wait before [key] may be tried again (0 = go ahead). */
    fun remainingMillis(key: String): Long {
        val state = store.read(key)
        val longest = lockoutSeconds(state.failed) * 1000L
        return remainingLockoutMillis(state.failed, state.lastFailureAt, clock()).coerceAtMost(longest)
    }

    /** Counts one wrong try and returns the wait that now applies (0 for the first few). */
    fun recordFailure(key: String): Long {
        val state = store.read(key)
        store.write(key, AttemptState(minOf(state.failed + 1, MAX_COUNTED_FAILURES), clock()))
        return remainingMillis(key)
    }

    /** A right PIN (or code) starts the count again. */
    fun recordSuccess(key: String) {
        store.clear(key)
    }

    /** Forget everything, for example after a backup was restored (user ids may mean other people then). */
    fun clearAll() {
        store.clearAll()
    }
}

/** Whole seconds for a wait in milliseconds, rounded up, so "0 seconds" is never shown while still waiting. */
fun waitSeconds(millis: Long): Long = if (millis <= 0L) 0L else (millis + 999L) / 1000L
