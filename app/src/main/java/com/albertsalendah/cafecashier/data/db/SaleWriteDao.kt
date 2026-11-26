package com.albertsalendah.cafecashier.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

// Writes a sale together with its lines, all or nothing. An abstract class (not an interface like
// SaleDao) so that saveSale and cancelOpenSale can be @Transaction methods with a body, the same
// pattern as ExpenseCategoryDao. Adding this DAO does not change the database schema.
@Dao
abstract class SaleWriteDao {

    @Query("SELECT * FROM sale WHERE id = :id")
    abstract suspend fun getSale(id: Long): Sale?

    @Query("SELECT * FROM sale_item WHERE saleId = :saleId ORDER BY id")
    abstract suspend fun getItems(saleId: Long): List<SaleItem>

    @Insert
    abstract suspend fun insertSale(sale: Sale): Long

    @Update
    abstract suspend fun updateSale(sale: Sale)

    @Insert
    abstract suspend fun insertItems(items: List<SaleItem>)

    @Update
    abstract suspend fun updateItems(items: List<SaleItem>)

    // Saves a bill or a payment. [sale].id == 0 inserts a new sale with its lines; otherwise the
    // existing sale and its lines are updated (lines with id 0 are new ones), but only while that
    // sale is still OPEN. Returns the sale id, or null (nothing changed) if the sale is no longer
    // open, for example already paid or cancelled.
    @Transaction
    open suspend fun saveSale(sale: Sale, items: List<SaleItem>): Long? {
        if (sale.id == 0L) {
            val id = insertSale(sale)
            insertItems(items.map { it.copy(id = 0L, saleId = id) })
            return id
        }
        val existing = getSale(sale.id) ?: return null
        if (existing.status != SaleStatus.OPEN) return null
        updateSale(sale)
        val (saved, added) = items.partition { it.id != 0L }
        updateItems(saved.map { it.copy(saleId = sale.id) })
        insertItems(added.map { it.copy(saleId = sale.id) })
        return sale.id
    }

    // Cancels an open bill: status CANCELLED with the reason and time (and who did it, if known).
    // Its lines and amounts stay as they were last saved. Returns false (nothing changed) if the
    // sale is not open.
    @Transaction
    open suspend fun cancelOpenSale(saleId: Long, reason: String, now: Long, by: Actor? = null): Boolean {
        val existing = getSale(saleId) ?: return false
        if (existing.status != SaleStatus.OPEN) return false
        updateSale(
            existing.copy(
                status = SaleStatus.CANCELLED,
                cancelledAt = now,
                cancelReason = reason,
                cancelledById = by?.id,
                cancelledByName = by?.name
            )
        )
        return true
    }

    // Voids a PAID sale: status CANCELLED with the reason and time. Its paid time, payment details,
    // lines and amounts stay as they were, so the history still shows what was voided and when it was
    // paid. Every sum only counts PAID sales, so it drops out of the cash and the reports by itself.
    // Returns false (nothing changed) if the sale is not PAID, so a second tap can't void it twice.
    @Transaction
    open suspend fun voidPaidSale(saleId: Long, reason: String, now: Long, by: Actor? = null): Boolean {
        val existing = getSale(saleId) ?: return false
        if (existing.status != SaleStatus.PAID) return false
        updateSale(
            existing.copy(
                status = SaleStatus.CANCELLED,
                cancelledAt = now,
                cancelReason = reason,
                cancelledById = by?.id,
                cancelledByName = by?.name
            )
        )
        return true
    }
}
