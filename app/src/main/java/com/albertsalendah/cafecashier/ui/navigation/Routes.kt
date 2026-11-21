package com.albertsalendah.cafecashier.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Back-stack keys. Each must be @Serializable and a NavKey so the stack survives process death.
// The app's home: the bottom bar with the Order, Bills and Menu tabs.
@Serializable
data object Home : NavKey

@Serializable
data object Cart : NavKey

@Serializable
data object Checkout : NavKey

@Serializable
data object CategoryList : NavKey

@Serializable
data object ProductList : NavKey

@Serializable
data object AddOnList : NavKey

@Serializable
data object OrderDiscountList : NavKey

// productId == null means "new product"; categoryId preselects the category for a new product.
@Serializable
data class ProductEdit(val productId: Long? = null, val categoryId: Long? = null) : NavKey
