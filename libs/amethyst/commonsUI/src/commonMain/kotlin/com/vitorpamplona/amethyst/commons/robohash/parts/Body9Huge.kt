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
fun Body9HugePreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    body9Huge(SolidColor(Color.Yellow), this)
                },
            ),
        contentDescription = "",
    )
}

fun body9Huge(
    fgColor: SolidColor,
    builder: Builder,
) {
    // body
    builder.addPath(pathData4, fill = fgColor)
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)

    // base neck
    builder.addPath(pathData7, fill = Black, stroke = Black, fillAlpha = 0.6f, strokeLineWidth = 1.0f)

    // neck
    builder.addPath(pathData11, fill = Gray, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData10, fill = SolidColor(Color.White), fillAlpha = 0.7f)

    // neck base shade
    builder.addPath(pathData17, fill = Black, fillAlpha = 0.4f)

    // left body shade
    builder.addPath(pathData18, fill = SolidColor(Color.White), fillAlpha = 0.7f)

    // left arm shades
    builder.addPath(pathData20, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData23, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData22, fill = Black, fillAlpha = 0.2f, stroke = Black, strokeAlpha = 0.1f, strokeLineWidth = 1f)

    // right arm shade
    builder.addPath(pathData24, fill = Black, fillAlpha = 0.4f, stroke = Black, strokeLineWidth = 1.0f)

    // right body shade
    builder.addPath(pathData26, fill = Black, fillAlpha = 0.4f)

    // Neck shade
    builder.addPath(pathData12, fill = SolidColor(Color(0xFF58595b)))

    // Shoulder lines
    builder.addPath(pathData21, stroke = Black, strokeLineWidth = 0.75f)

    // Joints and body divisions
    builder.addPath(pathData13, stroke = Black, strokeLineWidth = 1.0f)
}

