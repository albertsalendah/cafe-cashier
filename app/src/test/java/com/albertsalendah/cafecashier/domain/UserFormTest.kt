package com.albertsalendah.cafecashier.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserFormTest {

    private val owner = UserSummary(1, "Richard", UserRole.ADMIN, true)
    private val sari = UserSummary(2, "Sari", UserRole.STAFF, true)
    private val gone = UserSummary(3, "Budi", UserRole.STAFF, false)
    private val users = listOf(owner, sari, gone)

    @Test
    fun aGoodNewUserPasses() {
        assertNull(checkUserForm("Dewi", UserRole.STAFF, "4821", "4821", users, null))
    }

    @Test
    fun theFirstAdminPassesWithNoOtherUsers() {
        assertNull(checkUserForm("Richard", UserRole.ADMIN, "123456", "123456", emptyList(), null))
    }

    @Test
    fun aBlankOrTooLongNameIsRefused() {
        assertEquals(UserFormError.NAME_INVALID, checkUserForm("  ", UserRole.STAFF, "4821", "4821", users, null))
        assertEquals(UserFormError.NAME_INVALID, checkUserForm("x".repeat(31), UserRole.STAFF, "4821", "4821", users, null))
    }

    @Test
    fun aNameUsedByAnotherActiveUserIsRefusedIgnoringCase() {
        assertEquals(UserFormError.NAME_TAKEN, checkUserForm(" sari ", UserRole.STAFF, "4821", "4821", users, null))
    }

    @Test
    fun aDeactivatedUsersNameMayBeUsedAgain() {
        assertNull(checkUserForm("Budi", UserRole.STAFF, "4821", "4821", users, null))
    }

    @Test
    fun aBadPinIsRefused() {
        assertEquals(UserFormError.PIN_INVALID, checkUserForm("Dewi", UserRole.STAFF, "12", "12", users, null))
        assertEquals(UserFormError.PIN_INVALID, checkUserForm("Dewi", UserRole.STAFF, "", "", users, null))
        assertEquals(UserFormError.PIN_INVALID, checkUserForm("Dewi", UserRole.STAFF, "12ab", "12ab", users, null))
    }

    @Test
    fun twoDifferentPinsAreRefused() {
        assertEquals(UserFormError.PIN_MISMATCH, checkUserForm("Dewi", UserRole.STAFF, "4821", "4822", users, null))
        assertEquals(UserFormError.PIN_MISMATCH, checkUserForm("Dewi", UserRole.STAFF, "4821", null, users, null))
    }

    @Test
    fun editingWithoutANewPinNeedsNoPin() {
        assertNull(checkUserForm("Sari Dewi", UserRole.STAFF, null, null, users, 2))
    }

    @Test
    fun editingKeepsTheUsersOwnNameFree() {
        assertNull(checkUserForm("Sari", UserRole.STAFF, null, null, users, 2))
        assertEquals(UserFormError.NAME_TAKEN, checkUserForm("Sari", UserRole.STAFF, null, null, users, 1))
    }

    @Test
    fun theOnlyAdminCannotBeMadeStaff() {
        assertEquals(UserFormError.LAST_ADMIN, checkUserForm("Richard", UserRole.STAFF, null, null, users, 1))
        assertNull(checkUserForm("Richard", UserRole.ADMIN, null, null, users, 1))
    }

    @Test
    fun staffCanBePromoted() {
        assertNull(checkUserForm("Sari", UserRole.ADMIN, null, null, users, 2))
    }

    @Test
    fun theNameIsCheckedBeforeThePin() {
        assertEquals(UserFormError.NAME_INVALID, checkUserForm("", UserRole.STAFF, "1", "2", users, null))
    }
}
