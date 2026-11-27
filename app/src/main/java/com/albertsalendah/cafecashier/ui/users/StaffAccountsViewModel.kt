package com.albertsalendah.cafecashier.ui.users

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.AccountsDao
import com.albertsalendah.cafecashier.data.db.AppUser
import com.albertsalendah.cafecashier.data.db.AppUserDao
import com.albertsalendah.cafecashier.data.db.SettingsDao
import com.albertsalendah.cafecashier.domain.UserFormError
import com.albertsalendah.cafecashier.domain.UserRole
import com.albertsalendah.cafecashier.domain.UserSummary
import com.albertsalendah.cafecashier.domain.canDeactivate
import com.albertsalendah.cafecashier.domain.checkUserForm
import com.albertsalendah.cafecashier.domain.cleanUserName
import com.albertsalendah.cafecashier.domain.generateRecoveryCode
import com.albertsalendah.cafecashier.domain.generateSalt
import com.albertsalendah.cafecashier.domain.hashSecret
import com.albertsalendah.cafecashier.domain.isUserNameTaken
import com.albertsalendah.cafecashier.domain.normalizeRecoveryCode
import com.albertsalendah.cafecashier.domain.remainingLockoutMillis
import com.albertsalendah.cafecashier.domain.verifySecret
import com.albertsalendah.cafecashier.ui.session.SessionHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What an Admin PIN is asked for on this screen. */
enum class AdminAction { TURN_OFF, NEW_RECOVERY_CODE }

/** The one dialog that is open (if any). */
sealed interface AccountsDialog {
    data object Setup : AccountsDialog
    data class AdminPin(val action: AdminAction) : AccountsDialog

    /** [userId] null = a new user. */
    data class EditUser(val userId: Long?) : AccountsDialog

    /** Shown once, right after it was made. Only kept in memory. */
    data class RecoveryCode(val code: String) : AccountsDialog
}

private class Hashed(val hash: String, val salt: String)

