package com.albertsalendah.cafecashier.data

import com.albertsalendah.cafecashier.data.db.MenuSeedDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// One-time import of the old menu. [readJson] returns the text of assets/seed_menu.json
// (passed in so this class needs no Android Context).
class MenuSeeder(
    private val dao: MenuSeedDao,
    private val readJson: () -> String
) {
    // true = imported. false = the menu was not empty, nothing changed.
    // Throws if the file is unreadable or the database write fails (nothing is kept then).
    suspend fun importOldMenu(): Boolean = withContext(Dispatchers.IO) {
        dao.importMenu(parseSeedMenu(readJson()).toEntries())
    }
}
