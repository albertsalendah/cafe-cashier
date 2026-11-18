package com.albertsalendah.cafecashier.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Checks the real file the app ships. Unit tests run with the app module as the working directory.
class SeedMenuTest {

    private val menu = parseSeedMenu(File("src/main/assets/seed_menu.json").readText())
    private val products = menu.categories.flatMap { it.products }

    @Test
    fun seedHasTheFiveOldCategoriesAndFortyFiveProducts() {
        assertEquals(
            listOf("FOODS", "DRINKS", "SNACKS", "OTHERS", "PACKS"),
            menu.categories.map { it.name }
        )
        assertEquals(45, products.size)
    }

    @Test
    fun noNameCarriesTheOldBrandWord() {
        for (name in menu.categories.map { it.name } + products.map { it.name }) {
            assertTrue("old brand word in \"$name\"", !name.contains("marikh", ignoreCase = true))
        }
    }

    @Test
    fun namesAreTidyAndPricesArePositive() {
        for (product in products) {
            assertTrue("blank name", product.name.isNotBlank())
            assertEquals("untrimmed: \"${product.name}\"", product.name.trim(), product.name)
            assertTrue("double space: \"${product.name}\"", !product.name.contains("  "))
            assertTrue("price of ${product.name}", product.price > 0)
        }
    }

    @Test
    fun entriesKeepTheFileOrderAndLeaveTheCategoryIdToTheDao() {
        val entries = menu.toEntries()
        assertEquals(menu.categories.size, entries.size)
        entries.forEachIndexed { index, (category, items) ->
            assertEquals(index, category.sortOrder)
            assertEquals(menu.categories[index].products.size, items.size)
            assertTrue(items.all { it.categoryId == 0L && !it.archived && it.discountPercent == 0 })
        }
    }
}
