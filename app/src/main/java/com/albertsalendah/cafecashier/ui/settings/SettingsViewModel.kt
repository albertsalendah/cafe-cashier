package com.albertsalendah.cafecashier.ui.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertsalendah.cafecashier.data.ProductImageStore
import com.albertsalendah.cafecashier.data.db.Settings
import com.albertsalendah.cafecashier.data.db.SettingsDao
import com.albertsalendah.cafecashier.domain.parseRoundingStep
import com.albertsalendah.cafecashier.domain.parseTaxPercent
import com.albertsalendah.cafecashier.ui.cleanAmountDigits
import java.io.File
import kotlinx.coroutines.launch

private const val MAX_NAME_LENGTH = 60
private const val MAX_TEXT_LENGTH = 200

// The Settings form: nothing is saved until Save. Tax is optional: switched off = tax percent 0, and no
// tax line appears anywhere in the app. The logo file lives next to the product photos
// (ProductImageStore); a picture copied in during this edit is deleted again unless Save keeps it.
class SettingsViewModel(
    private val settingsDao: SettingsDao,
    private val imageStore: ProductImageStore
) : ViewModel() {

    // False until the saved settings have been read into the fields.
    var ready by mutableStateOf(false)
        private set

    var storeName by mutableStateOf("")
        private set
    var address by mutableStateOf("")
        private set
    var receiptFooter by mutableStateOf("")
        private set

    var taxEnabled by mutableStateOf(false)
        private set
    var taxText by mutableStateOf("")
        private set
    var roundingText by mutableStateOf("")
        private set

    var accentColor by mutableStateOf<Int?>(null)
        private set

    var logoName by mutableStateOf<String?>(null)
        private set
    var importing by mutableStateOf(false)
        private set
    var logoFailed by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set

    val logoFile: File? get() = logoName?.let(imageStore::fileFor)

    private var original = Settings()
    private val imported = mutableSetOf<String>()
    private var savedOk = false

    init {
        viewModelScope.launch {
            val s = settingsDao.get() ?: Settings()
            original = s
            storeName = s.storeName
            address = s.address
            receiptFooter = s.receiptFooter
            taxEnabled = s.taxPercent > 0
            taxText = if (s.taxPercent > 0) s.taxPercent.toString() else ""
            roundingText = s.roundingStep.toString()
            accentColor = s.accentColor
            logoName = s.logoPath
            ready = true
        }
    }

    // What Save would store, or null while a field is not valid yet.
    private val taxToSave: Int? get() = parseTaxPercent(taxEnabled, taxText)
    private val stepToSave: Long? get() = parseRoundingStep(roundingText)

    val taxInvalid: Boolean get() = taxEnabled && taxText.isNotEmpty() && taxToSave == null
    val roundingInvalid: Boolean get() = roundingText.isNotEmpty() && stepToSave == null

    val canSave: Boolean get() = ready && !saving && !importing && taxToSave != null && stepToSave != null

    // Something differs from what is saved.
    val isDirty: Boolean
        get() = ready && (
            storeName.trim() != original.storeName ||
                address.trim() != original.address ||
                receiptFooter.trim() != original.receiptFooter ||
                taxEnabled != (original.taxPercent > 0) ||
                (if (taxEnabled) taxText else "") != (if (original.taxPercent > 0) original.taxPercent.toString() else "") ||
                roundingText != original.roundingStep.toString() ||
                accentColor != original.accentColor ||
                logoName != original.logoPath
            )

    fun onStoreNameChange(value: String) {
        storeName = value.take(MAX_NAME_LENGTH)
    }

    fun onAddressChange(value: String) {
        address = value.take(MAX_TEXT_LENGTH)
    }

    fun onReceiptFooterChange(value: String) {
        receiptFooter = value.take(MAX_TEXT_LENGTH)
    }

    fun onTaxEnabledChange(enabled: Boolean) {
        taxEnabled = enabled
    }

    fun onTaxTextChange(value: String) {
        taxText = cleanAmountDigits(value, maxDigits = 3)
    }

    fun onRoundingChange(value: String) {
        roundingText = cleanAmountDigits(value, maxDigits = 6)
    }

    fun onAccentChange(argb: Int?) {
        accentColor = argb
    }

    // Copies the picked picture into app storage (downscaled). Nothing is permanent until Save.
    fun onLogoPicked(uri: Uri) {
        logoFailed = false
        importing = true
        viewModelScope.launch {
            val name = imageStore.import(uri)
            importing = false
            if (name == null) {
                logoFailed = true
                return@launch
            }
            dropUnsavedLogo() // replaces a logo picked earlier in this same edit
            imported += name
            logoName = name
        }
    }

    fun onLogoRemoved() {
        logoFailed = false
        dropUnsavedLogo()
        logoName = null
    }

    // Deletes the current logo only if it was copied in during this edit. The saved logo is kept
    // until Save, so leaving the screen without saving changes nothing.
    private fun dropUnsavedLogo() {
        val current = logoName
        if (current != null && current in imported) {
            imageStore.delete(current)
            imported -= current
        }
    }

    // Saves the form and calls [onDone]. The PIN fields of the stored row are left untouched.
    fun save(onDone: () -> Unit) {
        val tax = taxToSave ?: return
        val step = stepToSave ?: return
        if (!canSave) return
        saving = true
        viewModelScope.launch {
            val current = settingsDao.get() ?: Settings()
            settingsDao.upsert(
                current.copy(
                    storeName = storeName.trim(),
                    address = address.trim(),
                    receiptFooter = receiptFooter.trim(),
                    taxPercent = tax,
                    roundingStep = step,
                    logoPath = logoName,
                    accentColor = accentColor
                )
            )
            savedOk = true
            // The logo that was replaced or removed is no longer used, and neither is a picture
            // picked along the way that is not the final one.
            if (current.logoPath != logoName) imageStore.delete(current.logoPath)
            imported.filter { it != logoName }.forEach(imageStore::delete)
            saving = false
            onDone()
        }
    }

    // Left without saving (Cancel, Back): throw away pictures copied in during this edit.
    override fun onCleared() {
        if (!savedOk) imported.forEach(imageStore::delete)
    }
}
