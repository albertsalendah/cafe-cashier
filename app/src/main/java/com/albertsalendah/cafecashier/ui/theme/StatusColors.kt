package com.albertsalendah.cafecashier.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Status colors that Material 3's color scheme doesn't have. Each pair is chosen to stay readable
// (about 4.5:1 or better) on a light and on a dark surface. Red reuses colorScheme.error.
// Color is never the only signal: every status also has its own words.
object StatusColors {
    val green: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

    val orange: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFB85600)
}
