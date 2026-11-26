package com.albertsalendah.cafecashier.ui.backup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.domain.backupFileName
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import com.jakewharton.processphoenix.ProcessPhoenix
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Save everything to one file, or bring a saved file back. The file goes through the system
// "save as" and "open" screens, so the app needs no storage permission.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val viewModel = viewModel<BackupViewModel>(factory = appViewModelFactory())
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val working = viewModel.busy || viewModel.restarting

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) viewModel.onBackupTargetPicked(uri) }

    // Any file type: the app checks the content itself (some file managers label a zip oddly).
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.onRestoreSourcePicked(uri) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(context.getString(it)) }
    }

    // The restore was confirmed: restart the app. The swap happens in CafeCashierApp at the next start.
    LaunchedEffect(viewModel.restarting) {
        if (viewModel.restarting) ProcessPhoenix.triggerRebirth(context)
    }

    // No leaving while a backup is being written or a restore is on its way.
    BackHandler(enabled = working) { }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !working) { Text(stringResource(R.string.back)) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Back up ----
            Text(stringResource(R.string.backup_section_backup), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.backup_explain), style = MaterialTheme.typography.bodyMedium)
            val last = viewModel.lastBackupMillis
            Text(
                text = if (last == null) {
                    stringResource(R.string.backup_never)
                } else {
                    stringResource(R.string.backup_last, formatBackupTime(last))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { saveLauncher.launch(backupFileName(System.currentTimeMillis(), ZoneId.systemDefault())) },
                enabled = !working,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.backup_now)) }

            HorizontalDivider()

            // ---- Restore ----
            Text(stringResource(R.string.backup_section_restore), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.backup_restore_explain), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { openLauncher.launch(arrayOf("*/*")) },
                enabled = !working,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.backup_restore_pick)) }

            if (working) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(
                        stringResource(if (viewModel.restarting) R.string.backup_restarting else R.string.backup_working),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    val pending = viewModel.pendingRestore
    if (pending != null && !viewModel.restarting) {
        AlertDialog(
            onDismissRequest = viewModel::onRestoreDeclined,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_message, formatBackupTime(pending.createdAtMillis))) },
            confirmButton = {
                TextButton(onClick = viewModel::onRestoreConfirmed) {
                    Text(stringResource(R.string.backup_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onRestoreDeclined) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// 26 Nov 2026, 19:30 (the phone's time zone)
private fun formatBackupTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault()))
