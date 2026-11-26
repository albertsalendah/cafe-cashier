package com.albertsalendah.cafecashier.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.albertsalendah.cafecashier.domain.UserRole

// Turning staff accounts on for the first time touches two tables (the first Admin and the Settings
// row with the recovery code), so it is one all-or-nothing @Transaction, the same pattern as
// SaleWriteDao. No schema change.
@Dao
abstract class AccountsDao {

    @Query("SELECT * FROM settings WHERE id = 1")
    abstract suspend fun getSettings(): Settings?

    @Update
    abstract suspend fun updateSettings(settings: Settings)

    @Insert
    abstract suspend fun insertUser(user: AppUser): Long

    @Query("SELECT COUNT(*) FROM app_user")
    abstract suspend fun userCount(): Int

    // Creates the first Admin and switches accounts on, saving the recovery code's hash and salt.
    // Returns false (nothing changed) if any user exists already or the Settings row is missing.
    @Transaction
    open suspend fun setUpAccounts(admin: AppUser, recoveryHash: String, recoverySalt: String): Boolean {
        if (userCount() > 0) return false
        val settings = getSettings() ?: return false
        insertUser(admin.copy(id = 0, role = UserRole.ADMIN, active = true))
        updateSettings(
            settings.copy(accountsEnabled = true, recoveryHash = recoveryHash, recoverySalt = recoverySalt)
        )
        return true
    }
}
