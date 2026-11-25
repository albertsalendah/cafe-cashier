package com.albertsalendah.cafecashier.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.db.Expense
import com.albertsalendah.cafecashier.data.db.ExpenseCategoryDao
import com.albertsalendah.cafecashier.data.db.ExpenseDao
import com.albertsalendah.cafecashier.data.db.Sale
import com.albertsalendah.cafecashier.data.db.SaleDao
import com.albertsalendah.cafecashier.data.db.SaleItemDao
import com.albertsalendah.cafecashier.data.db.SettingsDao
import com.albertsalendah.cafecashier.domain.EpochDayRange
import com.albertsalendah.cafecashier.domain.RangePreset
import com.albertsalendah.cafecashier.domain.SalesReport
import com.albertsalendah.cafecashier.domain.buildReport
import com.albertsalendah.cafecashier.domain.dayRangeMillis
import com.albertsalendah.cafecashier.domain.presetRange
import com.albertsalendah.cafecashier.domain.withFrom
import com.albertsalendah.cafecashier.domain.withTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

// Everything the Reports screen shows and exports for one range. The raw lists are kept for the CSV files.
data class ReportUiState(
    val range: EpochDayRange,
    val report: SalesReport,
    val paidSales: List<Sale>,
    val voidedSales: List<Sale>,
    val expenses: List<Expense>,         // every expense of the range, cancelled ones included
    val categoryNames: Map<Long, String>,
    val storeName: String,
    val zone: ZoneId
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    saleDao: SaleDao,
    saleItemDao: SaleItemDao,
    expenseDao: ExpenseDao,
    categoryDao: ExpenseCategoryDao,
    settingsDao: SettingsDao,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _preset = MutableStateFlow(RangePreset.TODAY)
    val preset: StateFlow<RangePreset> = _preset.asStateFlow()

    private val _range = MutableStateFlow(presetRange(RangePreset.TODAY, today())!!)
    val range: StateFlow<EpochDayRange> = _range.asStateFlow()

    // Live: a new sale, a void or an expense updates the report. null = still loading. After a range
    // change this briefly still holds the old range; the screen compares state.range with range.
    val state: StateFlow<ReportUiState?> = _range.flatMapLatest { range ->
        val from = dayRangeMillis(range.fromDay, clock.zone).fromMillis
        val to = dayRangeMillis(range.toDay, clock.zone).toMillis
        val data = combine(
            saleDao.observePaidBetween(from, to),
            saleDao.observeVoidedBetween(from, to),
            saleItemDao.observePaidItemsBetween(from, to),
            expenseDao.observeBetween(range.fromDay, range.toDay),
            categoryDao.observeAll()
        ) { paid, voided, items, expenses, categories ->
            val names = categories.associate { it.id to it.name }
            ReportUiState(
                range = range,
                report = buildReport(range, paid, voided, items, expenses, names, clock.zone),
                paidSales = paid,
                voidedSales = voided,
                expenses = expenses,
                categoryNames = names,
                storeName = "",
                zone = clock.zone
            )
        }
        combine(data, settingsDao.observe()) { d, settings ->
            d.copy(storeName = settings?.storeName?.trim().orEmpty())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun today(): Long = LocalDate.now(clock).toEpochDay()

    // A ready-made range, or Custom (keeps the range as it is and lets the user move either end).
    fun selectPreset(preset: RangePreset) {
        _preset.value = preset
        presetRange(preset, today())?.let { _range.value = it }
    }

    fun setFrom(day: Long) {
        _preset.value = RangePreset.CUSTOM
        _range.value = _range.value.withFrom(day)
    }

    fun setTo(day: Long) {
        _preset.value = RangePreset.CUSTOM
        _range.value = _range.value.withTo(day)
    }
}
