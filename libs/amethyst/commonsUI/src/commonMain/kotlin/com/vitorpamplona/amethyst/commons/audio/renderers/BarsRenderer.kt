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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import com.vitorpamplona.amethyst.commons.audio.SpectrumCanvas
import com.vitorpamplona.amethyst.commons.audio.VisualizerPalette
import com.vitorpamplona.amethyst.commons.audio.VisualizerRenderer
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.commons.audio.wrapHue
import kotlinx.coroutines.flow.Flow
import kotlin.math.min

object BarsRenderer : VisualizerRenderer {
    override val style = VisualizerStyle.BARS

    @Composable
    override fun Render(
        spectrum: Flow<Spectrum>,
        palette: VisualizerPalette,
        modifier: Modifier,
    ) {
        SpectrumCanvas(spectrum, palette, modifier, animated = false) { bins, _, pal ->
            if (bins.isEmpty()) return@SpectrumCanvas
            val n = bins.size
            val gap = 2f
            val barW = (size.width - gap * (n - 1)) / n
            if (barW <= 0f) return@SpectrumCanvas
            val mid = size.height / 2f
            val radius = CornerRadius(min(barW / 2f, 3f), min(barW / 2f, 3f))
            for (i in 0 until n) {
                val v = bins[i].coerceIn(0f, 1f)
                val half = v * (size.height / 2f - 2f)
                if (half <= 0f) continue
                val hue = pal.midHue + (pal.highHue - pal.midHue) * (i / n.toFloat())
                drawRoundRect(
                    color = Color.hsl(hue.wrapHue(), pal.saturation, pal.lightness),
                    topLeft = Offset(i * (barW + gap), mid - half),
                    size = Size(barW, half * 2f),
                    cornerRadius = radius,
                )
            }
        }
    }
}
