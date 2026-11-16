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
fun Mouth1Cylinder() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    mouth1Cylinder(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun mouth1Cylinder(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData3, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 0.75f)
    builder.addPath(pathData5, stroke = Black, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(126.0f, 180.0f)
        arcToRelative(129.54f, 129.54f, 0.0f, false, true, 3.0f, 25.0f)
        arcToRelative(19.93f, 19.93f, 0.0f, false, false, 10.0f, 1.0f)
        arcToRelative(24.23f, 24.23f, 0.0f, false, false, 8.5f, -3.5f)
        reflectiveCurveToRelative(-4.0f, -23.0f, -4.0f, -25.0f)
        curveToRelative(0.0f, 0.0f, -6.0f, 2.0f, -9.0f, 2.0f)
        curveToRelative(-1.54f, 0.0f, -3.73f, 0.13f, -5.51f, 0.26f)
        reflectiveCurveTo(126.0f, 180.0f, 126.0f, 180.0f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(131.5f, 190.71f)
        arcToRelative(113.49f, 113.49f, 0.0f, false, true, 1.0f, 12.25f)
        lineToRelative(-3.0f, 1.54f)
        reflectiveCurveToRelative(-1.0f, -14.0f, -2.28f, -18.07f)
        curveToRelative(-0.72f, -2.86f, -1.15f, -6.11f, -1.15f, -6.11f)
        lineToRelative(3.43f, 0.18f)
        reflectiveCurveTo(130.5f, 182.55f, 131.5f, 190.71f)
        close()
        moveTo(129.29f, 205.11f)
        lineToRelative(3.21f, -1.61f)
        arcToRelative(32.0f, 32.0f, 0.0f, false, false, 7.0f, -1.0f)
        arcToRelative(53.36f, 53.36f, 0.0f, false, false, 7.56f, -2.57f)
        lineToRelative(0.44f, 2.57f)
        reflectiveCurveToRelative(-4.93f, 3.27f, -7.0f, 3.14f)
        reflectiveCurveTo(138.07f, 207.71f, 129.29f, 205.11f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(131.81f, 193.42f)
        curveToRelative(0.93f, 0.47f, 1.84f, 0.92f, 1.69f, -0.92f)
        curveToRelative(0.0f, 0.0f, 0.0f, -2.0f, 1.0f, -2.0f)
        reflectiveCurveToRelative(7.0f, -1.0f, 7.0f, -1.0f)
        arcToRelative(1.0f, 1.0f, 0.0f, false, true, 1.0f, 1.0f)
        curveToRelative(0.0f, 1.0f, 0.0f, 3.0f, 1.0f, 2.0f)
        arcToRelative(3.44f, 3.44f, 0.0f, false, true, 2.12f, -1.0f)
    }
