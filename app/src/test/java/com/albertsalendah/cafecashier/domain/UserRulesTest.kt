package com.albertsalendah.cafecashier.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class UserRulesTest {

    // ---- permissions ----

    @Test
    fun anAdminMayDoEverything() {
        AppArea.values().forEach { assertTrue(it.name, canAccess(UserRole.ADMIN, it)) }
    }

    @Test
    fun staffMayOnlyTakeOrdersWorkWithBillsAndCancelAnOpenBill() {
        val allowed = AppArea.values().filter { canAccess(UserRole.STAFF, it) }.toSet()
        assertEquals(setOf(AppArea.ORDER, AppArea.BILLS, AppArea.CANCEL_BILL), allowed)
    }

    @Test
    fun staffMayNotVoidViewReportsOrManage() {
        listOf(AppArea.VOID_SALE, AppArea.SALES, AppArea.REPORTS, AppArea.CASH, AppArea.MENU,
            AppArea.SETTINGS, AppArea.BACKUP, AppArea.USERS)
            .forEach { assertFalse(it.name, canAccess(UserRole.STAFF, it)) }
    }

    // ---- PIN format ----

    @Test
    fun aPinIsFourToSixDigits() {
        assertTrue(isValidPin("1234"))
        assertTrue(isValidPin("123456"))
        assertTrue(isValidPin("0000"))
        assertFalse(isValidPin("123"))
        assertFalse(isValidPin("1234567"))
        assertFalse(isValidPin(""))
    }

    @Test
    fun aPinWithAnythingButAsciiDigitsIsRefused() {
        assertFalse(isValidPin("12a4"))
        assertFalse(isValidPin(" 1234"))
        assertFalse(isValidPin("12 34"))
        assertFalse(isValidPin("١٢٣٤")) // Arabic-Indic digits
    }

    // ---- hashing ----

    @Test
    fun theRightSecretVerifiesAndAWrongOneDoesNot() {
        val salt = generateSalt()
        val hash = hashSecret("4821", salt)
        assertTrue(verifySecret("4821", salt, hash))
        assertFalse(verifySecret("4822", salt, hash))
        assertFalse(verifySecret("4821", generateSalt(), hash))
    }

    @Test
    fun theHashIsRepeatableForTheSameSaltAndDiffersWithAnotherSalt() {
        assertEquals(hashSecret("4821", "aa"), hashSecret("4821", "aa"))
        assertNotEquals(hashSecret("4821", "aa"), hashSecret("4821", "bb"))
    }

    @Test
    fun theHashTextNamesTheMethodAndTheIterations() {
        assertTrue(hashSecret("1234", "s").startsWith("pbkdf2-sha256:$HASH_ITERATIONS:"))
    }

    @Test
    fun aHashMadeWithAnotherIterationCountStillVerifies() {
        val old = hashSecret("1234", "s", iterations = 1000)
        assertTrue(old.startsWith("pbkdf2-sha256:1000:"))
        assertTrue(verifySecret("1234", "s", old))
    }

    @Test
    fun brokenHashTextNeverVerifies() {
        listOf("", "garbage", "pbkdf2-sha256:1000", "pbkdf2-sha256:x:ab", "pbkdf2-sha256:1000:zz",
            "pbkdf2-sha256:0:ab", "pbkdf2-sha256:999999999:ab", "md5:1000:ab", "pbkdf2-sha256:1000:")
            .forEach { assertFalse(it, verifySecret("1234", "s", it)) }
    }

    @Test
    fun aSaltIs32HexCharactersAndDiffersEachTime() {
        val a = generateSalt()
        assertEquals(32, a.length)
        assertTrue(a.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(a, generateSalt())
    }

    // ---- recovery code ----

    @Test
    fun aRecoveryCodeHasTenEasyToReadCharacters() {
        val random = Random(7)
        repeat(200) {
            val code = generateRecoveryCode(random)
            assertEquals(10, code.length)
            assertTrue(code, code.all { it in "ABCDEFGHJKMNPQRSTUVWXYZ23456789" })
        }
    }

    @Test
    fun recoveryCodesDiffer() {
        val random = Random(1)
        val codes = (1..200).map { generateRecoveryCode(random) }.toSet()
        assertTrue(codes.size > 195)
    }

    @Test
    fun aRecoveryCodeIsShownInTwoGroups() {
        assertEquals("ABCDE-FGHJK", formatRecoveryCode("ABCDEFGHJK"))
    }

    @Test
    fun whatTheOwnerTypesIsComparedWithoutCaseSpacesOrDashes() {
        assertEquals("ABCDEFGHJK", normalizeRecoveryCode(" abcde-fghjk "))
        assertEquals("ABCDEFGHJK", normalizeRecoveryCode("ABCDE FGHJK"))
    }

    @Test
    fun theCodeShownAndTheCodeTypedGiveTheSameHash() {
        val code = generateRecoveryCode()
        val salt = generateSalt()
        val hash = hashSecret(code, salt)
        val typed = formatRecoveryCode(code).lowercase()
        assertTrue(verifySecret(normalizeRecoveryCode(typed), salt, hash))
    }

    // ---- lockout ----

    @Test
    fun noWaitForTheFirstFourWrongTries() {
        (0..4).forEach { assertEquals(0L, lockoutSeconds(it)) }
    }

    @Test
    fun theWaitGrowsAndStopsAtFifteenMinutes() {
        assertEquals(30L, lockoutSeconds(5))
        assertEquals(60L, lockoutSeconds(6))
        assertEquals(300L, lockoutSeconds(7))
        assertEquals(900L, lockoutSeconds(8))
        assertEquals(900L, lockoutSeconds(50))
    }

    @Test
    fun theRemainingWaitCountsDownToZero() {
        val failedAt = 1_000_000L
        assertEquals(30_000L, remainingLockoutMillis(5, failedAt, failedAt))
        assertEquals(20_000L, remainingLockoutMillis(5, failedAt, failedAt + 10_000))
        assertEquals(0L, remainingLockoutMillis(5, failedAt, failedAt + 30_000))
        assertEquals(0L, remainingLockoutMillis(5, failedAt, failedAt + 99_000))
        assertEquals(0L, remainingLockoutMillis(2, failedAt, failedAt))
    }

    // ---- names ----

    @Test
    fun aNameIsTrimmedAndItsSpacesCollapsed() {
        assertEquals("Sari Dewi", cleanUserName("  Sari   Dewi "))
    }

    @Test
    fun aBlankOrTooLongNameIsRefused() {
        assertNull(cleanUserName(""))
        assertNull(cleanUserName("   "))
        assertNull(cleanUserName("x".repeat(MAX_USER_NAME_LENGTH + 1)))
        assertEquals("x".repeat(MAX_USER_NAME_LENGTH), cleanUserName("x".repeat(MAX_USER_NAME_LENGTH)))
    }

    private val owner = UserSummary(1, "Richard", UserRole.ADMIN, true)
    private val sari = UserSummary(2, "Sari", UserRole.STAFF, true)
    private val oldBudi = UserSummary(3, "Budi", UserRole.STAFF, false)

    @Test
    fun aNameIsTakenByAnotherActiveUserIgnoringCase() {
        val users = listOf(owner, sari, oldBudi)
        assertTrue(isUserNameTaken("sari", users))
        assertFalse(isUserNameTaken("Dewi", users))
    }

    @Test
    fun aDeactivatedUsersNameCanBeUsedAgain() {
        assertFalse(isUserNameTaken("Budi", listOf(owner, sari, oldBudi)))
    }

    @Test
    fun editingAUserDoesNotClashWithTheirOwnName() {
        assertFalse(isUserNameTaken("Sari", listOf(owner, sari), exceptId = 2))
        assertTrue(isUserNameTaken("Sari", listOf(owner, sari), exceptId = 1))
    }

    // ---- last admin ----

    @Test
    fun theOnlyActiveAdminCannotBeDeactivatedOrDemoted() {
        val users = listOf(owner, sari)
        assertFalse(canDeactivate(users, 1))
        assertFalse(canChangeRole(users, 1, UserRole.STAFF))
        assertTrue(canChangeRole(users, 1, UserRole.ADMIN))
    }

    @Test
    fun withASecondActiveAdminEitherCanGo() {
        val second = UserSummary(4, "Dewi", UserRole.ADMIN, true)
        val users = listOf(owner, sari, second)
        assertTrue(canDeactivate(users, 1))
        assertTrue(canChangeRole(users, 4, UserRole.STAFF))
    }

    @Test
    fun aDeactivatedAdminDoesNotCountAsAnAdmin() {
        val gone = UserSummary(4, "Dewi", UserRole.ADMIN, false)
        assertFalse(canDeactivate(listOf(owner, gone), 1))
    }

    @Test
    fun staffCanAlwaysBeDeactivatedAndAnUnknownUserNever() {
        assertTrue(canDeactivate(listOf(owner, sari), 2))
        assertTrue(canChangeRole(listOf(owner, sari), 2, UserRole.ADMIN))
        assertFalse(canDeactivate(listOf(owner, sari), 99))
        assertFalse(canChangeRole(listOf(owner, sari), 99, UserRole.STAFF))
    }
}
