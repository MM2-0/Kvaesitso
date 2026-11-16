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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.vitorpamplona.amethyst.commons.drawing.DrawingBackground
import com.vitorpamplona.amethyst.commons.drawing.DrawingEditorState
import com.vitorpamplona.amethyst.commons.drawing.DrawingPaper
import com.vitorpamplona.amethyst.commons.drawing.DrawingReplay
import com.vitorpamplona.amethyst.commons.drawing.DrawingTool
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The incremental frame renderer must draw what [drawDrawing] draws at every
 * moment of the replay, including after looping back to the start, with an
 * eraser, spray, mirroring and a background with an overlay in play.
 */
class ReplayFrameRendererTest {
    private val w = 270
    private val h = 270

    private fun drawing(): DrawingEditorState {
        val state = DrawingEditorState(DrawingBackground.Paper(DrawingPaper.STICKY_MINT))
        var t = 0L
        state.width = 0.04f
        state.begin(0.1f, 0.5f, t)
        for (i in 1..40) state.extend(0.1f + i * 0.02f, 0.5f + 0.1f * sin(i / 4f), t + i * 20L)
        state.end()
        t += 1_000
        state.tool = DrawingTool.SPRAY
        state.mirror = true
        state.color = 0xFFE5484D.toInt()
        state.begin(0.2f, 0.2f, t)
        for (i in 1..15) state.extend(0.2f + i * 0.01f, 0.2f, t + i * 20L)
        state.end()
        t += 600
        state.mirror = false
        state.tool = DrawingTool.ERASER
        state.begin(0.5f, 0.3f, t)
        for (i in 1..10) state.extend(0.5f, 0.3f + i * 0.04f, t + i * 30L)
        state.end()
        return state
    }

    private fun pixels(block: DrawScope.() -> Unit): IntArray {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat()), block)
        return IntArray(w * h).also { image.readPixels(it) }
    }

    @Test
    fun drawsTheSameFramesAsTheFullRenderer() {
        val state = drawing()
        val strokes = state.strokes.toList()
        val replay = DrawingReplay(strokes)
        val painter = BackgroundPainter(state.background)
        val renderer = ReplayFrameRenderer(painter, strokes, replay)

        // Forward through every frame, then the finished drawing, then back to the start, like the exporter.
        val times = replay.frameTimes.toList() + Long.MAX_VALUE + replay.frameTimes.take(5)
        val image = ImageBitmap(w, h)
        val scope = CanvasDrawScope()
        for (t in times) {
            scope.draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) { with(renderer) { drawFrame(t) } }
            val fast = IntArray(w * h).also { image.readPixels(it) }
            val full = pixels { drawDrawing(painter, strokes, replay, t) }
            var worst = 0
            for (p in fast.indices) {
                for (shift in intArrayOf(16, 8, 0)) {
                    worst = maxOf(worst, abs(((fast[p] shr shift) and 0xFF) - ((full[p] shr shift) and 0xFF)))
                }
            }
            assertTrue(worst <= 3, "frame at $t ms differs by up to $worst levels per channel")
        }
    }
}
