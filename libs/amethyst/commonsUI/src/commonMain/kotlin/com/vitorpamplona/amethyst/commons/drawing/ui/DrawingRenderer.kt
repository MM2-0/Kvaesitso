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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntSize
import com.vitorpamplona.amethyst.commons.drawing.DrawingBackground
import com.vitorpamplona.amethyst.commons.drawing.DrawingReplay
import com.vitorpamplona.amethyst.commons.drawing.DrawingStroke
import com.vitorpamplona.amethyst.commons.drawing.DrawingTool
import com.vitorpamplona.amethyst.commons.drawing.PaperPattern
import com.vitorpamplona.amethyst.commons.service.image.argbPixelsToImageBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * One renderer for every place a drawing appears: the live canvas, the GIF's
 * frames and the PNG. Stroke coordinates are fractions of the canvas width, so
 * the same calls draw at any size. Everything here is deterministic, spray
 * included, so a frame of the replay and the live canvas show the same dots.
 *
 * Strokes are drawn on their own layer above the background, which is what lets
 * the eraser clear ink back down to whatever is underneath, a photo included.
 */

/** Background, then [strokes] (each cut to the points [replay] has reached by [timeMs], when given), then the background's overlay. */
fun DrawScope.drawDrawing(
    background: BackgroundPainter,
    strokes: List<DrawingStroke>,
    replay: DrawingReplay? = null,
    timeMs: Long = Long.MAX_VALUE,
) {
    with(background) { drawBase() }
    inStrokeLayer {
        for (i in strokes.indices) {
            val stroke = strokes[i]
            drawStroke(stroke, replay?.visiblePoints(i, timeMs) ?: stroke.size)
        }
    }
    with(background) { drawOverlay() }
}

/** Runs [block] in an offscreen layer, so an eraser in it clears only strokes, never the background. */
inline fun DrawScope.inStrokeLayer(block: DrawScope.() -> Unit) {
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
    block()
    drawContext.canvas.restore()
}

/**
 * Draws one [DrawingBackground]. Built once per background: a photo is turned
 * into an [ImageBitmap] here and reused for every frame.
 */
class BackgroundPainter(
    val background: DrawingBackground,
) {
    private val photo: ImageBitmap? =
        (background as? DrawingBackground.Photo)?.let { argbPixelsToImageBitmap(it.pixels, it.width, it.height) }

    /** Everything under the strokes. */
    fun DrawScope.drawBase() {
        if (photo != null) {
            drawImage(photo, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.High)
            return
        }
        val paper = (background as DrawingBackground.Paper).paper
        drawRect(Color(paper.background))
        val unit = size.width
        when (paper.pattern) {
            PaperPattern.PLAIN, PaperPattern.GLUE_STRIP -> {}

            PaperPattern.LINED -> {
                val stroke = max(1f, unit * 0.003f)
                var y = unit * 0.14f
                while (y < size.height) {
                    drawLine(RULE, Offset(0f, y), Offset(unit, y), stroke)
                    y += unit * 0.06f
                }
                drawLine(MARGIN, Offset(unit * 0.12f, 0f), Offset(unit * 0.12f, size.height), stroke)
            }

            PaperPattern.GRID -> {
                val stroke = max(1f, unit * 0.002f)
                val step = unit * 0.05f
                var x = step
                while (x < unit) {
                    drawLine(GRID_LINE, Offset(x, 0f), Offset(x, size.height), stroke)
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(GRID_LINE, Offset(0f, y), Offset(unit, y), stroke)
                    y += step
                }
            }

            PaperPattern.DOTS -> {
                val step = unit * 0.05f
                val dots = ArrayList<Offset>()
                var y = step
                while (y < size.height) {
                    var x = step
                    while (x < unit) {
                        dots.add(Offset(x, y))
                        x += step
                    }
                    y += step
                }
                drawPoints(dots, PointMode.Points, DOT, strokeWidth = max(1.5f, unit * 0.008f), cap = StrokeCap.Round)
            }

            PaperPattern.CHALKBOARD -> {
                // Old eraser smudges, seeded so every frame shows the same ones.
                val random = Random(7)
                repeat(7) {
                    val w = unit * (0.35f + random.nextFloat() * 0.45f)
                    val h = unit * (0.15f + random.nextFloat() * 0.2f)
                    drawOval(SMUDGE, Offset(random.nextFloat() * unit - w / 2, random.nextFloat() * size.height - h / 2), Size(w, h))
                }
            }
        }
    }

    /**
     * Anything that sits over the strokes: a sticky note's glue strip, a faint
     * shade over the top tenth, so ink drawn there looks like it is under it.
     */
    fun DrawScope.drawOverlay() {
        val paper = (background as? DrawingBackground.Paper)?.paper ?: return
        if (paper.pattern == PaperPattern.GLUE_STRIP) drawRect(GLUE_STRIP, size = Size(size.width, size.height * 0.1f))
    }
}

/**
 * The first [points] points of [stroke], and its reflection when it mirrors.
 * Draw it inside [inStrokeLayer] (or onto a strokes-only bitmap): the eraser clears pixels.
 */
