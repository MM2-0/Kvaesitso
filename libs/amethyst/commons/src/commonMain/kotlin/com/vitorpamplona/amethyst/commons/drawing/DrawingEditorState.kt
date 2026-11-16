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
package com.vitorpamplona.amethyst.commons.drawing

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Everything the hand-drawing editor holds: the background, the pen settings, the
 * finished strokes with their undo/redo history, and the stroke being drawn.
 *
 * Headless on purpose: the screen feeds it pointer positions already divided by
 * the canvas width (see [DrawingStroke]), and the GIF and PNG are rendered from
 * [strokes] afterwards, so nothing here knows about pixels.
 */
@Stable
class DrawingEditorState(
    initialBackground: DrawingBackground = DrawingBackground.Paper(DrawingPaper.PLAIN),
) {
    var background by mutableStateOf(initialBackground)
        private set
    var tool by mutableStateOf(DrawingTool.BRUSH)
    var color by mutableIntStateOf(DEFAULT_COLOR)

    /** Pen width as a fraction of the canvas width. */
    var width by mutableFloatStateOf(DEFAULT_WIDTH)
    var mirror by mutableStateOf(false)

    /** Finished strokes, oldest first. */
    val strokes = mutableStateListOf<DrawingStroke>()
    private val undone = mutableStateListOf<DrawingStroke>()

    /** The stroke under the finger, not yet in [strokes]. */
    var active: DrawingStroke? by mutableStateOf(null)
        private set

    /** Bumps on every point added to [active], so a canvas reading it redraws. */
    var activeVersion by mutableIntStateOf(0)
        private set

    private var nextSeed = 1

    val isEmpty: Boolean get() = strokes.isEmpty() && active == null
    val canUndo: Boolean get() = strokes.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()

    fun begin(
        x: Float,
        y: Float,
        timeMs: Long,
        pressure: Float = DrawingStroke.NO_PRESSURE,
    ) {
        val stroke = DrawingStroke(tool, color, width, mirror, nextSeed++)
        stroke.add(x, y, timeMs, pressure)
        active = stroke
        activeVersion++
    }

    fun extend(
        x: Float,
        y: Float,
        timeMs: Long,
        pressure: Float = DrawingStroke.NO_PRESSURE,
    ) {
        val stroke = active ?: return
        stroke.add(x, y, timeMs, pressure)
        activeVersion++
    }

    fun end() {
        val stroke = active ?: return
        active = null
        strokes.add(stroke)
        undone.clear()
    }

    fun cancel() {
        active = null
    }

    fun undo() {
        val last = strokes.removeLastOrNull() ?: return
        undone.add(last)
    }

    fun redo() {
        val last = undone.removeLastOrNull() ?: return
        strokes.add(last)
    }

    fun clear() {
        active = null
        strokes.clear()
        undone.clear()
    }

    /**
     * Swaps what the drawing sits on; the strokes stay. Black ink on a dark
     * board would vanish, so the default ink turns white there.
     */
    fun changeBackground(newBackground: DrawingBackground) {
        background = newBackground
        if (newBackground is DrawingBackground.Paper && newBackground.paper.isDark && color == DEFAULT_COLOR) {
            color = WHITE
        } else if (newBackground is DrawingBackground.Paper && !newBackground.paper.isDark && color == WHITE) {
            color = DEFAULT_COLOR
        }
    }

    /** Picking a colour while erasing means the user wants to draw again. */
    fun pickColor(argb: Int) {
        color = argb
        if (tool == DrawingTool.ERASER) tool = DrawingTool.BRUSH
    }

    companion object {
        const val DEFAULT_COLOR = 0xFF1D1B20.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val DEFAULT_WIDTH = 0.02f
        const val MIN_WIDTH = 0.004f
        const val MAX_WIDTH = 0.12f
    }
}
