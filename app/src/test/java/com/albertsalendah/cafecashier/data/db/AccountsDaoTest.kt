package com.albertsalendah.cafecashier.data.db

import com.albertsalendah.cafecashier.domain.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsDaoTest {

    private class FakeDao(var settings: Settings? = Settings()) : AccountsDao() {
        val users = mutableListOf<AppUser>()
        override suspend fun getSettings(): Settings? = settings
        override suspend fun updateSettings(settings: Settings) { this.settings = settings }
        override suspend fun insertUser(user: AppUser): Long {
            val id = users.size + 1L
            users += user.copy(id = id)
            return id
        }
        override suspend fun userCount(): Int = users.size
    }

    private fun admin(role: UserRole = UserRole.ADMIN) =
        AppUser(id = 77, name = "Richard", role = role, pinHash = "h", pinSalt = "s", active = false, createdAt = 5)

    @Test
    fun settingUpCreatesTheAdminAndSwitchesAccountsOn() = runBlocking {
        val dao = FakeDao()
        assertTrue(dao.setUpAccounts(admin(), "rh", "rs"))
        val user = dao.users.single()
        assertEquals("Richard", user.name)
        assertEquals(1L, user.id)                 // the id the caller gave is ignored
        assertEquals(UserRole.ADMIN, user.role)
        assertTrue(user.active)
        val s = dao.settings!!
        assertTrue(s.accountsEnabled)
        assertEquals("rh", s.recoveryHash)
        assertEquals("rs", s.recoverySalt)
    }

    @Test
    fun theFirstUserIsAlwaysAnActiveAdmin() = runBlocking {
        val dao = FakeDao()
        assertTrue(dao.setUpAccounts(admin(role = UserRole.STAFF), "rh", "rs"))
        assertEquals(UserRole.ADMIN, dao.users.single().role)
    }

    @Test
    fun settingUpAgainChangesNothing() = runBlocking {
        val dao = FakeDao()
        assertTrue(dao.setUpAccounts(admin(), "rh", "rs"))
        assertFalse(dao.setUpAccounts(admin(), "other", "other"))
        assertEquals(1, dao.users.size)
        assertEquals("rh", dao.settings!!.recoveryHash)
    }

    @Test
    fun withoutASettingsRowNothingIsCreated() = runBlocking {
        val dao = FakeDao(settings = null)
        assertFalse(dao.setUpAccounts(admin(), "rh", "rs"))
        assertTrue(dao.users.isEmpty())
        assertNull(dao.settings)
    }

    @Test
    fun otherSettingsAreKept() = runBlocking {
        val dao = FakeDao(Settings(storeName = "Cafe", taxPercent = 10))
        dao.setUpAccounts(admin(), "rh", "rs")
        assertEquals("Cafe", dao.settings!!.storeName)
        assertEquals(10, dao.settings!!.taxPercent)
    }
}
