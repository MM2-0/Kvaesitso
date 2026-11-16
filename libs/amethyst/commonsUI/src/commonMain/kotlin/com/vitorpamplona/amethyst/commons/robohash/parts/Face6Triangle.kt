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
fun Face6Triangle() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    face6Triangle(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun face6Triangle(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 2.0f)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData4, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData19, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData20, fill = Black, fillAlpha = 0.2f)
}

private val pathData1 =
    PathData {
        moveTo(71.5f, 130.5f)
        arcToRelative(51.83f, 51.83f, 0.0f, false, false, 6.0f, 29.0f)
        curveToRelative(8.0f, 15.0f, 39.0f, 71.0f, 66.0f, 70.0f)
        reflectiveCurveToRelative(76.0f, -50.0f, 76.0f, -93.0f)
        reflectiveCurveToRelative(-38.0f, -54.0f, -77.0f, -53.0f)
        reflectiveCurveTo(73.5f, 102.5f, 71.5f, 130.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(116.5f, 88.0f)
        reflectiveCurveToRelative(-24.18f, 5.5f, -35.59f, 20.5f)
        reflectiveCurveToRelative(-9.79f, 26.0f, -9.1f, 34.48f)
        reflectiveCurveToRelative(8.2f, 22.55f, 14.44f, 33.0f)
        reflectiveCurveToRelative(18.64f, 28.81f, 22.95f, 32.65f)
        reflectiveCurveToRelative(11.3f, 11.84f, 11.3f, 11.84f)
        reflectiveCurveToRelative(0.3f, -6.17f, -5.85f, -18.58f)
        reflectiveCurveTo(83.5f, 164.0f, 81.5f, 143.74f)
        reflectiveCurveTo(87.5f, 101.49f, 116.5f, 88.0f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(71.5f, 130.5f)
        arcToRelative(51.83f, 51.83f, 0.0f, false, false, 6.0f, 29.0f)
        curveToRelative(8.0f, 15.0f, 39.0f, 71.0f, 66.0f, 70.0f)
        reflectiveCurveToRelative(76.0f, -50.0f, 76.0f, -93.0f)
        reflectiveCurveToRelative(-38.0f, -54.0f, -77.0f, -53.0f)
        reflectiveCurveTo(73.5f, 102.5f, 71.5f, 130.5f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(75.44f, 115.88f)
        reflectiveCurveTo(108.5f, 100.5f, 140.5f, 101.5f)
        reflectiveCurveToRelative(65.0f, 7.0f, 67.0f, 30.0f)
        reflectiveCurveToRelative(-3.0f, 34.0f, -9.0f, 50.0f)
        arcToRelative(89.16f, 89.16f, 0.0f, false, true, -16.32f, 27.0f)
    }
private val pathData19 =
    PathData {
        moveTo(199.5f, 115.5f)
        lineToRelative(-9.0f, -5.07f)
        reflectiveCurveTo(188.0f, 110.0f, 191.0f, 120.0f)
        reflectiveCurveToRelative(6.5f, 25.5f, 4.5f, 40.5f)
        reflectiveCurveToRelative(-7.0f, 27.0f, -19.0f, 40.0f)
        reflectiveCurveToRelative(-17.37f, 20.78f, -17.19f, 23.89f)
        reflectiveCurveTo(184.5f, 208.5f, 188.5f, 202.5f)
        reflectiveCurveToRelative(11.93f, -23.83f, 16.0f, -39.0f)
        reflectiveCurveTo(211.5f, 124.5f, 199.5f, 115.5f)
        close()
    }
private val pathData20 =
    PathData {
        moveTo(201.0f, 100.0f)
        lineToRelative(-10.0f, 10.0f)
        reflectiveCurveToRelative(-23.39f, -8.18f, -41.7f, -8.09f)
        reflectiveCurveToRelative(-33.49f, -1.25f, -53.4f, 6.42f)
        lineTo(76.0f, 116.0f)
        reflectiveCurveToRelative(-0.08f, -2.83f, 6.71f, -9.66f)
        reflectiveCurveToRelative(18.0f, -18.34f, 48.89f, -22.09f)
        reflectiveCurveToRelative(49.94f, 2.2f, 58.92f, 6.73f)
        reflectiveCurveToRelative(12.0f, 7.09f, 12.0f, 7.09f)
        close()
    }
