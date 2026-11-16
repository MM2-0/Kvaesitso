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
import com.vitorpamplona.amethyst.commons.robohash.Gray
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Preview
@Composable
fun Body7NeckThinnerPreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    body7NeckThinner(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun body7NeckThinner(
    fgColor: SolidColor,
    builder: Builder,
) {
    // Body
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)

    // Darker neck
    builder.addPath(pathData6, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 1.0f)

    // Body shades
    builder.addPath(pathData9, fill = Black, fillAlpha = 0.4f)

    // gray neck
    builder.addPath(pathData12, fill = Gray, stroke = Black, strokeLineWidth = 1.0f)

    // neck shades
    builder.addPath(pathData13, fill = Black, fillAlpha = 0.2f)

    // Joints
    builder.addPath(pathData14, stroke = Black, strokeLineWidth = 1.0f)
}

private val pathData1 =
    PathData {
        moveTo(125.5f, 286.5f)
        reflectiveCurveToRelative(-5.0f, 5.0f, -4.0f, 15.0f)
        lineToRelative(83.0f, 1.0f)
        reflectiveCurveToRelative(-2.0f, -19.0f, -13.0f, -22.0f)
        reflectiveCurveToRelative(-16.0f, -1.95f, -16.0f, -1.95f)
        horizontalLineToRelative(-0.39f)
        lineToRelative(0.39f, 3.95f)
        reflectiveCurveToRelative(-4.64f, 7.17f, -15.82f, 7.08f)
        reflectiveCurveToRelative(-13.57f, -2.76f, -15.87f, -5.92f)
        curveToRelative(0.0f, 0.0f, -1.3f, -4.45f, -0.8f, -6.81f)
        curveTo(143.0f, 276.85f, 135.5f, 277.5f, 125.5f, 286.5f)
        close()
        moveTo(143.5f, 282.5f)
        reflectiveCurveToRelative(0.0f, 6.0f, 14.0f, 7.0f)
        reflectiveCurveToRelative(18.0f, -7.0f, 18.0f, -7.0f)
        lineToRelative(-1.0f, -10.0f)
        reflectiveCurveToRelative(-0.12f, -5.0f, -15.56f, -5.0f)
        reflectiveCurveToRelative(-14.44f, 4.0f, -15.44f, 6.0f)
        arcToRelative(10.07f, 10.07f, 0.0f, false, false, -0.43f, 4.0f)
        curveTo(143.14f, 280.0f, 143.5f, 282.5f, 143.5f, 282.5f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(143.5f, 272.5f)
        arcToRelative(4.87f, 4.87f, 0.0f, false, false, -0.19f, 1.41f)
        curveToRelative(0.0f, 2.53f, 1.69f, 7.59f, 15.19f, 7.59f)
        curveToRelative(18.0f, 0.0f, 16.0f, -9.0f, 16.0f, -9.0f)
        reflectiveCurveToRelative(-1.0f, -5.0f, -13.0f, -5.0f)
        reflectiveCurveTo(145.5f, 268.5f, 143.5f, 272.5f)
        close()
    }
private val pathData9 =
    PathData {
        moveTo(134.5f, 281.5f)
        reflectiveCurveToRelative(5.0f, -1.0f, 3.0f, 2.0f)
        reflectiveCurveToRelative(-7.0f, 4.0f, -9.0f, 9.0f)
        arcToRelative(17.55f, 17.55f, 0.0f, false, false, -1.2f, 9.07f)
        lineToRelative(-5.8f, -0.07f)
        reflectiveCurveTo(120.0f, 288.59f, 129.74f, 283.0f)
        arcTo(12.58f, 12.58f, 0.0f, false, true, 134.5f, 281.5f)
        close()
        moveTo(168.21f, 288.58f)
        reflectiveCurveToRelative(15.29f, -5.08f, 20.29f, -1.08f)
        reflectiveCurveToRelative(5.0f, 12.0f, 5.0f, 14.0f)
        reflectiveCurveToRelative(11.0f, 0.0f, 11.0f, 0.0f)
        reflectiveCurveToRelative(-2.7f, -17.32f, -10.85f, -20.16f)
        arcToRelative(50.64f, 50.64f, 0.0f, false, false, -18.15f, -2.84f)
        horizontalLineToRelative(0.0f)
        verticalLineToRelative(4.0f)
        reflectiveCurveTo(169.92f, 288.67f, 168.21f, 288.58f)
        close()
        moveTo(170.33f, 279.34f)
        verticalLineToRelative(7.9f)
        reflectiveCurveToRelative(5.17f, -3.74f, 5.17f, -4.74f)
        reflectiveCurveToRelative(-0.94f, -9.36f, -0.94f, -9.36f)
        reflectiveCurveTo(175.16f, 277.17f, 170.33f, 279.34f)
        close()
    }
private val pathData12 =
    PathData {
        moveTo(143.0f, 201.0f)
        reflectiveCurveToRelative(8.0f, 21.0f, 7.0f, 38.0f)
        reflectiveCurveToRelative(-2.0f, 35.0f, -2.0f, 35.0f)
        reflectiveCurveToRelative(0.5f, 3.5f, 10.5f, 3.5f)
        reflectiveCurveToRelative(13.0f, -5.0f, 13.0f, -5.0f)
        reflectiveCurveToRelative(0.0f, -37.0f, -3.0f, -53.0f)
        reflectiveCurveToRelative(-8.0f, -22.0f, -8.0f, -22.0f)
        reflectiveCurveTo(147.5f, 200.5f, 143.0f, 201.0f)
        close()
    }
private val pathData13 =
    PathData {
        moveTo(153.89f, 199.0f)
        reflectiveCurveToRelative(6.61f, 15.53f, 8.61f, 28.53f)
        reflectiveCurveToRelative(4.0f, 24.0f, 4.0f, 33.0f)
        verticalLineToRelative(15.63f)
        reflectiveCurveToRelative(5.0f, -1.63f, 5.0f, -3.63f)
        reflectiveCurveToRelative(1.23f, -37.53f, -5.39f, -62.77f)
        curveToRelative(0.0f, 0.0f, -3.61f, -10.23f, -5.61f, -12.23f)
        close()
    }
private val pathData14 =
    PathData {
        moveTo(150.0f, 226.5f)
        reflectiveCurveToRelative(14.89f, 2.35f, 18.24f, -8.32f)
        moveTo(150.5f, 237.5f)
        reflectiveCurveToRelative(1.0f, 4.0f, 8.0f, 3.0f)
        reflectiveCurveToRelative(11.0f, -4.0f, 12.0f, -6.0f)
        moveTo(149.63f, 250.63f)
        reflectiveCurveToRelative(0.88f, 3.88f, 6.88f, 3.88f)
        reflectiveCurveTo(170.0f, 251.0f, 171.24f, 248.74f)
        moveTo(147.5f, 213.5f)
        reflectiveCurveToRelative(1.0f, 2.0f, 6.0f, 2.0f)
        reflectiveCurveToRelative(12.85f, -4.58f, 12.43f, -6.29f)
        moveTo(149.0f, 262.5f)
        reflectiveCurveToRelative(0.0f, 5.0f, 8.18f, 4.0f)
        reflectiveCurveToRelative(12.77f, -3.62f, 14.06f, -6.31f)
    }