private val pathData1 =
    PathData {
        moveTo(222.5f, 301.5f)
        reflectiveCurveToRelative(-10.0f, -35.0f, 11.0f, -53.0f)
        reflectiveCurveToRelative(58.0f, -15.0f, 70.0f, 2.0f)
        reflectiveCurveToRelative(2.0f, 51.0f, 2.0f, 51.0f)
        close()
        moveTo(83.5f, 254.5f)
        reflectiveCurveToRelative(-6.0f, 28.0f, 67.0f, 26.0f)
        reflectiveCurveToRelative(77.0f, -30.0f, 76.0f, -36.0f)
        reflectiveCurveToRelative(-12.3f, -10.12f, -26.0f, -13.0f)
        curveToRelative(0.0f, 5.0f, 1.0f, 22.0f, 1.0f, 22.0f)
        reflectiveCurveToRelative(-8.0f, 13.0f, -46.0f, 17.0f)
        curveToRelative(-29.0f, 1.0f, -45.0f, -9.0f, -45.0f, -9.0f)
        reflectiveCurveToRelative(-1.5f, -16.0f, -2.5f, -23.13f)
        curveTo(96.5f, 241.75f, 85.5f, 247.5f, 83.5f, 254.5f)
        close()
        moveTo(110.5f, 261.5f)
        reflectiveCurveToRelative(20.0f, 11.0f, 44.0f, 9.0f)
        reflectiveCurveToRelative(36.0f, -7.0f, 47.0f, -17.0f)
        lineToRelative(-1.0f, -24.0f)
        arcToRelative(10.53f, 10.53f, 0.0f, false, true, -0.31f, 4.41f)
        curveToRelative(-0.69f, 1.59f, -4.63f, 16.78f, -42.16f, 19.68f)
        reflectiveCurveToRelative(-47.0f, -4.1f, -50.0f, -16.1f)
        close()
        moveTo(108.0f, 237.0f)
        reflectiveCurveToRelative(-1.0f, 20.0f, 45.0f, 17.0f)
        reflectiveCurveToRelative(48.5f, -20.5f, 47.5f, -24.5f)
        curveToRelative(-0.91f, -3.63f, -13.68f, -10.56f, -39.0f, -9.56f)
        curveToRelative(0.0f, 0.56f, 0.26f, 3.3f, 0.26f, 3.3f)
        curveToRelative(1.74f, 9.26f, 0.74f, 15.26f, -9.26f, 16.26f)
        curveToRelative(-3.41f, 0.33f, -10.0f, -5.0f, -10.0f, -5.0f)
        reflectiveCurveToRelative(-1.0f, -8.0f, -1.0f, -13.0f)
        curveTo(120.38f, 224.59f, 108.41f, 230.0f, 108.0f, 237.0f)
        close()

        moveTo(87.5f, 247.5f)
        reflectiveCurveToRelative(-19.0f, -10.0f, -37.0f, -5.0f)
        reflectiveCurveToRelative(-29.0f, 20.0f, -32.0f, 37.0f)
        curveToRelative(-1.0f, 11.0f, 1.0f, 16.0f, 3.0f, 20.0f)
        horizontalLineToRelative(64.0f)
        reflectiveCurveToRelative(2.0f, -5.0f, 0.0f, -16.0f)
        reflectiveCurveToRelative(-3.0f, -23.0f, -2.0f, -29.0f)
        reflectiveCurveTo(87.5f, 247.5f, 87.5f, 247.5f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(83.5f, 250.0f)
        horizontalLineToRelative(143.5f)
        verticalLineToRelative(58.5f)
        horizontalLineToRelative(-143.5f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(108.0f, 237.0f)
        reflectiveCurveToRelative(-1.0f, 20.0f, 45.0f, 17.0f)
        reflectiveCurveToRelative(48.5f, -20.5f, 47.5f, -24.5f)
        curveToRelative(-0.91f, -3.63f, -13.68f, -10.56f, -39.0f, -9.56f)
        curveToRelative(0.0f, 0.56f, 0.26f, 3.3f, 0.26f, 3.3f)
        curveToRelative(1.74f, 9.26f, 0.74f, 15.26f, -9.26f, 16.26f)
        curveToRelative(-3.41f, 0.33f, -10.0f, -5.0f, -10.0f, -5.0f)
        reflectiveCurveToRelative(-1.0f, -8.0f, -1.0f, -13.0f)
        curveTo(120.38f, 224.59f, 108.41f, 230.0f, 108.0f, 237.0f)
        close()
    }
private val pathData9 =
    PathData {
        moveTo(193.37f, 260.15f)
        curveToRelative(-6.66f, 3.83f, -18.28f, 8.29f, -37.87f, 10.35f)
        curveToRelative(-29.0f, 1.0f, -45.0f, -9.0f, -45.0f, -9.0f)
        reflectiveCurveToRelative(-1.5f, -16.0f, -2.5f, -23.13f)
        curveToRelative(-11.5f, 3.38f, -22.5f, 9.13f, -24.5f, 16.13f)
        curveToRelative(0.0f, 0.0f, -6.0f, 28.0f, 67.0f, 26.0f)
        curveToRelative(29.58f, -0.81f, 47.84f, -5.89f, 59.0f, -12.0f)
        close()
    }
private val pathData10 =
    PathData {
        moveTo(83.5f, 254.5f)
        reflectiveCurveToRelative(-6.0f, 28.0f, 67.0f, 26.0f)
        curveToRelative(29.58f, -0.81f, 47.84f, -5.89f, 59.0f, -12.0f)
        curveToRelative(-5.5f, -2.5f, -10.5f, -5.5f, -16.13f, -8.35f)
        curveToRelative(-6.66f, 3.83f, -18.28f, 8.29f, -37.87f, 10.35f)
        curveToRelative(-29.0f, 1.0f, -45.0f, -9.0f, -45.0f, -9.0f)
        reflectiveCurveToRelative(-1.5f, -16.0f, -2.5f, -23.13f)
        curveTo(96.5f, 241.75f, 85.5f, 247.5f, 83.5f, 254.5f)
        close()
    }
private val pathData11 =
    PathData {
        moveTo(141.5f, 190.5f)
        verticalLineToRelative(29.0f)
        arcToRelative(142.25f, 142.25f, 0.0f, false, false, 1.0f, 15.0f)
        arcToRelative(9.78f, 9.78f, 0.0f, false, false, 10.0f, 5.0f)
        curveToRelative(7.0f, -1.0f, 11.0f, -4.0f, 10.0f, -11.0f)
        curveToRelative(-0.36f, -2.54f, -0.73f, -5.0f, -1.0f, -7.56f)
        arcToRelative(138.9f, 138.9f, 0.0f, false, true, -1.0f, -17.44f)
        verticalLineToRelative(-14.0f)
        arcTo(69.37f, 69.37f, 0.0f, false, false, 141.5f, 190.5f)
        close()
    }
private val pathData12 =
    PathData {
        moveTo(152.5f, 190.5f)
        reflectiveCurveToRelative(0.0f, 7.0f, 2.0f, 12.0f)
        arcToRelative(55.9f, 55.9f, 0.0f, false, true, 3.13f, 17.34f)
        curveToRelative(-0.12f, 3.66f, 0.88f, 15.66f, -3.12f, 17.66f)
        reflectiveCurveToRelative(-2.0f, 2.0f, -2.0f, 2.0f)
        reflectiveCurveToRelative(7.0f, -1.57f, 8.48f, -3.79f)
        reflectiveCurveToRelative(1.94f, -3.24f, 1.23f, -9.23f)
        arcToRelative(177.33f, 177.33f, 0.0f, false, true, -1.71f, -24.86f)
        verticalLineTo(189.5f)
        arcToRelative(37.46f, 37.46f, 0.0f, false, false, -6.17f, -0.25f)
        lineToRelative(-1.83f, 0.25f)
        close()
    }
private val pathData13 =
    PathData {
        moveTo(141.5f, 190.5f)
        verticalLineToRelative(29.0f)
        arcToRelative(142.25f, 142.25f, 0.0f, false, false, 1.0f, 15.0f)
        arcToRelative(9.78f, 9.78f, 0.0f, false, false, 10.0f, 5.0f)
        curveToRelative(7.0f, -1.0f, 11.0f, -4.0f, 10.0f, -11.0f)
        curveToRelative(-0.36f, -2.54f, -0.73f, -5.0f, -1.0f, -7.56f)
        arcToRelative(138.9f, 138.9f, 0.0f, false, true, -1.0f, -17.44f)
        verticalLineToRelative(-14.0f)
        arcTo(69.37f, 69.37f, 0.0f, false, false, 141.5f, 190.5f)
        close()
        moveTo(161.25f, 222.25f)
        reflectiveCurveToRelative(-2.67f, 3.25f, -9.51f, 3.25f)
        reflectiveCurveToRelative(-10.13f, -2.0f, -10.13f, -2.0f)
        moveTo(160.0f, 208.5f)
        reflectiveCurveToRelative(-3.0f, 3.0f, -9.06f, 3.0f)
        reflectiveCurveTo(142.0f, 210.0f, 142.0f, 210.0f)
        moveTo(160.0f, 197.5f)
        reflectiveCurveToRelative(-2.0f, 2.0f, -8.06f, 2.0f)
        arcTo(86.48f, 86.48f, 0.0f, false, true, 142.0f, 199.0f)

        // body
        moveTo(209.51f, 268.45f)
        reflectiveCurveToRelative(18.0f, -9.0f, 17.0f, -24.0f)
        lineToRelative(0.74f, 11.0f)
        arcToRelative(39.65f, 39.65f, 0.0f, false, false, -7.12f, 24.19f)
        curveToRelative(0.38f, 14.81f, 2.38f, 21.81f, 2.38f, 21.81f)
        horizontalLineToRelative(-12.0f)
        moveTo(83.5f, 254.5f)
        reflectiveCurveToRelative(-6.0f, 28.0f, 67.0f, 26.0f)
        reflectiveCurveToRelative(77.0f, -30.0f, 76.0f, -36.0f)
        reflectiveCurveToRelative(-12.3f, -10.12f, -26.0f, -13.0f)
        curveToRelative(0.0f, 5.0f, 1.0f, 22.0f, 1.0f, 22.0f)
        reflectiveCurveToRelative(-8.0f, 13.0f, -46.0f, 17.0f)
        curveToRelative(-29.0f, 1.0f, -45.0f, -9.0f, -45.0f, -9.0f)
        reflectiveCurveToRelative(-1.5f, -16.0f, -2.5f, -23.13f)
        curveTo(96.5f, 241.75f, 85.5f, 247.5f, 83.5f, 254.5f)
        close()
    }
private val pathData17 =
    PathData {
        moveTo(188.0f, 246.51f)
        lineTo(189.52f, 262.0f)
        reflectiveCurveToRelative(6.63f, -4.0f, 9.06f, -6.0f)
        lineToRelative(2.42f, -2.0f)
        reflectiveCurveToRelative(0.72f, 0.24f, 0.36f, -3.88f)
        reflectiveCurveToRelative(-0.79f, -17.93f, -0.79f, -17.93f)
        reflectiveCurveTo(200.0f, 240.0f, 188.0f, 246.51f)
        close()
    }
private val pathData18 =
    PathData {
        moveTo(84.0f, 273.63f)
        reflectiveCurveTo(97.0f, 277.0f, 97.0f, 285.0f)
        reflectiveCurveToRelative(-1.0f, 9.0f, 0.0f, 14.0f)
        reflectiveCurveToRelative(0.0f, 4.0f, 0.0f, 4.0f)
        horizontalLineTo(84.0f)
        reflectiveCurveToRelative(3.32f, -3.0f, 2.41f, -11.26f)
        reflectiveCurveTo(85.0f, 279.5f, 85.0f, 279.5f)
        close()
    }
private val pathData19 =
    PathData {
        moveTo(87.5f, 247.5f)
        reflectiveCurveToRelative(-19.0f, -10.0f, -37.0f, -5.0f)
        reflectiveCurveToRelative(-29.0f, 20.0f, -32.0f, 37.0f)
        curveToRelative(-1.0f, 11.0f, 1.0f, 16.0f, 3.0f, 20.0f)
        horizontalLineToRelative(64.0f)
        reflectiveCurveToRelative(2.0f, -5.0f, 0.0f, -16.0f)
        reflectiveCurveToRelative(-3.0f, -23.0f, -2.0f, -29.0f)
        reflectiveCurveTo(87.5f, 247.5f, 87.5f, 247.5f)
        close()
    }
private val pathData20 =
    PathData {
        moveTo(87.5f, 247.5f)
        arcToRelative(63.65f, 63.65f, 0.0f, false, false, -25.06f, -6.39f)
        curveToRelative(7.06f, 2.39f, 3.06f, 8.39f, -8.94f, 13.39f)
        curveToRelative(-11.0f, 6.0f, -23.0f, 10.0f, -27.5f, 32.5f)
        curveToRelative(-0.67f, 7.37f, 0.84f, 13.18f, 1.5f, 14.5f)
        lineToRelative(58.0f, -2.0f)
        reflectiveCurveToRelative(2.0f, -5.0f, 0.0f, -16.0f)
        reflectiveCurveToRelative(-3.0f, -23.0f, -2.0f, -29.0f)
        reflectiveCurveTo(87.5f, 247.5f, 87.5f, 247.5f)
        close()
    }
private val pathData21 =
    PathData {
        moveTo(33.5f, 299.5f)
        reflectiveCurveToRelative(-7.0f, -19.0f, -3.0f, -35.0f)
        arcToRelative(28.63f, 28.63f, 0.0f, false, true, 17.0f, -21.06f)
        moveTo(292.5f, 241.5f)
        reflectiveCurveToRelative(-12.0f, -1.0f, -13.0f, 15.0f)
        curveToRelative(-1.0f, 14.0f, 4.0f, 41.0f, 7.0f, 45.0f)
    }
private val pathData22 =
    PathData {
        moveTo(62.44f, 241.12f)
        arcToRelative(3.46f, 3.46f, 0.0f, false, true, 3.06f, 3.38f)
        curveToRelative(0.0f, 3.0f, 0.0f, 4.0f, -10.0f, 9.0f)
        reflectiveCurveToRelative(-21.45f, 10.0f, -26.22f, 21.0f)
        reflectiveCurveToRelative(-2.62f, 25.0f, -2.62f, 25.0f)
        horizontalLineTo(21.5f)
        lineToRelative(-3.0f, -20.0f)
        reflectiveCurveTo(27.38f, 241.73f, 62.44f, 241.12f)
        close()
    }
private val pathData23 =
    PathData {
        moveTo(84.26f, 275.62f)
        arcTo(31.64f, 31.64f, 0.0f, false, true, 80.5f, 290.5f)
        arcToRelative(19.71f, 19.71f, 0.0f, false, true, -11.14f, 8.83f)
        lineToRelative(16.14f, 0.17f)
        reflectiveCurveToRelative(1.42f, -2.62f, 0.71f, -10.81f)
        arcTo(90.57f, 90.57f, 0.0f, false, false, 84.26f, 275.62f)
        close()
    }
private val pathData24 =
    PathData {
        moveTo(222.5f, 301.5f)
        reflectiveCurveToRelative(-10.0f, -35.0f, 11.0f, -53.0f)
        reflectiveCurveToRelative(58.0f, -15.0f, 70.0f, 2.0f)
        reflectiveCurveToRelative(2.0f, 51.0f, 2.0f, 51.0f)
        close()
    }
private val pathData26 =
    PathData {
        moveTo(226.5f, 244.5f)
        lineToRelative(0.74f, 11.0f)
        arcToRelative(39.65f, 39.65f, 0.0f, false, false, -7.12f, 24.19f)
        curveToRelative(0.38f, 14.81f, 2.38f, 21.81f, 2.38f, 21.81f)
        horizontalLineToRelative(-12.0f)
        reflectiveCurveToRelative(1.0f, -9.0f, 0.5f, -14.5f)
        reflectiveCurveToRelative(-1.49f, -18.55f, -1.49f, -18.55f)
        reflectiveCurveTo(227.5f, 259.5f, 226.5f, 244.5f)
        close()
    }
