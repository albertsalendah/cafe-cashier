package com.albertsalendah.cafecashier.domain

// Checks the add-user / edit-user form (rules in UserRules.kt), so the screen only shows the result.

enum class UserFormError { NAME_INVALID, NAME_TAKEN, PIN_INVALID, PIN_MISMATCH, LAST_ADMIN }

/**
 * Null if the form may be saved, otherwise the first problem found.
 * [pin] is null when the PIN is not being set or changed (editing without "Set a new PIN").
 * [editingId] is the user being edited, or null for a new user. [users] is everyone, active or not.
 */
fun checkUserForm(
    rawName: String,
    role: UserRole,
    pin: String?,
    pinAgain: String?,
    users: List<UserSummary>,
    editingId: Long?
): UserFormError? {
    val name = cleanUserName(rawName) ?: return UserFormError.NAME_INVALID
    if (isUserNameTaken(name, users, exceptId = editingId)) return UserFormError.NAME_TAKEN
    if (pin != null) {
        if (!isValidPin(pin)) return UserFormError.PIN_INVALID
        if (pin != pinAgain) return UserFormError.PIN_MISMATCH
    }
    if (editingId != null && !canChangeRole(users, editingId, role)) return UserFormError.LAST_ADMIN
    return null
}
