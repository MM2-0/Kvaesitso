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
import com.vitorpamplona.amethyst.commons.robohash.LightRed
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Eyes3Scott() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    eyes3Scott(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun eyes3Scott(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData3, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 1.0f)
    builder.addPath(pathData4, stroke = Black, strokeLineWidth = 0.75f)
    builder.addPath(pathData5, fill = LightRed, stroke = Black, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(127.5f, 127.5f)
        curveToRelative(11.08f, -0.53f, 43.0f, -2.0f, 43.0f, 11.0f)
        curveToRelative(1.0f, 15.0f, -16.69f, 15.0f, -23.34f, 15.52f)
        reflectiveCurveToRelative(-37.66f, 1.48f, -47.66f, 0.48f)
        reflectiveCurveToRelative(-15.0f, -4.0f, -15.0f, -12.0f)
        reflectiveCurveToRelative(8.0f, -11.0f, 17.0f, -13.0f)
        reflectiveCurveTo(121.0f, 127.81f, 127.5f, 127.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(94.39f, 132.09f)
        reflectiveCurveToRelative(2.78f, -4.25f, 37.95f, -4.92f)
        reflectiveCurveToRelative(37.17f, 6.33f, 38.17f, 11.33f)
        reflectiveCurveToRelative(-3.0f, 10.76f, -6.0f, 11.88f)
        curveToRelative(0.0f, 0.0f, 5.52f, -6.38f, -2.24f, -11.63f)
        reflectiveCurveToRelative(-22.72f, -6.58f, -29.74f, -6.91f)
        reflectiveCurveToRelative(-24.86f, -0.88f, -27.94f, -0.61f)
        reflectiveCurveTo(96.28f, 131.67f, 94.39f, 132.09f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(127.5f, 127.5f)
        curveToRelative(11.08f, -0.53f, 43.0f, -2.0f, 43.0f, 11.0f)
        curveToRelative(1.0f, 15.0f, -16.69f, 15.0f, -23.34f, 15.52f)
        reflectiveCurveToRelative(-37.66f, 1.48f, -47.66f, 0.48f)
        reflectiveCurveToRelative(-15.0f, -4.0f, -15.0f, -12.0f)
        reflectiveCurveToRelative(8.0f, -11.0f, 17.0f, -13.0f)
        reflectiveCurveTo(121.0f, 127.81f, 127.5f, 127.5f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(121.5f, 131.5f)
        curveToRelative(-19.0f, 0.0f, -36.0f, -3.0f, -37.0f, 11.0f)
        reflectiveCurveToRelative(19.0f, 12.0f, 40.0f, 12.0f)
        reflectiveCurveToRelative(42.0f, 0.0f, 42.0f, -9.0f)
        curveTo(166.5f, 134.5f, 140.5f, 131.5f, 121.5f, 131.5f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(121.5f, 140.5f)
        reflectiveCurveToRelative(29.0f, 0.0f, 32.0f, 1.0f)
        reflectiveCurveToRelative(2.0f, 4.0f, -1.0f, 4.0f)
        reflectiveCurveToRelative(-22.0f, -1.0f, -31.0f, -1.0f)
        reflectiveCurveToRelative(-22.0f, 1.0f, -26.0f, 0.0f)
        reflectiveCurveToRelative(-1.0f, -2.0f, 11.0f, -3.0f)
        reflectiveCurveTo(121.5f, 140.5f, 121.5f, 140.5f)
        close()
    }
