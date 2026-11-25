package com.albertsalendah.cafecashier.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.domain.CalcLine
import com.albertsalendah.cafecashier.domain.calculateSale
import com.albertsalendah.cafecashier.domain.contrastingTextColor
import com.albertsalendah.cafecashier.domain.parseRoundingStep
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import com.albertsalendah.cafecashier.ui.components.ProductImage
import com.albertsalendah.cafecashier.ui.components.ThousandsVisualTransformation
import com.albertsalendah.cafecashier.ui.formatRupiah
import com.albertsalendah.cafecashier.ui.theme.ACCENT_PRESETS
import com.albertsalendah.cafecashier.ui.theme.accentColorScheme

// The café's settings: name, address, receipt footer, logo, optional tax, rounding step and accent
// colour. Nothing is saved until Save; Back and Cancel ask first when something was changed.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onDone: () -> Unit) {
    val viewModel = viewModel<SettingsViewModel>(factory = appViewModelFactory())
    var showDiscard by rememberSaveable { mutableStateOf(false) }

    // The system photo picker: no storage permission needed, the user just picks one picture.
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onLogoPicked(uri)
    }

    BackHandler(enabled = viewModel.isDirty) { showDiscard = true }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }
    ) { innerPadding ->
        if (!viewModel.ready) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ---- Café ----
                SectionTitle(stringResource(R.string.settings_section_cafe))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProductImage(
                        imageFile = viewModel.logoFile,
                        name = viewModel.storeName.ifBlank { "Cafe" },
                        modifier = Modifier.size(96.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                logoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !viewModel.importing
                        ) {
                            Text(
                                stringResource(
                                    if (viewModel.logoName == null) R.string.choose_logo else R.string.change_logo
                                )
                            )
                        }
                        if (viewModel.logoName != null) {
                            TextButton(onClick = viewModel::onLogoRemoved) {
                                Text(stringResource(R.string.remove_logo))
                            }
                        }
                    }
                    if (viewModel.logoFailed) {
                        Text(
                            text = stringResource(R.string.logo_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                OutlinedTextField(
                    value = viewModel.storeName,
                    onValueChange = viewModel::onStoreNameChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.store_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewModel.address,
                    onValueChange = viewModel::onAddressChange,
                    minLines = 2,
                    label = { Text(stringResource(R.string.store_address)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewModel.receiptFooter,
                    onValueChange = viewModel::onReceiptFooterChange,
                    minLines = 2,
                    label = { Text(stringResource(R.string.receipt_footer)) },
                    supportingText = { Text(stringResource(R.string.receipt_footer_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // ---- Sales ----
                SectionTitle(stringResource(R.string.settings_section_sales))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.tax_switch),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.tax_switch_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.taxEnabled,
                        onCheckedChange = viewModel::onTaxEnabledChange,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                if (viewModel.taxEnabled) {
                    OutlinedTextField(
                        value = viewModel.taxText,
                        onValueChange = viewModel::onTaxTextChange,
                        singleLine = true,
                        suffix = { Text("%") },
                        label = { Text(stringResource(R.string.tax_percent)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = viewModel.taxInvalid,
                        supportingText = if (viewModel.taxInvalid) {
                            ({ Text(stringResource(R.string.tax_invalid)) })
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = viewModel.roundingText,
                    onValueChange = viewModel::onRoundingChange,
                    singleLine = true,
                    prefix = { Text("Rp ") },
                    label = { Text(stringResource(R.string.rounding_step)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandsVisualTransformation,
                    isError = viewModel.roundingInvalid,
                    supportingText = if (viewModel.roundingInvalid) {
                        ({ Text(stringResource(R.string.rounding_invalid)) })
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (step in listOf(1L, 100L, 500L, 1_000L)) {
                        FilterChip(
                            selected = viewModel.roundingText == step.toString(),
                            onClick = { viewModel.onRoundingChange(step.toString()) },
                            label = {
                                Text(if (step == 1L) stringResource(R.string.rounding_none) else formatRupiah(step))
                            }
                        )
                    }
                }
                RoundingExample(viewModel.roundingText)

                // ---- Appearance ----
                SectionTitle(stringResource(R.string.settings_section_appearance))

                Text(
                    text = stringResource(R.string.accent_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Swatch(
                        argb = null,
                        label = stringResource(R.string.accent_default_description),
                        selected = viewModel.accentColor == null,
                        onClick = { viewModel.onAccentChange(null) }
                    )
                    for (preset in ACCENT_PRESETS) {
                        Swatch(
                            argb = preset.argb,
                            label = stringResource(preset.nameRes),
                            selected = viewModel.accentColor == preset.argb,
                            onClick = { viewModel.onAccentChange(preset.argb) }
                        )
                    }
                }
                AccentPreview(viewModel.accentColor)

                // ---- Save / Cancel ----
                Button(
                    onClick = { viewModel.save(onDone) },
                    enabled = viewModel.canSave && viewModel.isDirty,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.save)) }
                TextButton(
                    onClick = { if (viewModel.isDirty) showDiscard = true else onDone() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.cancel)) }
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.settings_discard_title)) },
            text = { Text(stringResource(R.string.settings_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    onDone()
                }) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

// "Example: Rp 12.350 is charged as Rp 12.400." for the typed step, using the same calculation as a sale.
@Composable
private fun RoundingExample(roundingText: String) {
    val step = parseRoundingStep(roundingText) ?: return
    val sample = 12_350L
    val charged = calculateSale(listOf(CalcLine(sample, 1)), null, 0, step).total
    Text(
        text = if (charged == sample) {
            stringResource(R.string.rounding_example_none)
        } else {
            stringResource(R.string.rounding_example, formatRupiah(sample), formatRupiah(charged))
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// One round colour choice. [argb] == null is "no accent" (the system colours), drawn as an outlined
// circle with the word "Auto". The chosen one gets a thick ring and a tick.
@Composable
private fun Swatch(argb: Int?, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (argb != null) Modifier.background(Color(argb)) else Modifier)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (argb == null) {
            Text(
                text = stringResource(R.string.accent_default),
                style = MaterialTheme.typography.labelSmall
            )
        } else if (selected) {
            Text(
                text = "✓",
                color = Color(contrastingTextColor(argb)),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// A button and a selected chip drawn in the chosen accent, so the choice can be judged before saving.
// With no accent they use the current theme.
@Composable
private fun AccentPreview(accent: Int?) {
    val content: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {}) { Text(stringResource(R.string.accent_preview)) }
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text(stringResource(R.string.accent_preview)) }
            )
        }
    }
    if (accent == null) {
        content()
    } else {
        MaterialTheme(colorScheme = accentColorScheme(accent, isSystemInDarkTheme())) { content() }
    }
}
