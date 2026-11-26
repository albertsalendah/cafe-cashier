package com.albertsalendah.cafecashier.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.albertsalendah.cafecashier.domain.UserRole
import kotlinx.coroutines.flow.Flow

// A person who signs in (only used when Settings.accountsEnabled is on). Never deleted: a user who
// leaves is deactivated, so old sales keep pointing at a real row. The PIN is stored as a salted hash
// (see domain/UserRules.kt), never as digits.
@Entity(tableName = "app_user")
data class AppUser(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: UserRole,
    val pinHash: String,
    val pinSalt: String,
    val active: Boolean = true,
    val createdAt: Long                     // epoch millis
)

/** Who did something, as saved on a sale: the id for linking and the name as it was at that moment. */
data class Actor(val id: Long, val name: String)

fun AppUser.toActor() = Actor(id, name)

@Dao
interface AppUserDao {
    // Everyone, active first (the Users screen), then by name.
    @Query("SELECT * FROM app_user ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<AppUser>>

    // Only those who may sign in (the lock screen).
    @Query("SELECT * FROM app_user WHERE active = 1 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<AppUser>>

    @Query("SELECT * FROM app_user WHERE id = :id")
    suspend fun getById(id: Long): AppUser?

    @Query("SELECT COUNT(*) FROM app_user")
    suspend fun count(): Int

    @Insert
    suspend fun insert(user: AppUser): Long

    @Update
    suspend fun update(user: AppUser)
}
