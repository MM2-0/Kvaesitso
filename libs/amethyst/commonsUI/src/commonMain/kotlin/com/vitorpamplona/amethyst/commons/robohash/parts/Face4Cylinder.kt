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
fun Face4Cylinder() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    face4Cylinder(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun face4Cylinder(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.5f)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData3, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 1.0f)
    builder.addPath(pathData5, fill = Black, fillAlpha = 0.2f)
}

private val pathData1 =
    PathData {
        moveTo(91.5f, 107.5f)
        reflectiveCurveToRelative(4.0f, 64.0f, 4.0f, 77.0f)
        reflectiveCurveToRelative(1.0f, 38.0f, 1.0f, 38.0f)
        reflectiveCurveToRelative(4.0f, 11.0f, 22.0f, 10.0f)
        curveToRelative(36.5f, 0.5f, 60.0f, -11.0f, 66.0f, -16.0f)
        curveToRelative(0.0f, 0.0f, -3.0f, -106.0f, -3.0f, -116.0f)
        reflectiveCurveToRelative(4.0f, -20.0f, -36.0f, -22.0f)
        curveToRelative(0.0f, 0.0f, -35.59f, 1.45f, -44.8f, 8.73f)
        curveToRelative(0.0f, 0.0f, -10.2f, 2.27f, -9.2f, 7.27f)
        reflectiveCurveTo(91.5f, 107.5f, 91.5f, 107.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(95.8f, 102.0f)
        reflectiveCurveToRelative(2.7f, 53.53f, 3.7f, 68.53f)
        reflectiveCurveToRelative(5.0f, 42.0f, 5.0f, 45.0f)
        verticalLineToRelative(14.71f)
        reflectiveCurveToRelative(-7.0f, -3.71f, -8.0f, -7.71f)
        reflectiveCurveToRelative(-3.65f, -88.21f, -3.83f, -95.61f)
        reflectiveCurveTo(91.5f, 107.5f, 91.5f, 107.5f)
        verticalLineToRelative(-13.0f)
        reflectiveCurveTo(91.1f, 100.43f, 95.8f, 102.0f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(145.5f, 78.5f)
        reflectiveCurveToRelative(-53.0f, 5.0f, -54.0f, 16.0f)
        reflectiveCurveToRelative(23.0f, 14.0f, 37.0f, 13.0f)
        reflectiveCurveToRelative(52.0f, -7.0f, 53.0f, -16.0f)
        reflectiveCurveTo(156.5f, 77.5f, 145.5f, 78.5f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(168.5f, 103.5f)
        reflectiveCurveToRelative(1.0f, 49.0f, 1.0f, 57.0f)
        reflectiveCurveToRelative(1.0f, 35.0f, 1.0f, 41.0f)
        reflectiveCurveToRelative(-1.0f, 22.92f, -1.0f, 22.92f)
        lineToRelative(15.0f, -7.92f)
        reflectiveCurveToRelative(-2.94f, -96.67f, -3.0f, -100.83f)
        reflectiveCurveToRelative(0.0f, -25.17f, 0.0f, -25.17f)
        curveToRelative(0.34f, 3.51f, -3.36f, 6.57f, -11.0f, 9.17f)
        lineToRelative(-2.0f, 0.72f)
        close()
    }
