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
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.commons.robohash.Black
import com.vitorpamplona.amethyst.commons.robohash.LightRed
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Preview
@Composable
fun Accessory4SatellitePreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    accessory4Satellite(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun accessory4Satellite(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData3, stroke = Black, strokeLineWidth = 0.75f)
    builder.addPath(pathData4, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData6, fill = LightRed, stroke = Black, strokeLineWidth = 0.75f)
    builder.addPath(pathData9, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData7, fill = Black, fillAlpha = 0.2f)
}

private val pathData1 =
    PathData {
        moveTo(139.5f, 51.5f)
        reflectiveCurveToRelative(-24.0f, -2.0f, -32.0f, 33.0f)
        curveToRelative(0.0f, 0.0f, -6.0f, 25.0f, 20.0f, 25.0f)
        reflectiveCurveToRelative(32.0f, -17.0f, 34.0f, -32.0f)
        reflectiveCurveTo(149.5f, 51.5f, 139.5f, 51.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(139.5f, 51.5f)
        curveToRelative(-15.88f, 0.08f, -26.77f, 10.62f, -32.0f, 33.0f)
        curveToRelative(0.0f, 0.0f, -5.0f, 26.0f, 20.0f, 25.0f)
        reflectiveCurveToRelative(34.0f, -18.0f, 34.0f, -32.0f)
        curveTo(161.5f, 60.5f, 149.5f, 51.5f, 139.5f, 51.5f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(143.5f, 53.5f)
        reflectiveCurveToRelative(14.0f, 7.0f, 10.0f, 29.0f)
        reflectiveCurveToRelative(-26.0f, 25.0f, -33.0f, 24.0f)
        reflectiveCurveToRelative(-18.0f, -7.0f, -10.34f, -30.84f)
        curveTo(114.5f, 59.5f, 130.5f, 47.5f, 143.5f, 53.5f)
        close()
        moveTo(130.09f, 76.5f)
        lineToRelative(4.46f, 2.0f)
        reflectiveCurveToRelative(0.89f, 2.0f, 0.0f, 3.0f)
        arcToRelative(4.07f, 4.07f, 0.0f, false, true, -2.67f, 1.0f)
        lineTo(129.0f, 80.79f)
        arcTo(5.22f, 5.22f, 0.0f, false, false, 130.09f, 76.5f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(121.5f, 63.5f)
        curveToRelative(-3.0f, 1.87f, -8.0f, 6.0f, -9.0f, 11.0f)
        reflectiveCurveToRelative(-1.0f, 14.0f, 0.0f, 16.0f)
        reflectiveCurveToRelative(4.0f, 1.0f, 4.0f, -2.0f)
        reflectiveCurveToRelative(0.0f, -17.0f, 5.0f, -21.0f)
        reflectiveCurveTo(123.4f, 62.32f, 121.5f, 63.5f)
        close()
        moveTo(128.5f, 57.5f)
        reflectiveCurveToRelative(-6.0f, 4.0f, -3.0f, 5.0f)
        reflectiveCurveToRelative(6.0f, -3.0f, 6.0f, -3.0f)
        reflectiveCurveTo(133.5f, 56.5f, 128.5f, 57.5f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(125.5f, 71.5f)
        reflectiveCurveToRelative(-7.0f, -2.0f, -10.0f, 1.0f)
        curveToRelative(-1.88f, 2.07f, 0.0f, 8.0f, 5.0f, 10.0f)
        reflectiveCurveToRelative(8.0f, -1.0f, 9.0f, -4.0f)
        reflectiveCurveTo(127.5f, 71.5f, 125.5f, 71.5f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(131.0f, 79.0f)
        lineToRelative(4.0f, 2.0f)
        lineToRelative(-0.38f, -2.33f)
        lineToRelative(-4.24f, -2.04f)
        lineToRelative(-0.32f, 1.97f)
        lineToRelative(0.94f, 0.4f)
        close()
        moveTo(123.5f, 71.5f)
        reflectiveCurveToRelative(5.0f, 2.0f, 4.0f, 6.0f)
        reflectiveCurveToRelative(-6.49f, 4.82f, -8.25f, 4.41f)
        reflectiveCurveToRelative(7.54f, 3.33f, 9.89f, -2.54f)
        reflectiveCurveTo(127.0f, 72.0f, 123.5f, 71.5f)
        close()
    }
private val pathData9 =
    PathData {
        moveTo(117.5f, 75.5f)
        arcToRelative(2.5f, 2.0f, 0.0f, true, false, 5.0f, 0.0f)
        arcToRelative(2.5f, 2.0f, 0.0f, true, false, -5.0f, 0.0f)
        close()
    }
