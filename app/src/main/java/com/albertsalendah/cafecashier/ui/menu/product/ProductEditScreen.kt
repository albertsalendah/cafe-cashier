package com.albertsalendah.cafecashier.ui.menu.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.albertsalendah.cafecashier.CafeCashierApp
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.ui.components.ExpandableText
import com.albertsalendah.cafecashier.ui.components.ThousandsVisualTransformation
import com.albertsalendah.cafecashier.ui.epochDayToPickerMillis
import com.albertsalendah.cafecashier.ui.formatEpochDay
import com.albertsalendah.cafecashier.ui.pickerMillisToEpochDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    productId: Long?,
    initialCategoryId: Long?,
    onDone: () -> Unit
) {
    val app = LocalContext.current.applicationContext as CafeCashierApp
    val viewModel = viewModel<ProductEditViewModel>(
        factory = viewModelFactory {
            initializer {
                ProductEditViewModel(
                    productDao = app.database.productDao(),
                    categoryDao = app.database.categoryDao(),
                    productId = productId,
                    initialCategoryId = initialCategoryId
                )
            }
        }
    )
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoryList = categories
    var showHideDialog by rememberSaveable { mutableStateOf(false) }
    var categoryMenuOpen by rememberSaveable { mutableStateOf(false) }
    var showDateConfirm by rememberSaveable { mutableStateOf(false) }
    // Which date picker is open: "start", "end" or none.
    var pickerFor by rememberSaveable { mutableStateOf<String?>(null) }

    // The product was removed from under us: nothing to edit, so close.
    LaunchedEffect(viewModel.missing) {
        if (viewModel.missing) onDone()
    }

    // A new product with no category chosen yet starts on the first category.
    LaunchedEffect(categoryList, viewModel.categoryId) {
        if (viewModel.isNew && viewModel.categoryId == null && !categoryList.isNullOrEmpty()) {
            viewModel.onCategoryChange(categoryList.first().id)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (viewModel.isNew) R.string.new_product else R.string.edit_product)
                    )
                }
            )
        }
    ) { innerPadding ->
        when {
            !viewModel.ready || categoryList == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            categoryList.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.add_category_first)) }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = viewModel::onNameChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.product_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Category picker: a button showing the current category that opens a menu.
                Box {
                    OutlinedButton(
                        onClick = { categoryMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val current = categoryList.firstOrNull { it.id == viewModel.categoryId }
                        Text(
                            stringResource(R.string.category_label) + ": " + (current?.name ?: "-")
                        )
                    }
                    DropdownMenu(
                        expanded = categoryMenuOpen,
                        onDismissRequest = { categoryMenuOpen = false }
                    ) {
                        categoryList.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    viewModel.onCategoryChange(category.id)
                                    categoryMenuOpen = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = viewModel.priceText,
                    onValueChange = viewModel::onPriceChange,
                    singleLine = true,
                    prefix = { Text("Rp ") },
                    label = { Text(stringResource(R.string.price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    // Shows 12500 as 12.500 while typing; the stored text stays plain digits.
                    visualTransformation = ThousandsVisualTransformation,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = viewModel.discountText,
                    onValueChange = viewModel::onDiscountChange,
                    singleLine = true,
                    suffix = { Text("%") },
                    label = { Text(stringResource(R.string.discount_percent)) },
                    supportingText = { Text(stringResource(R.string.discount_percent_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // The dates only matter while there is a discount, so they appear after a percent is typed.
                if (viewModel.discountPercent > 0) {
                    DateRow(
                        label = stringResource(R.string.discount_start_date),
                        day = viewModel.discountStartDay,
                        onPick = { pickerFor = "start" },
                        onClear = { viewModel.onDiscountStartChange(null) }
                    )
                    DateRow(
                        label = stringResource(R.string.discount_end_date),
                        day = viewModel.discountEndDay,
                        onPick = { pickerFor = "end" },
                        onClear = { viewModel.onDiscountEndChange(null) }
                    )
                    if (viewModel.datesInvalid) {
                        Text(
                            text = stringResource(R.string.discount_dates_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Button(
                    onClick = {
                        // A discount with a missing date needs an explicit "yes" first.
                        if (viewModel.dateWarning != null) showDateConfirm = true
                        else viewModel.save(onDone)
                    },
                    enabled = viewModel.canSave,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.save)) }

                TextButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.cancel)) }

                // Only an existing product can be hidden.
                if (!viewModel.isNew) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { showHideDialog = true }) {
                            Text(stringResource(R.string.hide_from_menu))
                        }
                        ExpandableText(
                            shortText = stringResource(R.string.hide_helper_short),
                            fullText = stringResource(R.string.hide_helper)
                        )
                    }
                }
            }
        }
    }

    if (showHideDialog) {
        AlertDialog(
            onDismissRequest = { showHideDialog = false },
            title = { Text(stringResource(R.string.hide_item_title, viewModel.name)) },
            text = { Text(stringResource(R.string.hide_helper)) },
            confirmButton = {
                TextButton(onClick = {
                    showHideDialog = false
                    viewModel.hide(onDone)
                }) { Text(stringResource(R.string.hide)) }
            },
            dismissButton = {
                TextButton(onClick = { showHideDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val warning = viewModel.dateWarning
    if (showDateConfirm && warning != null) {
        AlertDialog(
            onDismissRequest = { showDateConfirm = false },
            title = { Text(stringResource(R.string.discount_confirm_title)) },
            text = { Text(discountWarningText(warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showDateConfirm = false
                    viewModel.save(onDone)
                }) { Text(stringResource(R.string.save_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { showDateConfirm = false }) {
                    Text(stringResource(R.string.go_back))
                }
            }
        )
    }

    when (pickerFor) {
        "start" -> DayPickerDialog(
            initialDay = viewModel.discountStartDay,
            onPicked = {
                viewModel.onDiscountStartChange(it)
                pickerFor = null
            },
            onDismiss = { pickerFor = null }
        )

        "end" -> DayPickerDialog(
            initialDay = viewModel.discountEndDay,
            onPicked = {
                viewModel.onDiscountEndChange(it)
                pickerFor = null
            },
            onDismiss = { pickerFor = null }
        )
    }
}

// One date line: a button showing the date (or "not set") that opens the picker, and Clear.
@Composable
private fun DateRow(
    label: String,
    day: Long?,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) {
            Text(label + ": " + (day?.let { formatEpochDay(it) } ?: stringResource(R.string.date_not_set)))
        }
        if (day != null) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
        }
    }
}

// The picker reports the chosen date as UTC midnight millis; DateFormat.kt converts it to the
// epoch day the database stores (and back) without any time zone, which avoids off-by-one days.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(
    initialDay: Long?,
    onPicked: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDay?.let(::epochDayToPickerMillis)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPicked(pickerMillisToEpochDay(it)) } },
                enabled = state.selectedDateMillis != null
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

// The explanation shown before saving a discount with a missing date. The parts that decide what
// will happen (the missing date's consequence and the dates themselves) are bold and red.
@Composable
private fun discountWarningText(warning: DiscountDateWarning): AnnotatedString {
    val color = MaterialTheme.colorScheme.error
    val question = stringResource(R.string.discount_confirm_question)
    return when (warning) {
        DiscountDateWarning.NoDates -> {
            val always = stringResource(R.string.discount_hl_always)
            highlighted(stringResource(R.string.discount_warn_none, always), listOf(always), question, color)
        }

        is DiscountDateWarning.NoEndDate -> {
            val date = formatEpochDay(warning.startDay)
            val never = stringResource(R.string.discount_hl_never_ends)
            highlighted(
                stringResource(R.string.discount_warn_no_end, date, never),
                listOf(date, never), question, color
            )
        }

        is DiscountDateWarning.NoStartDate -> {
            val now = stringResource(R.string.discount_hl_starts_now)
            val date = formatEpochDay(warning.endDay)
            highlighted(
                stringResource(R.string.discount_warn_no_start, now, date),
                listOf(now, date), question, color
            )
        }
    }
}

private fun highlighted(
    text: String,
    highlights: List<String>,
    tail: String,
    color: Color
): AnnotatedString = buildAnnotatedString {
    append(text)
    for (part in highlights) {
        var from = text.indexOf(part)
        while (from >= 0) {
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color), from, from + part.length)
            from = text.indexOf(part, from + part.length)
        }
    }
    append("\n\n")
    append(tail)
}
