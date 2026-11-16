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
import com.vitorpamplona.amethyst.commons.robohash.OrangeThree
import com.vitorpamplona.amethyst.commons.robohash.OrangeTwo
import com.vitorpamplona.amethyst.commons.robohash.Yellow
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Mouth8Buttons() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    mouth8Buttons(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun mouth8Buttons(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.4f, strokeAlpha = 0.4f)
    builder.addPath(pathData4, stroke = Black, strokeLineWidth = 0.5f)
    builder.addPath(pathData7, fill = OrangeThree, stroke = Black, strokeLineWidth = 1.2f)
    builder.addPath(pathData8, fill = Yellow, stroke = Black, strokeLineWidth = 0.75f)
    builder.addPath(pathData10, fill = OrangeTwo, stroke = Black, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(109.5f, 197.5f)
        curveToRelative(0.0f, 4.0f, 1.0f, 5.0f, 1.0f, 5.0f)
        horizontalLineToRelative(34.0f)
        curveToRelative(10.0f, 0.0f, 21.0f, -1.0f, 21.0f, -1.0f)
        verticalLineToRelative(-23.0f)
        lineToRelative(-1.0f, -4.0f)
        horizontalLineToRelative(-2.0f)
        curveToRelative(-2.0f, 0.0f, -54.5f, 0.5f, -54.5f, 0.5f)
        verticalLineToRelative(11.0f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(163.5f, 174.5f)
        verticalLineToRelative(25.0f)
        reflectiveCurveToRelative(-52.0f, 1.5f, -53.75f, 1.0f)
        lineToRelative(0.61f, 1.79f)
        lineToRelative(28.9f, 0.21f)
        reflectiveCurveToRelative(19.65f, -0.79f, 21.7f, -0.65f)
        arcTo(9.0f, 9.0f, 0.0f, false, false, 165.0f, 201.0f)
        lineToRelative(0.41f, -22.84f)
        reflectiveCurveTo(164.0f, 174.0f, 163.5f, 174.5f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(113.5f, 200.5f)
        horizontalLineToRelative(27.0f)
        curveToRelative(10.0f, 0.0f, 23.0f, -1.0f, 23.0f, -1.0f)
        verticalLineToRelative(-24.0f)
        moveTo(163.5f, 199.5f)
        lineTo(165.0f, 201.0f)
        moveTo(162.0f, 177.0f)
        verticalLineToRelative(12.0f)
    }

private val pathData7 =
    PathData {
        moveTo(112.5f, 177.5f)
        horizontalLineToRelative(27.0f)
        curveToRelative(9.0f, 0.0f, 20.0f, -1.0f, 20.0f, -1.0f)
        verticalLineToRelative(20.0f)
        lineToRelative(-54.0f, 1.0f)
        arcToRelative(34.21f, 34.21f, 0.0f, false, true, 2.0f, -11.0f)
        arcTo(44.27f, 44.27f, 0.0f, false, true, 112.5f, 177.5f)
        close()
    }
private val pathData8 =
    PathData {
        moveTo(159.0f, 176.5f)
        reflectiveCurveToRelative(-3.0f, 7.5f, -4.0f, 11.5f)
        arcToRelative(87.0f, 87.0f, 0.0f, false, false, -1.59f, 8.61f)
        lineToRelative(-12.91f, 0.19f)
        reflectiveCurveToRelative(-1.0f, -2.3f, 1.0f, -7.3f)
        reflectiveCurveToRelative(5.0f, -12.0f, 5.0f, -12.0f)
        reflectiveCurveTo(157.5f, 177.0f, 159.0f, 176.5f)
        close()
        moveTo(128.5f, 196.89f)
        horizontalLineToRelative(-11.0f)
        arcToRelative(19.28f, 19.28f, 0.0f, false, true, 2.0f, -11.22f)
        curveToRelative(3.0f, -6.12f, 3.75f, -8.16f, 3.75f, -8.16f)
        horizontalLineToRelative(11.56f)
        reflectiveCurveToRelative(-1.31f, 3.06f, -2.31f, 6.12f)
        reflectiveCurveToRelative(-4.0f, 6.12f, -4.0f, 10.2f)
        close()
    }
private val pathData10 =
    PathData {
        moveTo(135.13f, 177.5f)
        horizontalLineTo(146.5f)
        lineToRelative(-5.43f, 13.25f)
        arcToRelative(8.08f, 8.08f, 0.0f, false, false, -0.82f, 4.0f)
        curveToRelative(0.25f, 1.89f, 0.0f, 2.25f, 0.0f, 2.25f)
        horizontalLineTo(128.5f)
        arcToRelative(23.82f, 23.82f, 0.0f, false, true, 0.92f, -7.35f)
        curveToRelative(1.08f, -3.08f, 2.1f, -3.0f, 3.59f, -7.58f)
        reflectiveCurveTo(135.13f, 177.5f, 135.13f, 177.5f)
        close()
    }
