package com.albertsalendah.cafecashier.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.data.db.SaleDao
import com.albertsalendah.cafecashier.domain.HistorySummary
import com.albertsalendah.cafecashier.domain.dayRangeMillis
import com.albertsalendah.cafecashier.domain.summarizeHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

// One day of the sales history, newest first.
data class SalesUiState(
    val day: Long,
    val sales: List<Sale>,
    val summary: HistorySummary
)

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModel(
    saleDao: SaleDao,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    // The day being looked at (epoch day, café time zone). Starts on today.
    private val _day = MutableStateFlow(LocalDate.now(clock).toEpochDay())
    val day: StateFlow<Long> = _day.asStateFlow()

    // Live: a new payment or a void updates the list. null = still loading. After a day change this
    // briefly still holds the old day; the screen compares state.day with day (same as the Cash tab).
    val state: StateFlow<SalesUiState?> = _day.flatMapLatest { day ->
        val range = dayRangeMillis(day, clock.zone)
        saleDao.observeHistoryBetween(range.fromMillis, range.toMillis).map { sales ->
            SalesUiState(day, sales, summarizeHistory(sales))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
}
