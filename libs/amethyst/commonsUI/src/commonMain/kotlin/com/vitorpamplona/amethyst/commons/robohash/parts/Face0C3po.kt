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
fun Face0C3po() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    face0C3po(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun face0C3po(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.5f)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData5, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData6, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData7, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(144.5f, 87.5f)
        reflectiveCurveToRelative(-51.0f, 3.0f, -53.0f, 55.0f)
        curveToRelative(0.0f, 0.0f, 0.0f, 27.0f, 5.0f, 42.0f)
        reflectiveCurveToRelative(10.0f, 38.0f, 10.0f, 38.0f)
        lineToRelative(16.0f, 16.0f)
        lineToRelative(1.0f, 5.0f)
        reflectiveCurveToRelative(13.5f, -1.5f, 19.0f, -1.0f)
        reflectiveCurveToRelative(14.0f, 2.0f, 14.0f, 2.0f)
        reflectiveCurveToRelative(6.0f, -13.0f, 19.0f, -19.0f)
        reflectiveCurveToRelative(18.0f, -8.0f, 18.0f, -8.0f)
        reflectiveCurveToRelative(-4.0f, -35.0f, -4.0f, -52.0f)
        reflectiveCurveTo(201.5f, 88.5f, 144.5f, 87.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(115.14f, 97.69f)
        reflectiveCurveToRelative(6.36f, 5.81f, -2.64f, 14.81f)
        reflectiveCurveToRelative(-16.0f, 23.0f, -13.0f, 44.0f)
        reflectiveCurveToRelative(10.0f, 40.0f, 10.0f, 50.0f)
        reflectiveCurveToRelative(-0.67f, 18.19f, -0.67f, 18.19f)
        lineToRelative(-2.33f, -2.19f)
        reflectiveCurveToRelative(-15.0f, -45.0f, -15.0f, -76.5f)
        reflectiveCurveTo(115.14f, 97.69f, 115.14f, 97.69f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(158.5f, 92.5f)
        reflectiveCurveToRelative(20.0f, 15.0f, 18.0f, 32.0f)
        reflectiveCurveToRelative(-8.0f, 28.0f, -12.0f, 29.0f)
        arcToRelative(19.27f, 19.27f, 0.0f, false, true, 8.0f, 16.0f)
        curveToRelative(0.0f, 11.0f, 1.0f, 50.0f, 1.0f, 50.0f)
        lineToRelative(0.34f, 6.83f)
        lineToRelative(19.66f, -8.83f)
        reflectiveCurveToRelative(-3.77f, -39.0f, -3.38f, -63.49f)
        reflectiveCurveToRelative(1.38f, -55.13f, -31.62f, -64.82f)
        curveTo(158.5f, 89.19f, 155.5f, 90.5f, 158.5f, 92.5f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(124.5f, 211.5f)
        lineToRelative(37.0f, -1.0f)
        lineToRelative(4.76f, 21.18f)
        reflectiveCurveToRelative(-8.76f, 8.82f, -8.76f, 11.82f)
        curveToRelative(-2.0f, 1.0f, -10.0f, -1.0f, -18.0f, -1.0f)
        arcToRelative(147.84f, 147.84f, 0.0f, false, false, -16.0f, 1.0f)
        reflectiveCurveTo(118.5f, 224.5f, 124.5f, 211.5f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(159.5f, 212.5f)
        arcToRelative(19.89f, 19.89f, 0.0f, false, false, -4.0f, 14.0f)
        curveToRelative(1.0f, 8.0f, 2.0f, 17.0f, 2.0f, 17.0f)
        lineToRelative(9.0f, -12.0f)
        lineToRelative(-5.0f, -21.0f)
        close()
    }
