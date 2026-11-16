package com.albertsalendah.cafecashier.data

import kotlin.math.roundToInt

// Product photos are stored with their longest side at most this many pixels.
const val MAX_IMAGE_SIDE = 1024

// Power-of-two shrink factor for decoding: as large as possible while the decoded image still has
// its longest side at least [maxSide]. Decoding a 12 MP photo at 1/4 size avoids ever holding the
// full bitmap in memory.
fun sampleSizeFor(width: Int, height: Int, maxSide: Int = MAX_IMAGE_SIDE): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
    return sample
}

// The size to store: the same proportions, longest side at most [maxSide], never below 1 px.
// Images that already fit are left as they are (never enlarged).
fun fitWithin(width: Int, height: Int, maxSide: Int = MAX_IMAGE_SIDE): Pair<Int, Int> {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return width to height
    val scale = maxSide.toFloat() / longest
    return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
}
