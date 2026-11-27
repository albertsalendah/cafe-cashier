package com.albertsalendah.cafecashier.ui.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.db.AppUser
import com.albertsalendah.cafecashier.data.db.AppUserDao
import com.albertsalendah.cafecashier.data.db.SettingsDao
import com.albertsalendah.cafecashier.domain.PinAttempts
import com.albertsalendah.cafecashier.domain.RECOVERY_ATTEMPT_KEY
import com.albertsalendah.cafecashier.domain.RECOVERY_CODE_LENGTH
import com.albertsalendah.cafecashier.domain.UserRole
import com.albertsalendah.cafecashier.domain.attemptKeyForUser
import com.albertsalendah.cafecashier.domain.generateSalt
import com.albertsalendah.cafecashier.domain.hashSecret
import com.albertsalendah.cafecashier.domain.isValidPin
import com.albertsalendah.cafecashier.domain.normalizeRecoveryCode
import com.albertsalendah.cafecashier.domain.verifySecret
import com.albertsalendah.cafecashier.domain.waitSeconds
import com.albertsalendah.cafecashier.ui.session.SessionHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The two steps of "Forgot PIN?" (Admin only). */
enum class RecoveryStep { CODE, NEW_PIN }

enum class RecoveryError { WRONG_CODE, PIN_INVALID, PIN_MISMATCH, FAILED }

private sealed interface TryResult {
    data object Ok : TryResult
    data class Locked(val waitMillis: Long) : TryResult
    data class Wrong(val waitMillis: Long) : TryResult
}

