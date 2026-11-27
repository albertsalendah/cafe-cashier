package com.albertsalendah.cafecashier.domain

// Pure rules for "who is using the app right now" (no Android classes, so they are unit-tested on the JVM).

/** What the app should show, given the settings, the users and who (if anyone) is signed in. */
sealed interface Access {
    /** Staff accounts are off: no sign-in, and everyone may do everything, as before. */
    data object Open : Access

    /** Staff accounts are on and nobody valid is signed in: show the lock screen. */
    data object Locked : Access

    data class SignedIn(val user: UserSummary) : Access
}

/**
 * [signedInId] is only an id; the user is looked up in [users] every time, so a user who was
 * deactivated (or removed) while signed in is locked out at once, and a changed role or name applies
 * at once. [setupHold] is true for the moment between creating the first Admin and the Admin having
 * saved the recovery code: the app stays open so the one-time code dialog is not torn down.
 */
fun resolveAccess(
    accountsEnabled: Boolean,
    users: List<UserSummary>,
    signedInId: Long?,
    setupHold: Boolean
): Access {
    if (!accountsEnabled || setupHold) return Access.Open
    val id = signedInId ?: return Access.Locked
    val user = users.firstOrNull { it.id == id && it.active } ?: return Access.Locked
    return Access.SignedIn(user)
}

/** The role the role limits go by: everything while accounts are off, null while locked. */
fun Access.effectiveRole(): UserRole? =
    when (this) {
        Access.Open -> UserRole.ADMIN
        Access.Locked -> null
        is Access.SignedIn -> user.role
    }

/**
 * Whether the order in progress must be thrown away when [newUserId] signs in. The person who locked
 * the app ([lastUserId]) gets their cart back; a different person starts with an empty one, so an
 * order never leaks from one user to the next. Null [lastUserId] = nobody locked it (first sign-in).
 */
fun shouldClearDraft(lastUserId: Long?, newUserId: Long): Boolean =
    lastUserId != null && lastUserId != newUserId
