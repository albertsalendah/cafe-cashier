package com.albertsalendah.cafecashier.ui.menu.product

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albertsalendah.cafecashier.R
import com.albertsalendah.cafecashier.ui.appViewModelFactory
import com.albertsalendah.cafecashier.ui.formatRupiah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    onAddProduct: (categoryId: Long?) -> Unit,
    onEditProduct: (productId: Long) -> Unit
) {
    val viewModel = viewModel<ProductListViewModel>(factory = appViewModelFactory())
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val categoryList = categories
    val productList = products

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.products)) }) },
        floatingActionButton = {
            // Products need a category, so there is nothing to add until one exists.
            if (!categoryList.isNullOrEmpty()) {
                ExtendedFloatingActionButton(onClick = { onAddProduct(selectedId) }) {
                    Text(stringResource(R.string.add_product))
                }
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
            ) { Text(stringResource(R.string.add_category_first)) }

            else -> Column(modifier = Modifier.padding(innerPadding)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
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

                    else -> LazyColumn(
                        // Space at the bottom so the last row isn't hidden behind the button.
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(productList, key = { it.id }) { product ->
                            ListItem(
                                headlineContent = { Text(product.name) },
                                supportingContent = { Text(formatRupiah(product.price)) },
                                modifier = Modifier.clickable { onEditProduct(product.id) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
