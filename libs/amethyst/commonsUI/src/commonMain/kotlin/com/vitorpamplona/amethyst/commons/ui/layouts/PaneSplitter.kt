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

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness

/** How wide a strip around the line the pointer can grab. */
private val GrabWidth = 9.dp

/**
 * The line between two side-by-side panes, which the user drags sideways to resize them. It takes
 * the layout width of a plain divider, so the panes sit where they did, but it can be grabbed a few
 * dp to either side, over the panes' edges. [onDrag] gets each move in dp, positive to the right.
 * The line thickens and takes the accent color while hovered or dragged.
 */
@Composable
fun PaneSplitter(
    onDrag: (deltaDp: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging

    Box(
        modifier
            .fillMaxHeight()
            // Over the neighbouring panes, so the strip past the line still reaches the splitter.
            .zIndex(1f)
            .layout { measurable, constraints ->
                val line = DividerThickness.roundToPx()
                val grab = GrabWidth.roundToPx()
                val placeable = measurable.measure(constraints.copy(minWidth = grab, maxWidth = grab))
                layout(line, placeable.height) { placeable.place((line - grab) / 2, 0) }
            }.horizontalResizeCursor()
            .hoverable(interaction)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, dragAmount ->
                    change.consume()
                    currentOnDrag(dragAmount.toDp().value)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            VerticalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
        } else {
            VerticalDivider(thickness = DividerThickness)
        }
    }
}

/** The platform's sideways-resize pointer, where there is a pointer. */
internal expect fun Modifier.horizontalResizeCursor(): Modifier
