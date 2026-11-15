package com.albertsalendah.cafecashier.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// DAILY_CASH reduces the cash drawer; OPERATIONAL does not.
enum class ExpensePool { DAILY_CASH, OPERATIONAL }

@Entity(
    tableName = "expense",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseCategory::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("categoryId"), Index("day")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val unitAmount: Long,                   // whole rupiah
    val quantity: Int,
    val total: Long,                        // unitAmount x quantity, stored so it never changes retroactively
    val pool: ExpensePool,
    val day: Long,                          // epoch day in the café's time zone
    val cancelled: Boolean = false          // a wrong expense is cancelled, never deleted
)

// No delete. Cancelling = update with cancelled = true; the list queries still return cancelled
// rows so the UI can show them struck through, while sumTotal skips them.
@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense WHERE day = :day ORDER BY id")
    fun observeByDay(day: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expense WHERE day >= :fromDay AND day <= :toDay ORDER BY day, id")
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<Expense>>

    // COALESCE so a day with no expenses gives 0, not NULL (the old app failed here).
    // Cancelled expenses are not counted. Both bounds inclusive; for a single day pass the same value twice.
    @Query(
        "SELECT COALESCE(SUM(total), 0) FROM expense " +
            "WHERE cancelled = 0 AND pool = :pool AND day >= :fromDay AND day <= :toDay"
    )
    suspend fun sumTotal(pool: ExpensePool, fromDay: Long, toDay: Long): Long

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    @Insert
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)
}
