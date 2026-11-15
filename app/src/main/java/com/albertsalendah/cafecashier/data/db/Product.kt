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

@Entity(
    tableName = "product",
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
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val price: Long,                       // whole rupiah
    val imagePath: String? = null,         // file path in app storage
    val discountPercent: Int = 0,          // whole percent, 0 = no discount
    val discountStartDay: Long? = null,    // epoch day, inclusive
    val discountEndDay: Long? = null,      // epoch day, inclusive
    val archived: Boolean = false
)

@Dao
interface ProductDao {
    @Query("SELECT * FROM product WHERE archived = 0 AND categoryId = :categoryId ORDER BY name")
    fun observeActiveByCategory(categoryId: Long): Flow<List<Product>>

    @Query("SELECT * FROM product WHERE id = :id")
    suspend fun getById(id: Long): Product?

    @Insert
    suspend fun insert(product: Product): Long

    // Archiving = update with archived = true. Products are never deleted.
    @Update
    suspend fun update(product: Product)
}