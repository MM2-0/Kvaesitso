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
fun Accessory3ButtonPreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    accessory3Button(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun accessory3Button(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData2, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 0.75f)
    builder.addPath(pathData3, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData5, fill = LightRed, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData6, fill = Black, fillAlpha = 0.1f)
    builder.addPath(pathData9, fill = Black, fillAlpha = 0.4f)
}

private val pathData1 =
    PathData {
        moveTo(136.5f, 83.5f)
        curveToRelative(-3.39f, 0.31f, -11.0f, 2.0f, -13.0f, 4.0f)
        arcToRelative(4.38f, 4.38f, 0.0f, false, false, -1.0f, 3.0f)
        verticalLineToRelative(5.0f)
        arcToRelative(18.26f, 18.26f, 0.0f, false, false, 13.0f, 3.0f)
        curveToRelative(8.0f, -1.0f, 16.0f, -6.0f, 16.0f, -6.0f)
        reflectiveCurveToRelative(0.0f, -6.0f, -1.0f, -7.0f)
        reflectiveCurveToRelative(-1.0f, -2.0f, -5.0f, -2.0f)
        reflectiveCurveTo(138.74f, 83.3f, 136.5f, 83.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(125.7f, 86.16f)
        reflectiveCurveToRelative(-2.2f, 1.34f, -1.2f, 3.34f)
        reflectiveCurveToRelative(7.0f, 3.0f, 12.0f, 2.0f)
        arcToRelative(53.37f, 53.37f, 0.0f, false, false, 11.0f, -4.0f)
        reflectiveCurveToRelative(3.57f, -2.09f, 1.29f, -3.54f)
        reflectiveCurveTo(131.89f, 82.83f, 125.7f, 86.16f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(125.7f, 86.16f)
        reflectiveCurveToRelative(-2.2f, 1.34f, -1.2f, 3.34f)
        reflectiveCurveToRelative(7.0f, 3.0f, 12.0f, 2.0f)
        arcTo(55.23f, 55.23f, 0.0f, false, false, 145.11f, 89.0f)
        curveToRelative(-1.11f, -2.0f, -8.42f, -5.89f, -8.42f, -5.89f)
        reflectiveCurveTo(129.47f, 84.13f, 125.7f, 86.16f)
        close()
        moveTo(144.5f, 88.5f)
        curveToRelative(1.0f, 1.0f, 0.0f, 7.3f, 0.0f, 7.3f)
        lineToRelative(7.0f, -3.3f)
        reflectiveCurveToRelative(0.0f, -6.14f, -1.5f, -7.57f)
        curveTo(148.0f, 88.0f, 147.0f, 88.0f, 144.5f, 88.5f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(136.5f, 83.5f)
        curveToRelative(-3.39f, 0.31f, -11.0f, 2.0f, -13.0f, 4.0f)
        arcToRelative(4.38f, 4.38f, 0.0f, false, false, -1.0f, 3.0f)
        verticalLineToRelative(5.0f)
        arcToRelative(18.26f, 18.26f, 0.0f, false, false, 13.0f, 3.0f)
        curveToRelative(8.0f, -1.0f, 16.0f, -6.0f, 16.0f, -6.0f)
        reflectiveCurveToRelative(0.0f, -6.0f, -1.0f, -7.0f)
        reflectiveCurveToRelative(-1.0f, -2.0f, -5.0f, -2.0f)
        reflectiveCurveTo(138.74f, 83.3f, 136.5f, 83.5f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(134.5f, 73.5f)
        reflectiveCurveToRelative(-5.0f, 1.0f, -5.0f, 6.0f)
        reflectiveCurveToRelative(1.0f, 7.0f, 1.0f, 7.0f)
        arcToRelative(18.58f, 18.58f, 0.0f, false, false, 8.0f, 0.0f)
        curveToRelative(4.0f, -1.0f, 5.0f, -3.0f, 5.0f, -3.0f)
        reflectiveCurveTo(142.5f, 73.5f, 134.5f, 73.5f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(139.5f, 75.5f)
        reflectiveCurveToRelative(2.0f, 3.0f, 1.0f, 6.0f)
        reflectiveCurveToRelative(-6.0f, 5.0f, -6.0f, 5.0f)
        reflectiveCurveToRelative(8.0f, -1.0f, 9.0f, -3.0f)
        reflectiveCurveTo(140.5f, 76.5f, 139.5f, 75.5f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(134.5f, 73.5f)
        reflectiveCurveToRelative(-5.0f, 1.0f, -5.0f, 6.0f)
        reflectiveCurveToRelative(1.0f, 7.0f, 1.0f, 7.0f)
        arcToRelative(18.58f, 18.58f, 0.0f, false, false, 8.0f, 0.0f)
        curveToRelative(4.0f, -1.0f, 5.0f, -3.0f, 5.0f, -3.0f)
        reflectiveCurveTo(142.5f, 73.5f, 134.5f, 73.5f)
        close()
    }
private val pathData9 =
    PathData {
        moveTo(133.5f, 75.5f)
        arcToRelative(1.0f, 1.0f, 0.0f, false, true, 1.0f, 1.0f)
        curveToRelative(0.0f, 1.0f, 0.0f, 6.0f, -2.0f, 6.0f)
        reflectiveCurveToRelative(-3.0f, 0.0f, -2.0f, -4.0f)
        reflectiveCurveTo(133.5f, 75.5f, 133.5f, 75.5f)
        close()
    }
