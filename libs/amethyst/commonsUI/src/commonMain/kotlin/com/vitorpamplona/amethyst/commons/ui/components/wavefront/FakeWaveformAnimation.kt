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
package com.vitorpamplona.amethyst.commons.ui.components.wavefront

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import kotlin.math.PI
import kotlin.math.sin

@Preview
@Composable
fun FakeWaveformAnimationPreview() {
    val state =
        remember {
            mutableFloatStateOf((500 % 1000.0f) / 1000.0f)
        }
    ThemeComparisonColumn {
        FakeWaveformAnimation(state, 50, Modifier.width(200.dp))
    }
}

@Composable
fun FakeWaveformAnimation(
    waveformProgress: MutableFloatState,
    barCount: Int = 50,
    modifier: Modifier = Modifier,
) {
    val barBrush =
        Brush.linearGradient(
            colors = listOf(Color(0xff2598cf), Color(0xff652d80)),
            start = Offset(0.2f * 128f, 0f),
            end = Offset(0.2f * 128f + 128f, 0f),
        )

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .requiredHeight(48.dp)
                .padding(horizontal = 10.dp),
    ) {
        val barWidth = 3.dp.toPx()
        val barPadding = 2.dp.toPx()
        val barRadius = 2.dp.toPx()
        val totalBarWidth = barWidth + barPadding
        val startX = (size.width - (barCount * totalBarWidth - barPadding)) / 2f

        for (i in 0 until barCount) {
            val phase = (i.toFloat() / barCount) * 2f * PI.toFloat()
            val wave = sin(waveformProgress.floatValue * 2f * PI.toFloat() + phase)
            val barFraction = ((wave + 1f) / 2f)
            val barHeight = size.height * barFraction
            val x = startX + i * totalBarWidth
            val y = (size.height - barHeight) / 2f

            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barRadius, barRadius),
                style = Fill,
            )
        }
    }
}
