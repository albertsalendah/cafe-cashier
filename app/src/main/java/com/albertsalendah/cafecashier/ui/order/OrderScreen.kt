package com.albertsalendah.cafecashier.ui.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.CafeCashierApp
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.OrderType
import com.albertsalendah.cafecashier.domain.effectiveDiscountPercent
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import com.albertsalendah.cafecashier.ui.components.ProductImage
import com.albertsalendah.cafecashier.ui.formatRupiah
import com.albertsalendah.cafecashier.ui.theme.StatusColors
import com.albertsalendah.cafecashier.ui.todayEpochDay

// The Order tab: pick Dine In or Takeaway, pick a category, tap a product to add one to the cart.
// The bar at the bottom opens the cart once it has something in it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(onOpenCart: () -> Unit) {
    val viewModel = viewModel<OrderViewModel>(factory = appViewModelFactory())
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val quantities by viewModel.quantities.collectAsStateWithLifecycle()
    val amounts by viewModel.amounts.collectAsStateWithLifecycle()
    val categoryList = categories
    val productList = products
    val today = remember { todayEpochDay() }
    val context = LocalContext.current
    val imageStore = remember { (context.applicationContext as CafeCashierApp).imageStore }
    val green = StatusColors.green
    var showCloseConfirm by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            if (draft.saleId != null) {
                                stringResource(R.string.edit_bill)
                            } else {
                                stringResource(R.string.new_order)
                            }
                        )
                    }
                )
                // While a saved bill is loaded, say clearly which one it is.
                if (draft.saleId != null) {
                    BillStrip(
                        label = billLabel(draft),
                        customer = draft.customerName.trim(),
                        onClose = {
                            if (viewModel.hasUnsavedChanges()) showCloseConfirm = true else viewModel.closeBill()
                        }
                    )
                }
            }
        },
        // Also shown when every line of a saved bill is cancelled, so it can still be reached.
        bottomBar = {
            if (draft.lines.isNotEmpty()) {
                CartBar(
                    itemCount = draft.itemCount,
                    total = amounts.total,
                    billLabel = if (draft.saleId != null) billLabel(draft) else null,
                    onClick = onOpenCart
                )
            }
        }
    ) { innerPadding ->
        when {
            categoryList == null -> Box(
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
            ) { Text(stringResource(R.string.order_empty_menu)) }

            else -> Column(modifier = Modifier.padding(innerPadding)) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OrderType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = draft.orderType == type,
                            onClick = { viewModel.setOrderType(type) },
                            shape = SegmentedButtonDefaults.itemShape(index, OrderType.entries.size),
                            label = { Text(orderTypeLabel(type)) }
                        )
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categoryList, key = { it.id }) { category ->
                        FilterChip(
                            selected = category.id == selectedId,
                            onClick = { viewModel.selectCategory(category.id) },
                            label = { Text(category.name) }
                        )
                    }
                }
                HorizontalDivider()

                when {
                    productList == null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    productList.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { Text(stringResource(R.string.products_empty)) }

                    else -> LazyColumn {
                        items(productList, key = { it.id }) { product ->
                            // Same window rule as the checkout, so the "% off" shown is what is charged.
                            val percent = effectiveDiscountPercent(
                                product.discountPercent,
                                product.discountStartDay,
                                product.discountEndDay,
                                today
                            )
                            val offText = if (percent > 0) stringResource(R.string.discount_off, percent) else null
                            val inCart = quantities[product.id] ?: 0
                            ListItem(
                                leadingContent = {
                                    ProductImage(
                                        imageFile = product.imagePath?.let(imageStore::fileFor),
                                        name = product.name,
                                        modifier = Modifier.size(56.dp)
                                    )
                                },
                                headlineContent = { Text(product.name) },
                                supportingContent = {
                                    Text(
                                        buildAnnotatedString {
                                            append(formatRupiah(product.price))
                                            if (offText != null) {
                                                append(" · ")
                                                withStyle(SpanStyle(color = green, fontWeight = FontWeight.Medium)) {
                                                    append(offText)
                                                }
                                            }
                                        }
                                    )
                                },
                                // Once the product is in the cart a stepper shows its quantity: "-" takes
                                // one off, "+" adds one (the same as tapping the row).
                                trailingContent = if (inCart > 0) {
                                    {
                                        QuantityStepper(
                                            quantity = inCart,
                                            onDecrease = { viewModel.decrease(product.id) },
                                            onIncrease = { viewModel.addProduct(product) }
                                        )
                                    }
                                } else null,
                                modifier = Modifier.clickable { viewModel.addProduct(product) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showCloseConfirm = false
                    viewModel.closeBill()
                }) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            }
        )
    }
}

// "Dine in" / "Takeaway". Public so the cart and the bills screens use the same words.
@Composable
fun orderTypeLabel(type: OrderType): String = when (type) {
    OrderType.DINE_IN -> stringResource(R.string.order_type_dine_in)
    OrderType.TAKEAWAY -> stringResource(R.string.order_type_takeaway)
}

// A coloured strip under the top bar: "Bill #3 · Table A3", the customer below it, and a button to
// let go of the bill. Only shown while a saved bill is being edited.
@Composable
private fun BillStrip(label: String, customer: String, onClose: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (customer.isNotEmpty()) {
                    Text(text = customer, style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.close_bill)) }
        }
    }
}

// Bottom bar with the number of items and the total; tapping it opens the cart. For a saved bill
// the bill and table are shown above the item count.
@Composable
private fun CartBar(itemCount: Int, total: Long, billLabel: String?, onClick: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (billLabel != null) {
                    Text(text = billLabel, fontWeight = FontWeight.Bold)
                }
                Text(pluralStringResource(R.plurals.cart_items, itemCount, itemCount))
            }
            Text(formatRupiah(total))
        }
    }
}
