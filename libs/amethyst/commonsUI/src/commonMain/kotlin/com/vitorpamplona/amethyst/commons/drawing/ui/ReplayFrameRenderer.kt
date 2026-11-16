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

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.vitorpamplona.amethyst.commons.drawing.DrawingReplay
import com.vitorpamplona.amethyst.commons.drawing.DrawingStroke
import kotlin.math.roundToInt

/**
 * Draws replay frames for [replay] in constant time per frame, however long the
 * drawing is: what [drawDrawing] draws, without redrawing every stroke each time.
 *
 * Replay times only grow from one stroke to the next, so at any moment every
 * stroke is either done, not started, or (just one of them) half drawn. Done
 * strokes are painted once into a transparent layer that only grows while time
 * moves forward; each frame is then the background (also painted once, at this
 * size, so a photo is not rescaled every frame), that layer, the half-drawn
 * stroke on top of it in the same layer (so an eraser still clears the done
 * strokes, never the background) and the background's overlay.
 *
 * Going back in time (a looping preview restarting) starts the layer over.
 */
class ReplayFrameRenderer(
    private val painter: BackgroundPainter,
    private val strokes: List<DrawingStroke>,
    private val replay: DrawingReplay,
) {
    private val scope = CanvasDrawScope()
    private var base: ImageBitmap? = null
    private var done: ImageBitmap? = null
    private var doneCount = 0
    private var lastTimeMs = Long.MIN_VALUE

    fun DrawScope.drawFrame(timeMs: Long) {
        val w = size.width.roundToInt().coerceAtLeast(1)
        val h = size.height.roundToInt().coerceAtLeast(1)
        val density = Density(density, fontScale)

        val background =
            base?.takeIf { it.width == w && it.height == h }
                ?: ImageBitmap(w, h).also { image ->
                    paint(image, density) { with(painter) { drawBase() } }
                    base = image
                    done = null
                }

        var layer = done
        if (layer == null || timeMs < lastTimeMs) {
            layer = layer?.also { paint(it, density) { drawRect(Color.Black, blendMode = BlendMode.Clear) } } ?: ImageBitmap(w, h)
            done = layer
            doneCount = 0
        }
        lastTimeMs = timeMs

        // Paint every stroke that has finished by now into the done layer, in order.
        val from = doneCount
        while (doneCount < strokes.size && replay.visiblePoints(doneCount, timeMs) >= strokes[doneCount].size) doneCount++
        if (doneCount > from) {
            paint(layer, density) { for (i in from until doneCount) drawStroke(strokes[i]) }
        }

        drawImage(background)
        inStrokeLayer {
            drawImage(layer)
            if (doneCount < strokes.size) {
                val partial = replay.visiblePoints(doneCount, timeMs)
                if (partial > 0) drawStroke(strokes[doneCount], partial)
            }
        }
        with(painter) { drawOverlay() }
    }

    private fun paint(
        image: ImageBitmap,
        density: Density,
        block: DrawScope.() -> Unit,
    ) {
        scope.draw(density, LayoutDirection.Ltr, Canvas(image), Size(image.width.toFloat(), image.height.toFloat()), block)
    }
}
