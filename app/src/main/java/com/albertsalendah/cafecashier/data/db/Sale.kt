package com.albertsalendah.cafecashier.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class SaleStatus { OPEN, PAID, CANCELLED }

enum class OrderType { DINE_IN, TAKEAWAY }

enum class PaymentMethod { CASH }

@Entity(
    tableName = "sale",
    indices = [Index("status"), Index("paidAt")]
)
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val status: SaleStatus = SaleStatus.OPEN,
    val orderType: OrderType,
    val tableNumber: String? = null,
    val customerName: String? = null,
    val createdAt: Long,                    // epoch millis
    val paidAt: Long? = null,               // epoch millis, reports group by this
    val cancelledAt: Long? = null,
    val cancelReason: String? = null,
    val paymentMethod: PaymentMethod? = null,   // null until paid
    val cashReceived: Long? = null,
    val changeAmount: Long? = null,

    // Amounts, all whole rupiah, saved separately so the total can be reconciled:
    // subtotal - productDiscountTotal - orderDiscountAmount + taxAmount + rounding = total
    val subtotal: Long = 0,                 // gross: sum of qty x unit price, before any discount
    val productDiscountTotal: Long = 0,
    val orderDiscountType: DiscountType? = null,    // as applied to this sale
    val orderDiscountValue: Long? = null,           // as applied to this sale
    val orderDiscountAmount: Long = 0,
    val taxPercent: Int = 0,                // whole percent, snapshot of the setting at sale time
    val taxAmount: Long = 0,
    val rounding: Long = 0,
    val total: Long = 0,

    // Who handled the sale (only when staff accounts are on; otherwise all null). The name is saved as
    // it was at that moment, so renaming or deactivating a user never changes history.
    val createdById: Long? = null,
    val createdByName: String? = null,
    val paidById: Long? = null,
    val paidByName: String? = null,
    val cancelledById: Long? = null,          // who cancelled the open bill, or voided the paid sale
    val cancelledByName: String? = null
)

// No delete: a wrong sale is cancelled (status CANCELLED + reason) and stays in the history.
@Dao
interface SaleDao {
    @Query("SELECT * FROM sale WHERE id = :id")
    suspend fun getById(id: Long): Sale?

    @Query("SELECT * FROM sale WHERE id = :id")
    fun observeById(id: Long): Flow<Sale?>

    @Query("SELECT * FROM sale WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: SaleStatus): Flow<List<Sale>>

    // fromMillis inclusive, toMillis exclusive. The caller builds the day boundaries in the café's time zone.
    @Query("SELECT * FROM sale WHERE status = 'PAID' AND paidAt >= :fromMillis AND paidAt < :toMillis ORDER BY paidAt")
    fun observePaidBetween(fromMillis: Long, toMillis: Long): Flow<List<Sale>>

    // COALESCE so a day with no cash sales gives 0, not NULL (the old app crashed here).
    @Query(
        "SELECT COALESCE(SUM(total), 0) FROM sale " +
            "WHERE status = 'PAID' AND paymentMethod = 'CASH' AND paidAt >= :fromMillis AND paidAt < :toMillis"
    )
    suspend fun sumCashPaidBetween(fromMillis: Long, toMillis: Long): Long

    // Sales history for one span (a day, or a report range): everything that was paid in it, whether it
    // is still PAID or was voided later (CANCELLED with a paidAt), plus bills that were cancelled
    // without ever being paid (placed by their cancel time). Newest first.
    @Query(
        "SELECT * FROM sale WHERE " +
            "(paidAt IS NOT NULL AND status IN ('PAID', 'CANCELLED') AND paidAt >= :fromMillis AND paidAt < :toMillis) " +
            "OR (status = 'CANCELLED' AND paidAt IS NULL AND cancelledAt >= :fromMillis AND cancelledAt < :toMillis) " +
            "ORDER BY COALESCE(paidAt, cancelledAt) DESC, id DESC"
    )
    fun observeHistoryBetween(fromMillis: Long, toMillis: Long): Flow<List<Sale>>

    // Sales that were paid in the span and voided afterwards. Reports list them apart from the totals.
    @Query(
        "SELECT * FROM sale WHERE status = 'CANCELLED' AND paidAt IS NOT NULL " +
            "AND paidAt >= :fromMillis AND paidAt < :toMillis ORDER BY paidAt"
    )
    fun observeVoidedBetween(fromMillis: Long, toMillis: Long): Flow<List<Sale>>

    // Same sum as above, but live: the Cash tab's closing view updates when a payment comes in.
    @Query(
        "SELECT COALESCE(SUM(total), 0) FROM sale " +
            "WHERE status = 'PAID' AND paymentMethod = 'CASH' AND paidAt >= :fromMillis AND paidAt < :toMillis"
    )
    fun observeCashPaidTotal(fromMillis: Long, toMillis: Long): Flow<Long>

    @Insert
    suspend fun insert(sale: Sale): Long

    @Update
    suspend fun update(sale: Sale)
}
