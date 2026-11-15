package com.albertsalendah.cafecashier.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,           // single row, always id 1
    val storeName: String = "",
    val address: String = "",
    val receiptFooter: String = "",
    val taxPercent: Int = 0,               // whole percent
    val roundingStep: Long = 100,          // rupiah, rounds the final total up
    val logoPath: String? = null,
    val accentColor: Int? = null,          // ARGB
    val pinHash: String? = null,
    val pinSalt: String? = null            // needed to verify the salted PIN hash
)

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<Settings?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): Settings?

    @Upsert
    suspend fun upsert(settings: Settings)

    // Call once at app start (from the Application subclass) so the row always exists.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(settings: Settings)
}