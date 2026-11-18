package com.albertsalendah.cafecashier.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.MenuSeeder
import com.albertsalendah.cafecashier.data.db.MenuSeedDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// One-shot messages for the screen to show once (a snackbar), then clear with messageShown().
enum class MenuHomeMessage { ImportDone, ImportFailed }

class MenuHomeViewModel(
    menuSeedDao: MenuSeedDao,
    private val seeder: MenuSeeder
) : ViewModel() {

    // The import row shows only while the menu is completely empty (hidden items count too), so
    // it can't be used twice. false until the first count arrives, so the row doesn't flash.
    val canImport: StateFlow<Boolean> = combine(
        menuSeedDao.observeCategoryCount(),
        menuSeedDao.observeProductCount()
    ) { categories, products -> categories == 0 && products == 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _message = MutableStateFlow<MenuHomeMessage?>(null)
    val message: StateFlow<MenuHomeMessage?> = _message.asStateFlow()

    fun importOldMenu() {
        viewModelScope.launch {
            try {
                // false = the menu got items in the meantime; the row disappears by itself.
                if (seeder.importOldMenu()) _message.value = MenuHomeMessage.ImportDone
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = MenuHomeMessage.ImportFailed
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
