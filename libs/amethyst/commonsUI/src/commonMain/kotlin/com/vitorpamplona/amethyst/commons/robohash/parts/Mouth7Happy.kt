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
import com.vitorpamplona.amethyst.commons.robohash.Yellow
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Mouth7Happy() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    mouth7Happy(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun mouth7Happy(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor)
    builder.addPath(pathData2, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 1.0f)
    builder.addPath(pathData3, fill = OrangeThree, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData4, fill = Yellow, stroke = Black, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(148.0f, 175.0f)
        reflectiveCurveToRelative(-6.5f, 0.5f, -12.5f, 1.5f)
        arcToRelative(83.3f, 83.3f, 0.0f, false, true, -12.0f, 1.0f)
        verticalLineToRelative(14.0f)
        curveToRelative(0.0f, 2.0f, 3.0f, 13.0f, 13.0f, 12.0f)
        reflectiveCurveToRelative(13.0f, -7.0f, 13.0f, -11.0f)
        reflectiveCurveToRelative(-1.0f, -17.0f, -1.0f, -17.0f)
        horizontalLineToRelative(0.0f)
        reflectiveCurveTo(148.5f, 174.5f, 148.0f, 175.0f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(148.0f, 175.0f)
        reflectiveCurveToRelative(-6.5f, 0.5f, -12.5f, 1.5f)
        arcToRelative(83.3f, 83.3f, 0.0f, false, true, -12.0f, 1.0f)
        verticalLineToRelative(14.0f)
        curveToRelative(0.0f, 2.0f, 3.0f, 13.0f, 13.0f, 12.0f)
        reflectiveCurveToRelative(13.0f, -7.0f, 13.0f, -11.0f)
        reflectiveCurveToRelative(-1.0f, -17.0f, -1.0f, -17.0f)
        horizontalLineToRelative(0.0f)
        reflectiveCurveTo(148.5f, 174.5f, 148.0f, 175.0f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(129.59f, 177.21f)
        lineTo(130.0f, 191.0f)
        reflectiveCurveToRelative(1.0f, 7.0f, 7.0f, 8.0f)
        reflectiveCurveToRelative(12.0f, -0.83f, 12.5f, -4.92f)
        reflectiveCurveToRelative(-1.0f, -19.58f, -1.0f, -19.58f)
        lineToRelative(-10.88f, 1.67f)
        curveTo(135.5f, 176.5f, 129.59f, 177.21f, 129.59f, 177.21f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(137.25f, 176.19f)
        lineToRelative(0.22f, 22.88f)
        reflectiveCurveToRelative(-7.0f, -0.57f, -7.39f, -7.64f)
        curveToRelative(-0.58f, -4.93f, -0.58f, -13.93f, -0.58f, -13.93f)
        close()
    }
