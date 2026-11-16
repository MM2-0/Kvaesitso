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
import com.vitorpamplona.amethyst.commons.robohash.Brown
import com.vitorpamplona.amethyst.commons.robohash.LightBrown
import com.vitorpamplona.amethyst.commons.robohash.LightGray
import com.vitorpamplona.amethyst.commons.robohash.LightRed
import com.vitorpamplona.amethyst.commons.robohash.MediumGray
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Preview
@Composable
fun Accessory2HornRedPreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    accessory2HornRed(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun accessory2HornRed(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData2, fill = LightBrown, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData3, fill = Brown)
    builder.addPath(pathData4, fill = LightRed)
    builder.addPath(pathData6, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 0.75f)
    builder.addPath(pathData8, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData9, fill = LightGray, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData10, fill = MediumGray)
    builder.addPath(pathData11, stroke = Black, strokeLineWidth = 1.0f)
}

private val pathData1 =
    PathData {
        moveTo(122.5f, 94.5f)
        reflectiveCurveToRelative(4.0f, 3.0f, 13.0f, 3.0f)
        reflectiveCurveToRelative(16.0f, -6.0f, 16.0f, -6.0f)
        arcToRelative(26.39f, 26.39f, 0.0f, false, false, -1.0f, -7.0f)
        curveToRelative(-1.0f, -3.0f, -10.49f, -2.87f, -15.74f, -1.93f)
        reflectiveCurveToRelative(-11.26f, 0.93f, -12.26f, 3.93f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(125.5f, 26.0f)
        arcToRelative(7.5f, 6.5f, 0.0f, true, false, 15.0f, 0.0f)
        arcToRelative(7.5f, 6.5f, 0.0f, true, false, -15.0f, 0.0f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(140.0f, 25.0f)
        curveToRelative(0.19f, 0.68f, -5.51f, 8.12f, -11.57f, 6.27f)
        arcToRelative(8.65f, 8.65f, 0.0f, false, false, 7.91f, 0.53f)
        curveTo(141.0f, 30.0f, 140.0f, 25.0f, 140.0f, 25.0f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(126.856f, 25.249f)
        arcToRelative(2.238f, 4.475f, 60.113f, true, false, 7.76f, -4.46f)
        arcToRelative(2.238f, 4.475f, 60.113f, true, false, -7.76f, 4.46f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(125.5f, 26.0f)
        arcToRelative(7.5f, 6.5f, 0.0f, true, false, 15.0f, 0.0f)
        arcToRelative(7.5f, 6.5f, 0.0f, true, false, -15.0f, 0.0f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(135.5f, 82.5f)
        reflectiveCurveToRelative(-13.0f, 1.0f, -13.0f, 4.0f)
        reflectiveCurveToRelative(9.0f, 4.0f, 13.0f, 4.0f)
        reflectiveCurveToRelative(15.0f, -2.0f, 15.0f, -6.0f)
        reflectiveCurveTo(135.5f, 82.5f, 135.5f, 82.5f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(122.5f, 94.5f)
        reflectiveCurveToRelative(4.0f, 3.0f, 13.0f, 3.0f)
        reflectiveCurveToRelative(16.0f, -6.0f, 16.0f, -6.0f)
        arcToRelative(26.39f, 26.39f, 0.0f, false, false, -1.0f, -7.0f)
        curveToRelative(-1.0f, -3.0f, -10.49f, -2.87f, -15.74f, -1.93f)
        reflectiveCurveToRelative(-11.26f, 0.93f, -12.26f, 3.93f)
        close()
    }
private val pathData8 =
    PathData {
        moveTo(148.19f, 93.77f)
        curveToRelative(0.31f, -1.27f, -0.69f, -6.27f, -0.69f, -6.27f)
        reflectiveCurveToRelative(-7.41f, -2.59f, -8.2f, -2.3f)
        reflectiveCurveToRelative(1.86f, -0.8f, 2.0f, -1.75f)
        reflectiveCurveToRelative(0.23f, -1.43f, 0.23f, -1.43f)
        reflectiveCurveToRelative(3.12f, 0.0f, 5.53f, 0.25f)
        reflectiveCurveToRelative(3.41f, 1.47f, 3.41f, 2.35f)
        lineToRelative(0.64f, 2.58f)
        lineToRelative(0.36f, 4.29f)
        lineToRelative(-3.0f, 2.0f)
    }
private val pathData9 =
    PathData {
        moveTo(133.0f, 29.24f)
        reflectiveCurveToRelative(-7.5f, 13.21f, -6.5f, 28.88f)
        arcToRelative(245.78f, 245.78f, 0.0f, false, false, 3.0f, 26.43f)
        reflectiveCurveToRelative(0.0f, 2.0f, 4.0f, 2.0f)
        arcToRelative(14.0f, 14.0f, 0.0f, false, false, 7.0f, -2.0f)
        arcToRelative(3.4f, 3.4f, 0.0f, false, false, 1.0f, -2.0f)
        curveToRelative(0.0f, -1.0f, -11.0f, -18.6f, -8.0f, -48.94f)
        curveTo(133.5f, 33.64f, 134.5f, 27.77f, 133.0f, 29.24f)
        close()
    }
private val pathData10 =
    PathData {
        moveTo(130.5f, 46.5f)
        arcToRelative(94.41f, 94.41f, 0.0f, false, false, 2.0f, 18.0f)
        arcToRelative(145.29f, 145.29f, 0.0f, false, false, 6.0f, 19.0f)
        lineToRelative(0.52f, 1.8f)
        reflectiveCurveToRelative(2.48f, -0.8f, 2.48f, -2.8f)
        curveToRelative(0.0f, 0.0f, -6.45f, -14.41f, -7.23f, -22.71f)
        reflectiveCurveTo(132.5f, 39.5f, 133.5f, 33.5f)
        curveToRelative(0.0f, 0.0f, -0.08f, -3.33f, 0.21f, -2.91f)
        reflectiveCurveTo(130.0f, 38.0f, 130.5f, 46.5f)
        close()
    }
private val pathData11 =
    PathData {
        moveTo(133.0f, 29.24f)
        reflectiveCurveToRelative(-7.5f, 13.21f, -6.5f, 28.88f)
        arcToRelative(245.78f, 245.78f, 0.0f, false, false, 3.0f, 26.43f)
        reflectiveCurveToRelative(0.0f, 2.0f, 4.0f, 2.0f)
        arcToRelative(14.0f, 14.0f, 0.0f, false, false, 7.0f, -2.0f)
        arcToRelative(3.4f, 3.4f, 0.0f, false, false, 1.0f, -2.0f)
        curveToRelative(0.0f, -1.0f, -11.0f, -18.6f, -8.0f, -48.94f)
        curveTo(133.5f, 33.64f, 134.5f, 27.77f, 133.0f, 29.24f)
        close()
        moveTo(126.0f, 46.0f)
        reflectiveCurveToRelative(-9.0f, 1.0f, -9.0f, 6.0f)
        reflectiveCurveToRelative(10.0f, 5.0f, 13.0f, 5.0f)
        reflectiveCurveToRelative(12.0f, -1.0f, 12.0f, -6.0f)
        reflectiveCurveToRelative(-8.0f, -5.0f, -8.0f, -5.0f)
    }
