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
package com.vitorpamplona.amethyst.commons.ui.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Two panes side by side, [first] taking [splitFraction] of the width. On Android the split
 * follows a vertical fold or hinge when the device has one (Accompanist's fold-aware TwoPane);
 * elsewhere it is a plain proportional split. With [onDividerDrag], the line between them is a
 * [PaneSplitter] the user drags (in dp, positive to the right), except along a fold.
 */
@Composable
expect fun HorizontalTwoPane(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    splitFraction: Float,
    modifier: Modifier = Modifier,
    onDividerDrag: ((deltaDp: Float) -> Unit)? = null,
)

/** The plain proportional split, with a [PaneSplitter] between the panes when they can be resized. */
@Composable
internal fun ProportionalTwoPane(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    splitFraction: Float,
    modifier: Modifier,
    onDividerDrag: ((deltaDp: Float) -> Unit)?,
) {
    Row(modifier) {
        Box(Modifier.weight(splitFraction).fillMaxHeight()) { first() }
        if (onDividerDrag != null) PaneSplitter(onDrag = onDividerDrag)
        Box(Modifier.weight(1f - splitFraction).fillMaxHeight()) { second() }
    }
}