// The lock screen: tap a name, enter the PIN. Wrong tries are counted per user and survive the app
// being killed (PinAttempts), with the waiting times of lockoutSeconds(). "Forgot PIN?" is for an
// Admin with the recovery code, then a new PIN; a staff PIN is reset by an Admin on the Staff accounts
// screen. PINs and codes are only passed in as arguments, never kept here. Hashing and the attempt
// bookkeeping run off the main thread. This ViewModel belongs to the activity, so it outlives one
// lock screen: onShown() clears whatever the previous visit left behind.
class LockViewModel(
    private val settingsDao: SettingsDao,
    private val appUserDao: AppUserDao,
    private val session: SessionHolder,
    private val attempts: PinAttempts
) : ViewModel() {

    val storeName: StateFlow<String> = settingsDao.observe()
        .map { it?.storeName.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // Only users who may sign in. Null until the first read.
    val users: StateFlow<List<AppUser>?> = appUserDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var selectedId by mutableStateOf<Long?>(null)
        private set
    var pinWrong by mutableStateOf(false)
        private set
    var lockedSeconds by mutableStateOf(0L)
        private set

    // Goes up after every wrong try, so the screen can clear the field it is showing.
    var failureCount by mutableStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var recovery by mutableStateOf<RecoveryStep?>(null)
        private set
    var recoveryError by mutableStateOf<RecoveryError?>(null)
        private set

    // True once the right recovery code was entered, until the new PIN is saved or the dialog closed.
    private var recoveryVerified = false

    /** Every time the lock screen appears. */
    fun onShown() {
        selectedId = null
        pinWrong = false
        lockedSeconds = 0
        recovery = null
        recoveryError = null
        recoveryVerified = false
    }

    // ---- sign in ----

    fun onSelect(user: AppUser) {
        if (busy) return
        selectedId = user.id
        pinWrong = false
        refreshLock(attemptKeyForUser(user.id))
    }

    fun onBackToNames() {
        if (busy) return
        selectedId = null
        pinWrong = false
        lockedSeconds = 0
    }

    fun onSignIn(user: AppUser, pin: String) {
        if (busy || recovery != null) return
        busy = true
        viewModelScope.launch {
            // The list on screen may be a moment old: check the user as stored now.
            val fresh = appUserDao.getById(user.id)?.takeIf { it.active }
            if (fresh == null) {
                selectedId = null
                busy = false
                return@launch
            }
            val key = attemptKeyForUser(fresh.id)
            val result = withContext(Dispatchers.Default) {
                val wait = attempts.remainingMillis(key)
                when {
                    wait > 0 -> TryResult.Locked(wait)
                    verifySecret(pin, fresh.pinSalt, fresh.pinHash) -> {
                        attempts.recordSuccess(key)
                        TryResult.Ok
                    }

                    else -> TryResult.Wrong(attempts.recordFailure(key))
                }
            }
            when (result) {
                TryResult.Ok -> {
                    pinWrong = false
                    lockedSeconds = 0
                    selectedId = null
                    session.signIn(fresh.id)
                }

                is TryResult.Locked -> {
                    pinWrong = false
                    lockedSeconds = waitSeconds(result.waitMillis)
                }

                is TryResult.Wrong -> {
                    pinWrong = true
                    failureCount++
                    lockedSeconds = waitSeconds(result.waitMillis)
                }
            }
            busy = false
        }
    }

    /** Called every second while a waiting time is shown. */
    fun tick() {
        val key = currentKey() ?: return
        lockedSeconds = waitSeconds(attempts.remainingMillis(key))
        if (lockedSeconds == 0L) pinWrong = false
    }

    // ---- forgot PIN (Admin, with the recovery code) ----

    fun onForgotPin(user: AppUser) {
        if (busy || user.role != UserRole.ADMIN) return
        recovery = RecoveryStep.CODE
        recoveryError = null
        recoveryVerified = false
        refreshLock(RECOVERY_ATTEMPT_KEY)
    }

    fun onRecoveryDismiss() {
        if (busy) return
        recovery = null
        recoveryError = null
        recoveryVerified = false
        pinWrong = false
        selectedId?.let { refreshLock(attemptKeyForUser(it)) } ?: run { lockedSeconds = 0 }
    }

    fun onRecoveryCode(input: String) {
        if (busy || recovery != RecoveryStep.CODE) return
        recoveryError = null
        val code = normalizeRecoveryCode(input)
        // Can't be right, so don't count it as a guess.
        if (code.length != RECOVERY_CODE_LENGTH) {
            recoveryError = RecoveryError.WRONG_CODE
            return
        }
        busy = true
        viewModelScope.launch {
            val settings = settingsDao.get()
            val hash = settings?.recoveryHash
            val salt = settings?.recoverySalt
            val result = withContext(Dispatchers.Default) {
                val wait = attempts.remainingMillis(RECOVERY_ATTEMPT_KEY)
                when {
                    wait > 0 -> TryResult.Locked(wait)
                    hash != null && salt != null && verifySecret(code, salt, hash) -> {
                        attempts.recordSuccess(RECOVERY_ATTEMPT_KEY)
                        TryResult.Ok
                    }

                    else -> TryResult.Wrong(attempts.recordFailure(RECOVERY_ATTEMPT_KEY))
                }
            }
            when (result) {
                TryResult.Ok -> {
                    recoveryVerified = true
                    recovery = RecoveryStep.NEW_PIN
                    lockedSeconds = 0
                }

                is TryResult.Locked -> lockedSeconds = waitSeconds(result.waitMillis)

                is TryResult.Wrong -> {
                    recoveryError = RecoveryError.WRONG_CODE
                    failureCount++
                    lockedSeconds = waitSeconds(result.waitMillis)
                }
            }
            busy = false
        }
    }

    /** The new PIN for [user] after the recovery code was accepted; signs the Admin in. */
    fun onRecoveryNewPin(user: AppUser, pin: String, pinAgain: String) {
        if (busy || recovery != RecoveryStep.NEW_PIN || !recoveryVerified) return
        recoveryError = when {
            !isValidPin(pin) -> RecoveryError.PIN_INVALID
            pin != pinAgain -> RecoveryError.PIN_MISMATCH
            else -> null
        }
        if (recoveryError != null) return
        busy = true
        viewModelScope.launch {
            val fresh = appUserDao.getById(user.id)
            if (fresh == null || !fresh.active || fresh.role != UserRole.ADMIN) {
                recoveryError = RecoveryError.FAILED
                busy = false
                return@launch
            }
            val salt = generateSalt()
            val hash = withContext(Dispatchers.Default) { hashSecret(pin, salt) }
            appUserDao.update(fresh.copy(pinHash = hash, pinSalt = salt))
            withContext(Dispatchers.Default) { attempts.recordSuccess(attemptKeyForUser(fresh.id)) }
            recoveryVerified = false
            recovery = null
            recoveryError = null
            pinWrong = false
            lockedSeconds = 0
            selectedId = null
            session.signIn(fresh.id)
            busy = false
        }
    }

    // ---- helpers ----

    // The counter the waiting time on screen belongs to: the recovery code while its dialog is open,
    // otherwise the selected user's PIN.
    private fun currentKey(): String? =
        if (recovery != null) RECOVERY_ATTEMPT_KEY else selectedId?.let { attemptKeyForUser(it) }

    private fun refreshLock(key: String) {
        lockedSeconds = waitSeconds(attempts.remainingMillis(key))
    }
}
