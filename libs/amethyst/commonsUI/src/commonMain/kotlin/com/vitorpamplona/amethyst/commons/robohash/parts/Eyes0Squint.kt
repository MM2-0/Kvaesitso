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
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Eyes0Squint() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    eyes0Squint(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun eyes0Squint(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor)
    builder.addPath(pathData3, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 1.0f)
    builder.addPath(pathData4, fill = Brown, stroke = Black, strokeLineWidth = 0.5f)
}

private val pathData1 =
    PathData {
        moveTo(144.5f, 141.5f)
        reflectiveCurveToRelative(5.0f, 9.0f, 9.0f, 7.0f)
        reflectiveCurveToRelative(13.0f, -9.0f, 13.0f, -9.0f)
        verticalLineToRelative(-9.0f)
        reflectiveCurveToRelative(-2.0f, -1.0f, -7.0f, 0.0f)
        reflectiveCurveToRelative(-14.0f, 5.0f, -14.0f, 5.0f)
        reflectiveCurveTo(142.5f, 137.5f, 144.5f, 141.5f)
        close()
        moveTo(118.0f, 141.0f)
        lineToRelative(-5.0f, 10.0f)
        reflectiveCurveToRelative(-7.5f, -2.5f, -10.5f, -4.5f)
        reflectiveCurveToRelative(-4.0f, -3.0f, -4.0f, -6.0f)
        lineToRelative(1.0f, -3.0f)
        arcToRelative(13.6f, 13.6f, 0.0f, false, true, 7.0f, 0.0f)
        curveToRelative(4.0f, 1.0f, 11.0f, 2.0f, 11.0f, 2.0f)
        reflectiveCurveTo(118.5f, 139.5f, 118.0f, 141.0f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(144.5f, 141.5f)
        reflectiveCurveToRelative(5.0f, 9.0f, 9.0f, 7.0f)
        reflectiveCurveToRelative(13.0f, -9.0f, 13.0f, -9.0f)
        verticalLineToRelative(-9.0f)
        reflectiveCurveToRelative(-2.0f, -1.0f, -7.0f, 0.0f)
        reflectiveCurveToRelative(-14.0f, 5.0f, -14.0f, 5.0f)
        reflectiveCurveTo(142.5f, 137.5f, 144.5f, 141.5f)
        close()
        moveTo(118.0f, 141.0f)
        lineToRelative(-5.0f, 10.0f)
        reflectiveCurveToRelative(-7.5f, -2.5f, -10.5f, -4.5f)
        reflectiveCurveToRelative(-4.0f, -3.0f, -4.0f, -6.0f)
        lineToRelative(1.0f, -3.0f)
        arcToRelative(13.6f, 13.6f, 0.0f, false, true, 7.0f, 0.0f)
        arcToRelative(37.46f, 37.46f, 0.0f, false, false, 8.0f, 1.0f)
        reflectiveCurveTo(118.5f, 139.5f, 118.0f, 141.0f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(144.5f, 139.5f)
        reflectiveCurveToRelative(2.0f, 2.0f, 6.0f, 1.0f)
        reflectiveCurveToRelative(10.0f, -6.0f, 12.0f, -7.0f)
        arcToRelative(18.66f, 18.66f, 0.0f, false, false, 4.0f, -3.0f)
        reflectiveCurveToRelative(-3.22f, -1.0f, -9.11f, 0.52f)
        arcToRelative(67.92f, 67.92f, 0.0f, false, false, -11.89f, 4.48f)
        reflectiveCurveTo(142.5f, 137.5f, 144.5f, 139.5f)
        close()
        moveTo(100.5f, 139.5f)
        lineToRelative(6.0f, 3.0f)
        curveToRelative(2.0f, 1.0f, 9.7f, 2.6f, 11.35f, -1.2f)
        reflectiveCurveToRelative(-11.6f, -4.69f, -16.0f, -4.24f)
        curveToRelative(0.0f, 0.0f, -3.0f, -0.17f, -2.67f, 0.94f)
        arcTo(2.3f, 2.3f, 0.0f, false, false, 100.5f, 139.5f)
        close()
    }
