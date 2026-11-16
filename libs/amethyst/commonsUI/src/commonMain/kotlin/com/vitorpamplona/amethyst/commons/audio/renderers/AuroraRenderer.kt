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
package com.vitorpamplona.amethyst.commons.audio.renderers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import com.vitorpamplona.amethyst.commons.audio.SpectrumCanvas
import com.vitorpamplona.amethyst.commons.audio.VisualizerPalette
import com.vitorpamplona.amethyst.commons.audio.VisualizerRenderer
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.commons.audio.wrapHue
import kotlinx.coroutines.flow.Flow
import kotlin.math.sin

/**
 * Three overlapping glowing ribbons. Each ribbon is a stroked line whose vertical displacement from
 * its centre is the live spectrum of its band (interpolated across the width), so the ribbon's shape
 * tracks the audio; the glow thickness swells with the overall energy. A small time shimmer (±8%)
 * adds life without driving the motion. The line is clamped inside the canvas (accounting for the
 * stroke width) so the tops/bottoms never get cut.
 */
object AuroraRenderer : VisualizerRenderer {
    override val style = VisualizerStyle.AURORA

    private class Ribbon(
        val hue: (VisualizerPalette) -> Float,
        val yo: Float,
        val lo: Float,
        val hi: Float,
        val dir: Float,
    )

    private val ribbons =
        listOf(
            Ribbon({ it.midHue - 40f }, 0.55f, 0f, 0.5f, -1f),
            Ribbon({ it.lowHue }, 0.5f, 0.15f, 0.85f, 1f),
            Ribbon({ it.highHue }, 0.45f, 0.5f, 1f, -1f),
        )

    @Composable
    override fun Render(
        spectrum: Flow<Spectrum>,
        palette: VisualizerPalette,
        modifier: Modifier,
    ) {
        val paths = remember { List(ribbons.size) { Path() } }
        // Each ribbon's gradient depends only on the palette (the animation lives in the Path
        // geometry), so build the brushes once instead of allocating three Colors + a Brush per
        // ribbon on every frame.
        val brushes =
            remember(palette) {
                ribbons.map { ribbon ->
                    val hue = ribbon.hue(palette).wrapHue()
                    Brush.horizontalGradient(
                        0f to Color.hsl(hue, palette.saturation, palette.lightness, 0f),
                        0.5f to Color.hsl(hue, palette.saturation, palette.lightness, 0.6f),
                        1f to Color.hsl(hue, palette.saturation, palette.lightness, 0f),
                    )
                }
            }
        SpectrumCanvas(spectrum, palette, modifier) { bins, t, _ ->
            if (bins.isEmpty()) return@SpectrumCanvas
            val n = bins.size
            val w = size.width
            val h = size.height

            // overall energy → glow thickness swells with loudness (bounded)
            var energy = 0f
            for (b in bins) energy += b
            energy /= bins.size
            val strokeW = 12f + energy * 30f
            val half = strokeW / 2f

            ribbons.forEachIndexed { index, r ->
                val path = paths[index]
                path.reset()
                var x = 0f
                while (x <= w) {
                    val f = x / w
                    // interpolate this ribbon's band → the displacement IS the spectrum
                    val fb = (r.lo + (r.hi - r.lo) * f) * (n - 1)
                    val i0 = fb.toInt().coerceIn(0, n - 1)
                    val i1 = (i0 + 1).coerceAtMost(n - 1)
                    val v = bins[i0] + (bins[i1] - bins[i0]) * (fb - i0)
                    val shimmer = 1f + 0.08f * sin(f * 9f + t)
                    val disp = (v * shimmer).coerceIn(0f, 1f) * h * 0.40f
                    // maxOf guards the reversed-range crash when the canvas is shorter than the
                    // loudness-swelled stroke (h < strokeW makes half > h - half).
                    val y = (h * r.yo + r.dir * disp).coerceIn(half, maxOf(half, h - half))
                    if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    x += 5f
                }
                drawPath(
                    path = path,
                    brush = brushes[index],
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}
