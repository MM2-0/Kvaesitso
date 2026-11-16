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
import com.vitorpamplona.amethyst.commons.robohash.MediumGray
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Accessory9Horn() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    accessory9Horn(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun accessory9Horn(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData3, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData4, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData5, fill = Black, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData6, fill = MediumGray)
    builder.addPath(pathData7, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData8, fill = Black, fillAlpha = 0.4f)
}

private val pathData1 =
    PathData {
        moveTo(122.5f, 88.5f)
        verticalLineToRelative(6.0f)
        reflectiveCurveToRelative(3.0f, 4.0f, 13.0f, 3.0f)
        curveToRelative(12.5f, -0.5f, 16.0f, -5.0f, 16.0f, -5.0f)
        verticalLineToRelative(-6.0f)
        lineToRelative(-2.0f, -3.0f)
        arcToRelative(63.26f, 63.26f, 0.0f, false, false, -15.0f, 0.0f)
        curveTo(126.5f, 84.5f, 122.5f, 85.5f, 122.5f, 88.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(122.5f, 88.5f)
        verticalLineToRelative(6.0f)
        reflectiveCurveToRelative(2.0f, 3.0f, 13.0f, 3.0f)
        reflectiveCurveToRelative(16.0f, -5.0f, 16.0f, -5.0f)
        verticalLineToRelative(-6.0f)
        lineToRelative(-2.0f, -3.0f)
        arcToRelative(63.26f, 63.26f, 0.0f, false, false, -15.0f, 0.0f)
        curveTo(126.5f, 84.5f, 122.5f, 85.5f, 122.5f, 88.5f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(142.5f, 89.5f)
        curveToRelative(0.2f, -0.05f, 1.28f, -0.44f, 1.5f, -0.5f)
        curveToRelative(-0.63f, -1.83f, -4.53f, -5.64f, -5.5f, -5.7f)
        curveToRelative(-7.0f, -0.41f, -11.66f, 1.0f, -13.22f, 2.0f)
        curveToRelative(-1.78f, 1.16f, -3.78f, 2.16f, 1.22f, 4.16f)
        curveTo(129.5f, 90.5f, 138.5f, 90.5f, 142.5f, 89.5f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(142.5f, 89.5f)
        curveToRelative(4.0f, -1.0f, 10.83f, -4.83f, 3.92f, -5.92f)
        reflectiveCurveToRelative(-19.36f, 0.59f, -21.14f, 1.75f)
        reflectiveCurveToRelative(-3.78f, 2.16f, 1.22f, 4.16f)
        curveTo(129.5f, 90.5f, 138.5f, 90.5f, 142.5f, 89.5f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(130.5f, 52.5f)
        lineTo(128.24f, 84.0f)
        reflectiveCurveToRelative(1.26f, 3.47f, 7.26f, 2.47f)
        reflectiveCurveToRelative(7.33f, -3.44f, 7.33f, -3.44f)
        lineTo(132.5f, 54.5f)
        reflectiveCurveTo(131.5f, 51.5f, 130.5f, 52.5f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(131.5f, 55.5f)
        lineToRelative(7.06f, 30.26f)
        reflectiveCurveToRelative(3.94f, -0.26f, 3.94f, -2.26f)
        reflectiveCurveToRelative(-10.0f, -29.0f, -10.0f, -29.0f)
        reflectiveCurveTo(130.5f, 50.5f, 131.5f, 55.5f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(130.5f, 52.5f)
        lineTo(128.24f, 84.0f)
        reflectiveCurveToRelative(1.26f, 3.47f, 7.26f, 2.47f)
        reflectiveCurveToRelative(7.33f, -3.44f, 7.33f, -3.44f)
        lineTo(132.5f, 54.5f)
        reflectiveCurveTo(131.5f, 51.5f, 130.5f, 52.5f)
        close()
    }
private val pathData8 =
    PathData {
        moveTo(144.09f, 88.72f)
        verticalLineToRelative(8.0f)
        lineToRelative(7.41f, -4.26f)
        verticalLineToRelative(-6.0f)
        lineToRelative(-2.0f, -3.0f)
        curveTo(150.0f, 85.88f, 147.82f, 87.51f, 144.09f, 88.72f)
        close()
    }
