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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp

/**
 * A mouse wheel or trackpad scroll over the empty gutters beside a centered column of at most
 * [columnMaxWidth] scrolls whatever is under the same height in the column, as a browser does
 * with a centered page. Compose only sends a wheel event to what is under the pointer, and the
 * gutters hold nothing that scrolls, so the event is sent again at the column's center.
 *
 * A scroll that lands in the column and that nothing there takes is left alone, so a re-sent
 * event can't come back here.
 */
@Composable
fun Modifier.gutterWheelScrollsColumn(columnMaxWidth: Dp): Modifier {
    val redispatch = rememberWheelRedispatcher()
    return pointerInput(columnMaxWidth, redispatch) {
        val columnWidth = columnMaxWidth.toPx()
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                if (change.isConsumed) continue
                val gutter = (size.width - columnWidth) / 2f
                if (gutter <= 0f) continue
                val x = change.position.x
                if (x >= gutter && x <= size.width - gutter) continue
                redispatch(event, size.width / 2f - x)
            }
        }
    }
}

/**
 * Sends a wheel [PointerEvent] again, moved sideways by a number of pixels, to the window it came
 * from. A platform without a wheel does nothing.
 */
@Composable
internal expect fun rememberWheelRedispatcher(): (event: PointerEvent, dxPx: Float) -> Unit
