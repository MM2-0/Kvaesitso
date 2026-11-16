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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.drawing.DrawingBackground
import com.vitorpamplona.amethyst.commons.drawing.DrawingEditorState
import com.vitorpamplona.amethyst.commons.drawing.DrawingStroke
import com.vitorpamplona.amethyst.commons.drawing.PaperPattern
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drawing_empty_hint
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlin.math.roundToInt

/**
 * The paper, sized to the largest rectangle of its aspect that fits, taking
 * one finger, mouse or stylus at a time.
 *
 * Finished strokes are painted once into a bitmap that only grows as strokes are
 * added, and the stroke under the finger is drawn live on top of it, so a long
 * drawing does not redraw every old stroke on every move.
 */
@Composable
fun DrawingCanvas(
    state: DrawingEditorState,
    modifier: Modifier = Modifier,
    painter: BackgroundPainter = remember(state.background) { BackgroundPainter(state.background) },
) {
    val background = painter.background
    val layer = remember { FinishedStrokesLayer() }

    PaperFrame(background, modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state) { trackStrokes(state) },
        ) {
            val finished = layer.update(this, state.strokes)
            // Read so that every new point redraws.
            state.activeVersion
            val active = state.active

            with(painter) { drawBase() }
            inStrokeLayer {
                drawImage(finished)
                active?.let { drawStroke(it) }
            }
            with(painter) { drawOverlay() }
        }

        if (state.isEmpty) {
            val paper = (background as? DrawingBackground.Paper)?.paper
            Text(
                text = stringRes(Res.string.drawing_empty_hint),
                // Over a photo nothing guarantees contrast, so the hint gets a chip of its own.
                modifier =
                    if (paper == null) {
                        Modifier.align(Alignment.Center).background(HINT_CHIP, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp)
                    } else {
                        Modifier.align(Alignment.Center)
                    },
                color = if (paper == null || paper.isDark) HINT_ON_DARK else HINT_COLOR,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The paper's frame: the largest rectangle of the background's shape that fits,
 * with rounded corners, or a sharp-cornered sticky note casting a deeper shadow.
 */
@Composable
fun PaperFrame(
    background: DrawingBackground,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val aspect = background.aspect
        val width = if (maxWidth * aspect <= maxHeight) maxWidth else maxHeight / aspect
        val isSticky = (background as? DrawingBackground.Paper)?.paper?.pattern == PaperPattern.GLUE_STRIP
        val shape = if (isSticky) RoundedCornerShape(3.dp) else RoundedCornerShape(16.dp)
        Box(
            Modifier
                .size(width, width * aspect)
                .shadow(if (isSticky) 14.dp else 6.dp, shape)
                .clip(shape),
            content = content,
        )
    }
}

private val HINT_COLOR = Color(0x66000000)
private val HINT_ON_DARK = Color(0xB3FFFFFF)
private val HINT_CHIP = Color(0x80000000)

/** One pointer from down to up becomes one stroke, in coordinates divided by the canvas width. */
private suspend fun PointerInputScope.trackStrokes(state: DrawingEditorState) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val unit = size.width.toFloat()
        state.begin(down.position.x / unit, down.position.y / unit, down.uptimeMillis, pressureOf(down))
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                // Points the platform batched between frames, so a fast stroke stays smooth.
                change.historical.forEach {
                    state.extend(it.position.x / unit, it.position.y / unit, it.uptimeMillis, pressureOf(change))
                }
                state.extend(change.position.x / unit, change.position.y / unit, change.uptimeMillis, pressureOf(change))
                change.consume()
                if (!change.pressed) break
            }
        } finally {
            state.end()
        }
    }
}

/** Only a stylus reports pressure worth following; a finger's is contact area and a mouse's is fixed. */
private fun pressureOf(change: PointerInputChange): Float = if (change.type == PointerType.Stylus && change.pressure > 0f) change.pressure else DrawingStroke.NO_PRESSURE

/**
 * Every finished stroke, on a transparent bitmap so the background shows
 * through and an eraser stroke can clear pixels in it. Adding a stroke paints
 * just that stroke onto it; undo or clear wipes and repaints it, a new size starts a new one.
 * Strokes only change by being appended or removed from the end, so checking
 * the last one painted is enough to know which case this is.
 */
private class FinishedStrokesLayer {
    private var bitmap: ImageBitmap? = null
    private var painted: DrawingStroke? = null
    private var paintedCount = 0
    private val scope = CanvasDrawScope()

    fun update(
        drawScope: DrawScope,
        strokes: List<DrawingStroke>,
    ): ImageBitmap {
        val w =
            drawScope.size.width
                .roundToInt()
                .coerceAtLeast(1)
        val h =
            drawScope.size.height
                .roundToInt()
                .coerceAtLeast(1)
        var image = bitmap
        if (image == null || image.width != w || image.height != h) {
            image = ImageBitmap(w, h)
            bitmap = image
            paintedCount = 0
        } else if (paintedCount > strokes.size || (paintedCount > 0 && strokes[paintedCount - 1] !== painted)) {
            // Undo or clear: wipe the bitmap rather than allocate a new one on every tap.
            draw(drawScope, image) { drawRect(Color.Black, blendMode = BlendMode.Clear) }
            paintedCount = 0
        }

        if (paintedCount < strokes.size) {
            val from = paintedCount
            draw(drawScope, image) {
                for (i in from until strokes.size) drawStroke(strokes[i])
            }
            paintedCount = strokes.size
        }
        painted = strokes.lastOrNull()
        return image
    }

    private fun draw(
        density: DrawScope,
        image: ImageBitmap,
        block: DrawScope.() -> Unit,
    ) {
        scope.draw(
            Density(density.density, density.fontScale),
            density.layoutDirection,
            Canvas(image),
            Size(image.width.toFloat(), image.height.toFloat()),
            block,
        )
    }
}
