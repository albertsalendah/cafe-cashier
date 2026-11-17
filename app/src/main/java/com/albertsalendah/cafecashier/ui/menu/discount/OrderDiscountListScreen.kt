package com.albertsalendah.cafecashier.ui.menu.discount

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.data.db.DiscountType
import com.albertsalendah.cafecashier.data.db.OrderDiscount
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import com.albertsalendah.cafecashier.ui.formatRupiah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDiscountListScreen() {
    val viewModel = viewModel<OrderDiscountListViewModel>(factory = appViewModelFactory())
    val discounts by viewModel.discounts.collectAsStateWithLifecycle()
    val discountList = discounts

    // Which dialog is open. Ids (not objects) so the state survives rotation.
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.order_discounts)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true }) {
                Text(stringResource(R.string.add_order_discount))
            }
        }
    ) { innerPadding ->
        when {
            discountList == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            discountList.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.order_discounts_empty)) }

            else -> LazyColumn(
                modifier = Modifier.padding(innerPadding),
                // Space at the bottom so the last row isn't hidden behind the button.
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(discountList, key = { it.id }) { discount ->
                    ListItem(
                        headlineContent = { Text(discount.name) },
                        supportingContent = { Text(discountValueText(discount)) },
                        modifier = Modifier.clickable { editingId = discount.id }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAdd) {
        OrderDiscountDialog(
            title = stringResource(R.string.add_order_discount),
            initialName = "",
            initialType = DiscountType.PERCENT,
            initialValue = null,
            onSave = { name, type, value ->
                viewModel.add(name, type, value)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    discountList?.firstOrNull { it.id == editingId }?.let { discount ->
        OrderDiscountDialog(
            title = stringResource(R.string.edit_order_discount),
            initialName = discount.name,
            initialType = discount.type,
            initialValue = discount.value,
            onSave = { name, type, value ->
                viewModel.update(discount, name, type, value)
                editingId = null
            },
            onDismiss = { editingId = null },
            extra = {
                TextButton(onClick = {
                    editingId = null
                    deletingId = discount.id
                }) { Text(stringResource(R.string.delete)) }
            }
        )
    }

    // A real delete (there is no hide for discounts), so confirm first and say what is not affected.
    discountList?.firstOrNull { it.id == deletingId }?.let { discount ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.delete_item_title, discount.name)) },
            text = { Text(stringResource(R.string.delete_order_discount_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(discount)
                    deletingId = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// "10%" for a percent discount, "Rp 5.000" for a rupiah amount.
@Composable
private fun discountValueText(discount: OrderDiscount): String = when (discount.type) {
    DiscountType.PERCENT -> stringResource(R.string.order_discount_value_percent, discount.value.toInt())
    DiscountType.AMOUNT -> formatRupiah(discount.value)
}
