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
package com.vitorpamplona.amethyst.commons.ui.playback

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * True when the inline-feed audio player should be sized as a square.
 *
 * Audio is detected two ways: the imeta [isAudioMime] (known up front, no resize) and the runtime
 * [tracksAreAudio] (covers bare-URL audio once tracks resolve). Voice notes keep their seek-bar strip,
 * and the full-screen dialog fills the screen, so both opt out.
 */
fun shouldSquareAudioPlayer(
    isFullscreen: Boolean,
    isVoiceNote: Boolean,
    isAudioMime: Boolean,
    tracksAreAudio: Boolean,
): Boolean = !isFullscreen && !isVoiceNote && (isAudioMime || tracksAreAudio)

/**
 * Maximum height of the square. The player is always full width; below this height it is a true
 * square (1:1), and above it the height is capped (full width, shorter than square).
 */
internal val AUDIO_SQUARE_MAX_HEIGHT: Dp = 400.dp

/** Square side in px: the full width, but never taller than the cap. */
fun audioSquareSide(
    widthPx: Int,
    maxHeightPx: Int,
): Int = minOf(widthPx, maxHeightPx)

/**
 * Sizes the player as a square: full width, height = min(width, [maxHeight]). Only meaningful with a
 * bounded incoming width (the feed cell); with an unbounded width it measures normally and does
 * nothing, so it is safe to leave in the chain unconditionally if ever needed.
 */
fun Modifier.audioSquare(maxHeight: Dp = AUDIO_SQUARE_MAX_HEIGHT): Modifier =
    layout { measurable, constraints ->
        val width = constraints.maxWidth
        // Unbounded width, or first layout pass before the cell has a width: measure normally and let
        // the next pass impose the square once a real width arrives.
        if (!constraints.hasBoundedWidth || width == 0) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }
        val side = audioSquareSide(width, maxHeight.roundToPx())
        val placeable = measurable.measure(Constraints.fixed(width, side))
        layout(width, side) { placeable.placeRelative(0, 0) }
    }
