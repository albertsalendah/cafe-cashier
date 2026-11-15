package com.albertsalendah.cafecashier.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "category")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val archived: Boolean = false
)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<Category>>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Insert
    suspend fun insert(category: Category): Long

    // Archiving = update with archived = true. Categories are never deleted.
    @Update
    suspend fun update(category: Category)
}