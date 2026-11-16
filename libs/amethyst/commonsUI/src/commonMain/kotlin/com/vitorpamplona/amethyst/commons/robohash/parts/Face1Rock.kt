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
fun Face1Rock() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    face1Rock(SolidColor(Color.Yellow), this)
                },
            ),
        contentDescription = "",
    )
}

fun face1Rock(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.5f)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.2f)
    builder.addPath(pathData5, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 0.75f)
    builder.addPath(pathData7, fill = Black, fillAlpha = 0.4f)
}

private val pathData1 =
    PathData {
        moveTo(147.5f, 88.5f)
        reflectiveCurveToRelative(-64.0f, 9.0f, -63.0f, 72.0f)
        curveToRelative(0.0f, 0.0f, -6.0f, 51.0f, 54.0f, 50.0f)
        curveToRelative(0.0f, 0.0f, 66.0f, -6.0f, 65.0f, -53.0f)
        reflectiveCurveTo(172.0f, 87.0f, 147.5f, 88.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(154.5f, 90.5f)
        reflectiveCurveToRelative(18.0f, 12.0f, 25.0f, 47.0f)
        reflectiveCurveToRelative(2.0f, 48.0f, -7.0f, 55.0f)
        reflectiveCurveToRelative(-24.12f, 17.37f, -42.56f, 17.68f)
        curveToRelative(0.0f, 0.0f, 56.6f, 1.84f, 70.58f, -33.42f)
        curveToRelative(0.73f, -1.85f, 1.84f, -5.7f, 1.84f, -5.7f)
        reflectiveCurveTo(185.5f, 158.5f, 183.17f, 137.0f)
        curveToRelative(-1.67f, -14.45f, 2.33f, -24.45f, 6.49f, -24.63f)
        arcToRelative(7.0f, 7.0f, 0.0f, false, false, -0.63f, -1.08f)
        curveTo(179.68f, 98.0f, 160.0f, 87.0f, 154.5f, 90.5f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(147.5f, 87.5f)
        reflectiveCurveToRelative(-64.0f, 9.0f, -63.0f, 72.0f)
        curveToRelative(0.0f, 0.0f, -6.0f, 51.0f, 54.0f, 50.0f)
        curveToRelative(0.0f, 0.0f, 66.0f, -6.0f, 65.0f, -53.0f)
        reflectiveCurveTo(170.5f, 85.5f, 147.5f, 87.5f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(147.5f, 88.5f)
        reflectiveCurveToRelative(-64.0f, 9.0f, -63.0f, 72.0f)
        curveToRelative(0.0f, 0.0f, -6.0f, 51.0f, 54.0f, 50.0f)
        curveToRelative(0.0f, 0.0f, 66.0f, -6.0f, 65.0f, -53.0f)
        reflectiveCurveTo(171.5f, 86.5f, 147.5f, 88.5f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(92.22f, 125.67f)
        reflectiveCurveToRelative(2.28f, 14.83f, 0.28f, 24.83f)
        reflectiveCurveToRelative(-7.67f, 21.0f, -7.67f, 21.0f)
        reflectiveCurveTo(80.94f, 147.85f, 92.22f, 125.67f)
        close()
        moveTo(187.5f, 113.5f)
        reflectiveCurveToRelative(-7.0f, 10.0f, -4.0f, 26.0f)
        reflectiveCurveToRelative(12.0f, 27.67f, 18.5f, 29.83f)
        curveToRelative(0.0f, 0.0f, 5.83f, -35.17f, -12.83f, -57.0f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(112.5f, 112.5f)
        reflectiveCurveToRelative(-5.0f, -1.0f, -10.0f, 6.0f)
        reflectiveCurveToRelative(-6.0f, 11.0f, -5.0f, 13.0f)
        reflectiveCurveToRelative(7.0f, 1.0f, 11.0f, -7.0f)
        reflectiveCurveTo(114.5f, 114.5f, 112.5f, 112.5f)
        close()
        moveTo(117.5f, 102.5f)
        reflectiveCurveToRelative(-5.0f, 4.0f, -2.0f, 7.0f)
        arcToRelative(6.31f, 6.31f, 0.0f, false, false, 9.0f, 0.0f)
        curveToRelative(2.0f, -2.0f, 2.0f, -6.0f, -1.0f, -7.0f)
        arcTo(10.56f, 10.56f, 0.0f, false, false, 117.5f, 102.5f)
        close()
    }