// The Staff accounts screen: the on/off switch, the list of users, adding and editing them, and a
// new recovery code. Turning accounts ON for the first time creates the Admin and shows the recovery
// code once; turning them OFF (and a new recovery code) asks for an Admin PIN. Hashing runs off the
// main thread. Only an Admin reaches this screen once accounts are on (the Menu tab is hidden from
// staff); this class also keeps the session in step: the new Admin stays signed in after setup, and
// turning accounts off ends the session.
class StaffAccountsViewModel(
    private val settingsDao: SettingsDao,
    private val appUserDao: AppUserDao,
    private val accountsDao: AccountsDao,
    private val session: SessionHolder
) : ViewModel() {

    // Null until the first read.
    val accountsEnabled: StateFlow<Boolean?> = settingsDao.observe()
        .map { it?.accountsEnabled ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val users: StateFlow<List<AppUser>?> = appUserDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var dialog by mutableStateOf<AccountsDialog?>(null)
        private set
    var formError by mutableStateOf<UserFormError?>(null)
        private set
    var pinWrong by mutableStateOf(false)
        private set
    var pinLockedSeconds by mutableStateOf(0L)
        private set
    var busy by mutableStateOf(false)
        private set

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    // Wrong Admin PINs typed on this screen; after a few, the same waiting times as the lock screen.
    private var failedPins = 0
    private var lastPinFailureAt = 0L

    // Set from the moment the first Admin is created until the recovery code was saved: that Admin
    // is then signed in, so the lock screen does not cut in while the one-time code is on screen.
    private var pendingAdminName: String? = null

    // ---- the switch ----

    fun onSwitchToggled(on: Boolean) {
        if (busy) return
        resetDialogState()
        if (!on) {
            dialog = AccountsDialog.AdminPin(AdminAction.TURN_OFF)
        } else if (users.value.isNullOrEmpty()) {
            dialog = AccountsDialog.Setup
        } else {
            // Users exist from an earlier time: just switch back on (the lock screen appears at once).
            viewModelScope.launch {
                session.reset()
                setEnabled(true)
            }
        }
    }

    private suspend fun setEnabled(enabled: Boolean) {
        val current = settingsDao.get() ?: return
        settingsDao.upsert(current.copy(accountsEnabled = enabled))
    }

    // ---- first setup ----

    fun onSetupSubmit(name: String, pin: String, pinAgain: String) {
        if (busy || dialog != AccountsDialog.Setup) return
        val error = checkUserForm(name, UserRole.ADMIN, pin, pinAgain, emptyList(), null)
        formError = error
        if (error != null) return
        val cleanName = cleanUserName(name) ?: return
        busy = true
        session.holdForSetup(true)
        pendingAdminName = cleanName
        viewModelScope.launch {
            val code = generateRecoveryCode()
            val pinHash = hashed(pin)
            val recovery = hashed(normalizeRecoveryCode(code))
            val admin = AppUser(
                name = cleanName, role = UserRole.ADMIN,
                pinHash = pinHash.hash, pinSalt = pinHash.salt,
                createdAt = System.currentTimeMillis()
            )
            val ok = accountsDao.setUpAccounts(admin, recovery.hash, recovery.salt)
            busy = false
            if (ok) {
                dialog = AccountsDialog.RecoveryCode(code)
            } else {
                pendingAdminName = null
                session.holdForSetup(false)
                dialog = null
                _messages.emit(R.string.accounts_setup_failed)
            }
        }
    }

    // ---- Admin PIN check (turn off, new recovery code) ----

    fun onNewRecoveryCode() {
        if (busy) return
        resetDialogState()
        dialog = AccountsDialog.AdminPin(AdminAction.NEW_RECOVERY_CODE)
    }

    fun onAdminPinSubmit(pin: String) {
        val current = dialog as? AccountsDialog.AdminPin ?: return
        if (busy) return
        val wait = remainingLockoutMillis(failedPins, lastPinFailureAt, System.currentTimeMillis())
        if (wait > 0) {
            pinLockedSeconds = (wait + 999) / 1000
            pinWrong = false
            return
        }
        pinLockedSeconds = 0
        val admins = users.value.orEmpty().filter { it.active && it.role == UserRole.ADMIN }
        busy = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                admins.any { verifySecret(pin, it.pinSalt, it.pinHash) }
            }
            if (!ok) {
                failedPins++
                lastPinFailureAt = System.currentTimeMillis()
                pinWrong = true
                busy = false
                return@launch
            }
            failedPins = 0
            pinWrong = false
            when (current.action) {
                AdminAction.TURN_OFF -> {
                    // Off first (the app opens), then forget who was signed in.
                    setEnabled(false)
                    session.reset()
                    dialog = null
                }

                AdminAction.NEW_RECOVERY_CODE -> {
                    val code = generateRecoveryCode()
                    val recovery = hashed(normalizeRecoveryCode(code))
                    val settings = settingsDao.get()
                    if (settings != null) {
                        settingsDao.upsert(
                            settings.copy(recoveryHash = recovery.hash, recoverySalt = recovery.salt)
                        )
                        dialog = AccountsDialog.RecoveryCode(code)
                    } else {
                        dialog = null
                    }
                }
            }
            busy = false
        }
    }

    fun onRecoveryCodeSaved() {
        dialog = null
        // After the first setup the new Admin carries on signed in. (A later "new recovery code" has no
        // pending Admin: whoever is signed in stays signed in.)
        val adminName = pendingAdminName ?: return
        pendingAdminName = null
        viewModelScope.launch {
            val admin = appUserDao.observeActive().first()
                .firstOrNull { it.role == UserRole.ADMIN && it.name == adminName }
            if (admin != null) session.signIn(admin.id)
            session.holdForSetup(false)
        }
    }

    // ---- users ----

    fun onAddUser() {
        if (busy) return
        resetDialogState()
        dialog = AccountsDialog.EditUser(null)
    }

    fun onEditUser(userId: Long) {
        if (busy) return
        resetDialogState()
        dialog = AccountsDialog.EditUser(userId)
    }

    /** [pin] is null when the PIN of an existing user is left as it is. A new user always needs one. */
    fun onSaveUser(name: String, role: UserRole, pin: String?, pinAgain: String?) {
        val current = dialog as? AccountsDialog.EditUser ?: return
        if (busy) return
        val id = current.userId
        val error = checkUserForm(
            name, role,
            pin = if (id == null) pin.orEmpty() else pin,
            pinAgain = pinAgain,
            users = summaries(),
            editingId = id
        )
        formError = error
        if (error != null) return
        val cleanName = cleanUserName(name) ?: return
        busy = true
        viewModelScope.launch {
            if (id == null) {
                val h = hashed(pin.orEmpty())
                appUserDao.insert(
                    AppUser(
                        name = cleanName, role = role, pinHash = h.hash, pinSalt = h.salt,
                        createdAt = System.currentTimeMillis()
                    )
                )
            } else {
                val existing = appUserDao.getById(id)
                if (existing != null) {
                    val renamed = existing.copy(name = cleanName, role = role)
                    appUserDao.update(
                        if (pin != null) {
                            val h = hashed(pin)
                            renamed.copy(pinHash = h.hash, pinSalt = h.salt)
                        } else {
                            renamed
                        }
                    )
                }
            }
            busy = false
            dialog = null
        }
    }

    fun onSetActive(userId: Long, active: Boolean) {
        if (busy) return
        val list = users.value.orEmpty()
        val user = list.firstOrNull { it.id == userId } ?: return
        val all = summaries()
        if (!active && !canDeactivate(all, userId)) {
            formError = UserFormError.LAST_ADMIN
            return
        }
        if (active && isUserNameTaken(user.name, all, userId)) {
            formError = UserFormError.NAME_TAKEN
            return
        }
        busy = true
        viewModelScope.launch {
            appUserDao.update(user.copy(active = active))
            busy = false
            dialog = null
        }
    }

    fun onDismiss() {
        if (busy) return
        dialog = null
        resetDialogState()
    }

    // ---- helpers ----

    private fun summaries(): List<UserSummary> =
        users.value.orEmpty().map { UserSummary(it.id, it.name, it.role, it.active) }

    private fun resetDialogState() {
        formError = null
        pinWrong = false
        pinLockedSeconds = 0
    }

    // Leaving the screen before the code was saved cannot happen through the dialog, but never leave
    // the app held open: the next sign-in then needs the PIN.
    override fun onCleared() {
        if (pendingAdminName != null) session.holdForSetup(false)
    }

    // Salted hash, computed off the main thread (it is deliberately slow).
    private suspend fun hashed(secret: String): Hashed = withContext(Dispatchers.Default) {
        val salt = generateSalt()
        Hashed(hashSecret(secret, salt), salt)
    }
}
