package com.albertsalendah.cafecashier.ui.backup

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.BackupManager
import com.albertsalendah.cafecashier.data.RestoreStage
import com.albertsalendah.cafecashier.domain.BackupManifest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

// The Backup & restore screen. Backing up is one step (the system "save as" screen gives the target).
// Restoring is: pick a file -> it is unzipped and checked -> the user confirms -> the app restarts
// and swaps the data in at start-up (BackupManager / RestoreApplier). Nothing live changes before
// the user confirms.
class BackupViewModel(private val backupManager: BackupManager) : ViewModel() {

    var lastBackupMillis by mutableStateOf(backupManager.lastBackupMillis())
        private set

    // A backup or a check of a picked file is running.
    var busy by mutableStateOf(false)
        private set

    // A picked backup that passed the checks and waits for the user's yes.
    var pendingRestore by mutableStateOf<BackupManifest?>(null)
        private set

    // The user said yes; the screen restarts the app.
    var restarting by mutableStateOf(false)
        private set

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    fun onBackupTargetPicked(uri: Uri) {
        if (busy || restarting) return
        busy = true
        viewModelScope.launch {
            val ok = backupManager.createBackup(uri)
            if (ok) lastBackupMillis = backupManager.lastBackupMillis()
            busy = false
            _messages.emit(if (ok) R.string.backup_saved else R.string.backup_failed)
        }
    }

    fun onRestoreSourcePicked(uri: Uri) {
        if (busy || restarting) return
        busy = true
        viewModelScope.launch {
            val message = when (val result = backupManager.stageRestore(uri)) {
                is RestoreStage.Ready -> {
                    pendingRestore = result.manifest
                    null
                }

                RestoreStage.NotABackup -> R.string.backup_not_a_backup
                RestoreStage.MadeByNewerApp -> R.string.backup_newer_app
                RestoreStage.Failed -> R.string.backup_unreadable
            }
            busy = false
            if (message != null) _messages.emit(message)
        }
    }

    fun onRestoreConfirmed() {
        if (pendingRestore == null || restarting) return
        if (backupManager.confirmRestore()) {
            restarting = true
        } else {
            onRestoreDeclined()
            _messages.tryEmit(R.string.backup_unreadable)
        }
    }

    fun onRestoreDeclined() {
        if (restarting) return
        pendingRestore = null
        backupManager.discardStaged()
    }

    // Leaving the screen with an unconfirmed backup staged: throw it away. (At the next start the
    // app would also ignore it, because it was never confirmed.)
    override fun onCleared() {
        if (!restarting) backupManager.discardStaged()
    }
}
