package com.albertsalendah.cafecashier

import android.app.Application
import android.widget.Toast
import androidx.room.Room
import com.albertsalendah.cafecashier.data.BackupManager
import com.albertsalendah.cafecashier.data.DATABASE_FILE_NAME
import com.albertsalendah.cafecashier.data.LastUserStore
import com.albertsalendah.cafecashier.data.PrefsAttemptStore
import com.albertsalendah.cafecashier.data.PrefsLastUserStore
import com.albertsalendah.cafecashier.data.ProductImageStore
import com.albertsalendah.cafecashier.data.db.AppDatabase
import com.albertsalendah.cafecashier.data.db.DEFAULT_EXPENSE_CATEGORIES
import com.albertsalendah.cafecashier.data.db.MIGRATION_1_2
import com.albertsalendah.cafecashier.data.db.Settings
import com.albertsalendah.cafecashier.domain.PinAttempts
import com.albertsalendah.cafecashier.domain.RestoreOutcome
import com.albertsalendah.cafecashier.domain.applyStagedRestore
import com.albertsalendah.cafecashier.ui.order.OrderDraftHolder
import com.albertsalendah.cafecashier.ui.session.SessionHolder
import com.jakewharton.processphoenix.ProcessPhoenix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CafeCashierApp : Application() {

    // Created once, on first use. No destructive-migration fallback: the data belongs to the café.
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, DATABASE_FILE_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    // Product photos live in the app's own storage; see ProductImageStore.
    val imageStore: ProductImageStore by lazy { ProductImageStore(this) }

    // Backup and restore of the database and the photos; see BackupManager.
    val backupManager: BackupManager by lazy { BackupManager(this, database, imageStore.dir) }

    // The order being taken (in memory only), shared by the Order screen, the cart and the checkout.
    val orderDraftHolder: OrderDraftHolder = OrderDraftHolder()

    // Who is signed in while staff accounts are on (in memory only). When another person signs in,
    // the order in progress is thrown away.
    val session: SessionHolder by lazy {
        SessionHolder(database.settingsDao(), database.appUserDao()) { orderDraftHolder.clear() }
    }

    // Who signed in last (the lock screen preselects them).
    val lastUserStore: LastUserStore by lazy { PrefsLastUserStore(this) }

    // Wrong-PIN counters for the lock screen, kept in SharedPreferences so killing the app does not reset them.
    val pinAttempts: PinAttempts by lazy {
        PinAttempts(PrefsAttemptStore(this)) { System.currentTimeMillis() }
    }

    private val appScope =CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // After a restore the app restarts through ProcessPhoenix, which starts a short-lived helper
        // process that also creates this class while the old process may still be closing. That
        // helper must not touch the data, so it stops here.
        if (ProcessPhoenix.isPhoenixProcess(this)) return

        // First thing, before anything can open the database: if the user confirmed a restore,
        // swap the backup in now (see RestoreApplier for how it stays safe if anything goes wrong).
        applyPendingRestore()

        appScope.launch {
            // Make sure the single Settings row (id 1) exists. Does nothing if it is already there.
            database.settingsDao().insertIfAbsent(Settings())
            // First run only: add the default expense categories. Does nothing once the table has any row.
            database.expenseCategoryDao().seedIfEmpty(DEFAULT_EXPENSE_CATEGORIES)
        }
    }

    private fun applyPendingRestore() {
        val outcome = applyStagedRestore(
            stagingDir = BackupManager.stagingDir(this),
            databaseFile = getDatabasePath(DATABASE_FILE_NAME),
            imagesDir = imageStore.dir
        )
        when (outcome) {
            RestoreOutcome.RESTORED -> {
                // The restored users may have other ids: start the wrong-PIN counters again.
                pinAttempts.clearAll()
                Toast.makeText(this, R.string.restore_done, Toast.LENGTH_LONG).show()
            }
            RestoreOutcome.FAILED -> Toast.makeText(this, R.string.restore_failed_kept, Toast.LENGTH_LONG).show()
            RestoreOutcome.NOTHING_PENDING -> Unit
        }
    }
}
