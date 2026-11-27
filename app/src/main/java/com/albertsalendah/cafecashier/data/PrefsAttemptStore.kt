package com.albertsalendah.cafecashier.data

import android.content.Context
import android.content.SharedPreferences
import com.albertsalendah.cafecashier.domain.AttemptState
import com.albertsalendah.cafecashier.domain.AttemptStore

// Wrong-PIN counters in SharedPreferences, so killing the app does not reset the lockout. Written with
// commit() (not apply()) so a try that was just counted is on disk even if the process dies at once.
// Not part of a backup, and cleared after a restore (see CafeCashierApp).
class PrefsAttemptStore(context: Context) : AttemptStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("pin_attempts", Context.MODE_PRIVATE)

    override fun read(key: String): AttemptState =
        AttemptState(
            failed = prefs.getInt("$key.failed", 0),
            lastFailureAt = prefs.getLong("$key.last", 0L)
        )

    override fun write(key: String, state: AttemptState) {
        prefs.edit()
            .putInt("$key.failed", state.failed)
            .putLong("$key.last", state.lastFailureAt)
            .commit()
    }

    override fun clear(key: String) {
        prefs.edit().remove("$key.failed").remove("$key.last").commit()
    }

    override fun clearAll() {
        prefs.edit().clear().commit()
    }
}
