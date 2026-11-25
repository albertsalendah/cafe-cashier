package com.albertsalendah.cafecashier.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// seedIfEmpty is plain Kotlin on top of the abstract count/insertAll methods, so it is tested with an
// in-memory stand-in for those. (Room itself generates the real ones.)
class ExpenseCategoryDaoTest {

    private class FakeDao(val rows: MutableList<ExpenseCategory> = mutableListOf()) : ExpenseCategoryDao() {
        override fun observeActive(): Flow<List<ExpenseCategory>> = emptyFlow()
        override fun observeArchived(): Flow<List<ExpenseCategory>> = emptyFlow()
        override fun observeAll(): Flow<List<ExpenseCategory>> = emptyFlow()
        override suspend fun count(): Int = rows.size
        override suspend fun insert(category: ExpenseCategory): Long {
            rows += category.copy(id = rows.size + 1L)
            return rows.size.toLong()
        }
        override suspend fun insertAll(categories: List<ExpenseCategory>) {
            categories.forEach { insert(it) }
        }
        override suspend fun update(category: ExpenseCategory) {
            val i = rows.indexOfFirst { it.id == category.id }
            rows[i] = category
        }
    }

    @Test
    fun seedsTheNamesIntoAnEmptyTable() = runBlocking {
        val dao = FakeDao()
        assertTrue(dao.seedIfEmpty(listOf("a", "b")))
        assertEquals(listOf("a", "b"), dao.rows.map { it.name })
        assertTrue(dao.rows.none { it.archived })
    }

    @Test
    fun doesNothingWhenThereAreAlreadyRows() = runBlocking {
        val dao = FakeDao(mutableListOf(ExpenseCategory(id = 1, name = "mine")))
        assertFalse(dao.seedIfEmpty(listOf("a", "b")))
        assertEquals(listOf("mine"), dao.rows.map { it.name })
    }

    @Test
    fun aHiddenCategoryStillCountsSoTheDefaultsComeBackNever() = runBlocking {
        val dao = FakeDao(mutableListOf(ExpenseCategory(id = 1, name = "old", archived = true)))
        assertFalse(dao.seedIfEmpty(DEFAULT_EXPENSE_CATEGORIES))
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun seedingTwiceAddsTheDefaultsOnce() = runBlocking {
        val dao = FakeDao()
        dao.seedIfEmpty(DEFAULT_EXPENSE_CATEGORIES)
        dao.seedIfEmpty(DEFAULT_EXPENSE_CATEGORIES)
        assertEquals(DEFAULT_EXPENSE_CATEGORIES.size, dao.rows.size)
    }

    @Test
    fun theDefaultNamesAreNotBlankAndNotDuplicated() {
        assertTrue(DEFAULT_EXPENSE_CATEGORIES.none { it.isBlank() })
        assertEquals(DEFAULT_EXPENSE_CATEGORIES.size, DEFAULT_EXPENSE_CATEGORIES.toSet().size)
    }
}
