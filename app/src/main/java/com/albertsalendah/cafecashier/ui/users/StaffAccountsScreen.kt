package com.albertsalendah.cafecashier.ui.users

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.AppUser
import com.albertsalendah.cafecashier.domain.MAX_USER_NAME_LENGTH
import com.albertsalendah.cafecashier.domain.PIN_MAX_LENGTH
import com.albertsalendah.cafecashier.domain.UserFormError
import com.albertsalendah.cafecashier.domain.UserRole
import com.albertsalendah.cafecashier.ui.appViewModelFactory

// Menu > Staff accounts: the on/off switch, the users, and the recovery code. Only an Admin gets here
// once accounts are on: staff do not see the Menu tab (HomeScreen).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffAccountsScreen(onBack: () -> Unit) {
    val viewModel = viewModel<StaffAccountsViewModel>(factory = appViewModelFactory())
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val enabled by viewModel.accountsEnabled.collectAsState()
    val users by viewModel.users.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(context.getString(it)) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.staff_accounts_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            val isOn = enabled == true
            ListItem(
                headlineContent = { Text(stringResource(R.string.use_staff_accounts)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (isOn) R.string.staff_accounts_explain_on else R.string.staff_accounts_explain_off
                        )
                    )
                },
                trailingContent = {
                    Switch(
                        checked = isOn,
                        enabled = enabled != null && !viewModel.busy,
                        onCheckedChange = viewModel::onSwitchToggled
                    )
                }
            )
            HorizontalDivider()

            if (isOn) {
                Text(
                    stringResource(R.string.users_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
                )
                users.orEmpty().forEach { user ->
                    ListItem(
                        headlineContent = { Text(user.name) },
                        supportingContent = { Text(roleLine(user)) },
                        modifier = Modifier.clickable { viewModel.onEditUser(user.id) }
                    )
                    HorizontalDivider()
                }
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = viewModel::onAddUser, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.add_user))
                    }
                    OutlinedButton(onClick = viewModel::onNewRecoveryCode, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.new_recovery_code))
                    }
                }
            }
        }
    }

    when (val dialog = viewModel.dialog) {
        null -> Unit
        AccountsDialog.Setup -> SetupDialog(viewModel)
        is AccountsDialog.AdminPin -> AdminPinDialog(dialog.action, viewModel)
        is AccountsDialog.EditUser -> {
            val existing = users.orEmpty().firstOrNull { it.id == dialog.userId }
            // A deleted-from-under-us id cannot happen (users are never deleted), but do not crash if it does.
            if (dialog.userId == null || existing != null) UserDialog(existing, viewModel)
        }

        is AccountsDialog.RecoveryCode -> RecoveryDialog(dialog.code, viewModel)
    }
}

@Composable
private fun roleLine(user: AppUser): String {
    val role = stringResource(if (user.role == UserRole.ADMIN) R.string.user_role_admin else R.string.user_role_staff)
    return if (user.active) role else stringResource(R.string.user_role_deactivated, role)
}

@Composable
private fun errorText(error: UserFormError): String = stringResource(
    when (error) {
        UserFormError.NAME_INVALID -> R.string.user_error_name
        UserFormError.NAME_TAKEN -> R.string.user_error_name_taken
        UserFormError.PIN_INVALID -> R.string.user_error_pin
        UserFormError.PIN_MISMATCH -> R.string.user_error_pin_mismatch
        UserFormError.LAST_ADMIN -> R.string.user_error_last_admin
    }
)

