package com.albertsalendah.cafecashier.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "expense_category")
data class ExpenseCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val archived: Boolean = false
)

// Categories added the first time the app runs, so the expense form is usable straight away.
// The owner can rename or hide any of them and add their own.
val DEFAULT_EXPENSE_CATEGORIES = listOf(
    "Ingredients",
    "Packaging",
    "Cleaning supplies",
    "Salaries",
    "Rent",
    "Utilities",
    "Transport",
    "Repairs",
    "Other"
)

// An abstract class (not an interface like the simpler DAOs) so that seedIfEmpty can be a
// @Transaction method with a body, the same pattern as MenuSeedDao and SaleWriteDao.
// Expense categories are never deleted: hiding = update with archived = true.
@Dao
abstract class ExpenseCategoryDao {
    @Query("SELECT * FROM expense_category WHERE archived = 0 ORDER BY name")
    abstract fun observeActive(): Flow<List<ExpenseCategory>>

    @Query("SELECT * FROM expense_category WHERE archived = 1 ORDER BY name")
    abstract fun observeArchived(): Flow<List<ExpenseCategory>>

    // Every category, hidden ones included: an old expense must still show its category's name.
    @Query("SELECT * FROM expense_category")
    abstract fun observeAll(): Flow<List<ExpenseCategory>>

    // Whole-table count, hidden categories included.
    @Query("SELECT COUNT(*) FROM expense_category")
    abstract suspend fun count(): Int

    @Insert
    abstract suspend fun insert(category: ExpenseCategory): Long

    @Insert
    abstract suspend fun insertAll(categories: List<ExpenseCategory>)

    @Update
    abstract suspend fun update(category: ExpenseCategory)

    // Adds the given names, but only into a completely empty table (hidden categories count, and
    // nothing is ever deleted, so this adds the defaults once and never again). All or nothing.
    // Returns false and changes nothing when the table already has rows.
    @Transaction
    open suspend fun seedIfEmpty(names: List<String>): Boolean {
        if (count() > 0) return false
        insertAll(names.map { ExpenseCategory(name = it) })
        return true
    }
}
