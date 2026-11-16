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
fun Face5Baloon() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    face5Baloon(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun face5Baloon(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.5f)
    builder.addPath(pathData4, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData5, fill = Black, fillAlpha = 0.4f)
}

private val pathData1 =
    PathData {
        moveTo(156.0f, 83.0f)
        reflectiveCurveToRelative(-55.0f, 5.0f, -66.0f, 53.0f)
        reflectiveCurveToRelative(9.5f, 66.5f, 9.5f, 66.5f)
        reflectiveCurveToRelative(9.0f, 8.0f, 12.0f, 8.0f)
        reflectiveCurveToRelative(2.0f, -2.0f, 1.0f, -4.0f)
        arcToRelative(7.47f, 7.47f, 0.0f, false, false, -3.0f, -3.0f)
        reflectiveCurveToRelative(4.0f, 1.0f, 5.0f, 10.0f)
        reflectiveCurveToRelative(1.0f, 32.0f, 1.0f, 38.0f)
        verticalLineToRelative(12.0f)
        reflectiveCurveToRelative(2.0f, 8.0f, 17.0f, 8.0f)
        reflectiveCurveToRelative(30.0f, -7.0f, 30.0f, -7.0f)
        reflectiveCurveToRelative(-4.0f, -34.0f, -2.0f, -41.0f)
        reflectiveCurveToRelative(9.0f, -20.0f, 15.0f, -26.0f)
        reflectiveCurveToRelative(3.0f, -1.0f, 0.0f, 2.0f)
        arcToRelative(50.18f, 50.18f, 0.0f, false, false, -6.0f, 8.0f)
        reflectiveCurveToRelative(17.0f, -7.0f, 23.0f, -14.0f)
        reflectiveCurveToRelative(15.0f, -10.0f, 16.0f, -33.0f)
        reflectiveCurveTo(214.5f, 81.5f, 156.0f, 83.0f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(181.5f, 93.5f)
        reflectiveCurveToRelative(18.0f, 16.0f, 16.0f, 44.0f)
        reflectiveCurveToRelative(-6.0f, 41.0f, -28.0f, 54.0f)
        curveToRelative(0.0f, 0.0f, -16.0f, 3.0f, -19.0f, 28.0f)
        reflectiveCurveToRelative(0.0f, 49.27f, 0.0f, 49.27f)
        lineToRelative(12.0f, -4.27f)
        reflectiveCurveToRelative(-6.87f, -36.09f, 0.07f, -46.54f)
        reflectiveCurveToRelative(7.21f, -11.15f, 17.07f, -15.3f)
        reflectiveCurveToRelative(30.94f, -21.59f, 29.4f, -50.87f)
        reflectiveCurveToRelative(-0.09f, -56.0f, -35.32f, -67.16f)
        lineToRelative(-3.94f, -0.77f)
        reflectiveCurveTo(168.5f, 84.5f, 181.5f, 93.5f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(110.5f, 117.5f)
        reflectiveCurveToRelative(-7.0f, 1.0f, -10.0f, 11.0f)
        reflectiveCurveToRelative(-11.0f, 25.0f, -8.0f, 35.0f)
        reflectiveCurveToRelative(7.0f, 10.0f, 9.0f, 5.0f)
        reflectiveCurveToRelative(1.0f, -17.0f, 5.0f, -28.0f)
        reflectiveCurveTo(116.5f, 119.5f, 110.5f, 117.5f)
        close()
        moveTo(122.5f, 99.5f)
        reflectiveCurveToRelative(-9.0f, 6.0f, -9.0f, 9.0f)
        reflectiveCurveToRelative(0.0f, 4.0f, 5.0f, 4.0f)
        reflectiveCurveToRelative(13.0f, -4.0f, 13.0f, -9.0f)
        reflectiveCurveTo(128.5f, 96.5f, 122.5f, 99.5f)
        close()
    }
