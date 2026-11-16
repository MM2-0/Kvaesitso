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
fun Eyes9Shield() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    eyes9Shield(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun eyes9Shield(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData3, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData6, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(129.5f, 123.5f)
        reflectiveCurveToRelative(-30.0f, 3.0f, -31.0f, 15.0f)
        reflectiveCurveToRelative(8.0f, 13.0f, 15.0f, 14.0f)
        reflectiveCurveToRelative(20.0f, -2.0f, 26.0f, -3.0f)
        reflectiveCurveToRelative(25.0f, 1.0f, 27.0f, -13.0f)
        reflectiveCurveToRelative(-17.0f, -14.0f, -17.0f, -14.0f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(116.92f, 125.8f)
        reflectiveCurveToRelative(-15.42f, 2.7f, -16.42f, 12.7f)
        reflectiveCurveToRelative(15.5f, 13.0f, 24.25f, 10.5f)
        arcTo(97.37f, 97.37f, 0.0f, false, true, 150.0f, 145.44f)
        curveToRelative(6.5f, 0.06f, 16.78f, -3.69f, 16.64f, -11.31f)
        reflectiveCurveTo(157.5f, 122.5f, 149.5f, 122.5f)
        reflectiveCurveToRelative(-21.12f, 1.13f, -21.12f, 1.13f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(130.5f, 123.5f)
        lineToRelative(-8.0f, 25.5f)
        curveToRelative(1.6f, -0.06f, 5.38f, -0.65f, 9.0f, -1.2f)
        lineToRelative(7.0f, -24.8f)
        arcTo(47.26f, 47.26f, 0.0f, false, true, 130.5f, 123.5f)
        close()
        moveTo(120.77f, 124.91f)
        lineToRelative(-7.77f, 25.09f)
        lineToRelative(5.0f, 0.0f)
        lineToRelative(7.41f, -25.94f)
        lineToRelative(-4.64f, 0.85f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(106.61f, 129.46f)
        reflectiveCurveToRelative(-6.11f, 3.0f, -6.11f, 9.0f)
        reflectiveCurveToRelative(5.0f, 13.0f, 20.0f, 11.0f)
        reflectiveCurveToRelative(23.0f, -4.0f, 28.0f, -4.0f)
        reflectiveCurveToRelative(17.29f, -2.5f, 18.15f, -10.25f)
        curveToRelative(0.0f, 0.0f, 0.3f, 10.92f, -14.42f, 13.09f)
        reflectiveCurveToRelative(-16.25f, 1.2f, -21.0f, 2.68f)
        reflectiveCurveToRelative(-28.06f, 4.23f, -32.4f, -7.15f)
        curveTo(98.84f, 143.87f, 95.71f, 134.43f, 106.61f, 129.46f)
        close()
    }
