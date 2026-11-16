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
package com.vitorpamplona.amethyst.commons.ui.thread

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Creates a zebra pattern where each bar represents a reply level in a thread.
 * Used to visually indicate nesting depth in thread conversations.
 *
 * @param level The current nesting level (0 = root, 1 = first reply, etc.)
 * @param color The color used for non-selected level bars
 * @param selected The color used for the current/selected level bar
 */
fun Modifier.drawReplyLevel(
    level: State<Int>,
    color: Color,
    selected: Color,
): Modifier =
    this
        .drawBehind {
            val paddingDp = 2
            val strokeWidthDp = 2
            val levelWidthDp = strokeWidthDp + 1

            val padding = paddingDp.dp.toPx()
            val strokeWidth = strokeWidthDp.dp.toPx()
            val levelWidth = levelWidthDp.dp.toPx()

            repeat(level.value) {
                this.drawLine(
                    if (it == level.value - 1) selected else color,
                    Offset(padding + it * levelWidth, 0f),
                    Offset(padding + it * levelWidth, size.height),
                    strokeWidth = strokeWidth,
                )
            }

            return@drawBehind
        }.padding(start = (2 + (level.value * 3)).dp)

/**
 * Overload for non-State level value.
 */
fun Modifier.drawReplyLevel(
    level: Int,
    color: Color,
    selected: Color,
): Modifier =
    this
        .drawBehind {
            val paddingDp = 2
            val strokeWidthDp = 2
            val levelWidthDp = strokeWidthDp + 1

            val padding = paddingDp.dp.toPx()
            val strokeWidth = strokeWidthDp.dp.toPx()
            val levelWidth = levelWidthDp.dp.toPx()

            repeat(level) {
                this.drawLine(
                    if (it == level - 1) selected else color,
                    Offset(padding + it * levelWidth, 0f),
                    Offset(padding + it * levelWidth, size.height),
                    strokeWidth = strokeWidth,
                )
            }

            return@drawBehind
        }.padding(start = (2 + (level * 3)).dp)
