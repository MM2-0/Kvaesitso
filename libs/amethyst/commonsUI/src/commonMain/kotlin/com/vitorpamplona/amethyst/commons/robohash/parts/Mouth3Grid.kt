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
import com.vitorpamplona.amethyst.commons.robohash.DarkYellow
import com.vitorpamplona.amethyst.commons.robohash.Yellow
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Mouth3Grid() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    mouth3Grid(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun mouth3Grid(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.8f)
    builder.addPath(pathData2, fill = Yellow)
    builder.addPath(pathData3, fill = DarkYellow)
    builder.addPath(pathData7, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 0.75f)
    builder.addPath(pathData9, stroke = Black, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(104.0f, 173.0f)
        reflectiveCurveToRelative(5.5f, 23.5f, 5.5f, 27.5f)
        curveToRelative(0.0f, 0.0f, 6.0f, 5.0f, 24.0f, 5.0f)
        reflectiveCurveToRelative(41.0f, -10.0f, 43.0f, -14.0f)
        curveToRelative(0.0f, 0.0f, -4.0f, -29.0f, -5.0f, -30.0f)
        curveToRelative(0.0f, 0.0f, -25.0f, 11.0f, -45.0f, 12.0f)
        curveToRelative(-10.61f, 0.62f, -16.74f, 0.27f, -19.85f, -0.08f)
        arcTo(20.06f, 20.06f, 0.0f, false, true, 104.0f, 173.0f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(106.65f, 173.42f)
        reflectiveCurveToRelative(3.08f, 5.1f, 4.72f, 14.34f)
        reflectiveCurveToRelative(1.0f, 10.85f, 1.0f, 10.85f)
        reflectiveCurveTo(123.0f, 203.0f, 128.0f, 203.0f)
        arcToRelative(73.0f, 73.0f, 0.0f, false, false, 18.0f, -1.77f)
        arcToRelative(131.07f, 131.07f, 0.0f, false, false, 22.47f, -7.33f)
        lineToRelative(7.93f, -3.29f)
        lineToRelative(-3.18f, -21.13f)
        lineToRelative(-1.7f, -8.0f)
        reflectiveCurveToRelative(-26.0f, 11.0f, -45.0f, 12.0f)
        reflectiveCurveToRelative(-20.0f, 0.0f, -20.0f, 0.0f)
    }
private val pathData3 =
    PathData {
        moveTo(142.5f, 181.5f)
        verticalLineTo(190.0f)
        lineToRelative(17.5f, -4.0f)
        lineToRelative(1.0f, 6.0f)
        verticalLineToRelative(4.9f)
        lineToRelative(15.0f, -5.9f)
        lineToRelative(-3.0f, -20.5f)
        curveTo(173.15f, 170.88f, 153.19f, 178.85f, 142.5f, 181.5f)
        close()
        moveTo(142.4f, 190.0f)
        arcToRelative(38.22f, 38.22f, 0.0f, false, false, 2.1f, 11.5f)
        reflectiveCurveToRelative(-10.5f, 1.5f, -17.0f, 1.0f)
        reflectiveCurveTo(113.0f, 199.0f, 113.0f, 199.0f)
        lineToRelative(-2.5f, -15.5f)
        horizontalLineToRelative(11.0f)
        lineTo(123.0f, 191.0f)
        arcTo(122.0f, 122.0f, 0.0f, false, false, 142.4f, 190.0f)
        close()
        moveTo(118.0f, 174.0f)
        lineToRelative(3.5f, 9.0f)
        curveToRelative(7.62f, 0.33f, 21.0f, -1.5f, 21.0f, -1.5f)
        reflectiveCurveToRelative(-1.13f, -7.27f, -2.5f, -9.5f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(104.0f, 173.0f)
        reflectiveCurveToRelative(5.5f, 23.5f, 5.5f, 27.5f)
        curveToRelative(0.0f, 0.0f, 6.0f, 5.0f, 24.0f, 5.0f)
        reflectiveCurveToRelative(41.0f, -10.0f, 43.0f, -14.0f)
        curveToRelative(0.0f, 0.0f, -4.0f, -29.0f, -5.0f, -30.0f)
        curveToRelative(0.0f, 0.0f, -25.0f, 11.0f, -45.0f, 12.0f)
        curveToRelative(-10.61f, 0.62f, -16.74f, 0.27f, -19.85f, -0.08f)
        arcTo(20.06f, 20.06f, 0.0f, false, true, 104.0f, 173.0f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(112.33f, 198.61f)
        reflectiveCurveTo(122.0f, 203.0f, 130.0f, 203.0f)
        reflectiveCurveToRelative(23.5f, -2.5f, 32.5f, -6.5f)
        reflectiveCurveToRelative(13.88f, -5.89f, 13.88f, -5.89f)
        lineToRelative(0.12f, 0.89f)
        arcToRelative(31.45f, 31.45f, 0.0f, false, true, -8.77f, 5.75f)
        curveToRelative(-5.23f, 2.25f, -19.73f, 8.25f, -35.48f, 8.25f)
        reflectiveCurveToRelative(-22.75f, -5.0f, -22.75f, -5.0f)
        lineToRelative(2.83f, -1.89f)

        moveTo(107.5f, 174.5f)
        arcToRelative(93.08f, 93.08f, 0.0f, false, true, 4.0f, 14.0f)
        arcToRelative(67.34f, 67.34f, 0.0f, false, true, 1.0f, 10.0f)
        lineToRelative(-3.0f, 2.0f)
        reflectiveCurveToRelative(-2.2f, -15.2f, -4.6f, -23.6f)
        lineToRelative(-0.9f, -3.69f)
        lineToRelative(2.93f, 0.25f)
        close()
    }
private val pathData9 =
    PathData {
        moveTo(124.61f, 202.46f)
        curveToRelative(0.11f, -5.0f, -2.11f, -16.0f, -4.11f, -22.0f)
        reflectiveCurveToRelative(-3.42f, -6.66f, -3.42f, -6.66f)
        moveTo(140.25f, 171.58f)
        reflectiveCurveToRelative(2.25f, 5.92f, 2.25f, 9.92f)
        verticalLineToRelative(8.0f)
        reflectiveCurveToRelative(0.33f, 10.83f, 2.67f, 11.92f)
        moveTo(112.0f, 191.0f)
        reflectiveCurveToRelative(17.0f, 1.0f, 30.0f, -1.0f)
        reflectiveCurveToRelative(30.0f, -7.0f, 30.0f, -7.0f)
        lineToRelative(3.0f, -1.0f)
        moveTo(142.5f, 181.5f)
        verticalLineToRelative(8.42f)
        lineToRelative(17.51f, -3.7f)
        lineToRelative(1.0f, 8.28f)
        verticalLineTo(197.0f)
        reflectiveCurveToRelative(-1.0f, -9.0f, -2.0f, -14.0f)
        reflectiveCurveToRelative(-2.84f, -12.76f, -2.84f, -12.76f)
        moveTo(176.5f, 190.5f)
        lineTo(173.0f, 170.0f)
        curveToRelative(-1.23f, 1.23f, -7.76f, 4.0f, -15.16f, 6.6f)
        curveToRelative(-4.63f, 1.64f, -9.61f, 3.24f, -13.84f, 4.4f)
        curveToRelative(-11.0f, 3.0f, -33.55f, 2.5f, -33.55f, 2.5f)
    }
