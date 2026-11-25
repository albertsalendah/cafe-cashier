package com.albertsalendah.cafecashier

import android.app.Application
import androidx.room.Room
import com.albertsalendah.cafecashier.data.ProductImageStore
import com.albertsalendah.cafecashier.data.db.AppDatabase
import com.albertsalendah.cafecashier.data.db.DEFAULT_EXPENSE_CATEGORIES
import com.albertsalendah.cafecashier.data.db.Settings
import com.albertsalendah.cafecashier.ui.order.OrderDraftHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CafeCashierApp : Application() {

    // Created once, on first use. No destructive-migration fallback: the data belongs to the café.
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "cafe_cashier.db").build()
    }

    // Product photos live in the app's own storage; see ProductImageStore.
    val imageStore: ProductImageStore by lazy { ProductImageStore(this) }

    // The order being taken (in memory only), shared by the Order screen, the cart and the checkout.
    val orderDraftHolder: OrderDraftHolder = OrderDraftHolder()

    private val appScope =CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // Make sure the single Settings row (id 1) exists. Does nothing if it is already there.
            database.settingsDao().insertIfAbsent(Settings())
            // First run only: add the default expense categories. Does nothing once the table has any row.
            database.expenseCategoryDao().seedIfEmpty(DEFAULT_EXPENSE_CATEGORIES)
        }
    }
}
