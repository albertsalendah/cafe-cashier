package com.albertsalendah.cafecashier.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "add_on_option",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("categoryId")]
)
data class AddOnOption(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String
)

@Dao
interface AddOnOptionDao {
    @Query("SELECT * FROM add_on_option WHERE categoryId = :categoryId ORDER BY name")
    fun observeByCategory(categoryId: Long): Flow<List<AddOnOption>>

    @Insert
    suspend fun insert(option: AddOnOption): Long

    @Update
    suspend fun update(option: AddOnOption)

    // Safe to hard-delete: sale lines store the chosen add-ons as text.
    @Delete
    suspend fun delete(option: AddOnOption)
}