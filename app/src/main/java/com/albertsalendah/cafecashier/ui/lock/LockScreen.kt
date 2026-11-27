package com.albertsalendah.cafecashier.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.CafeCashierApp
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.AppUser
import com.albertsalendah.cafecashier.domain.PIN_LENGTH
import com.albertsalendah.cafecashier.domain.UserRole
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import com.albertsalendah.cafecashier.ui.components.ProductImage
import kotlinx.coroutines.delay

// Shown instead of the app while staff accounts are on and nobody is signed in: the café's name (and
// logo), a dropdown with who is signing in, six PIN boxes and a number pad. The sixth digit signs in.
// An Admin can use "Forgot PIN?" with the recovery code. There is no way past this screen except a
// right PIN (or a right recovery code and a new PIN).
@Composable
fun LockScreen() {
    val viewModel = viewModel<LockViewModel>(factory = appViewModelFactory())
    val users by viewModel.users.collectAsState()
    val branding by viewModel.branding.collectAsState()
    val selected = users.orEmpty().firstOrNull { it.id == viewModel.selectedId }

    // Start fresh every time the screen appears, then preselect (the last user who signed in).
    LaunchedEffect(Unit) { viewModel.onShown() }
    LaunchedEffect(users) { users?.let { viewModel.onUsersLoaded(it) } }

    // Counts a waiting time down on screen; the real time is kept by PinAttempts.
    LaunchedEffect(viewModel.lockedSeconds > 0) {
        while (viewModel.lockedSeconds > 0) {
            delay(1000)
            viewModel.tick()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Branding(branding)
                val list = users
                when {
                    list == null -> Unit
                    list.isEmpty() -> Text(
                        stringResource(R.string.lock_no_users),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    selected != null -> {
                        UserPicker(list, selected) { viewModel.onSelect(it) }
                        // Rebuilt for every user, so a half-typed PIN never moves to someone else.
                        androidx.compose.runtime.key(selected.id) { PinPad(selected, viewModel) }
                    }
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
private fun Branding(branding: LockBranding) {
    val imageStore = (LocalContext.current.applicationContext as CafeCashierApp).imageStore
    val title = if (branding.name.isBlank()) stringResource(R.string.app_name) else branding.name
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (branding.logoName != null) {
            ProductImage(
                imageFile = imageStore.fileFor(branding.logoName),
                name = title,
                modifier = Modifier.size(72.dp)
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Who is signing in. With more than one user it opens a menu; with one it is just a label.
@Composable
private fun UserPicker(users: List<AppUser>, selected: AppUser, onSelect: (AppUser) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val canPick = users.size > 1
    val content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.lock_who_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    selected.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    roleLabel(selected.role),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canPick) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        if (canPick) {
            OutlinedCard(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), content = content)
        } else {
            OutlinedCard(modifier = Modifier.fillMaxWidth(), content = content)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            users.forEach { user ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(user.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                roleLabel(user.role),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(user)
                    }
                )
            }
        }
    }
}

// The six boxes, the number pad and "Forgot PIN?". The PIN lives in plain remember on purpose (never
// in saved state). The sixth digit signs in; a refused try empties the boxes. While a wait is shown,
// or a check is running, the keys are off.
@Composable
private fun PinPad(user: AppUser, viewModel: LockViewModel) {
    var pin by remember { mutableStateOf("") }
    val locked = viewModel.lockedSeconds > 0
    val enabled = !viewModel.busy && !locked && viewModel.recovery == null
    val showError = viewModel.pinWrong && pin.isEmpty()
    val focus = remember { FocusRequester() }
    val progress = stringResource(R.string.lock_pin_progress, pin.length, PIN_LENGTH)

    LaunchedEffect(viewModel.failureCount) { pin = "" }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun press(digit: Char) {
        if (!enabled || pin.length >= PIN_LENGTH) return
        pin += digit
        if (pin.length == PIN_LENGTH) viewModel.onSignIn(user, pin)
    }

    fun erase() {
        if (enabled && pin.isNotEmpty()) pin = pin.dropLast(1)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .focusable()
            // A hardware keyboard (the emulator, a tablet with a keyboard) types digits too.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = event.utf16CodePoint
                when {
                    code >= '0'.code && code <= '9'.code -> {
                        press(code.toChar())
                        true
                    }

                    event.key == Key.Backspace || event.key == Key.Delete -> {
                        erase()
                        true
                    }

                    else -> false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.lock_enter_pin), style = MaterialTheme.typography.titleMedium)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics { contentDescription = progress }
        ) {
            repeat(PIN_LENGTH) { index -> PinBox(filled = index < pin.length, error = showError) }
        }

        // A fixed-height line, so the keys do not jump when a message appears.
        Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
            if (locked) {
                Text(
                    stringResource(R.string.pin_locked, viewModel.lockedSeconds.toInt()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (showError) {
                Text(
                    stringResource(R.string.pin_wrong),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("123", "456", "789").forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { digit -> PadKey(digit.toString(), enabled) { press(digit) } }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(modifier = Modifier.weight(1f))
                PadKey("0", enabled) { press('0') }
                PadKey(
                    label = "←",
                    enabled = enabled && pin.isNotEmpty(),
                    description = stringResource(R.string.lock_delete_digit)
                ) { erase() }
            }
        }

        if (user.role == UserRole.ADMIN) {
            TextButton(onClick = { viewModel.onForgotPin(user) }, enabled = !viewModel.busy) {
                Text(stringResource(R.string.lock_forgot_pin))
            }
        }
    }
}

@Composable
private fun PinBox(filled: Boolean, error: Boolean) {
    val color = when {
        error -> MaterialTheme.colorScheme.error
        filled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 52.dp)
            .border(if (filled || error) 2.dp else 1.dp, color, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (filled) Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
    }
}

@Composable
private fun RowScope.PadKey(
    label: String,
    enabled: Boolean,
    description: String? = null,
    onClick: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (enabled) onSurface else onSurface.copy(alpha = 0.38f),
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

// Digits only, at most PIN_LENGTH of them (used by the new-PIN dialog). Kept in plain remember on
// purpose: a PIN must not end up in saved instance state.
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
        onValueChange = { if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) onChange(it) },
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
