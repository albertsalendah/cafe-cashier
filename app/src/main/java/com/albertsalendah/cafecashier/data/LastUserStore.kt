package com.albertsalendah.cafecashier.data

import android.content.Context

// Who signed in last, so the lock screen can preselect them. Only an id (names are shown on the lock
// screen anyway); nothing secret. Not part of a backup.
interface LastUserStore {
    fun get(): Long?
    fun set(userId: Long)
}

class PrefsLastUserStore(context: Context) : LastUserStore {
    private val prefs = context.applicationContext.getSharedPreferences("lock_screen", Context.MODE_PRIVATE)

    override fun get(): Long? = if (prefs.contains("last_user_id")) prefs.getLong("last_user_id", 0L) else null

    override fun set(userId: Long) {
        prefs.edit().putLong("last_user_id", userId).apply()
    }
}
