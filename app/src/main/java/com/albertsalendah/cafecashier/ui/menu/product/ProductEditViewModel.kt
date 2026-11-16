package com.albertsalendah.cafecashier.ui.menu.product

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.db.Category
import com.albertsalendah.cafecashier.data.db.CategoryDao
import com.albertsalendah.cafecashier.data.db.Product
import com.albertsalendah.cafecashier.data.db.ProductDao
import com.albertsalendah.cafecashier.ui.todayEpochDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// What the "are you sure?" popup has to explain when a discount is saved with a missing date.
sealed interface DiscountDateWarning {
    data object NoDates : DiscountDateWarning
    data class NoEndDate(val startDay: Long) : DiscountDateWarning
    data class NoStartDate(val endDay: Long) : DiscountDateWarning

    // The end date is already behind us, so the discount would never apply to any sale.
    data class EndInPast(val endDay: Long) : DiscountDateWarning
}

// productId == null means a new product. Built with its own factory in ProductEditScreen
// because it needs the id (the shared AppViewModelFactory has no arguments).
class ProductEditViewModel(
    private val productDao: ProductDao,
    categoryDao: CategoryDao,
    private val productId: Long?,
    initialCategoryId: Long?
) : ViewModel() {

    // null = still loading.
    val categories: StateFlow<List<Category>?> = categoryDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var name by mutableStateOf("")
        private set
    var priceText by mutableStateOf("")
        private set
    var categoryId by mutableStateOf(initialCategoryId)
        private set

    // Discount: whole percent as typed digits ("" = none), and an optional window of epoch days
    // (both ends inclusive, null = open on that side; see domain/DiscountWindow.kt).
    var discountText by mutableStateOf("")
        private set
    var discountStartDay by mutableStateOf<Long?>(null)
        private set
    var discountEndDay by mutableStateOf<Long?>(null)
        private set

    // False while an existing product is still being loaded from the database.
    var ready by mutableStateOf(productId == null)
        private set

    // True if the product to edit no longer exists (the screen then just closes).
    var missing by mutableStateOf(false)
        private set

    private var original: Product? = null
    private var busy = false

    val isNew: Boolean get() = productId == null

    private val price: Long? get() = priceText.toLongOrNull()

    val discountPercent: Int get() = discountText.toIntOrNull() ?: 0

    // The dates only matter while there is a discount; with 0% they are not saved.
    val datesInvalid: Boolean
        get() {
            val start = discountStartDay
            val end = discountEndDay
            return discountPercent > 0 && start != null && end != null && end < start
        }

    // Non-null when the discount would be saved with an end date in the past, or with a missing start
    // and/or end date. The screen asks the user to confirm before saving in that case.
    // An existing discount that was not changed in this edit is not asked about again (renaming a
    // product must not re-ask about its old discount).
    val dateWarning: DiscountDateWarning?
        get() {
            val percent = discountPercent
            if (percent <= 0) return null
            val start = discountStartDay
            val end = discountEndDay
            val saved = original
            if (saved != null &&
                saved.discountPercent == percent &&
                saved.discountStartDay == start &&
                saved.discountEndDay == end
            ) return null

            // Most serious first: a discount that can never apply.
            if (end != null && end < todayEpochDay()) return DiscountDateWarning.EndInPast(end)
            return when {
                start != null && end != null -> null
                start != null -> DiscountDateWarning.NoEndDate(start)
                end != null -> DiscountDateWarning.NoStartDate(end)
                else -> DiscountDateWarning.NoDates
            }
        }

    val canSave: Boolean
        get() = name.isNotBlank() && (price ?: 0L) > 0L && categoryId != null && !datesInvalid

    init {
        if (productId != null) {
            viewModelScope.launch {
                val product = productDao.getById(productId)
                if (product == null) {
                    missing = true
                } else {
                    original = product
                    name = product.name
                    priceText = product.price.toString()
                    categoryId = product.categoryId
                    if (product.discountPercent > 0) {
                        discountText = product.discountPercent.toString()
                        discountStartDay = product.discountStartDay
                        discountEndDay = product.discountEndDay
                    }
                }
                ready = true
            }
        }
    }

    fun onNameChange(value: String) {
        name = value
    }

    // Digits only, whole rupiah. 12 digits is far above any real price and fits in a Long.
    fun onPriceChange(value: String) {
        priceText = value.filter { it.isDigit() }.take(12)
    }

    fun onCategoryChange(id: Long) {
        categoryId = id
    }

    // Digits only, at most 100 (a bigger number is clamped to 100).
    fun onDiscountChange(value: String) {
        val number = value.filter { it.isDigit() }.take(3).toIntOrNull()
        discountText = when {
            number == null -> ""
            number > 100 -> "100"
            else -> number.toString()
        }
    }

    fun onDiscountStartChange(epochDay: Long?) {
        discountStartDay = epochDay
    }

    fun onDiscountEndChange(epochDay: Long?) {
        discountEndDay = epochDay
    }

    fun save(onDone: () -> Unit) {
        val category = categoryId ?: return
        val priceValue = price ?: return
        if (!canSave || busy) return
        busy = true

        val percent = discountPercent
        // No discount = no dates stored.
        val start = if (percent > 0) discountStartDay else null
        val end = if (percent > 0) discountEndDay else null

        viewModelScope.launch {
            val existing = original
            if (existing == null) {
                productDao.insert(
                    Product(
                        categoryId = category,
                        name = name.trim(),
                        price = priceValue,
                        discountPercent = percent,
                        discountStartDay = start,
                        discountEndDay = end
                    )
                )
            } else {
                productDao.update(
                    existing.copy(
                        categoryId = category,
                        name = name.trim(),
                        price = priceValue,
                        discountPercent = percent,
                        discountStartDay = start,
                        discountEndDay = end
                    )
                )
            }
            onDone()
        }
    }

    // Products are never deleted; hiding sets archived = true.
    fun hide(onDone: () -> Unit) {
        val existing = original ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            productDao.update(existing.copy(archived = true))
            onDone()
        }
    }
}
