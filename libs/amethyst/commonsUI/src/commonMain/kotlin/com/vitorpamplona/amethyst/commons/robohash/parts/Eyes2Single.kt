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
import com.vitorpamplona.amethyst.commons.robohash.LightRed
import com.vitorpamplona.amethyst.commons.robohash.roboBuilder

@Composable
fun Eyes2Single() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    eyes2Single(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun eyes2Single(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.4f, strokeAlpha = 0.4f)
    builder.addPath(pathData3, fill = Black)
    builder.addPath(pathData4, fill = Brown, stroke = Black, strokeLineWidth = 0.5f)
    builder.addPath(pathData5, fill = LightRed, stroke = Black, strokeLineWidth = 0.5f)
    builder.addPath(pathData6, fill = Black)
    builder.addPath(pathData7, stroke = Black, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(135.5f, 123.5f)
        reflectiveCurveToRelative(-34.0f, 1.0f, -35.0f, 17.0f)
        reflectiveCurveToRelative(23.0f, 12.0f, 23.0f, 12.0f)
        reflectiveCurveToRelative(23.0f, -2.0f, 28.0f, -3.0f)
        reflectiveCurveToRelative(18.0f, -5.0f, 18.0f, -14.0f)
        reflectiveCurveToRelative(-8.0f, -13.0f, -17.0f, -13.0f)
        reflectiveCurveTo(139.5f, 123.5f, 135.5f, 123.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(117.31f, 152.44f)
        curveToRelative(-4.51f, 0.0f, -10.59f, -0.74f, -13.91f, -4.27f)
        arcToRelative(9.8f, 9.8f, 0.0f, false, true, -2.41f, -7.64f)
        curveToRelative(1.0f, -15.37f, 34.18f, -16.52f, 34.52f, -16.53f)
        horizontalLineToRelative(0.0f)
        curveToRelative(1.61f, 0.0f, 3.25f, -0.16f, 5.15f, -0.35f)
        arcTo(107.89f, 107.89f, 0.0f, false, true, 152.5f, 123.0f)
        curveToRelative(8.0f, 0.0f, 16.5f, 3.28f, 16.5f, 12.5f)
        curveToRelative(0.0f, 9.71f, -15.8f, 13.15f, -17.6f, 13.51f)
        curveToRelative(-4.92f, 1.0f, -27.72f, 3.0f, -27.95f, 3.0f)
        arcToRelative(42.0f, 42.0f, 0.0f, false, true, -6.14f, 0.44f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(152.5f, 123.5f)
        curveToRelative(7.73f, 0.0f, 16.0f, 3.15f, 16.0f, 12.0f)
        curveToRelative(0.0f, 7.56f, -10.81f, 11.74f, -17.2f, 13.0f)
        curveToRelative(-4.89f, 1.0f, -27.66f, 3.0f, -27.89f, 3.0f)
        horizontalLineToRelative(-0.08f)
        arcToRelative(41.19f, 41.19f, 0.0f, false, true, -6.0f, 0.43f)
        curveToRelative(-4.41f, 0.0f, -10.35f, -0.71f, -13.54f, -4.12f)
        arcToRelative(9.32f, 9.32f, 0.0f, false, true, -2.27f, -7.27f)
        curveToRelative(0.93f, -14.91f, 33.7f, -16.05f, 34.0f, -16.06f)
        curveToRelative(1.65f, 0.0f, 3.3f, -0.17f, 5.22f, -0.36f)
        arcToRelative(107.45f, 107.45f, 0.0f, false, true, 11.78f, -0.64f)
        moveToRelative(0.0f, -1.0f)
        curveToRelative(-9.0f, 0.0f, -13.0f, 1.0f, -17.0f, 1.0f)
        curveToRelative(0.0f, 0.0f, -34.0f, 1.0f, -35.0f, 17.0f)
        curveToRelative(-0.67f, 10.67f, 9.78f, 12.44f, 16.81f, 12.44f)
        arcToRelative(41.73f, 41.73f, 0.0f, false, false, 6.19f, -0.44f)
        reflectiveCurveToRelative(23.0f, -2.0f, 28.0f, -3.0f)
        reflectiveCurveToRelative(18.0f, -5.0f, 18.0f, -14.0f)
        reflectiveCurveToRelative(-8.0f, -13.0f, -17.0f, -13.0f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(106.5f, 132.5f)
        reflectiveCurveToRelative(-2.0f, 2.0f, -1.0f, 6.0f)
        reflectiveCurveToRelative(2.0f, 9.0f, 16.0f, 8.0f)
        arcToRelative(306.78f, 306.78f, 0.0f, false, false, 31.0f, -4.0f)
        curveToRelative(6.0f, -1.0f, 16.0f, -1.5f, 17.0f, -6.25f)
        reflectiveCurveToRelative(-2.18f, -11.89f, -12.09f, -13.32f)
        reflectiveCurveToRelative(-12.93f, -0.11f, -16.92f, 0.23f)
        reflectiveCurveToRelative(-8.25f, 0.45f, -11.62f, 0.89f)
        reflectiveCurveToRelative(-16.37f, 2.44f, -22.37f, 8.44f)
    }
private val pathData5 =
    PathData {
        moveTo(134.0f, 124.0f)
        reflectiveCurveToRelative(-4.0f, 2.0f, -4.0f, 6.0f)
        reflectiveCurveToRelative(3.5f, 11.5f, 10.5f, 10.5f)
        reflectiveCurveToRelative(9.0f, -5.0f, 9.0f, -11.0f)
        reflectiveCurveToRelative(-6.58f, -6.54f, -6.58f, -6.54f)
        reflectiveCurveToRelative(-3.42f, 0.54f, -4.42f, 0.54f)
        reflectiveCurveTo(134.0f, 124.0f, 134.0f, 124.0f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(138.0f, 130.5f)
        arcToRelative(2.0f, 2.5f, 0.0f, true, false, 4.0f, 0.0f)
        arcToRelative(2.0f, 2.5f, 0.0f, true, false, -4.0f, 0.0f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(107.5f, 131.5f)
        reflectiveCurveToRelative(-3.0f, 2.0f, -2.0f, 7.0f)
        curveToRelative(1.0f, 4.0f, 2.0f, 9.0f, 16.0f, 8.0f)
        arcToRelative(306.78f, 306.78f, 0.0f, false, false, 31.0f, -4.0f)
        curveToRelative(6.0f, -1.0f, 16.0f, -1.5f, 17.0f, -6.25f)
        reflectiveCurveToRelative(-2.18f, -11.89f, -12.09f, -13.32f)
        reflectiveCurveToRelative(-12.93f, -0.11f, -16.92f, 0.23f)
        reflectiveCurveToRelative(-8.25f, 0.45f, -11.62f, 0.89f)
        reflectiveCurveToRelative(-15.37f, 1.44f, -21.37f, 7.44f)
    }
