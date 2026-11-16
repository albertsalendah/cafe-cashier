package com.albertsalendah.cafecashier.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import kotlin.math.abs

// A product's picture as a rounded square, or, when there is no photo (or its file is missing or
// unreadable), a colored tile with the product's first letter. The tile sits underneath the photo,
// so it is also what shows while the photo loads. The caller sets the size.
@Composable
fun ProductImage(
    imageFile: File?,
    name: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tileColor(name)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = Color.Black.copy(alpha = 0.7f)
        )
        if (imageFile != null) {
            AsyncImage(
                model = imageFile,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// The same name always gets the same pastel color, so a product keeps its look in the list.
private fun tileColor(name: String): Color =
    Color.hsv(hue = (abs(name.trim().lowercase().hashCode()) % 360).toFloat(), saturation = 0.30f, value = 0.92f)
