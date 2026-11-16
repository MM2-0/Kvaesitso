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
fun Mouth9Closed() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    mouth9Closed(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun mouth9Closed(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData3, fill = Black, stroke = Black, fillAlpha = 0.1f, strokeAlpha = 0.1f, strokeLineWidth = 1.0f)
    builder.addPath(pathData4, fill = Black, fillAlpha = 0.4f, strokeAlpha = 1f, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData6, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 0.75f)
    builder.addPath(pathData7, stroke = Black, strokeLineWidth = 1.0f)
}

private val pathData1 =
    PathData {
        moveTo(105.5f, 180.5f)
        reflectiveCurveToRelative(18.0f, 3.0f, 32.0f, 0.0f)
        reflectiveCurveToRelative(32.0f, -11.0f, 32.0f, -11.0f)
        horizontalLineToRelative(1.33f)
        arcToRelative(1.13f, 1.13f, 0.0f, false, true, 0.85f, 0.65f)
        lineToRelative(4.82f, 17.35f)
        verticalLineToRelative(3.0f)
        arcToRelative(3.73f, 3.73f, 0.0f, false, true, -0.94f, 1.67f)
        curveToRelative(-2.53f, 1.85f, -9.8f, 6.88f, -17.06f, 9.3f)
        arcToRelative(72.67f, 72.67f, 0.0f, false, true, -25.0f, 4.0f)
        curveToRelative(-8.1f, 0.0f, -21.07f, -4.05f, -23.57f, -4.86f)
        lineToRelative(-0.43f, -0.14f)
        lineToRelative(-5.0f, -19.0f)
        arcTo(1.0f, 1.0f, 0.0f, false, true, 105.5f, 180.5f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(105.5f, 180.5f)
        reflectiveCurveToRelative(18.0f, 3.0f, 32.0f, 0.0f)
        reflectiveCurveToRelative(32.0f, -11.0f, 32.0f, -11.0f)
        horizontalLineToRelative(1.33f)
        arcToRelative(1.13f, 1.13f, 0.0f, false, true, 0.85f, 0.65f)
        lineToRelative(4.82f, 17.35f)
        verticalLineToRelative(3.0f)
        arcToRelative(3.73f, 3.73f, 0.0f, false, true, -0.94f, 1.67f)
        curveToRelative(-2.53f, 1.85f, -9.8f, 6.88f, -17.06f, 9.3f)
        arcToRelative(72.67f, 72.67f, 0.0f, false, true, -25.0f, 4.0f)
        curveToRelative(-8.1f, 0.0f, -21.07f, -4.05f, -23.57f, -4.86f)
        lineToRelative(-0.43f, -0.14f)
        lineToRelative(-5.0f, -19.0f)
        arcTo(1.0f, 1.0f, 0.0f, false, true, 105.5f, 180.5f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(108.33f, 195.83f)
        curveToRelative(0.0f, -0.11f, -2.8f, -10.45f, -3.4f, -12.94f)
        arcToRelative(6.79f, 6.79f, 0.0f, false, true, -0.3f, -2.0f)
        arcToRelative(2.1f, 2.1f, 0.0f, false, true, 0.46f, 0.0f)
        arcToRelative(3.0f, 3.0f, 0.0f, false, true, 1.77f, 0.84f)
        curveToRelative(0.32f, 0.85f, 3.0f, 8.16f, 4.0f, 11.88f)
        arcToRelative(31.56f, 31.56f, 0.0f, false, true, 1.0f, 5.64f)
        lineToRelative(-2.35f, 0.78f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(111.5f, 199.5f)
        reflectiveCurveToRelative(17.0f, 6.0f, 36.0f, 2.0f)
        reflectiveCurveToRelative(29.0f, -11.87f, 29.0f, -11.87f)
        verticalLineToRelative(0.88f)
        lineToRelative(-0.36f, 0.94f)
        lineToRelative(-0.44f, 0.64f)
        lineTo(174.0f, 193.33f)
        lineToRelative(-7.0f, 4.33f)
        lineTo(159.9f, 201.0f)
        lineToRelative(-10.68f, 3.0f)
        lineToRelative(-4.63f, 0.79f)
        lineToRelative(-5.34f, 0.51f)
        lineToRelative(-5.75f, 0.16f)
        lineToRelative(-5.06f, -0.39f)
        lineTo(121.0f, 203.7f)
        lineTo(114.57f, 202.0f)
        lineToRelative(-5.07f, -1.54f)
        lineToRelative(-0.12f, -0.67f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(116.51f, 200.51f)
        curveToRelative(1.0f, -3.0f, 3.0f, -11.0f, 3.0f, -11.0f)
        reflectiveCurveToRelative(4.0f, 2.0f, 13.0f, 2.0f)
        reflectiveCurveToRelative(27.0f, -7.0f, 27.0f, -7.0f)
        reflectiveCurveToRelative(7.0f, 6.0f, 8.0f, 10.0f)
    }
