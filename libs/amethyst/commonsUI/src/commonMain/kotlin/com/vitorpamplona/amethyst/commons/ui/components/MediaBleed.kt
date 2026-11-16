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

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How far a note's own media may reach past each side of its column. The edge-to-edge note style
 * sets it to the note's side padding, so images and videos run to the screen edges while the text
 * keeps its margin. It is 0 everywhere else: the other layouts, quotes, cards, and every kind that
 * does not hand it to its body through [ProvideMediaBleed].
 */
val LocalMediaBleed = compositionLocalOf { 0.dp }

/** Provides [bleed] to [content], adding no provider when it already holds that value (most notes). */
@Composable
fun ProvideMediaBleed(
    bleed: Dp,
    content: @Composable () -> Unit,
) {
    if (LocalMediaBleed.current == bleed) {
        content()
    } else {
        CompositionLocalProvider(LocalMediaBleed provides bleed, content = content)
    }
}

/**
 * Media that runs past its column by [LocalMediaBleed] on each side. [content] gets whether the
 * media should round its corners: it should not when it touches the screen edges. [enabled] keeps
 * media that draws as a card (a PDF preview) in the column with the other cards.
 */
@Composable
fun BleedingMedia(
    modifier: Modifier = Modifier,
    roundedCorner: Boolean = true,
    enabled: Boolean = true,
    content: @Composable (roundedCorner: Boolean) -> Unit,
) {
    val bleed = if (enabled) LocalMediaBleed.current else 0.dp
    if (bleed == 0.dp) {
        // The layouts without bleed, most users' feeds, get no extra layout node.
        if (modifier === Modifier) {
            ProvideMediaBleed(0.dp) { content(roundedCorner) }
        } else {
            Box(modifier) { ProvideMediaBleed(0.dp) { content(roundedCorner) } }
        }
    } else {
        Box(modifier.bleedHorizontally(bleed)) {
            // Nothing inside bleeds again: a gallery cell or a card in the media stays in place.
            ProvideMediaBleed(0.dp) { content(false) }
        }
    }
}

/**
 * Lets the content grow [bleed] past each side of the column, centered over it. The content keeps
 * the slot it fills in the column, so media narrower than the column takes no more room than it draws.
 */
fun Modifier.bleedHorizontally(bleed: Dp): Modifier =
    if (bleed == 0.dp) {
        this
    } else {
        bleedHorizontallyBy(bleed)
    }

private fun Modifier.bleedHorizontallyBy(bleed: Dp): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }

        val extra = bleed.roundToPx() * 2
        val placeable =
            measurable.measure(
                constraints.copy(
                    minWidth = constraints.minWidth + extra,
                    maxWidth = constraints.maxWidth + extra,
                ),
            )
        val width = placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, placeable.height) {
            placeable.placeRelative((width - placeable.width) / 2, 0)
        }
    }
