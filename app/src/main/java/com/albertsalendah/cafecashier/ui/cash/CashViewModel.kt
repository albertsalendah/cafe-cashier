package com.albertsalendah.cafecashier.ui.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.db.DailyCash
import com.albertsalendah.cafecashier.data.db.DailyCashDao
import com.albertsalendah.cafecashier.data.db.Expense
import com.albertsalendah.cafecashier.data.db.ExpenseCategory
import com.albertsalendah.cafecashier.data.db.ExpenseCategoryDao
import com.albertsalendah.cafecashier.data.db.ExpenseDao
import com.albertsalendah.cafecashier.data.db.ExpensePool
import com.albertsalendah.cafecashier.data.db.SaleDao
import com.albertsalendah.cafecashier.domain.DayClosing
import com.albertsalendah.cafecashier.domain.buildExpense
import com.albertsalendah.cafecashier.domain.calculateClosing
import com.albertsalendah.cafecashier.domain.dayRangeMillis
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// One expense with the name of its category (looked up among all categories, hidden ones included).
data class ExpenseRow(val expense: Expense, val categoryName: String)

// Everything the Cash tab shows for one day. [day] says which day it was built for.
data class CashUiState(
    val day: Long,
    val openingCash: Long?,            // null = not entered for this day
    val expenses: List<ExpenseRow>,    // cancelled ones included (shown struck through)
    val closing: DayClosing
)

@OptIn(ExperimentalCoroutinesApi::class)
class CashViewModel(
    private val dailyCashDao: DailyCashDao,
    private val expenseDao: ExpenseDao,
    private val expenseCategoryDao: ExpenseCategoryDao,
    private val saleDao: SaleDao,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    // The day being looked at (epoch day, café time zone). Starts on today.
    private val _day = MutableStateFlow(LocalDate.now(clock).toEpochDay())
    val day: StateFlow<Long> = _day.asStateFlow()

    // The categories the expense form can pick from (hidden ones are left out). null = still loading.
    val categories: StateFlow<List<ExpenseCategory>?> = expenseCategoryDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Rebuilt whenever the day changes, and live: a new payment, expense or opening cash updates it.
    // null = still loading. After a day change this briefly still holds the old day; the screen
    // compares state.day with day and shows the loading spinner until they match.
    val state: StateFlow<CashUiState?> = _day.flatMapLatest { day ->
        val range = dayRangeMillis(day, clock.zone)
        combine(
            dailyCashDao.observe(day),
            expenseDao.observeByDay(day),
            saleDao.observeCashPaidTotal(range.fromMillis, range.toMillis),
            expenseCategoryDao.observeAll()
        ) { dailyCash, expenses, cashSales, allCategories ->
            val names = allCategories.associate { it.id to it.name }
            CashUiState(
                day = day,
                openingCash = dailyCash?.openingCash,
                expenses = expenses.map { ExpenseRow(it, names[it.categoryId].orEmpty()) },
                closing = calculateClosing(dailyCash?.openingCash, cashSales, expenses)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Today in the café's time zone, read fresh each time (the app may stay open past midnight).
    fun today(): Long = LocalDate.now(clock).toEpochDay()

    fun selectDay(day: Long) {
        _day.value = day
    }

    fun previousDay() {
        _day.value -= 1
    }

    fun nextDay() {
        _day.value += 1
    }

    fun goToToday() {
        _day.value = today()
    }

    // Sets (or changes) the opening cash of the day being shown. 0 is a valid amount.
    fun setOpeningCash(amount: Long) {
        val day = _day.value
        viewModelScope.launch { dailyCashDao.upsert(DailyCash(day = day, openingCash = amount)) }
    }

    // Saves an expense on the day being shown. Does nothing if the input isn't valid
    // (the form keeps Save disabled in that case, so this is only a second guard).
    fun addExpense(categoryId: Long, name: String, unitAmount: Long, quantity: Int, pool: ExpensePool) {
        val expense = buildExpense(categoryId, name, unitAmount, quantity, pool, _day.value) ?: return
        viewModelScope.launch { expenseDao.insert(expense) }
    }

    // Expenses are never deleted: a wrong one is cancelled, stays in the list struck through, and is
    // not counted in any sum.
    fun cancelExpense(expense: Expense) {
        viewModelScope.launch { expenseDao.update(expense.copy(cancelled = true)) }
    }
}
