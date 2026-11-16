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
fun Body2ThinnestPreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    body2Thinnest(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun body2Thinnest(
    fgColor: SolidColor,
    builder: Builder,
) {
    // blue
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)

    // Gray arms and legs
    builder.addPath(pathData8, fill = Gray, stroke = Black, strokeLineWidth = 1.0f)

    // right side shade
    builder.addPath(pathData11, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 1.0f)

    // Joints
    builder.addPath(pathData18, stroke = Black, strokeLineWidth = 1.0f)

    // Shades
    builder.addPath(pathData4, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData6, fill = Black, fillAlpha = 0.2f)
}

private val pathData1 =
    PathData {
        moveTo(180.45f, 252.21f)
        reflectiveCurveToRelative(-17.95f, -2.71f, -29.95f, 2.29f)
        reflectiveCurveToRelative(-15.0f, 8.0f, -16.0f, 24.0f)
        curveToRelative(0.0f, 0.0f, 1.0f, 27.0f, 1.0f, 28.0f)
        reflectiveCurveToRelative(49.0f, 1.0f, 49.0f, 1.0f)
        verticalLineToRelative(-9.0f)
        arcToRelative(27.15f, 27.15f, 0.0f, false, true, -3.0f, -13.0f)
        curveToRelative(0.0f, -8.0f, 7.0f, -6.16f, 7.0f, -6.16f)
        reflectiveCurveTo(177.0f, 279.0f, 177.24f, 263.25f)
        arcTo(16.2f, 16.2f, 0.0f, false, true, 180.45f, 252.21f)
        close()
        moveTo(133.5f, 277.5f)
        reflectiveCurveToRelative(-6.92f, -1.5f, -8.0f, -5.25f)
        lineToRelative(-0.68f, -1.62f)
        curveToRelative(-0.37f, -6.66f, 1.07f, -12.0f, 5.72f, -15.0f)
        curveToRelative(0.0f, 0.0f, 5.17f, -4.37f, 11.55f, -4.24f)
        curveToRelative(0.0f, 0.0f, 5.37f, 0.13f, 7.37f, 3.13f)
        curveToRelative(0.0f, 0.0f, -9.0f, 3.6f, -12.0f, 9.3f)
        reflectiveCurveToRelative(-3.09f, 13.32f, -3.0f, 14.0f)
        close()
        moveTo(188.5f, 247.5f)
        reflectiveCurveToRelative(-8.0f, 2.0f, -10.0f, 8.0f)
        reflectiveCurveToRelative(-3.0f, 19.0f, 8.0f, 23.0f)
        reflectiveCurveToRelative(19.0f, -7.0f, 19.0f, -17.0f)
        reflectiveCurveTo(201.5f, 245.5f, 188.5f, 247.5f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(134.5f, 254.5f)
        reflectiveCurveToRelative(3.0f, -1.0f, 3.0f, 0.0f)
        reflectiveCurveToRelative(-6.0f, 5.0f, -7.0f, 10.0f)
        reflectiveCurveToRelative(0.0f, 8.0f, -2.0f, 8.0f)
        reflectiveCurveToRelative(-4.67f, 0.09f, -2.84f, -10.45f)
        arcTo(13.0f, 13.0f, 0.0f, false, true, 134.5f, 254.5f)
        close()
        moveTo(142.5f, 259.5f)
        arcToRelative(2.19f, 2.19f, 0.0f, false, true, 3.0f, 1.0f)
        quadToRelative(1.5f, 3.0f, -3.0f, 9.0f)
        curveToRelative(-3.0f, 4.0f, -3.0f, 14.0f, -2.0f, 19.0f)
        reflectiveCurveToRelative(2.0f, 10.0f, 2.0f, 13.0f)
        horizontalLineToRelative(-7.16f)
        reflectiveCurveTo(134.0f, 284.0f, 134.75f, 275.26f)
        reflectiveCurveToRelative(2.49f, -12.37f, 6.12f, -15.56f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(189.5f, 248.5f)
        reflectiveCurveToRelative(9.0f, 0.0f, 9.0f, 10.0f)
        reflectiveCurveToRelative(-4.0f, 16.0f, -9.0f, 17.0f)
        reflectiveCurveToRelative(-7.0f, 0.77f, -7.0f, 0.77f)
        arcToRelative(12.77f, 12.77f, 0.0f, false, false, 8.0f, 3.07f)
        curveToRelative(5.0f, 0.16f, 15.0f, -4.84f, 15.0f, -17.84f)
        reflectiveCurveToRelative(-7.26f, -14.64f, -14.13f, -14.32f)
        reflectiveCurveTo(188.5f, 248.5f, 189.5f, 248.5f)
        close()
        moveTo(197.5f, 279.5f)
        reflectiveCurveToRelative(5.0f, 8.0f, 6.0f, 15.0f)
        reflectiveCurveToRelative(0.0f, 9.0f, 0.0f, 9.0f)
        horizontalLineToRelative(8.0f)
        reflectiveCurveTo(213.0f, 293.0f, 206.76f, 280.74f)
        curveToRelative(0.0f, 0.0f, -3.26f, -6.24f, -5.26f, -7.24f)
        arcToRelative(22.0f, 22.0f, 0.0f, false, true, -4.74f, 4.65f)
        close()
        moveTo(159.0f, 191.5f)
        horizontalLineToRelative(6.5f)
        verticalLineToRelative(10.0f)
        reflectiveCurveToRelative(1.0f, 20.0f, 0.89f, 29.33f)
        arcTo(182.26f, 182.26f, 0.0f, false, false, 168.0f, 252.75f)
        horizontalLineToRelative(0.0f)
        curveToRelative(-0.5f, 4.25f, -5.5f, 5.75f, -5.5f, 5.75f)
        verticalLineToRelative(-2.0f)
        curveToRelative(0.0f, -2.0f, 1.0f, -15.0f, 1.0f, -23.0f)
        reflectiveCurveToRelative(-4.0f, -40.0f, -4.0f, -40.0f)
        close()
    }
private val pathData8 =
    PathData {
        moveTo(201.86f, 273.17f)
        reflectiveCurveToRelative(9.64f, 10.33f, 9.64f, 28.33f)
        horizontalLineToRelative(-13.0f)
        reflectiveCurveToRelative(2.74f, -14.31f, -7.13f, -22.15f)
        arcTo(11.59f, 11.59f, 0.0f, false, false, 201.86f, 273.17f)
        close()
        moveTo(130.17f, 276.81f)
        arcToRelative(64.62f, 64.62f, 0.0f, false, true, -1.51f, 10.87f)
        curveToRelative(-1.2f, 5.22f, -3.35f, 11.0f, -7.16f, 13.82f)
        lineToRelative(13.79f, -0.59f)
        reflectiveCurveToRelative(-0.79f, -21.41f, -0.79f, -22.41f)
        close()
        moveTo(150.0f, 192.0f)
        reflectiveCurveToRelative(1.0f, 61.0f, 2.0f, 64.0f)
        curveToRelative(0.0f, 0.0f, 2.0f, 5.0f, 10.0f, 3.0f)
        curveToRelative(0.0f, 0.0f, 6.0f, -1.0f, 6.0f, -7.0f)
        curveToRelative(0.0f, 0.0f, -3.5f, -40.5f, -2.5f, -60.5f)
        curveTo(165.5f, 191.5f, 152.5f, 191.5f, 150.0f, 192.0f)
        close()
    }
private val pathData11 =
    PathData {
        moveTo(169.5f, 304.5f)
        reflectiveCurveToRelative(-7.0f, -21.0f, 0.0f, -42.0f)
        curveToRelative(0.0f, 0.0f, 3.39f, -7.79f, 10.2f, -10.39f)
        lineToRelative(0.8f, 0.39f)
        reflectiveCurveToRelative(-3.64f, 3.11f, -3.32f, 10.06f)
        reflectiveCurveToRelative(2.16f, 14.4f, 9.74f, 16.17f)
        arcToRelative(5.19f, 5.19f, 0.0f, false, false, -5.0f, 3.77f)
        curveToRelative(-1.44f, 4.0f, 0.38f, 11.28f, 1.47f, 13.64f)
        arcTo(13.05f, 13.05f, 0.0f, false, true, 184.5f, 301.0f)
        close()
    }
private val pathData18 =
    PathData {
        moveTo(134.5f, 290.5f)
        reflectiveCurveToRelative(-2.86f, -2.88f, -5.93f, -2.44f)
        moveTo(124.0f, 299.0f)
        reflectiveCurveToRelative(5.55f, -1.48f, 9.0f, 2.0f)
        moveTo(164.5f, 202.5f)
        reflectiveCurveToRelative(0.0f, 3.0f, -5.0f, 3.0f)
        reflectiveCurveToRelative(-8.56f, -1.0f, -9.28f, -1.5f)
        moveTo(165.0f, 214.5f)
        reflectiveCurveToRelative(-1.0f, 2.0f, -5.18f, 2.0f)
        arcToRelative(39.35f, 39.35f, 0.0f, false, true, -9.38f, -1.58f)
        moveTo(166.0f, 224.5f)
        reflectiveCurveToRelative(0.0f, 2.0f, -4.62f, 3.0f)
        reflectiveCurveToRelative(-10.38f, -1.0f, -10.38f, -1.0f)
        moveTo(166.0f, 233.5f)
        reflectiveCurveToRelative(0.0f, 2.0f, -3.21f, 3.0f)
        arcToRelative(16.53f, 16.53f, 0.0f, false, true, -11.79f, -1.0f)
        moveTo(167.0f, 244.5f)
        arcToRelative(7.0f, 7.0f, 0.0f, false, true, -5.17f, 4.0f)
        curveToRelative(-4.13f, 1.0f, -10.48f, -1.51f, -10.48f, -1.51f)
        moveTo(207.0f, 281.6f)
        reflectiveCurveToRelative(-4.0f, -2.4f, -12.0f, 2.4f)
        moveTo(211.42f, 296.16f)
        reflectiveCurveToRelative(-2.24f, -4.33f, -12.58f, 1.51f)
    }
