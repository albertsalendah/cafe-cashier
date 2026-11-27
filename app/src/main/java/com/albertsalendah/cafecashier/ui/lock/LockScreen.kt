package com.albertsalendah.cafecashier.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.AppUser
import com.albertsalendah.cafecashier.domain.PIN_MAX_LENGTH
import com.albertsalendah.cafecashier.domain.PIN_MIN_LENGTH
import com.albertsalendah.cafecashier.domain.UserRole
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import kotlinx.coroutines.delay

// Shown instead of the app while staff accounts are on and nobody is signed in: tap your name, then
// type your PIN. An Admin can use "Forgot PIN?" with the recovery code. There is no way past this
// screen except a right PIN (or a right recovery code and a new PIN).
@Composable
fun LockScreen() {
    val viewModel = viewModel<LockViewModel>(factory = appViewModelFactory())
    val users by viewModel.users.collectAsState()
    val storeName by viewModel.storeName.collectAsState()
    val selected = users.orEmpty().firstOrNull { it.id == viewModel.selectedId }

    // Start from the name list every time the screen appears.
    LaunchedEffect(Unit) { viewModel.onShown() }

    // Counts a waiting time down on screen; the real time is kept by PinAttempts.
    LaunchedEffect(viewModel.lockedSeconds > 0) {
        while (viewModel.lockedSeconds > 0) {
            delay(1000)
            viewModel.tick()
        }
    }

    // Back from the PIN field returns to the names; on the name list it leaves the app, as usual.
    BackHandler(enabled = selected != null && viewModel.recovery == null) { viewModel.onBackToNames() }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val title = if (storeName.isBlank()) stringResource(R.string.app_name) else storeName
                Text(title, style = MaterialTheme.typography.headlineMedium)
                if (selected == null) {
                    NameList(users, viewModel)
                } else {
                    PinEntry(selected, viewModel)
                }
            }
        }
    }

    if (selected != null) {
        when (viewModel.recovery) {
            null -> Unit
            RecoveryStep.CODE -> RecoveryCodeDialog(viewModel)
            RecoveryStep.NEW_PIN -> NewPinDialog(selected, viewModel)
        }
    }
}

@Composable
private fun roleLabel(role: UserRole): String =
    stringResource(if (role == UserRole.ADMIN) R.string.user_role_admin else R.string.user_role_staff)

@Composable
private fun NameList(users: List<AppUser>?, viewModel: LockViewModel) {
    Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleMedium)
    when {
        users == null -> Unit
        users.isEmpty() -> Text(stringResource(R.string.lock_no_users), style = MaterialTheme.typography.bodyMedium)
        else -> Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider()
            users.forEach { user ->
                ListItem(
                    headlineContent = { Text(user.name) },
                    supportingContent = { Text(roleLabel(user.role)) },
                    modifier = Modifier.clickable { viewModel.onSelect(user) }
                )
                HorizontalDivider()
            }
        }
    }
}

