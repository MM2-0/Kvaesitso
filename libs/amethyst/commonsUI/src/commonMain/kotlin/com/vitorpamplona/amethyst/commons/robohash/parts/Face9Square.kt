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
fun Face9Square() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    face9Square(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun face9Square(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 2.0f)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData5, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(177.0f, 71.0f)
        reflectiveCurveToRelative(-73.0f, -2.0f, -82.0f, 51.0f)
        curveToRelative(0.0f, 0.0f, -4.5f, 17.5f, 1.5f, 40.5f)
        reflectiveCurveToRelative(14.0f, 50.0f, 14.0f, 50.0f)
        reflectiveCurveToRelative(6.0f, 10.0f, 46.0f, 6.0f)
        reflectiveCurveToRelative(75.0f, -25.0f, 75.0f, -25.0f)
        reflectiveCurveToRelative(-10.0f, -100.0f, -16.0f, -108.0f)
        reflectiveCurveTo(203.0f, 72.0f, 177.0f, 71.0f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(135.5f, 78.5f)
        reflectiveCurveToRelative(1.0f, 4.0f, -5.0f, 9.0f)
        reflectiveCurveToRelative(-20.0f, 12.0f, -25.0f, 31.0f)
        reflectiveCurveToRelative(0.0f, 29.0f, 2.0f, 37.0f)
        reflectiveCurveToRelative(7.0f, 25.0f, 6.0f, 35.0f)
        reflectiveCurveToRelative(-4.75f, 16.0f, -4.75f, 16.0f)
        reflectiveCurveToRelative(-21.38f, -60.92f, -12.82f, -89.0f)
        reflectiveCurveTo(129.0f, 81.0f, 135.5f, 78.5f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(205.5f, 86.5f)
        reflectiveCurveToRelative(-27.0f, 2.0f, -29.0f, 28.0f)
        reflectiveCurveToRelative(9.0f, 73.0f, 9.0f, 73.0f)
        lineToRelative(5.38f, 23.67f)
        lineTo(231.5f, 193.5f)
        reflectiveCurveToRelative(-8.0f, -94.0f, -15.0f, -106.0f)
        curveTo(216.48f, 87.51f, 208.5f, 86.5f, 205.5f, 86.5f)
        close()
    }
