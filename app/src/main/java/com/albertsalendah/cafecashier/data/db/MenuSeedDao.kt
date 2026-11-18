package com.albertsalendah.cafecashier.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// Only used by the one-time import of the old menu. An abstract class (not an interface like the
// other DAOs) so that importMenu can be a @Transaction method with a body: all or nothing.
@Dao
abstract class MenuSeedDao {

    // Whole-table counts, hidden items included: the import is only offered on a truly empty menu.
    @Query("SELECT COUNT(*) FROM category")
    abstract fun observeCategoryCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM product")
    abstract fun observeProductCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM category")
    abstract suspend fun categoryCount(): Int

    @Query("SELECT COUNT(*) FROM product")
    abstract suspend fun productCount(): Int

    @Insert
    abstract suspend fun insertCategory(category: Category): Long

    @Insert
    abstract suspend fun insertProducts(products: List<Product>)

    // Inserts every category with its products, but only into a completely empty menu.
    // Returns false and changes nothing otherwise. If anything fails, nothing is kept.
    // The products' categoryId is a placeholder here; it is replaced with the new category's id.
    @Transaction
    open suspend fun importMenu(entries: List<Pair<Category, List<Product>>>): Boolean {
        if (categoryCount() > 0 || productCount() > 0) return false
        for ((category, products) in entries) {
            val categoryId = insertCategory(category)
            insertProducts(products.map { it.copy(categoryId = categoryId) })
        }
        return true
    }
}
