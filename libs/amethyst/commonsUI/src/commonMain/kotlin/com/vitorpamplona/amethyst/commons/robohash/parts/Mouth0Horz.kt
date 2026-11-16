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
package com.vitorpamplona.amethyst.commons.robohash.parts

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.vitorpamplona.amethyst.commons.robohash.Black
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Mouth0Horz() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    mouth0Horz(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun mouth0Horz(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor)
    builder.addPath(pathData2, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 1.0f)
    builder.addPath(pathData3, fill = Black)
}

private val pathData1 =
    PathData {
        moveTo(123.0f, 183.0f)
        lineToRelative(-1.0f, 9.0f)
        arcToRelative(26.74f, 26.74f, 0.0f, false, false, 3.5f, 0.5f)
        curveToRelative(2.0f, 0.44f, 22.0f, 0.35f, 23.0f, -1.0f)
        lineToRelative(-1.0f, -9.0f)
        reflectiveCurveTo(132.5f, 181.5f, 123.0f, 183.0f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(123.0f, 183.0f)
        lineToRelative(-1.0f, 9.0f)
        arcToRelative(26.74f, 26.74f, 0.0f, false, false, 3.5f, 0.5f)
        curveToRelative(2.0f, 0.44f, 22.0f, 0.35f, 23.0f, -1.0f)
        lineToRelative(-1.0f, -9.0f)
        reflectiveCurveTo(132.5f, 181.5f, 123.0f, 183.0f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(123.5f, 183.34f)
        reflectiveCurveToRelative(3.07f, 2.66f, 12.26f, 2.66f)
        reflectiveCurveTo(147.0f, 182.45f, 147.0f, 182.45f)
        arcToRelative(113.13f, 113.13f, 0.0f, false, false, -12.2f, -0.28f)
        curveTo(129.63f, 182.45f, 124.52f, 182.45f, 123.5f, 183.34f)
        close()
    }
