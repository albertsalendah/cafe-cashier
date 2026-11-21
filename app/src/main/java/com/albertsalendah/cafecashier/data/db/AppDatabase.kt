package com.albertsalendah.cafecashier.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        Category::class,
        Product::class,
        AddOnOption::class,
        OrderDiscount::class,
        Settings::class,
        Sale::class,
        SaleItem::class,
        DailyCash::class,
        ExpenseCategory::class,
        Expense::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun addOnOptionDao(): AddOnOptionDao
    abstract fun orderDiscountDao(): OrderDiscountDao
    abstract fun settingsDao(): SettingsDao
    abstract fun saleDao(): SaleDao
    abstract fun saleItemDao(): SaleItemDao
    abstract fun saleWriteDao(): SaleWriteDao
    abstract fun dailyCashDao(): DailyCashDao
    abstract fun expenseCategoryDao(): ExpenseCategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun menuSeedDao(): MenuSeedDao
}