fun DrawScope.drawStroke(
    stroke: DrawingStroke,
    points: Int = stroke.size,
) {
    val count = points.coerceAtMost(stroke.size)
    if (count <= 0) return
    drawStrokeOnce(stroke, count)
    if (stroke.mirror) {
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2f, 0f)) {
            drawStrokeOnce(stroke, count)
        }
    }
}

private fun DrawScope.drawStrokeOnce(
    stroke: DrawingStroke,
    count: Int,
) {
    val unit = size.width
    val width = max(1f, stroke.width * unit)
    when (stroke.tool) {
        DrawingTool.BRUSH -> {
            if (hasPressure(stroke, count)) {
                drawPressureLine(stroke, count, unit, width, Color(stroke.color))
            } else {
                drawSmoothLine(stroke, count, unit, width, Color(stroke.color))
            }
        }

        DrawingTool.PENCIL -> {
            drawSmoothLine(stroke, count, unit, max(1f, width * PENCIL_SCALE), Color(stroke.color).copy(alpha = PENCIL_ALPHA))
        }

        DrawingTool.ERASER -> {
            drawSmoothLine(stroke, count, unit, width * ERASER_SCALE, Color.Black, BlendMode.Clear)
        }

        DrawingTool.SPRAY -> {
            drawSpray(stroke, count, unit, width)
        }
    }
}

private fun hasPressure(
    stroke: DrawingStroke,
    count: Int,
): Boolean {
    for (i in 0 until count) if (stroke.pressure(i) != DrawingStroke.NO_PRESSURE) return true
    return false
}

/** A line through the points, smoothed with quadratic curves through each pair's midpoint. */
private fun DrawScope.drawSmoothLine(
    stroke: DrawingStroke,
    count: Int,
    unit: Float,
    width: Float,
    color: Color,
    blendMode: BlendMode = DrawScope.DefaultBlendMode,
) {
    val x0 = stroke.x(0) * unit
    val y0 = stroke.y(0) * unit
    if (count == 1) {
        drawCircle(color, width / 2f, Offset(x0, y0), blendMode = blendMode)
        return
    }
    val path = Path()
    path.moveTo(x0, y0)
    for (i in 1 until count - 1) {
        val x = stroke.x(i) * unit
        val y = stroke.y(i) * unit
        path.quadraticTo(x, y, (x + stroke.x(i + 1) * unit) / 2f, (y + stroke.y(i + 1) * unit) / 2f)
    }
    path.lineTo(stroke.x(count - 1) * unit, stroke.y(count - 1) * unit)
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blendMode)
}

/** Segment by segment, each as wide as the stylus pressed: light touch thin, firm touch bold. */
private fun DrawScope.drawPressureLine(
    stroke: DrawingStroke,
    count: Int,
    unit: Float,
    width: Float,
    color: Color,
) {
    if (count == 1) {
        drawCircle(color, width * pressureScale(stroke.pressure(0)) / 2f, Offset(stroke.x(0) * unit, stroke.y(0) * unit))
        return
    }
    for (i in 1 until count) {
        drawLine(
            color = color,
            start = Offset(stroke.x(i - 1) * unit, stroke.y(i - 1) * unit),
            end = Offset(stroke.x(i) * unit, stroke.y(i) * unit),
            strokeWidth = width * pressureScale(stroke.pressure(i)),
            cap = StrokeCap.Round,
        )
    }
}

private fun pressureScale(pressure: Float) = 0.35f + 1.3f * pressure.coerceIn(0f, 1f)

/**
 * A spray can: dots scattered around each point, denser near the middle. The
 * stroke's seed fixes where they land, so a replay frame shows exactly the dots
 * the finished drawing has.
 */
private fun DrawScope.drawSpray(
    stroke: DrawingStroke,
    count: Int,
    unit: Float,
    width: Float,
) {
    val random = Random(stroke.seed)
    val radius = width * SPRAY_SPREAD
    val dot = max(1f, unit * SPRAY_DOT)
    val dots = ArrayList<Offset>(count * SPRAY_DOTS_PER_POINT)
    for (i in 0 until count) {
        val cx = stroke.x(i) * unit
        val cy = stroke.y(i) * unit
        repeat(SPRAY_DOTS_PER_POINT) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val distance = sqrt(random.nextFloat()) * radius
            dots.add(Offset(cx + cos(angle) * distance, cy + sin(angle) * distance))
        }
    }
    drawPoints(dots, PointMode.Points, Color(stroke.color).copy(alpha = SPRAY_ALPHA), strokeWidth = dot, cap = StrokeCap.Round)
}

private val GLUE_STRIP = Color(0x0B000000)
private val RULE = Color(0xFFBCD0E6)
private val MARGIN = Color(0xFFE6A3A0)
private val GRID_LINE = Color(0xFFD3DFEC)
private val DOT = Color(0xFFC9C0AD)
private val SMUDGE = Color(0x08FFFFFF)
private const val PENCIL_SCALE = 0.4f
private const val PENCIL_ALPHA = 0.75f
private const val ERASER_SCALE = 1.6f
private const val SPRAY_SPREAD = 1.5f
private const val SPRAY_DOT = 0.005f
private const val SPRAY_DOTS_PER_POINT = 14
private const val SPRAY_ALPHA = 0.55f
