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
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import com.vitorpamplona.amethyst.commons.audio.SpectrumCanvas
import com.vitorpamplona.amethyst.commons.audio.VisualizerPalette
import com.vitorpamplona.amethyst.commons.audio.VisualizerRenderer
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.commons.audio.wrapHue
import kotlinx.coroutines.flow.Flow
import kotlin.math.sin

/**
 * Three translucent, overlapping filled waves. Each wave's OUTLINE is the live spectrum of its
 * frequency band (low / mid / high), interpolated across the width, so the shape tracks the audio
 * directly. A small time shimmer (±6%) adds life without driving the motion, and the height is
 * clamped to 0.92·h so the fill never reaches or leaves the top edge.
 */
object WavesRenderer : VisualizerRenderer {
    override val style = VisualizerStyle.WAVES

    private class Layer(
        val lo: Float,
        val hi: Float,
        val hue: (VisualizerPalette) -> Float,
        val amp: Float,
        val phase: Float,
    )

    private val layers =
        listOf(
            Layer(0f, 0.4f, { it.lowHue }, 1.0f, 0f),
            Layer(0.2f, 0.75f, { it.midHue }, 0.9f, 1.5f),
            Layer(0.5f, 1f, { it.highHue }, 0.8f, 3f),
        )

    @Composable
    override fun Render(
        spectrum: Flow<Spectrum>,
        palette: VisualizerPalette,
        modifier: Modifier,
    ) {
        val paths = remember { List(layers.size) { Path() } }
        // Each layer's gradient depends only on the palette (the animation lives in the Path
        // geometry), so build the brushes once instead of allocating two Colors + a Brush per layer
        // on every frame.
        val brushes =
            remember(palette) {
                layers.map { layer ->
                    val hue = layer.hue(palette).wrapHue()
                    Brush.verticalGradient(
                        0f to Color.hsl(hue, palette.saturation, palette.lightness, 0.85f),
                        1f to Color.hsl(hue, palette.saturation, palette.lightness * 0.8f, 0.05f),
                    )
                }
            }
        SpectrumCanvas(spectrum, palette, modifier) { bins, t, _ ->
            if (bins.isEmpty()) return@SpectrumCanvas
            val n = bins.size
            val w = size.width
            val h = size.height
            layers.forEachIndexed { index, layer ->
                val path = paths[index]
                path.reset()
                path.moveTo(0f, h)
                var x = 0f
                while (x <= w) {
                    val f = x / w
                    // interpolate the spectrum within this layer's band → smooth, audio-driven outline
                    val fb = (layer.lo + (layer.hi - layer.lo) * f) * (n - 1)
                    val i0 = fb.toInt().coerceIn(0, n - 1)
                    val i1 = (i0 + 1).coerceAtMost(n - 1)
                    val v = (bins[i0] + (bins[i1] - bins[i0]) * (fb - i0)) * layer.amp
                    val shimmer = 1f + 0.06f * sin(f * 12f + layer.phase + t * 1.5f)
                    val height = (v * shimmer).coerceIn(0f, 1f) * h * 0.92f
                    path.lineTo(x, h - height)
                    x += 4f
                }
                path.lineTo(w, h)
                path.close()
                drawPath(
                    path = path,
                    brush = brushes[index],
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}
