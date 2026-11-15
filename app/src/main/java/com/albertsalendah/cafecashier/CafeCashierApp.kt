package com.albertsalendah.cafecashier

import android.app.Application
import androidx.room.Room
import com.albertsalendah.cafecashier.data.db.AppDatabase
import com.albertsalendah.cafecashier.data.db.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CafeCashierApp : Application() {

    // Created once, on first use. No destructive-migration fallback: the data belongs to the café.
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "cafe_cashier.db").build()
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Make sure the single Settings row (id 1) exists. Does nothing if it is already there.
        appScope.launch { database.settingsDao().insertIfAbsent(Settings()) }
    }
}
