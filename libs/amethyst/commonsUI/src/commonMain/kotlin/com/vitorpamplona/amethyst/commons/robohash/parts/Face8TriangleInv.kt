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
fun Face8TriangleInv() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    face8TriangleInv(SolidColor(Color.Yellow), this)
                },
            ),
        contentDescription = "",
    )
}

fun face8TriangleInv(
    fgColor: SolidColor,
    builder: Builder,
) {
    // face
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)

    // shades
    builder.addPath(pathData5, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData6, fill = Black, fillAlpha = 0.1f)
    builder.addPath(pathData7, fill = Black, fillAlpha = 0.4f)

    // outer line
    builder.addPath(pathData2, stroke = Black, strokeLineWidth = 1.0f)

    // inner line
    builder.addPath(pathData20, stroke = Black, strokeLineWidth = 1.0f)
}

private val pathData1 =
    PathData {
        moveTo(143.0f, 89.0f)
        reflectiveCurveToRelative(-39.0f, 6.0f, -64.0f, 66.0f)
        reflectiveCurveToRelative(15.5f, 70.5f, 34.5f, 74.5f)
        reflectiveCurveToRelative(85.0f, 13.0f, 102.0f, -27.0f)
        reflectiveCurveTo(175.5f, 85.5f, 143.0f, 89.0f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(143.0f, 88.0f)
        reflectiveCurveToRelative(-39.0f, 6.0f, -64.0f, 66.0f)
        reflectiveCurveToRelative(15.5f, 70.5f, 34.5f, 74.5f)
        reflectiveCurveToRelative(85.0f, 13.0f, 102.0f, -27.0f)
        reflectiveCurveTo(175.5f, 84.5f, 143.0f, 88.0f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(161.38f, 106.09f)
        arcToRelative(20.31f, 20.31f, 0.0f, false, false, -1.88f, 4.41f)
        curveToRelative(-1.0f, 6.0f, 2.0f, 11.0f, 9.0f, 19.0f)
        reflectiveCurveToRelative(20.0f, 27.0f, 21.0f, 45.0f)
        reflectiveCurveToRelative(1.0f, 35.0f, -13.0f, 45.0f)
        reflectiveCurveToRelative(-24.21f, 14.08f, -24.21f, 14.08f)
        reflectiveCurveToRelative(34.71f, 0.42f, 47.8f, -19.51f)
        curveToRelative(11.41f, -15.58f, 10.59f, -36.0f, 0.0f, -59.77f)
        curveToRelative(-11.09f, -27.31f, -33.81f, -44.6f, -33.81f, -44.6f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(167.41f, 97.44f)
        curveToRelative(-2.41f, -0.44f, -5.91f, 9.06f, -5.91f, 9.06f)
        reflectiveCurveToRelative(27.0f, 14.0f, 39.0f, 48.0f)
        reflectiveCurveToRelative(8.86f, 41.0f, 5.43f, 50.0f)
        reflectiveCurveToRelative(-17.62f, 24.63f, -36.0f, 27.81f)
        reflectiveCurveToRelative(37.6f, -2.82f, 45.6f, -29.82f)
        curveToRelative(11.5f, -39.5f, -22.87f, -80.23f, -22.87f, -80.23f)
        reflectiveCurveTo(175.0f, 101.0f, 167.41f, 97.44f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(91.5f, 140.5f)
        reflectiveCurveToRelative(-14.0f, 24.0f, -12.0f, 43.0f)
        reflectiveCurveToRelative(12.0f, 26.0f, 19.0f, 31.0f)
        reflectiveCurveToRelative(9.2f, 13.33f, 9.2f, 13.33f)
        reflectiveCurveTo(72.0f, 224.0f, 71.08f, 186.84f)
        curveToRelative(0.0f, 0.0f, 3.19f, -39.76f, 30.31f, -68.55f)
        lineTo(113.22f, 108.0f)
        reflectiveCurveTo(95.5f, 132.5f, 91.5f, 140.5f)
        close()
    }
private val pathData20 =
    PathData {
        moveTo(139.5f, 98.5f)
        reflectiveCurveToRelative(35.0f, 3.0f, 57.0f, 48.0f)
        reflectiveCurveToRelative(13.83f, 72.0f, -24.08f, 85.0f)
        curveToRelative(-54.92f, 7.0f, -79.92f, -7.0f, -91.0f, -16.46f)
        curveToRelative(-1.5f, 0.92f, -21.0f, -20.3f, -3.0f, -60.55f)
        curveTo(95.74f, 116.0f, 122.5f, 97.5f, 139.5f, 98.5f)
        close()
    }
