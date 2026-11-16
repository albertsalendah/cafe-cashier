package com.albertsalendah.cafecashier.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Back-stack keys. Each must be @Serializable and a NavKey so the stack survives process death.
@Serializable
data object MenuHome : NavKey

@Serializable
data object CategoryList : NavKey

@Serializable
data object ProductList : NavKey

// productId == null means "new product"; categoryId preselects the category for a new product.
@Serializable
data class ProductEdit(val productId: Long? = null, val categoryId: Long? = null) : NavKey
