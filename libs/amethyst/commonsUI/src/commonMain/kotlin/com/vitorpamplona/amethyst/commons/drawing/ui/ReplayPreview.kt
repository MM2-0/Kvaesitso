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
package com.vitorpamplona.amethyst.commons.drawing.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import com.vitorpamplona.amethyst.commons.drawing.DrawingBackground
import com.vitorpamplona.amethyst.commons.drawing.DrawingReplay
import com.vitorpamplona.amethyst.commons.drawing.DrawingStroke
import com.vitorpamplona.amethyst.commons.drawing.PaperPattern

/**
 * The drawing replaying itself, frame for frame as the posted GIF will
 * ([DrawingReplay.replayTimeAt]), looping, drawn live rather than from the
 * encoded file so it appears the moment the user taps Next.
 */
@Composable
fun ReplayPreview(
    background: DrawingBackground,
    strokes: List<DrawingStroke>,
    modifier: Modifier = Modifier,
    painter: BackgroundPainter = remember(background) { BackgroundPainter(background) },
) {
    val replay = remember(strokes) { DrawingReplay(strokes) }
    val renderer = remember(painter, strokes, replay) { ReplayFrameRenderer(painter, strokes, replay) }
    val loopMs = replay.durationMs.toInt().coerceAtLeast(1)
    val playback =
        rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = loopMs.toFloat(),
            animationSpec = infiniteRepeatable(tween(loopMs, easing = LinearEasing), RepeatMode.Restart),
        )
    // The replay only moves every FRAME_MS and stands still through the lead-in and the hold:
    // the canvas redraws when the frame changes, not on every display refresh.
    val frameTime = remember(replay, playback) { derivedStateOf { replay.replayTimeAt(playback.value.toLong()) } }

    // A sticky note is shown stuck up slightly askew, the way it will look in a feed.
    val sticky = (background as? DrawingBackground.Paper)?.paper?.pattern == PaperPattern.GLUE_STRIP
    PaperFrame(background, modifier.rotate(if (sticky) STICKY_TILT else 0f)) {
        Canvas(Modifier.fillMaxSize()) {
            // Read here, in the draw phase: each animation frame redraws without recomposing.
            with(renderer) { drawFrame(frameTime.value) }
        }
    }
}

private const val STICKY_TILT = -2f
