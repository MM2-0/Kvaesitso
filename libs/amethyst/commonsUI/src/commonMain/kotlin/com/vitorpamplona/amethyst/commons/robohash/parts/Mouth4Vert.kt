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
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Mouth4Vert() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    mouth4Vert(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun mouth4Vert(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData3, fill = Black, fillAlpha = 0.4f, strokeAlpha = 0.4f)
    builder.addPath(pathData5, fill = Brown, stroke = Black, strokeLineWidth = 0.5f)
    builder.addPath(pathData7, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(130.5f, 175.5f)
        verticalLineToRelative(15.0f)
        arcToRelative(73.09f, 73.09f, 0.0f, false, false, 1.0f, 13.0f)
        reflectiveCurveToRelative(6.0f, 1.0f, 6.0f, 0.0f)
        reflectiveCurveToRelative(-1.0f, -28.0f, -1.0f, -28.0f)
        curveTo(134.18f, 175.38f, 131.28f, 175.06f, 130.5f, 175.5f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(131.65f, 203.0f)
        arcToRelative(75.68f, 75.68f, 0.0f, false, true, -1.4f, -15.06f)
        arcToRelative(102.33f, 102.33f, 0.0f, false, true, 0.48f, -12.27f)
        arcToRelative(3.0f, 3.0f, 0.0f, false, true, 1.25f, -0.25f)
        arcToRelative(5.33f, 5.33f, 0.0f, false, true, 1.0f, 0.1f)
        lineToRelative(0.43f, 0.2f)
        horizontalLineToRelative(0.0f)
        reflectiveCurveToRelative(0.0f, 0.16f, -0.13f, 0.72f)
        arcToRelative(138.49f, 138.49f, 0.0f, false, false, -1.0f, 14.06f)
        arcToRelative(64.14f, 64.14f, 0.0f, false, false, 1.0f, 10.92f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(137.19f, 201.56f)
        arcToRelative(5.59f, 5.59f, 0.0f, false, false, -2.48f, -0.65f)
        arcToRelative(3.64f, 3.64f, 0.0f, false, false, -1.34f, 0.25f)
        arcToRelative(39.16f, 39.16f, 0.0f, false, true, -1.12f, -9.0f)
        curveToRelative(0.0f, -4.56f, 1.26f, -14.93f, 1.47f, -16.62f)
        lineToRelative(2.54f, 0.17f)
        lineToRelative(0.56f, 15.47f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(137.5f, 201.5f)
        verticalLineToRelative(2.0f)
        lineToRelative(-5.7f, 0.0f)
        lineToRelative(1.7f, -2.0f)
        reflectiveCurveTo(134.5f, 199.5f, 137.5f, 201.5f)
        close()
    }
