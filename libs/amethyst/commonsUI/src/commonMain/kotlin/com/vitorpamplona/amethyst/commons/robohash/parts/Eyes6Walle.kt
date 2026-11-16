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
import com.vitorpamplona.amethyst.commons.robohash.Brown
import com.vitorpamplona.amethyst.commons.robohash.LightYellow
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Eyes6WallE() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    eyes6WallE(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun eyes6WallE(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor)
    builder.addPath(pathData2, fill = Black, stroke = Black, fillAlpha = 0.6f, strokeLineWidth = 1.0f)
    builder.addPath(pathData3, fill = Brown, stroke = Black, strokeLineWidth = 0.5f)
    builder.addPath(pathData4, fill = LightYellow, stroke = Black, strokeLineWidth = 0.5f)
    builder.addPath(pathData6, fill = Black)
    builder.addPath(pathData8, stroke = Black, strokeLineWidth = 1.0f)
}

private val pathData1 =
    PathData {
        moveTo(99.5f, 142.5f)
        reflectiveCurveToRelative(0.0f, -10.0f, 12.0f, -14.0f)
        reflectiveCurveToRelative(26.0f, -4.0f, 26.0f, -4.0f)
        reflectiveCurveToRelative(20.0f, -1.0f, 25.0f, 2.0f)
        reflectiveCurveToRelative(5.0f, 9.0f, 5.0f, 11.0f)
        reflectiveCurveToRelative(-2.0f, 11.0f, -19.0f, 13.0f)
        reflectiveCurveToRelative(-33.0f, 3.0f, -33.0f, 3.0f)
        reflectiveCurveTo(99.5f, 153.5f, 99.5f, 142.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(99.5f, 142.5f)
        reflectiveCurveToRelative(0.0f, -10.0f, 12.0f, -14.0f)
        reflectiveCurveToRelative(26.0f, -4.0f, 26.0f, -4.0f)
        reflectiveCurveToRelative(20.0f, -1.0f, 25.0f, 2.0f)
        reflectiveCurveToRelative(5.0f, 9.0f, 5.0f, 11.0f)
        reflectiveCurveToRelative(-2.0f, 11.0f, -19.0f, 13.0f)
        reflectiveCurveToRelative(-33.0f, 3.0f, -33.0f, 3.0f)
        reflectiveCurveTo(99.5f, 153.5f, 99.5f, 142.5f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(107.16f, 130.47f)
        reflectiveCurveTo(105.0f, 133.0f, 105.0f, 138.0f)
        reflectiveCurveToRelative(2.0f, 9.0f, 8.0f, 10.0f)
        reflectiveCurveToRelative(20.5f, -1.5f, 20.5f, -1.5f)
        reflectiveCurveToRelative(11.0f, -2.0f, 18.0f, -2.0f)
        reflectiveCurveToRelative(15.74f, -2.78f, 15.87f, -5.89f)
        reflectiveCurveToRelative(-0.17f, -7.11f, -2.52f, -10.11f)
        reflectiveCurveToRelative(-7.12f, -3.56f, -10.24f, -3.78f)
        reflectiveCurveToRelative(-12.52f, -0.54f, -14.82f, -0.38f)
        reflectiveCurveToRelative(-10.17f, 0.47f, -12.23f, 0.81f)
        reflectiveCurveToRelative(-7.31f, 1.1f, -7.31f, 1.1f)
        lineToRelative(-7.61f, 1.87f)
        reflectiveCurveTo(107.83f, 129.44f, 107.16f, 130.47f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(109.0f, 135.0f)
        reflectiveCurveToRelative(-1.0f, 11.0f, 10.0f, 10.0f)
        reflectiveCurveToRelative(10.0f, -10.0f, 10.0f, -10.0f)
        reflectiveCurveToRelative(0.0f, -6.0f, -3.0f, -8.0f)
        reflectiveCurveToRelative(-2.85f, -1.25f, -2.85f, -1.25f)
        reflectiveCurveToRelative(-11.28f, 2.64f, -12.21f, 2.94f)
        reflectiveCurveTo(109.0f, 133.0f, 109.0f, 135.0f)
        close()
        moveTo(142.0f, 125.0f)
        reflectiveCurveToRelative(-3.0f, 2.0f, -3.0f, 7.0f)
        reflectiveCurveToRelative(3.5f, 10.5f, 9.5f, 10.5f)
        reflectiveCurveToRelative(12.0f, -4.0f, 12.0f, -10.0f)
        reflectiveCurveToRelative(-3.66f, -7.5f, -3.66f, -7.5f)
        lineToRelative(-4.78f, -0.47f)
        lineToRelative(-4.22f, -0.16f)
        horizontalLineToRelative(-3.44f)
        lineToRelative(-1.91f, 0.13f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(119.42f, 133.0f)
        reflectiveCurveToRelative(-1.17f, 0.0f, -1.17f, 2.5f)
        reflectiveCurveToRelative(1.17f, 2.5f, 1.17f, 2.5f)
        arcToRelative(2.51f, 2.51f, 0.0f, false, false, 0.0f, -5.0f)
        close()
        moveTo(150.08f, 130.0f)
        arcToRelative(1.8f, 1.8f, 0.0f, false, false, -1.82f, 1.94f)
        curveToRelative(-0.07f, 2.0f, 0.78f, 3.0f, 1.65f, 3.06f)
        reflectiveCurveToRelative(1.78f, -0.94f, 1.85f, -2.94f)
        arcToRelative(1.8f, 1.8f, 0.0f, false, false, -1.68f, -2.06f)
    }
private val pathData8 =
    PathData {
        moveTo(99.5f, 142.5f)
        reflectiveCurveToRelative(0.0f, -10.0f, 12.0f, -14.0f)
        reflectiveCurveToRelative(26.0f, -4.0f, 26.0f, -4.0f)
        reflectiveCurveToRelative(20.0f, -1.0f, 25.0f, 2.0f)
        reflectiveCurveToRelative(5.0f, 9.0f, 5.0f, 11.0f)
        reflectiveCurveToRelative(-2.0f, 11.0f, -19.0f, 13.0f)
        reflectiveCurveToRelative(-33.0f, 3.0f, -33.0f, 3.0f)
        reflectiveCurveTo(99.5f, 153.5f, 99.5f, 142.5f)
        close()
    }
