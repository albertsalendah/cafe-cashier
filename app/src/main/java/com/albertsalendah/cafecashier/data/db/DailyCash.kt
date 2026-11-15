package com.albertsalendah.cafecashier.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Opening cash per day. Closing cash is calculated when needed, never stored.
@Entity(tableName = "daily_cash")
data class DailyCash(
    @PrimaryKey val day: Long,              // epoch day in the café's time zone
    val openingCash: Long
)

@Dao
interface DailyCashDao {
    @Query("SELECT * FROM daily_cash WHERE day = :day")
    fun observe(day: Long): Flow<DailyCash?>

    @Query("SELECT * FROM daily_cash WHERE day = :day")
    suspend fun get(day: Long): DailyCash?

    @Upsert
    suspend fun upsert(dailyCash: DailyCash)
}
