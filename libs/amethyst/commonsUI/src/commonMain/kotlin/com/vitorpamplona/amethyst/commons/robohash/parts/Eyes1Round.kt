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
import com.vitorpamplona.amethyst.commons.robohash.Brown
import com.vitorpamplona.amethyst.commons.robohash.DarkYellow
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Eyes1Round() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    eyes1Round(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun eyes1Round(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor)
    builder.addPath(pathData3, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 0.5f)
    builder.addPath(pathData4, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 1.0f)
    builder.addPath(pathData7, fill = Brown, stroke = Black, strokeLineWidth = 0.5f)
    builder.addPath(pathData9, fill = DarkYellow)
}

private val pathData1 =
    PathData {
        moveTo(110.5f, 122.5f)
        curveToRelative(-4.0f, 0.0f, -17.0f, 2.0f, -17.0f, 17.0f)
        reflectiveCurveToRelative(10.0f, 17.0f, 17.0f, 17.0f)
        reflectiveCurveToRelative(15.0f, -5.0f, 15.0f, -18.0f)
        reflectiveCurveTo(114.5f, 122.5f, 110.5f, 122.5f)
        close()
        moveTo(153.5f, 121.5f)
        curveToRelative(-4.0f, 0.0f, -17.5f, 2.5f, -17.5f, 17.5f)
        reflectiveCurveToRelative(10.5f, 16.5f, 17.5f, 16.5f)
        reflectiveCurveToRelative(16.0f, -5.0f, 16.0f, -18.0f)
        curveTo(167.0f, 122.0f, 157.5f, 121.5f, 153.5f, 121.5f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(110.5f, 126.5f)
        reflectiveCurveToRelative(-13.0f, 0.0f, -13.0f, 12.0f)
        reflectiveCurveToRelative(10.0f, 13.0f, 13.0f, 13.0f)
        reflectiveCurveToRelative(12.0f, -2.0f, 12.0f, -14.52f)
        curveTo(122.5f, 137.0f, 121.5f, 127.5f, 110.5f, 126.5f)
        close()
        moveTo(140.1f, 137.73f)
        reflectiveCurveToRelative(-2.0f, 13.2f, 12.18f, 13.2f)
        reflectiveCurveToRelative(14.22f, -10.15f, 14.22f, -13.2f)
        reflectiveCurveToRelative(-3.0f, -12.18f, -12.18f, -12.18f)
        reflectiveCurveTo(140.1f, 132.65f, 140.1f, 137.73f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(154.5f, 121.5f)
        reflectiveCurveToRelative(-20.0f, 0.0f, -19.0f, 18.0f)
        reflectiveCurveToRelative(19.0f, 16.0f, 19.0f, 16.0f)
        reflectiveCurveToRelative(16.0f, -2.0f, 15.0f, -17.0f)
        reflectiveCurveTo(158.5f, 121.5f, 154.5f, 121.5f)
        close()
        moveTo(110.5f, 122.5f)
        curveToRelative(-4.0f, 0.0f, -17.0f, 2.0f, -17.0f, 17.0f)
        reflectiveCurveToRelative(10.0f, 17.0f, 17.0f, 17.0f)
        reflectiveCurveToRelative(15.0f, -5.0f, 15.0f, -18.0f)
        reflectiveCurveTo(114.5f, 122.5f, 110.5f, 122.5f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(109.0f, 127.0f)
        reflectiveCurveToRelative(-9.0f, 1.0f, -9.0f, 9.0f)
        reflectiveCurveToRelative(2.5f, 12.5f, 10.5f, 12.5f)
        reflectiveCurveToRelative(12.0f, -5.0f, 12.0f, -10.0f)
        reflectiveCurveTo(117.5f, 126.5f, 109.0f, 127.0f)
        close()
        moveTo(154.0f, 126.0f)
        reflectiveCurveToRelative(-10.5f, 0.52f, -11.0f, 9.4f)
        reflectiveCurveTo(147.0f, 149.0f, 154.0f, 149.0f)
        reflectiveCurveToRelative(12.0f, -6.26f, 12.0f, -11.49f)
        reflectiveCurveTo(161.5f, 125.51f, 154.0f, 126.0f)
        close()
    }
private val pathData9 =
    PathData {
        moveTo(110.26f, 134.06f)
        arcToRelative(0.92f, 0.92f, 0.0f, false, false, -0.24f, 0.05f)
        curveToRelative(-0.36f, 0.13f, -1.0f, 0.61f, -1.0f, 2.31f)
        curveToRelative(0.0f, 2.36f, 0.0f, 3.54f, 1.26f, 3.54f)
        reflectiveCurveToRelative(2.51f, 0.0f, 2.51f, -3.54f)
        curveTo(112.77f, 134.06f, 111.12f, 133.86f, 110.26f, 134.06f)
        close()
        moveTo(154.26f, 134.06f)
        arcToRelative(0.92f, 0.92f, 0.0f, false, false, -0.24f, 0.05f)
        curveToRelative(-0.36f, 0.13f, -1.0f, 0.61f, -1.0f, 2.31f)
        curveToRelative(0.0f, 2.36f, 0.0f, 3.54f, 1.26f, 3.54f)
        reflectiveCurveToRelative(2.51f, 0.0f, 2.51f, -3.54f)
        curveTo(156.77f, 134.06f, 155.12f, 133.86f, 154.26f, 134.06f)
        close()
    }
