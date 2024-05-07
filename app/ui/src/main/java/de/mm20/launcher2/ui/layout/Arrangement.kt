package de.mm20.launcher2.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Density

@Stable
val Arrangement.BottomReversed: Arrangement.Vertical
    get() = object : Arrangement.Vertical {
        override fun Density.arrange(
            totalSize: Int,
            sizes: IntArray,
            outPositions: IntArray
        ) = placeRightOrBottom(totalSize, sizes, outPositions, reverseInput = true)

        override fun toString() = "Arrangement#BottomReversed"
    }

@Stable
val Arrangement.TopReversed: Arrangement.Vertical
    get() = object : Arrangement.Vertical {
        override fun Density.arrange(
            totalSize: Int,
            sizes: IntArray,
            outPositions: IntArray
        ) = placeLeftOrTop(sizes, outPositions, reverseInput = true)

        override fun toString() = "Arrangement#TopReversed"
    }

internal fun placeRightOrBottom(
    totalSize: Int,
    size: IntArray,
    outPosition: IntArray,
    reverseInput: Boolean
) {
    val consumedSize = size.fold(0) { a, b -> a + b }
    var current = totalSize - consumedSize
    size.forEachIndexed(reverseInput) { index, it ->
        outPosition[index] = current
        current += it
    }
}

internal fun placeLeftOrTop(size: IntArray, outPosition: IntArray, reverseInput: Boolean) {
    var current = 0
    size.forEachIndexed(reverseInput) { index, it ->
        outPosition[index] = current
        current += it
    }
}

private inline fun IntArray.forEachIndexed(reversed: Boolean, action: (Int, Int) -> Unit) {
    if (!reversed) {
        forEachIndexed(action)
    } else {
        for (i in (size - 1) downTo 0) {
            action(i, get(i))
        }
    }
}