// Digits only, at most PIN_MAX_LENGTH of them. Kept in plain remember on purpose: a PIN must not
// end up in saved instance state.
@Composable
private fun PinField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= PIN_MAX_LENGTH && it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun WaitOrWrong(viewModel: LockViewModel, wrong: Boolean) {
    if (viewModel.lockedSeconds > 0) {
        Text(
            stringResource(R.string.pin_locked, viewModel.lockedSeconds.toInt()),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    } else if (wrong) {
        Text(
            stringResource(R.string.pin_wrong),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PinEntry(user: AppUser, viewModel: LockViewModel) {
    var pin by remember(user.id) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val canSubmit = pin.length >= PIN_MIN_LENGTH && !viewModel.busy && viewModel.lockedSeconds == 0L
    val submit = { if (canSubmit) viewModel.onSignIn(user, pin) }

    // A wrong try empties the field.
    LaunchedEffect(viewModel.failureCount) { pin = "" }
    LaunchedEffect(user.id) { focus.requestFocus() }

    Text(stringResource(R.string.lock_enter_pin, user.name), style = MaterialTheme.typography.titleMedium)
    PinField(
        label = stringResource(R.string.lock_pin_label),
        value = pin,
        onChange = { pin = it },
        modifier = Modifier.focusRequester(focus),
        isError = viewModel.pinWrong,
        enabled = !viewModel.busy,
        imeAction = ImeAction.Done,
        onDone = { submit() }
    )
    WaitOrWrong(viewModel, viewModel.pinWrong)
    Button(onClick = { submit() }, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.lock_sign_in))
    }
    if (user.role == UserRole.ADMIN) {
        TextButton(onClick = { viewModel.onForgotPin(user) }, enabled = !viewModel.busy) {
            Text(stringResource(R.string.lock_forgot_pin))
        }
    }
    TextButton(onClick = viewModel::onBackToNames, enabled = !viewModel.busy) {
        Text(stringResource(R.string.lock_other_name))
    }
}

// Step 1 of "Forgot PIN?": the recovery code saved when accounts were set up.
@Composable
private fun RecoveryCodeDialog(viewModel: LockViewModel) {
    var code by remember { mutableStateOf("") }
    // A wrong code empties the field.
    LaunchedEffect(viewModel.failureCount) { code = "" }
    AlertDialog(
        onDismissRequest = viewModel::onRecoveryDismiss,
        title = { Text(stringResource(R.string.recovery_enter_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.recovery_enter_help), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 16) code = it },
                    label = { Text(stringResource(R.string.recovery_code_label)) },
                    singleLine = true,
                    isError = viewModel.recoveryError == RecoveryError.WRONG_CODE,
                    enabled = !viewModel.busy,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.onRecoveryCode(code) }),
                    modifier = Modifier.fillMaxWidth()
                )
                if (viewModel.lockedSeconds > 0) {
                    WaitOrWrong(viewModel, wrong = false)
                } else if (viewModel.recoveryError == RecoveryError.WRONG_CODE) {
                    Text(
                        stringResource(R.string.recovery_code_wrong),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.onRecoveryCode(code) },
                enabled = !viewModel.busy && viewModel.lockedSeconds == 0L
            ) { Text(stringResource(R.string.recovery_continue)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onRecoveryDismiss, enabled = !viewModel.busy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// Step 2: the code was right, so choose a new PIN for this Admin. Saving signs the Admin in.
@Composable
private fun NewPinDialog(user: AppUser, viewModel: LockViewModel) {
    var pin by remember { mutableStateOf("") }
    var pinAgain by remember { mutableStateOf("") }
    val error = viewModel.recoveryError
    AlertDialog(
        onDismissRequest = viewModel::onRecoveryDismiss,
        title = { Text(stringResource(R.string.recovery_new_pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.recovery_new_pin_help, user.name),
                    style = MaterialTheme.typography.bodyMedium
                )
                PinField(
                    label = stringResource(R.string.user_pin),
                    value = pin,
                    onChange = { pin = it },
                    isError = error == RecoveryError.PIN_INVALID,
                    enabled = !viewModel.busy
                )
                PinField(
                    label = stringResource(R.string.user_pin_again),
                    value = pinAgain,
                    onChange = { pinAgain = it },
                    isError = error == RecoveryError.PIN_MISMATCH,
                    enabled = !viewModel.busy,
                    imeAction = ImeAction.Done,
                    onDone = { viewModel.onRecoveryNewPin(user, pin, pinAgain) }
                )
                if (error != null && error != RecoveryError.WRONG_CODE) {
                    Text(
                        stringResource(
                            when (error) {
                                RecoveryError.PIN_INVALID -> R.string.user_error_pin
                                RecoveryError.PIN_MISMATCH -> R.string.user_error_pin_mismatch
                                else -> R.string.recovery_failed
                            }
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.onRecoveryNewPin(user, pin, pinAgain) },
                enabled = !viewModel.busy
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onRecoveryDismiss, enabled = !viewModel.busy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
