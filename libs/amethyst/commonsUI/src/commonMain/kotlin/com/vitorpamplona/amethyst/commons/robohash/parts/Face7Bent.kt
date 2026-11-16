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
fun Face7Bent() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    face7Bent(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun face7Bent(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.5f)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData5, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 1.0f)
}

private val pathData1 =
    PathData {
        moveTo(212.5f, 90.5f)
        reflectiveCurveToRelative(-19.5f, -20.5f, -55.0f, -12.0f)
        curveToRelative(-31.0f, 8.0f, -61.0f, 14.0f, -64.0f, 60.0f)
        arcToRelative(122.71f, 122.71f, 0.0f, false, false, 4.0f, 35.0f)
        curveToRelative(5.0f, 19.0f, 11.0f, 42.0f, 12.0f, 45.0f)
        curveToRelative(0.0f, 0.0f, 8.0f, 9.0f, 25.0f, 9.0f)
        reflectiveCurveToRelative(56.0f, -10.0f, 56.0f, -10.0f)
        reflectiveCurveToRelative(11.0f, -5.0f, 12.0f, -6.0f)
        reflectiveCurveToRelative(-20.0f, -60.0f, -11.0f, -86.0f)
        curveToRelative(0.0f, 0.0f, 3.0f, -7.0f, 14.0f, -8.0f)
        reflectiveCurveToRelative(14.0f, 0.0f, 14.0f, 0.0f)
        reflectiveCurveTo(217.5f, 96.5f, 212.5f, 90.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(130.5f, 88.5f)
        reflectiveCurveToRelative(-9.0f, 6.0f, -12.0f, 9.0f)
        reflectiveCurveToRelative(-16.0f, 15.0f, -18.0f, 28.0f)
        reflectiveCurveToRelative(2.0f, 38.0f, 4.0f, 46.0f)
        reflectiveCurveToRelative(5.0f, 21.0f, 4.0f, 26.0f)
        reflectiveCurveToRelative(-2.93f, 6.66f, -2.93f, 6.66f)
        reflectiveCurveTo(99.5f, 183.5f, 97.5f, 173.5f)
        reflectiveCurveToRelative(-6.0f, -27.0f, -1.52f, -51.0f)
        reflectiveCurveTo(130.5f, 85.5f, 130.5f, 88.5f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(209.5f, 92.5f)
        reflectiveCurveToRelative(-15.0f, 0.0f, -21.0f, 6.0f)
        reflectiveCurveToRelative(-16.0f, 16.0f, -13.0f, 41.0f)
        reflectiveCurveToRelative(12.0f, 65.0f, 12.0f, 65.0f)
        lineToRelative(3.0f, 13.0f)
        lineToRelative(12.0f, -6.0f)
        reflectiveCurveToRelative(-22.77f, -76.47f, -7.39f, -90.23f)
        curveToRelative(0.0f, 0.0f, 2.39f, -4.77f, 24.39f, -3.77f)
        curveToRelative(0.0f, 0.0f, -0.4f, -21.0f, -5.7f, -25.0f)
        close()
    }
