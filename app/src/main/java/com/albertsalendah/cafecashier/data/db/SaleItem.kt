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

enum class SaleItemStatus { ACTIVE, CANCELLED }

@Entity(
    tableName = "sale_item",
    foreignKeys = [
        ForeignKey(
            entity = Sale::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("saleId"), Index("productId")]
)
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    // Snapshot of the product at sale time, so later menu edits never change old sales.
    val productName: String,
    val unitPrice: Long,
    val discountPercent: Int = 0,           // the percent that applied at sale time (0 if outside the date window)
    val quantity: Int,
    val addOns: String = "",                // chosen add-on names as text
    val note: String? = null,
    val status: SaleItemStatus = SaleItemStatus.ACTIVE
)

// No delete: a cancelled line stays visible (struck through) and is not counted.
@Dao
interface SaleItemDao {
    @Query("SELECT * FROM sale_item WHERE saleId = :saleId ORDER BY id")
    fun observeBySale(saleId: Long): Flow<List<SaleItem>>

    @Query("SELECT * FROM sale_item WHERE saleId = :saleId ORDER BY id")
    suspend fun getBySale(saleId: Long): List<SaleItem>

    @Insert
    suspend fun insert(item: SaleItem): Long

    @Insert
    suspend fun insertAll(items: List<SaleItem>)

    @Update
    suspend fun update(item: SaleItem)
}
