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
fun Accessory8Brush() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    accessory8Brush(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun accessory8Brush(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor)
    builder.addPath(pathData2, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData3, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 1.0f)
    builder.addPath(pathData4, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData5, fill = SolidColor(Color(0xFF716558)), stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData6, fill = SolidColor(Color(0xFF9a8479)), stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData7, fill = SolidColor(Color(0xFF716558)), stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData8, fill = SolidColor(Color(0xFFc1b49a)), stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData9, fill = SolidColor(Color(0xFFc1b49a)), stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData10, fill = Black, fillAlpha = 0.2f)
}

private val pathData1 =
    PathData {
        moveTo(135.0f, 83.0f)
        reflectiveCurveToRelative(-13.0f, 2.0f, -13.0f, 5.0f)
        arcToRelative(54.33f, 54.33f, 0.0f, false, false, 0.5f, 6.5f)
        reflectiveCurveToRelative(4.0f, 4.0f, 13.0f, 3.0f)
        arcToRelative(37.83f, 37.83f, 0.0f, false, false, 16.0f, -6.0f)
        verticalLineToRelative(-5.0f)
        lineToRelative(-3.0f, -4.0f)
        reflectiveCurveTo(143.5f, 81.5f, 135.0f, 83.0f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(135.0f, 83.0f)
        reflectiveCurveToRelative(-13.0f, 2.0f, -13.0f, 5.0f)
        arcToRelative(54.33f, 54.33f, 0.0f, false, false, 0.5f, 6.5f)
        reflectiveCurveToRelative(4.0f, 4.0f, 13.0f, 3.0f)
        arcToRelative(37.83f, 37.83f, 0.0f, false, false, 16.0f, -6.0f)
        verticalLineToRelative(-5.0f)
        lineToRelative(-3.0f, -4.0f)
        reflectiveCurveTo(143.5f, 81.5f, 135.0f, 83.0f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(123.5f, 88.5f)
        reflectiveCurveToRelative(2.0f, 3.0f, 10.0f, 2.0f)
        reflectiveCurveToRelative(14.0f, -3.0f, 15.0f, -5.0f)
        reflectiveCurveToRelative(1.19f, -2.37f, -1.41f, -3.18f)
        reflectiveCurveToRelative(-13.22f, 0.86f, -16.41f, 1.52f)
        reflectiveCurveTo(122.5f, 85.5f, 123.5f, 88.5f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(123.5f, 88.5f)
        reflectiveCurveToRelative(2.0f, 3.0f, 10.0f, 2.0f)
        arcToRelative(47.41f, 47.41f, 0.0f, false, false, 11.86f, -2.78f)
        curveToRelative(-0.38f, 0.16f, -4.11f, -2.4f, -4.11f, -2.4f)
        reflectiveCurveToRelative(1.43f, -3.14f, 2.0f, -3.18f)
        arcToRelative(105.65f, 105.65f, 0.0f, false, false, -12.56f, 1.69f)
        curveTo(127.5f, 84.5f, 122.5f, 85.5f, 123.5f, 88.5f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(139.5f, 66.5f)
        reflectiveCurveToRelative(-4.0f, -16.0f, 3.0f, -27.0f)
        curveToRelative(0.0f, 0.0f, -2.0f, -7.0f, -6.0f, -3.0f)
        curveToRelative(0.0f, 0.0f, -4.0f, 3.0f, -5.0f, 15.0f)
        arcToRelative(66.0f, 66.0f, 0.0f, false, false, 2.0f, 22.0f)
        reflectiveCurveTo(138.5f, 73.5f, 139.5f, 66.5f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(119.5f, 36.5f)
        reflectiveCurveToRelative(1.0f, 13.0f, 3.0f, 19.0f)
        reflectiveCurveToRelative(4.0f, 11.0f, 6.0f, 16.0f)
        reflectiveCurveToRelative(5.0f, 7.0f, 6.0f, 5.0f)
        reflectiveCurveToRelative(0.0f, -3.0f, -2.0f, -10.0f)
        reflectiveCurveToRelative(-5.0f, -18.0f, -5.0f, -25.0f)
        verticalLineToRelative(-7.0f)
        arcToRelative(10.34f, 10.34f, 0.0f, false, false, -4.0f, -1.0f)
        curveTo(121.5f, 33.5f, 119.5f, 34.5f, 119.5f, 36.5f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(110.5f, 50.5f)
        reflectiveCurveToRelative(8.0f, 8.0f, 10.0f, 13.0f)
        reflectiveCurveToRelative(5.0f, 13.0f, 6.0f, 10.0f)
        reflectiveCurveToRelative(-3.0f, -18.0f, -6.0f, -22.0f)
        arcToRelative(61.22f, 61.22f, 0.0f, false, false, -5.0f, -6.0f)
        reflectiveCurveTo(110.5f, 45.5f, 110.5f, 50.5f)
        close()
    }
private val pathData8 =
    PathData {
        moveTo(136.5f, 86.5f)
        reflectiveCurveToRelative(-5.0f, -19.0f, -19.0f, -28.0f)
        reflectiveCurveToRelative(-20.0f, -7.0f, -20.0f, -7.0f)
        reflectiveCurveToRelative(-4.0f, 3.0f, 1.0f, 6.0f)
        reflectiveCurveToRelative(12.0f, 5.0f, 18.0f, 10.0f)
        reflectiveCurveToRelative(12.0f, 14.0f, 12.0f, 16.0f)
        reflectiveCurveTo(131.5f, 88.5f, 136.5f, 86.5f)
        close()
    }
private val pathData9 =
    PathData {
        moveTo(134.5f, 73.5f)
        reflectiveCurveToRelative(6.0f, -25.0f, 16.0f, -32.0f)
        reflectiveCurveToRelative(9.0f, 2.0f, 9.0f, 2.0f)
        verticalLineToRelative(4.0f)
        reflectiveCurveToRelative(-4.0f, -2.0f, -8.0f, 5.0f)
        reflectiveCurveToRelative(-9.0f, 19.0f, -9.0f, 23.0f)
        verticalLineToRelative(8.0f)
        reflectiveCurveToRelative(-4.0f, 5.0f, -6.0f, 3.0f)
        curveToRelative(0.0f, 0.0f, -3.24f, -7.59f, -3.12f, -8.3f)
        reflectiveCurveTo(134.5f, 73.5f, 134.5f, 73.5f)
        close()
    }
private val pathData10 =
    PathData {
        moveTo(144.36f, 88.12f)
        verticalLineToRelative(7.11f)
        lineToRelative(7.14f, -3.73f)
        lineToRelative(0.07f, -4.64f)
        lineTo(150.0f, 84.0f)
        arcTo(11.59f, 11.59f, 0.0f, false, true, 144.36f, 88.12f)
        close()
    }
