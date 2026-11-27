package com.albertsalendah.cafecashier.ui.session

import com.albertsalendah.cafecashier.data.db.AppUserDao
import com.albertsalendah.cafecashier.data.db.SettingsDao
import com.albertsalendah.cafecashier.domain.Access
import com.albertsalendah.cafecashier.domain.UserSummary
import com.albertsalendah.cafecashier.domain.resolveAccess
import com.albertsalendah.cafecashier.domain.shouldClearDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

// Who is signed in. One instance for the whole app (CafeCashierApp.session). It keeps only the user's
// id, in memory: killing or restarting the app locks it again, on purpose (a shared café phone).
// What the app shows is worked out by [access] from the settings, the users and that id, so a user who
// is deactivated or whose role changes is affected at once.
//
// [onUserChanged] throws away the order in progress (the app passes orderDraftHolder::clear).
class SessionHolder(
    settingsDao: SettingsDao,
    appUserDao: AppUserDao,
    private val onUserChanged: () -> Unit
) {
    private val signedInId = MutableStateFlow<Long?>(null)
    private val setupHold = MutableStateFlow(false)

    // Who locked the app last. The same person signing back in keeps their cart; anyone else starts
    // with an empty one (see shouldClearDraft).
    private var lastUserId: Long? = null

    val access: Flow<Access> = combine(
        settingsDao.observe(),
        appUserDao.observeAll(),
        signedInId,
        setupHold
    ) { settings, users, id, hold ->
        resolveAccess(
            accountsEnabled = settings?.accountsEnabled ?: false,
            users = users.map { UserSummary(it.id, it.name, it.role, it.active) },
            signedInId = id,
            setupHold = hold
        )
    }.distinctUntilChanged()

    /** Call only after the PIN was checked (or the recovery code and a new PIN were accepted). */
    fun signIn(userId: Long) {
        if (shouldClearDraft(lastUserId, userId)) onUserChanged()
        lastUserId = null
        signedInId.value = userId
    }

    /** The Lock button. Also called by the lock screen itself, so a stale id can never sign anyone in again. */
    fun signOut() {
        signedInId.value?.let { lastUserId = it }
        signedInId.value = null
    }

    /** Accounts were switched off or on again: forget who was signed in and who locked last. */
    fun reset() {
        lastUserId = null
        signedInId.value = null
    }

    /**
     * Keeps the app open while the first Admin has been created but has not yet saved the recovery
     * code (otherwise the lock screen would appear at once and the one-time code would be lost).
     */
    fun holdForSetup(on: Boolean) {
        setupHold.value = on
    }
}
