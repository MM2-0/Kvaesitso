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
fun Face3Oval() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    face3Oval(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun face3Oval(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.5f)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData3, fill = Black, fillAlpha = 0.4f)
}

private val pathData1 =
    PathData {
        moveTo(148.0f, 87.0f)
        reflectiveCurveToRelative(-56.5f, 0.5f, -56.5f, 63.93f)
        reflectiveCurveToRelative(33.0f, 84.57f, 48.0f, 84.57f)
        reflectiveCurveToRelative(45.0f, -8.05f, 51.0f, -41.28f)
        curveToRelative(0.0f, 0.0f, 1.0f, -16.11f, 0.0f, -25.17f)
        reflectiveCurveToRelative(-1.0f, -29.2f, -1.0f, -32.22f)
        reflectiveCurveTo(186.5f, 88.51f, 148.0f, 87.0f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(151.5f, 92.5f)
        reflectiveCurveToRelative(17.0f, 12.0f, 22.0f, 42.0f)
        reflectiveCurveToRelative(7.0f, 44.0f, 3.0f, 63.0f)
        reflectiveCurveTo(167.0f, 226.0f, 161.75f, 229.25f)
        reflectiveCurveToRelative(21.67f, -4.94f, 26.71f, -26.84f)
        reflectiveCurveToRelative(1.77f, -35.94f, 1.77f, -35.94f)
        lineToRelative(-1.0f, -32.24f)
        reflectiveCurveToRelative(-5.0f, -35.64f, -26.88f, -43.18f)
        curveToRelative(0.0f, 0.0f, -11.87f, -4.54f, -17.37f, -3.0f)
        reflectiveCurveTo(148.5f, 89.5f, 151.5f, 92.5f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(113.5f, 110.5f)
        reflectiveCurveToRelative(-4.0f, 0.0f, -4.0f, 4.0f)
        reflectiveCurveToRelative(4.0f, 7.0f, 7.0f, 5.0f)
        reflectiveCurveToRelative(5.0f, -5.0f, 4.0f, -7.0f)
        reflectiveCurveTo(118.5f, 108.5f, 113.5f, 110.5f)
        close()
        moveTo(108.5f, 123.5f)
        reflectiveCurveToRelative(-4.0f, -1.0f, -7.0f, 5.0f)
        reflectiveCurveToRelative(-4.0f, 15.0f, -3.0f, 24.0f)
        arcToRelative(33.42f, 33.42f, 0.0f, false, false, 8.0f, 18.0f)
        curveToRelative(2.0f, 2.0f, 6.0f, 3.0f, 5.0f, -4.0f)
        reflectiveCurveToRelative(-5.0f, -14.0f, -4.0f, -25.0f)
        reflectiveCurveTo(114.5f, 124.5f, 108.5f, 123.5f)
        close()
    }
