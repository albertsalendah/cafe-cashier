package com.albertsalendah.cafecashier.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "expense_category")
data class ExpenseCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val archived: Boolean = false
)

@Dao
interface ExpenseCategoryDao {
    @Query("SELECT * FROM expense_category WHERE archived = 0 ORDER BY name")
    fun observeActive(): Flow<List<ExpenseCategory>>

    @Insert
    suspend fun insert(category: ExpenseCategory): Long

    // Archiving = update with archived = true. Expense categories are never deleted.
    @Update
    suspend fun update(category: ExpenseCategory)
}
