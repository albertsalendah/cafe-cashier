package com.albertsalendah.cafecashier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albertsalendah.cafecashier.ui.navigation.AppNav
import com.albertsalendah.cafecashier.ui.theme.CafeCashierTheme
import kotlinx.coroutines.flow.map

// The accent colour once the settings have been read (the colour itself may still be null = none).
private data class AccentState(val argb: Int?)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsDao = (application as CafeCashierApp).database.settingsDao()
        setContent {
            // Wait for the first read of the settings so the app doesn't flash the wrong colours.
            // A saved accent colour applies at once, everywhere.
            val accent by remember { settingsDao.observe().map { AccentState(it?.accentColor) } }
                .collectAsStateWithLifecycle(initialValue = null)
            accent?.let { state ->
                CafeCashierTheme(accentColor = state.argb) {
                    AppNav()
                }
            }
        }
    }
}
