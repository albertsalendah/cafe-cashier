package com.albertsalendah.cafecashier.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.albertsalendah.cafecashier.ui.groupThousands
import com.albertsalendah.cafecashier.ui.thousandsSeparatorsBefore

// Shows "12500" as "12.500" while typing. Only the display changes: the text the ViewModel
// holds stays plain digits, so its digits-only and length rules don't change.
object ThousandsVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = groupThousands(digits)

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + thousandsSeparatorsBefore(digits.length, offset)

            // The cursor position in the plain digits = how many digits sit before it.
            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset).count { it != '.' }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
