package com.albertsalendah.cafecashier.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class DiscountType { PERCENT, AMOUNT }

@Entity(tableName = "order_discount")
data class OrderDiscount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: DiscountType,
    val value: Long                        // whole percent if PERCENT, rupiah if AMOUNT
)

@Dao
interface OrderDiscountDao {
    @Query("SELECT * FROM order_discount ORDER BY name")
    fun observeAll(): Flow<List<OrderDiscount>>

    @Insert
    suspend fun insert(discount: OrderDiscount): Long

    @Update
    suspend fun update(discount: OrderDiscount)

    // Safe to hard-delete: sales snapshot the type and value they used.
    @Delete
    suspend fun delete(discount: OrderDiscount)
}