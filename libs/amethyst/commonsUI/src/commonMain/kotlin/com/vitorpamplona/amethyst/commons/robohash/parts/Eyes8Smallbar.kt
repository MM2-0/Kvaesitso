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
import com.vitorpamplona.amethyst.commons.robohash.LightYellow
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Eyes8SmallBar() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    eyes8SmallBar(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun eyes8SmallBar(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor)
    builder.addPath(pathData2, stroke = Black, strokeLineWidth = 0.75f)
    builder.addPath(pathData3, fill = Brown, stroke = Black, strokeLineWidth = 0.5f)
    builder.addPath(pathData4, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 0.75f)
    builder.addPath(pathData5, fill = LightYellow)
    builder.addPath(pathData7, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(127.5f, 124.5f)
        reflectiveCurveToRelative(-43.0f, -1.0f, -45.0f, 16.0f)
        curveToRelative(-1.5f, 15.5f, 25.0f, 13.0f, 25.0f, 13.0f)
        horizontalLineToRelative(35.0f)
        reflectiveCurveToRelative(27.0f, 0.0f, 27.0f, -13.0f)
        reflectiveCurveTo(158.5f, 123.5f, 127.5f, 124.5f)
        close()
    }

private val pathData2 =
    PathData {
        moveTo(127.5f, 124.5f)
        reflectiveCurveToRelative(-43.0f, -1.0f, -45.0f, 16.0f)
        curveToRelative(-1.0f, 15.0f, 21.0f, 13.0f, 25.0f, 13.0f)
        horizontalLineToRelative(35.0f)
        reflectiveCurveToRelative(27.0f, 0.0f, 27.0f, -13.0f)
        reflectiveCurveTo(158.5f, 123.5f, 127.5f, 124.5f)
        close()
    }

private val pathData3 =
    PathData {
        moveTo(126.0f, 132.0f)
        curveToRelative(-15.5f, 0.5f, -28.5f, -1.5f, -30.5f, 6.5f)
        curveToRelative(-2.0f, 9.0f, 16.0f, 7.0f, 22.0f, 7.0f)
        reflectiveCurveToRelative(27.0f, -1.0f, 30.0f, -1.0f)
        reflectiveCurveToRelative(10.0f, -1.0f, 10.0f, -3.0f)
        curveTo(157.5f, 133.5f, 142.5f, 131.5f, 126.0f, 132.0f)
        close()
    }

private val pathData4 =
    PathData {
        moveTo(90.5f, 140.5f)
        curveToRelative(0.0f, 1.0f, -5.0f, 9.0f, 32.0f, 8.0f)
        reflectiveCurveToRelative(35.0f, -4.33f, 35.0f, -7.67f)
        reflectiveCurveToRelative(-8.64f, -9.23f, -21.32f, -8.78f)
        reflectiveCurveTo(95.5f, 128.5f, 90.5f, 140.5f)
        close()
    }

private val pathData5 =
    PathData {
        moveTo(109.5f, 139.0f)
        arcToRelative(2.5f, 3.5f, 0.0f, true, false, 5.0f, 0.0f)
        arcToRelative(2.5f, 3.5f, 0.0f, true, false, -5.0f, 0.0f)
        close()
        moveTo(135.5f, 139.0f)
        arcToRelative(2.5f, 3.5f, 0.0f, true, false, 5.0f, 0.0f)
        arcToRelative(2.5f, 3.5f, 0.0f, true, false, -5.0f, 0.0f)
        close()
    }

private val pathData7 =
    PathData {
        moveTo(93.5f, 129.5f)
        reflectiveCurveToRelative(27.0f, -3.0f, 41.0f, -2.0f)
        curveToRelative(11.0f, 0.0f, 24.0f, 3.0f, 29.0f, 10.0f)
        reflectiveCurveToRelative(1.39f, 10.59f, -0.3f, 11.8f)
        reflectiveCurveToRelative(6.3f, -2.8f, 6.3f, -8.8f)
        reflectiveCurveToRelative(-1.61f, -11.29f, -14.3f, -14.65f)
        reflectiveCurveToRelative(-30.2f, -1.18f, -33.95f, -1.26f)
        reflectiveCurveTo(98.5f, 126.5f, 93.5f, 129.5f)
        close()
    }
