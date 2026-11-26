package com.albertsalendah.cafecashier.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Random
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// Pure rules for staff accounts (no Android classes, so they are unit-tested on the JVM):
// who may open what, PIN format and hashing, the recovery code, wrong-PIN lockout, and the
// rules for editing the list of users.
//
// Honest limit: a 4 to 6 digit PIN can always be guessed offline from a copied database file,
// whatever the hashing. The lock keeps casual users out; it is not a vault.

enum class UserRole { ADMIN, STAFF }

/** Things a user may be allowed to do or open. */
enum class AppArea {
    ORDER,
    BILLS,
    CANCEL_BILL,
    CASH,
    SALES,
    VOID_SALE,
    REPORTS,
    MENU,
    SETTINGS,
    BACKUP,
    USERS
}

private val STAFF_AREAS = setOf(AppArea.ORDER, AppArea.BILLS, AppArea.CANCEL_BILL)

/** Admin may do everything; staff may take orders, work with bills and cancel an open bill. */
fun canAccess(role: UserRole, area: AppArea): Boolean =
    when (role) {
        UserRole.ADMIN -> true
        UserRole.STAFF -> area in STAFF_AREAS
    }

// ---- PIN ----

const val PIN_MIN_LENGTH = 4
const val PIN_MAX_LENGTH = 6

/** 4 to 6 characters, ASCII digits only. */
fun isValidPin(pin: String): Boolean =
    pin.length in PIN_MIN_LENGTH..PIN_MAX_LENGTH && pin.all { it in '0'..'9' }

// ---- hashing (PIN and recovery code) ----

private const val HASH_PREFIX = "pbkdf2-sha256"
const val HASH_ITERATIONS = 100_000
private const val MAX_STORED_ITERATIONS = 2_000_000
private const val SALT_BYTES = 16

/** A fresh random salt (32 hex characters). Store it next to the hash. */
fun generateSalt(random: Random = SecureRandom()): String {
    val bytes = ByteArray(SALT_BYTES)
    random.nextBytes(bytes)
    return toHex(bytes)
}

/**
 * Salted PBKDF2 hash of a PIN or recovery code, as `pbkdf2-sha256:<iterations>:<hex>`. The iteration
 * count is part of the text, so it can be raised later and old hashes still verify.
 */
fun hashSecret(secret: String, salt: String, iterations: Int = HASH_ITERATIONS): String =
    "$HASH_PREFIX:$iterations:${toHex(pbkdf2(secret, salt, iterations))}"

/** True if [secret] with [salt] gives [storedHash]. Compares in constant time; garbage input gives false. */
fun verifySecret(secret: String, salt: String, storedHash: String): Boolean {
    val parts = storedHash.split(":")
    if (parts.size != 3 || parts[0] != HASH_PREFIX) return false
    val iterations = parts[1].toIntOrNull() ?: return false
    if (iterations !in 1..MAX_STORED_ITERATIONS) return false
    val expected = parts[2].toByteArrayOrNull() ?: return false
    return MessageDigest.isEqual(pbkdf2(secret, salt, iterations), expected)
}

private fun pbkdf2(secret: String, salt: String, iterations: Int): ByteArray {
    val spec = PBEKeySpec(secret.toCharArray(), salt.toByteArray(Charsets.UTF_8), iterations, 256)
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
}

private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

private fun String.toByteArrayOrNull(): ByteArray? {
    if (isEmpty() || length % 2 != 0) return null
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        val hi = Character.digit(this[2 * i], 16)
        val lo = Character.digit(this[2 * i + 1], 16)
        if (hi < 0 || lo < 0) return null
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

// ---- recovery code ----

// No I, L, O, 0 or 1: easy to misread when written down.
private const val RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
const val RECOVERY_CODE_LENGTH = 10

/** A new recovery code: 10 characters from a 31-letter-and-digit alphabet (about 49 bits). */
fun generateRecoveryCode(random: Random = SecureRandom()): String =
    buildString {
        repeat(RECOVERY_CODE_LENGTH) { append(RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)]) }
    }

/** `ABCDE-FGHJK`, how the code is shown to the owner. */
fun formatRecoveryCode(code: String): String = code.chunked(5).joinToString("-")

/** What the owner typed, made comparable: upper case, without spaces or dashes. */
fun normalizeRecoveryCode(input: String): String =
    input.filter { !it.isWhitespace() && it != '-' }.uppercase()

// ---- wrong-PIN lockout ----

private const val FREE_ATTEMPTS = 5

/** Seconds to wait after [failedAttempts] wrong tries in a row: none for 4, then 30 s, 1 min, 5 min, 15 min. */
fun lockoutSeconds(failedAttempts: Int): Long =
    when {
        failedAttempts < FREE_ATTEMPTS -> 0L
        failedAttempts == FREE_ATTEMPTS -> 30L
        failedAttempts == FREE_ATTEMPTS + 1 -> 60L
        failedAttempts == FREE_ATTEMPTS + 2 -> 300L
        else -> 900L
    }

/** Milliseconds still to wait now, given the number of wrong tries and when the last one happened. */
fun remainingLockoutMillis(failedAttempts: Int, lastFailureAtMillis: Long, nowMillis: Long): Long {
    val until = lastFailureAtMillis + lockoutSeconds(failedAttempts) * 1000L
    return (until - nowMillis).coerceAtLeast(0L)
}

// ---- the list of users ----

data class UserSummary(
    val id: Long,
    val name: String,
    val role: UserRole,
    val active: Boolean
)

const val MAX_USER_NAME_LENGTH = 30

/** The name trimmed with inner spaces collapsed, or null if it is blank or too long. */
fun cleanUserName(raw: String): String? {
    val name = raw.trim().replace(Regex("\\s+"), " ")
    if (name.isEmpty() || name.length > MAX_USER_NAME_LENGTH) return null
    return name
}

/** Whether another ACTIVE user already has this name (ignoring case). [exceptId] is the user being edited. */
fun isUserNameTaken(name: String, users: List<UserSummary>, exceptId: Long? = null): Boolean =
    users.any { it.active && it.id != exceptId && it.name.equals(name, ignoreCase = true) }

private fun isLastActiveAdmin(users: List<UserSummary>, userId: Long): Boolean {
    val target = users.firstOrNull { it.id == userId } ?: return false
    if (!target.active || target.role != UserRole.ADMIN) return false
    return users.none { it.id != userId && it.active && it.role == UserRole.ADMIN }
}

/** A user may be deactivated unless they are the last active Admin. */
fun canDeactivate(users: List<UserSummary>, userId: Long): Boolean =
    users.any { it.id == userId } && !isLastActiveAdmin(users, userId)

/** A user may be given [newRole] unless that would leave no active Admin. */
fun canChangeRole(users: List<UserSummary>, userId: Long, newRole: UserRole): Boolean =
    users.any { it.id == userId } && (newRole == UserRole.ADMIN || !isLastActiveAdmin(users, userId))
