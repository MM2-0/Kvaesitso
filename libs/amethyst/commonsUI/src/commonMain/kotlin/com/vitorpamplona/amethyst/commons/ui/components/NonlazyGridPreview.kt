/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import kotlin.math.ceil
import kotlin.math.sqrt

@Composable
fun GridPreviewTemplate(count: Int) {
    AutoNonlazyGrid(count) { idx ->
        Box(Modifier.background(Color.Green).fillMaxSize())
        Text(idx.toString())
    }
}

@Composable
@Preview(device = "spec:width=300px,height=300px,dpi=440")
fun Items1() = GridPreviewTemplate(1)

@Composable
@Preview(device = "spec:width=300px,height=300px,dpi=440")
fun Items2() = GridPreviewTemplate(2)

@Composable
@Preview(device = "spec:width=300px,height=300px,dpi=440")
fun Items3() = GridPreviewTemplate(3)

@Composable
@Preview(device = "spec:width=300px,height=300px,dpi=440")
fun Items4() = GridPreviewTemplate(4)

@Composable
@Preview(device = "spec:width=300px,height=300px,dpi=440")
fun Items5() = GridPreviewTemplate(5)

@Composable
@Preview(device = "spec:width=300px,height=300px,dpi=440")
fun Items6() = GridPreviewTemplate(6)

@Composable
@Preview(device = "spec:width=300px,height=300px,dpi=440")
fun Items7() = GridPreviewTemplate(7)

@Composable
@Preview(device = "spec:width=300px,height=300px,dpi=440")
fun Items8() = GridPreviewTemplate(8)

@Composable
@Preview(device = "spec:width=300px,height=300px,dpi=440")
fun Items9() = GridPreviewTemplate(9)

@Composable
@Preview(device = "spec:width=300px,height=300px,dpi=440")
fun Items10() = GridPreviewTemplate(10)

/**
 * A square-ish grid of [itemCount] cells. Its cells share one [ZoomSourceGroup]: these grids hold
 * a post's pictures, and the viewer opened from one closes into whichever one it ends on.
 */
@Composable
fun AutoNonlazyGrid(
    itemCount: Int,
    modifier: Modifier = Modifier.aspectRatio(1f),
    content: @Composable (Int) -> Unit,
) {
    if (itemCount > 0) {
        ProvideZoomSourceGroup {
            NonlazyGrid(ceil(sqrt(itemCount.toDouble())).toInt(), itemCount, modifier, content)
        }
    }
}

@Composable
fun NonlazyGrid(
    columns: Int,
    itemCount: Int,
    modifier: Modifier = Modifier.aspectRatio(1f),
    content: @Composable (Int) -> Unit,
) {
    val skipItems = ((columns * columns) - itemCount)
    val skipInEveryRow = (skipItems / columns)
    val shouldSkip = skipItems % columns
    var addSkips = shouldSkip

    val newColumns = columns - skipInEveryRow

    Column(modifier = modifier, verticalArrangement = spacedBy(Size5dp)) {
        val rows = ceil(itemCount.toDouble() / newColumns).toInt()

        for (rowId in 0 until rows) {
            val firstIndex = if (rowId == 0) 0 else (rowId * newColumns) - (shouldSkip - addSkips)

            Row(Modifier.weight(1f), horizontalArrangement = spacedBy(Size5dp)) {
                val thisRowColumns =
                    if (addSkips > 0) {
                        addSkips--
                        newColumns - 1
                    } else {
                        newColumns
                    }

                for (columnId in 0 until thisRowColumns) {
                    val index = firstIndex + columnId
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (index < itemCount) {
                            content(index)
                        }
                    }
                }
            }
        }
    }
}
