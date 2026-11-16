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
import com.vitorpamplona.amethyst.commons.robohash.LightRed
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Accessory6Hat() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    accessory6Hat(SolidColor(Color.Yellow), this)
                },
            ),
        contentDescription = "",
    )
}

fun accessory6Hat(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData6, fill = LightRed, stroke = Black, strokeLineWidth = 0.75f)
    builder.addPath(pathData9, stroke = Black, strokeLineWidth = 1.0f)
}

private val pathData1 =
    PathData {
        moveTo(131.0f, 59.0f)
        reflectiveCurveToRelative(-13.0f, 1.0f, -14.0f, 6.0f)
        arcToRelative(2.62f, 2.62f, 0.0f, false, false, 0.5f, 2.5f)
        curveToRelative(1.0f, 1.0f, 3.0f, 5.0f, 4.0f, 8.0f)
        reflectiveCurveToRelative(2.0f, 5.0f, 0.0f, 5.0f)
        reflectiveCurveToRelative(-8.0f, 2.0f, -10.0f, 4.0f)
        reflectiveCurveToRelative(-1.0f, 5.0f, -1.0f, 5.0f)
        lineToRelative(1.0f, 9.0f)
        arcToRelative(33.15f, 33.15f, 0.0f, false, false, 18.0f, 5.0f)
        curveToRelative(16.5f, 0.5f, 34.0f, -9.0f, 35.0f, -10.0f)
        reflectiveCurveToRelative(-1.0f, -11.0f, -1.0f, -11.0f)
        curveToRelative(0.16f, -0.17f, -2.84f, -5.17f, -4.0f, -5.0f)
        curveToRelative(-1.0f, 0.14f, -12.0f, 0.0f, -12.0f, 0.0f)
        reflectiveCurveToRelative(0.0f, -15.0f, -1.0f, -16.0f)
        reflectiveCurveTo(141.5f, 58.5f, 131.0f, 59.0f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(151.5f, 88.5f)
        reflectiveCurveToRelative(1.14f, 9.54f, 0.57f, 10.77f)
        curveToRelative(0.0f, 0.0f, 12.43f, -4.77f, 12.43f, -5.77f)
        reflectiveCurveToRelative(0.0f, -9.0f, -1.0f, -11.0f)
        arcToRelative(27.36f, 27.36f, 0.0f, false, false, -4.0f, -5.0f)
        reflectiveCurveToRelative(1.59f, 1.44f, -0.21f, 3.72f)
        reflectiveCurveToRelative(-5.36f, 6.31f, -8.08f, 6.3f)
        close()
        moveTo(121.5f, 80.5f)
        reflectiveCurveToRelative(-7.3f, 1.22f, -8.65f, 3.11f)
        reflectiveCurveToRelative(0.21f, 5.68f, 3.93f, 6.29f)
        reflectiveCurveToRelative(12.31f, 2.13f, 27.0f, -0.63f)
        lineToRelative(7.42f, -1.75f)
        lineToRelative(-10.71f, -4.0f)
        arcTo(38.39f, 38.39f, 0.0f, false, false, 121.5f, 80.5f)
        close()
        moveTo(137.0f, 67.0f)
        lineToRelative(3.87f, 16.18f)
        reflectiveCurveTo(147.0f, 80.0f, 147.0f, 77.0f)
        reflectiveCurveToRelative(0.0f, -13.73f, 0.0f, -13.73f)
        reflectiveCurveTo(145.09f, 67.0f, 137.0f, 67.0f)
        close()
        moveTo(136.18f, 59.0f)
        lineToRelative(0.82f, 8.0f)
        reflectiveCurveToRelative(-18.0f, 2.0f, -20.0f, -2.0f)
        reflectiveCurveTo(130.36f, 58.0f, 136.18f, 59.0f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(121.5f, 75.5f)
        reflectiveCurveToRelative(0.0f, 2.0f, 6.0f, 3.0f)
        reflectiveCurveToRelative(13.0f, -2.0f, 16.0f, -4.0f)
        reflectiveCurveToRelative(3.33f, -3.41f, 3.33f, -3.41f)
        lineToRelative(0.67f, 6.41f)
        arcToRelative(10.67f, 10.67f, 0.0f, false, true, -8.0f, 6.0f)
        curveToRelative(-13.0f, 3.0f, -16.91f, -3.58f, -16.91f, -3.58f)
        close()
    }
private val pathData9 =
    PathData {
        moveTo(132.5f, 67.5f)
        reflectiveCurveToRelative(15.0f, -1.0f, 14.0f, -5.0f)
        reflectiveCurveToRelative(-14.0f, -3.56f, -14.0f, -3.56f)
        reflectiveCurveTo(118.0f, 60.0f, 117.26f, 64.25f)
        reflectiveCurveTo(132.5f, 67.5f, 132.5f, 67.5f)
        close()

        moveTo(113.16f, 83.3f)
        reflectiveCurveToRelative(-6.66f, 9.2f, 23.18f, 7.0f)
        curveToRelative(25.17f, -1.75f, 23.8f, -12.39f, 23.8f, -12.39f)
        lineToRelative(3.36f, 4.64f)
        reflectiveCurveToRelative(2.0f, 10.0f, 1.0f, 11.0f)
        reflectiveCurveToRelative(-18.0f, 10.0f, -33.68f, 10.0f)
        arcToRelative(45.47f, 45.47f, 0.0f, false, true, -16.46f, -3.35f)
        arcToRelative(7.75f, 7.75f, 0.0f, false, true, -2.86f, -1.61f)
        lineToRelative(-1.2f, -10.0f)
        arcTo(5.34f, 5.34f, 0.0f, false, true, 113.16f, 83.3f)
        close()
    }
