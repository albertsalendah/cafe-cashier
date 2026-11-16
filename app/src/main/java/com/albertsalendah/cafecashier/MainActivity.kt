package com.albertsalendah.cafecashier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.albertsalendah.cafecashier.ui.navigation.AppNav
import com.albertsalendah.cafecashier.ui.theme.CafeCashierTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CafeCashierTheme {
                AppNav()
            }
        }
    }
}