// Digits only, at most PIN_MAX_LENGTH of them. Kept in plain remember on purpose: a PIN must not
// end up in saved instance state.
@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= PIN_MAX_LENGTH && it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SetupDialog(viewModel: StaffAccountsViewModel) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pinAgain by remember { mutableStateOf("") }
    val error = viewModel.formError
    AlertDialog(
        onDismissRequest = viewModel::onDismiss,
        title = { Text(stringResource(R.string.accounts_setup_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.accounts_setup_intro), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= MAX_USER_NAME_LENGTH) name = it },
                    label = { Text(stringResource(R.string.user_name)) },
                    singleLine = true,
                    isError = error == UserFormError.NAME_INVALID,
                    modifier = Modifier.fillMaxWidth()
                )
                PinField(stringResource(R.string.user_pin), pin, { pin = it }, error == UserFormError.PIN_INVALID)
                PinField(stringResource(R.string.user_pin_again), pinAgain, { pinAgain = it }, error == UserFormError.PIN_MISMATCH)
                if (error != null) {
                    Text(errorText(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.onSetupSubmit(name, pin, pinAgain) },
                enabled = !viewModel.busy
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onDismiss, enabled = !viewModel.busy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun AdminPinDialog(action: AdminAction, viewModel: StaffAccountsViewModel) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = viewModel::onDismiss,
        title = { Text(stringResource(R.string.admin_pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        if (action == AdminAction.TURN_OFF) R.string.admin_pin_turn_off else R.string.admin_pin_new_code
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                PinField(stringResource(R.string.user_pin), pin, { pin = it }, viewModel.pinWrong)
                if (viewModel.pinLockedSeconds > 0) {
                    Text(
                        stringResource(R.string.pin_locked, viewModel.pinLockedSeconds.toInt()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (viewModel.pinWrong) {
                    Text(
                        stringResource(R.string.pin_wrong),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.onAdminPinSubmit(pin) }, enabled = !viewModel.busy) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onDismiss, enabled = !viewModel.busy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** [existing] null = a new user. */
@Composable
private fun UserDialog(existing: AppUser?, viewModel: StaffAccountsViewModel) {
    val editing = existing != null
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var role by remember { mutableStateOf(existing?.role ?: UserRole.STAFF) }
    var changePin by remember { mutableStateOf(!editing) }
    var pin by remember { mutableStateOf("") }
    var pinAgain by remember { mutableStateOf("") }
    val error = viewModel.formError
    AlertDialog(
        onDismissRequest = viewModel::onDismiss,
        title = {
            Text(stringResource(if (editing) R.string.user_dialog_edit_title else R.string.user_dialog_add_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= MAX_USER_NAME_LENGTH) name = it },
                    label = { Text(stringResource(R.string.user_name)) },
                    singleLine = true,
                    isError = error == UserFormError.NAME_INVALID || error == UserFormError.NAME_TAKEN,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == UserRole.STAFF,
                        onClick = { role = UserRole.STAFF },
                        label = { Text(stringResource(R.string.user_role_staff)) }
                    )
                    FilterChip(
                        selected = role == UserRole.ADMIN,
                        onClick = { role = UserRole.ADMIN },
                        label = { Text(stringResource(R.string.user_role_admin)) }
                    )
                }
                if (editing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { changePin = !changePin }
                    ) {
                        Checkbox(checked = changePin, onCheckedChange = { changePin = it })
                        Text(stringResource(R.string.user_set_new_pin))
                    }
                }
                if (changePin) {
                    PinField(stringResource(R.string.user_pin), pin, { pin = it }, error == UserFormError.PIN_INVALID)
                    PinField(stringResource(R.string.user_pin_again), pinAgain, { pinAgain = it }, error == UserFormError.PIN_MISMATCH)
                }
                if (error != null) {
                    Text(errorText(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (existing != null) {
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    if (existing.active) {
                        Text(stringResource(R.string.deactivate_help), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = { viewModel.onSetActive(existing.id, false) },
                            enabled = !viewModel.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.deactivate_user)) }
                    } else {
                        Text(stringResource(R.string.user_deactivated_info), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = { viewModel.onSetActive(existing.id, true) },
                            enabled = !viewModel.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.reactivate_user)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.onSaveUser(
                        name, role,
                        pin = if (changePin) pin else null,
                        pinAgain = if (changePin) pinAgain else null
                    )
                },
                enabled = !viewModel.busy
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onDismiss, enabled = !viewModel.busy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// Shown once. Cannot be dismissed by tapping outside or Back: the only way out is the button, so
// nobody closes it by accident before saving the code.
@Composable
private fun RecoveryDialog(code: String, viewModel: StaffAccountsViewModel) {
    val context = LocalContext.current
    val shown = com.albertsalendah.cafecashier.domain.formatRecoveryCode(code)
    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.recovery_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.recovery_explain), style = MaterialTheme.typography.bodyMedium)
                Text(shown, style = MaterialTheme.typography.headlineMedium)
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.recovery_share_text, shown))
                        }
                        context.startActivity(
                            Intent.createChooser(send, context.getString(R.string.recovery_share_title))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.recovery_share)) }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::onRecoveryCodeSaved) { Text(stringResource(R.string.recovery_saved)) }
        }
    )
}
