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
fun Accessory7Antenna() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    accessory7Antenna(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun accessory7Antenna(
    fgColor: SolidColor,
    builder: Builder,
) {
    // Blye support
    builder.addPath(pathData1, fill = fgColor)

    // Blue Countour
    builder.addPath(pathData8, stroke = Black, strokeLineWidth = 1.0f)

    // Column Contour
    builder.addPath(pathData11, fill = Black, stroke = Black, fillAlpha = 0.2f, strokeLineWidth = 1.0f)

    // Stickts
    builder.addPath(pathData14, fill = Black)

    // Curved Stick
    builder.addPath(pathData23, stroke = Black, strokeLineWidth = 1.5f)

    // Shades
    builder.addPath(pathData22, fill = Black, fillAlpha = 0.2f)
}

private val pathData1 =
    PathData {
        moveTo(128.5f, 86.5f)
        arcToRelative(3.49f, 3.49f, 0.0f, false, false, -1.0f, 2.0f)
        verticalLineToRelative(3.0f)
        reflectiveCurveToRelative(3.0f, 3.0f, 9.0f, 3.0f)
        reflectiveCurveToRelative(12.0f, -4.0f, 12.0f, -4.0f)
        verticalLineToRelative(-5.0f)
        reflectiveCurveToRelative(-1.0f, -1.0f, -6.0f, -1.0f)
        reflectiveCurveTo(131.5f, 84.5f, 128.5f, 86.5f)
        close()
        moveTo(173.5f, 21.5f)
        lineToRelative(-35.0f, 17.0f)
        reflectiveCurveToRelative(-0.57f, 2.41f, 0.22f, 3.71f)
        lineToRelative(0.78f, 1.29f)
        lineToRelative(37.0f, -18.0f)
        reflectiveCurveTo(178.5f, 20.5f, 173.5f, 21.5f)
        close()
        moveTo(96.5f, 58.5f)
        reflectiveCurveToRelative(-2.0f, 2.0f, -1.0f, 3.0f)
        reflectiveCurveToRelative(3.0f, 0.0f, 3.0f, 0.0f)
        lineToRelative(33.66f, -15.9f)
        arcToRelative(6.44f, 6.44f, 0.0f, false, true, -0.66f, -3.1f)
        close()
        moveTo(134.5f, 47.5f)
        lineToRelative(-1.0f, 37.0f)
        arcToRelative(4.33f, 4.33f, 0.0f, false, false, 4.0f, 2.0f)
        arcToRelative(7.65f, 7.65f, 0.0f, false, false, 5.0f, -2.0f)
        lineToRelative(-2.0f, -40.0f)
        reflectiveCurveTo(135.5f, 44.5f, 134.5f, 47.5f)
        close()
        moveTo(136.5f, 38.5f)
        lineToRelative(-4.0f, 2.0f)
        arcToRelative(3.76f, 3.76f, 0.0f, false, false, -0.81f, 1.55f)
        arcToRelative(5.34f, 5.34f, 0.0f, false, false, -0.19f, 1.45f)
        curveToRelative(0.0f, 2.0f, 2.0f, 4.0f, 3.0f, 4.0f)
        curveToRelative(0.0f, 0.0f, -0.17f, -0.54f, 1.42f, -1.77f)
        arcToRelative(8.26f, 8.26f, 0.0f, false, true, 4.32f, -1.22f)
        horizontalLineToRelative(0.26f)
        reflectiveCurveToRelative(-2.0f, -2.0f, -2.0f, -3.0f)
        verticalLineToRelative(-3.0f)
        close()
    }
private val pathData8 =
    PathData {
        moveTo(134.5f, 47.5f)
        lineToRelative(-1.0f, 37.0f)
        arcToRelative(4.33f, 4.33f, 0.0f, false, false, 4.0f, 2.0f)
        arcToRelative(7.65f, 7.65f, 0.0f, false, false, 5.0f, -2.0f)
        lineToRelative(-2.0f, -40.0f)
        reflectiveCurveTo(135.5f, 44.5f, 134.5f, 47.5f)
        close()
        moveTo(128.5f, 86.5f)
        arcToRelative(3.49f, 3.49f, 0.0f, false, false, -1.0f, 2.0f)
        verticalLineToRelative(3.0f)
        reflectiveCurveToRelative(3.0f, 3.0f, 9.0f, 3.0f)
        reflectiveCurveToRelative(12.0f, -4.0f, 12.0f, -4.0f)
        verticalLineToRelative(-5.0f)
        reflectiveCurveToRelative(-0.8f, -1.0f, -5.8f, -1.0f)
        arcToRelative(7.15f, 7.15f, 0.0f, false, true, -4.57f, 2.0f)
        curveToRelative(-1.63f, 0.0f, -4.17f, 0.0f, -4.63f, -1.72f)
        arcTo(10.71f, 10.71f, 0.0f, false, false, 128.5f, 86.5f)
        close()
        moveTo(136.5f, 38.5f)
        lineToRelative(-4.0f, 2.0f)
        arcToRelative(3.76f, 3.76f, 0.0f, false, false, -0.81f, 1.55f)
        arcToRelative(5.34f, 5.34f, 0.0f, false, false, -0.19f, 1.45f)
        curveToRelative(0.0f, 2.0f, 2.0f, 4.0f, 3.0f, 4.0f)
        curveToRelative(0.0f, 0.0f, -0.17f, -0.54f, 1.42f, -1.77f)
        arcToRelative(8.26f, 8.26f, 0.0f, false, true, 4.32f, -1.22f)
        horizontalLineToRelative(0.26f)
        reflectiveCurveToRelative(-2.0f, -2.0f, -2.0f, -3.0f)
        verticalLineToRelative(-3.0f)
        close()
    }
private val pathData11 =
    PathData {
        moveTo(128.5f, 87.5f)
        reflectiveCurveToRelative(0.0f, 2.0f, 6.0f, 2.0f)
        reflectiveCurveToRelative(11.0f, -1.0f, 13.0f, -3.0f)
        curveToRelative(0.48f, -0.55f, 0.5f, -1.75f, -2.25f, -1.87f)
        curveToRelative(-1.0f, 0.0f, -3.15f, 0.0f, -3.15f, 0.0f)
        arcToRelative(6.77f, 6.77f, 0.0f, false, true, -4.1f, 1.8f)
        curveToRelative(-3.5f, 0.1f, -4.31f, -1.6f, -4.31f, -1.6f)
        reflectiveCurveTo(128.5f, 85.5f, 128.5f, 87.5f)
        close()
        moveTo(96.5f, 58.5f)
        reflectiveCurveToRelative(-2.0f, 2.0f, -1.0f, 3.0f)
        reflectiveCurveToRelative(3.0f, 0.0f, 3.0f, 0.0f)
        lineToRelative(33.8f, -15.6f)
        arcToRelative(7.45f, 7.45f, 0.0f, false, true, -0.8f, -3.4f)
        close()
        moveTo(173.5f, 21.5f)
        lineToRelative(-35.0f, 17.0f)
        reflectiveCurveToRelative(-0.57f, 2.41f, 0.22f, 3.71f)
        lineToRelative(0.78f, 1.29f)
        lineToRelative(37.0f, -18.0f)
        curveTo(177.52f, 25.52f, 179.49f, 20.53f, 173.5f, 21.5f)
        close()
        moveTo(175.5f, 23.5f)
        moveToRelative(-2.0f, 0.0f)
        arcToRelative(2.0f, 2.0f, 0.0f, true, true, 4.0f, 0.0f)
        arcToRelative(2.0f, 2.0f, 0.0f, true, true, -4.0f, 0.0f)
    }
private val pathData14 =
    PathData {
        moveTo(137.5f, 5.5f)
        reflectiveCurveToRelative(-2.0f, 0.0f, 1.0f, 2.0f)
        reflectiveCurveToRelative(32.0f, 15.0f, 32.0f, 15.0f)
        lineToRelative(2.0f, -1.0f)
        lineToRelative(-33.0f, -15.0f)
        close()
        moveTo(56.72f, 44.0f)
        arcToRelative(1.7f, 1.7f, 0.0f, false, false, 1.2f, 1.19f)
        lineTo(96.29f, 58.83f)
        lineToRelative(1.89f, -1.2f)
        lineTo(58.81f, 44.1f)
        reflectiveCurveTo(56.44f, 43.0f, 56.72f, 44.0f)
        close()
        moveTo(114.5f, 17.5f)
        reflectiveCurveToRelative(-2.0f, 0.0f, 1.0f, 2.0f)
        reflectiveCurveToRelative(32.0f, 15.0f, 32.0f, 15.0f)
        lineToRelative(2.0f, -1.0f)
        lineToRelative(-33.0f, -15.0f)
        close()
        moveTo(102.0f, 26.53f)
        reflectiveCurveToRelative(-2.0f, 0.12f, 1.12f, 1.94f)
        reflectiveCurveTo(133.0f, 40.2f, 133.0f, 40.2f)
        lineToRelative(1.91f, -0.77f)
        lineToRelative(-30.83f, -12.0f)
        close()
        moveTo(177.5f, 24.19f)
        lineTo(212.0f, 41.0f)
        curveToRelative(1.0f, 1.0f, 0.39f, 1.37f, -2.0f, 1.0f)
        lineTo(176.5f, 25.5f)
        reflectiveCurveTo(177.0f, 25.38f, 177.5f, 24.19f)
        close()
        moveTo(154.91f, 35.41f)
        lineToRelative(35.6f, 14.34f)
        reflectiveCurveTo(192.0f, 51.0f, 188.58f, 50.88f)
        lineTo(154.0f, 36.79f)
        close()
        moveTo(102.89f, 59.02f)
        lineToRelative(31.45f, 9.67f)
        lineToRelative(-0.69f, 1.29f)
        lineToRelative(-32.65f, -10.0f)
        lineToRelative(1.89f, -0.96f)
        close()
        moveTo(142.0f, 42.54f)
        lineToRelative(35.55f, 13.0f)
        curveToRelative(2.4f, 0.9f, 0.47f, 2.47f, -2.0f, 1.19f)
        lineTo(140.2f, 43.79f)
        close()
    }
private val pathData22 =
    PathData {
        moveTo(136.0f, 38.75f)
        arcToRelative(5.37f, 5.37f, 0.0f, false, false, -0.5f, 2.75f)
        curveToRelative(0.0f, 2.0f, 2.22f, 3.37f, 2.22f, 3.37f)
        lineToRelative(1.8f, 40.84f)
        lineTo(144.0f, 88.34f)
        verticalLineToRelative(4.0f)
        lineToRelative(4.46f, -1.89f)
        verticalLineToRelative(-5.0f)
        arcToRelative(35.36f, 35.36f, 0.0f, false, false, -6.0f, -1.0f)
        lineToRelative(-2.0f, -40.0f)
        arcToRelative(3.7f, 3.7f, 0.0f, false, true, -2.0f, -3.0f)
        verticalLineToRelative(-3.0f)
        close()
    }
private val pathData23 =
    PathData {
        moveTo(119.5f, 47.5f)
        lineToRelative(-32.0f, -12.0f)
        reflectiveCurveToRelative(-3.0f, -1.0f, -4.0f, 1.0f)
        reflectiveCurveToRelative(0.0f, 3.0f, 2.0f, 4.0f)
        reflectiveCurveToRelative(27.0f, 10.0f, 27.0f, 10.0f)
        moveTo(116.05f, 49.09f)
        lineTo(83.95f, 37.51f)
        moveTo(125.5f, 49.2f)
        lineTo(134.36f, 52.57f)
        moveTo(117.9f, 52.66f)
        lineTo(134.0f, 59.0f)
        moveTo(121.42f, 50.93f)
        lineTo(134.27f, 55.83f)
        moveTo(141.0f, 54.57f)
        lineTo(158.0f, 62.0f)
        reflectiveCurveToRelative(4.0f, 2.0f, 3.0f, 4.0f)
        reflectiveCurveToRelative(-5.0f, 1.0f, -6.0f, 1.0f)
        reflectiveCurveToRelative(-13.65f, -5.5f, -13.65f, -5.5f)
        moveTo(141.18f, 58.12f)
        lineTo(160.5f, 66.5f)
    }
