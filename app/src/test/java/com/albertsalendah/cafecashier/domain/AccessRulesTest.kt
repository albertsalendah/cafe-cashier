package com.albertsalendah.cafecashier.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessRulesTest {

    private val owner = UserSummary(1, "Richard", UserRole.ADMIN, active = true)
    private val anna = UserSummary(2, "Anna", UserRole.STAFF, active = true)
    private val gone = UserSummary(3, "Budi", UserRole.STAFF, active = false)
    private val users = listOf(owner, anna, gone)

    @Test
    fun accountsOffMeansOpenEvenWithUsersAndASession() {
        assertEquals(Access.Open, resolveAccess(false, users, signedInId = null, setupHold = false))
        assertEquals(Access.Open, resolveAccess(false, users, signedInId = 2, setupHold = false))
    }

    @Test
    fun accountsOnWithoutASessionIsLocked() {
        assertEquals(Access.Locked, resolveAccess(true, users, signedInId = null, setupHold = false))
    }

    @Test
    fun aSignedInActiveUserGetsTheirOwnSummary() {
        assertEquals(Access.SignedIn(anna), resolveAccess(true, users, 2, false))
        assertEquals(Access.SignedIn(owner), resolveAccess(true, users, 1, false))
    }

    @Test
    fun aDeactivatedOrUnknownUserIsLockedOut() {
        assertEquals(Access.Locked, resolveAccess(true, users, 3, false))
        assertEquals(Access.Locked, resolveAccess(true, users, 99, false))
        assertEquals(Access.Locked, resolveAccess(true, emptyList(), 1, false))
    }

    @Test
    fun aRoleChangeAppliesAtOnce() {
        val demoted = owner.copy(role = UserRole.STAFF)
        assertEquals(UserRole.STAFF, (resolveAccess(true, listOf(demoted, anna), 1, false) as Access.SignedIn).user.role)
    }

    @Test
    fun theSetupHoldKeepsTheAppOpenUntilReleased() {
        assertEquals(Access.Open, resolveAccess(true, listOf(owner), signedInId = null, setupHold = true))
        assertEquals(Access.Open, resolveAccess(true, listOf(owner), signedInId = 1, setupHold = true))
        assertEquals(Access.SignedIn(owner), resolveAccess(true, listOf(owner), signedInId = 1, setupHold = false))
        assertEquals(Access.Locked, resolveAccess(true, listOf(owner), signedInId = null, setupHold = false))
    }

    @Test
    fun effectiveRoleIsAdminWhenOpenAndNullWhenLocked() {
        assertEquals(UserRole.ADMIN, Access.Open.effectiveRole())
        assertNull(Access.Locked.effectiveRole())
        assertEquals(UserRole.STAFF, Access.SignedIn(anna).effectiveRole())
        assertEquals(UserRole.ADMIN, Access.SignedIn(owner).effectiveRole())
    }

    @Test
    fun staffNeverGetTheirOwnTabsAbove_OrderAndBills() {
        // The tabs are filtered with canAccess; spot-check the areas the Home tabs and screens use.
        assertTrue(canAccess(UserRole.STAFF, AppArea.ORDER))
        assertTrue(canAccess(UserRole.STAFF, AppArea.BILLS))
        assertTrue(canAccess(UserRole.STAFF, AppArea.CANCEL_BILL))
        listOf(AppArea.CASH, AppArea.SALES, AppArea.MENU, AppArea.USERS, AppArea.REPORTS, AppArea.SETTINGS, AppArea.BACKUP, AppArea.VOID_SALE)
            .forEach { assertFalse("$it", canAccess(UserRole.STAFF, it)) }
        AppArea.entries.forEach { assertTrue("$it", canAccess(UserRole.ADMIN, it)) }
    }

    @Test
    fun theSameCartIsKeptOnlyForThePersonWhoLocked() {
        assertFalse(shouldClearDraft(lastUserId = null, newUserId = 2))   // first sign-in
        assertFalse(shouldClearDraft(lastUserId = 2, newUserId = 2))      // same person back
        assertTrue(shouldClearDraft(lastUserId = 2, newUserId = 1))       // someone else
    }
}
