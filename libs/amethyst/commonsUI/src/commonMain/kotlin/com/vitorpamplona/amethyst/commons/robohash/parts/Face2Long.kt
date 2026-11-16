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
fun Face2Long() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    face2Long(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun face2Long(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.5f)
    builder.addPath(pathData4, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData5, fill = Black, fillAlpha = 0.4f)
}

private val pathData1 =
    PathData {
        moveTo(147.0f, 88.0f)
        curveToRelative(-20.15f, -0.5f, -56.5f, 14.5f, -56.5f, 57.5f)
        curveToRelative(0.0f, 0.0f, 1.0f, 22.0f, 6.0f, 41.0f)
        reflectiveCurveToRelative(10.0f, 34.0f, 10.0f, 37.0f)
        curveToRelative(0.0f, 0.0f, 3.0f, 12.0f, 25.0f, 11.0f)
        reflectiveCurveToRelative(62.0f, -15.0f, 62.0f, -15.0f)
        reflectiveCurveToRelative(-2.0f, -31.0f, -3.0f, -49.0f)
        reflectiveCurveTo(196.79f, 89.24f, 147.0f, 88.0f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(135.5f, 96.5f)
        reflectiveCurveToRelative(36.0f, -4.0f, 45.0f, 43.0f)
        curveToRelative(0.0f, 0.0f, 3.0f, 24.0f, 3.0f, 35.0f)
        reflectiveCurveToRelative(-1.0f, 25.0f, 1.0f, 36.0f)
        lineToRelative(2.0f, 11.0f)
        lineToRelative(7.0f, -2.0f)
        reflectiveCurveToRelative(-2.53f, -55.76f, -3.27f, -70.88f)
        reflectiveCurveTo(188.5f, 90.81f, 150.0f, 88.16f)
        curveToRelative(0.0f, 0.0f, -19.78f, -1.0f, -33.64f, 8.7f)
        curveToRelative(0.0f, 0.0f, -4.86f, 4.65f, 0.14f, 2.65f)
        reflectiveCurveTo(132.5f, 96.5f, 135.5f, 96.5f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(122.5f, 104.5f)
        reflectiveCurveToRelative(-4.0f, 1.0f, -4.0f, 4.0f)
        reflectiveCurveToRelative(2.0f, 7.0f, 5.0f, 6.0f)
        reflectiveCurveToRelative(5.0f, -5.0f, 4.0f, -7.0f)
        reflectiveCurveTo(125.5f, 103.5f, 122.5f, 104.5f)
        close()
        moveTo(115.5f, 111.5f)
        reflectiveCurveToRelative(-9.0f, -6.0f, -17.0f, 12.0f)
        reflectiveCurveToRelative(-1.0f, 37.0f, -1.0f, 37.0f)
        reflectiveCurveToRelative(2.0f, 6.0f, 8.0f, 6.0f)
        reflectiveCurveToRelative(4.0f, -9.0f, 4.0f, -9.0f)
        reflectiveCurveToRelative(-4.0f, -13.0f, -1.0f, -24.0f)
        reflectiveCurveToRelative(7.0f, -13.0f, 7.0f, -13.0f)
        reflectiveCurveTo(120.5f, 115.5f, 115.5f, 111.5f)
        close()
    }
