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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import com.vitorpamplona.amethyst.commons.audio.SpectrumCanvas
import com.vitorpamplona.amethyst.commons.audio.VisualizerPalette
import com.vitorpamplona.amethyst.commons.audio.VisualizerRenderer
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.commons.audio.wrapHue
import kotlinx.coroutines.flow.Flow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object RadialRenderer : VisualizerRenderer {
    override val style = VisualizerStyle.RADIAL

    @Composable
    override fun Render(
        spectrum: Flow<Spectrum>,
        palette: VisualizerPalette,
        modifier: Modifier,
    ) {
        SpectrumCanvas(spectrum, palette, modifier, animated = false) { bins, _, pal ->
            if (bins.isEmpty()) return@SpectrumCanvas
            val n = bins.size
            val cx = size.width / 2f
            val cy = size.height / 2f
            val minDim = min(size.width, size.height)
            // A zero/near-zero canvas (transient layout pass while audio plays) would make coreR == 0,
            // and Brush.radialGradient(radius = 0f) throws. Nothing meaningful to draw at that size.
            if (minDim <= 0f) return@SpectrumCanvas
            val r0 = minDim * 0.16f

            var energy = 0f
            val lowCount = min(8, n)
            for (i in 0 until lowCount) energy += bins[i]
            energy /= lowCount

            val coreR = r0 * (1.4f + energy)
            drawCircle(
                brush =
                    Brush.radialGradient(
                        0f to Color.hsl(pal.highHue, pal.saturation, 0.7f, 0.9f),
                        1f to Color.hsl(pal.highHue, pal.saturation, 0.6f, 0f),
                        center = Offset(cx, cy),
                        radius = coreR,
                    ),
                radius = coreR,
                center = Offset(cx, cy),
            )

            for (i in 0 until n) {
                val a = i / n.toFloat() * (2f * PI.toFloat()) - PI.toFloat() / 2f
                val len = r0 + bins[i].coerceIn(0f, 1f) * minDim * 0.32f
                val hue = (pal.midHue + (pal.highHue - pal.midHue) * (i / n.toFloat())).wrapHue()
                drawLine(
                    color = Color.hsl(hue, pal.saturation, pal.lightness),
                    start = Offset(cx + cos(a) * r0, cy + sin(a) * r0),
                    end = Offset(cx + cos(a) * len, cy + sin(a) * len),
                    strokeWidth = 2.4f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
