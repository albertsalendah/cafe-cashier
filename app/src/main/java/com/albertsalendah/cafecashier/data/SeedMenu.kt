package com.albertsalendah.cafecashier.data

import com.albertsalendah.cafecashier.data.db.Category
import com.albertsalendah.cafecashier.data.db.Product
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The old menu as shipped in assets/seed_menu.json: categories, each with its products.
// Only name and price: no photos, no stock, no discounts (those are set in the product form).
@Serializable
data class SeedProduct(val name: String, val price: Long)

@Serializable
data class SeedCategory(val name: String, val products: List<SeedProduct> = emptyList())

@Serializable
data class SeedMenu(val categories: List<SeedCategory>)

private val seedJson = Json { ignoreUnknownKeys = true }

fun parseSeedMenu(json: String): SeedMenu = seedJson.decodeFromString(SeedMenu.serializer(), json)

// Entities ready for MenuSeedDao.importMenu. Categories keep the file's order (as sortOrder);
// the products' categoryId is a placeholder that the DAO replaces with the new category's id.
fun SeedMenu.toEntries(): List<Pair<Category, List<Product>>> =
    categories.mapIndexed { index, category ->
        Category(name = category.name, sortOrder = index) to
            category.products.map { Product(categoryId = 0, name = it.name, price = it.price) }
    }
