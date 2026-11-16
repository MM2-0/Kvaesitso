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
fun Body4RoundPreview() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    body4Round(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun body4Round(
    fgColor: SolidColor,
    builder: Builder,
) {
    // Blue
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)

    // Gray arms and legs
    builder.addPath(pathData7, fill = Gray, stroke = Black, strokeLineWidth = 1.0f)

    // Joints
    builder.addPath(pathData26, stroke = Black, strokeLineWidth = 1.0f)

    // Shades
    builder.addPath(pathData11, fill = Black, fillAlpha = 0.2f)

    // Stronger Shades
    builder.addPath(pathData9, fill = Black, fillAlpha = 0.4f)
}

private val pathData1 =
    PathData {
        moveTo(137.41f, 252.15f)
        reflectiveCurveToRelative(-0.91f, -4.65f, -11.91f, -3.65f)
        reflectiveCurveToRelative(-13.0f, 12.0f, -13.0f, 18.0f)
        reflectiveCurveToRelative(4.0f, 13.0f, 12.0f, 14.0f)
        curveToRelative(0.0f, 0.0f, 3.0f, 0.0f, 3.0f, -1.0f)
        curveToRelative(0.0f, 0.0f, -3.17f, -6.44f, 0.41f, -16.72f)
        curveTo(127.91f, 262.78f, 133.33f, 254.8f, 137.41f, 252.15f)
        close()
        moveTo(194.5f, 244.5f)
        reflectiveCurveToRelative(-19.0f, 1.0f, -19.0f, 17.0f)
        reflectiveCurveToRelative(11.0f, 21.0f, 17.0f, 21.0f)
        reflectiveCurveToRelative(20.0f, -2.0f, 19.0f, -20.0f)
        reflectiveCurveTo(194.5f, 244.5f, 194.5f, 244.5f)
        close()
        moveTo(181.5f, 247.5f)
        arcToRelative(39.32f, 39.32f, 0.0f, false, false, -24.0f, -4.0f)
        curveToRelative(-14.0f, 2.0f, -28.0f, 14.0f, -30.0f, 19.0f)
        reflectiveCurveToRelative(-3.0f, 9.0f, 0.0f, 17.0f)
        reflectiveCurveToRelative(3.0f, 20.0f, 1.0f, 22.0f)
        horizontalLineToRelative(68.0f)
        reflectiveCurveToRelative(0.68f, -18.41f, -0.66f, -19.2f)
        curveToRelative(0.0f, 0.0f, -21.34f, 1.2f, -20.34f, -19.8f)
        arcToRelative(13.88f, 13.88f, 0.0f, false, true, 6.67f, -13.88f)
        close()
    }
private val pathData7 =
    PathData {
        moveTo(195.5f, 282.5f)
        reflectiveCurveToRelative(7.0f, 11.0f, 6.0f, 19.0f)
        horizontalLineToRelative(12.0f)
        reflectiveCurveToRelative(2.5f, -15.0f, -7.25f, -23.5f)
        curveTo(206.25f, 278.0f, 203.5f, 282.5f, 195.5f, 282.5f)
        close()
        moveTo(119.36f, 278.79f)
        reflectiveCurveToRelative(-3.86f, 5.71f, -4.86f, 10.71f)
        reflectiveCurveToRelative(1.0f, 12.0f, 1.0f, 12.0f)
        horizontalLineToRelative(10.0f)
        reflectiveCurveToRelative(-1.0f, -7.0f, 1.0f, -12.0f)
        reflectiveCurveToRelative(2.48f, -4.68f, 2.48f, -4.68f)
        lineToRelative(-1.48f, -5.32f)
        reflectiveCurveToRelative(-1.27f, 1.4f, -4.63f, 0.7f)
        reflectiveCurveToRelative(-3.87f, -1.4f, -3.87f, -1.4f)
        moveTo(147.0f, 200.0f)
        reflectiveCurveToRelative(-1.0f, 42.0f, 2.0f, 50.0f)
        curveToRelative(0.0f, 0.0f, 12.0f, 9.0f, 20.0f, -2.0f)
        curveToRelative(0.0f, 0.0f, -3.5f, -26.5f, -3.5f, -48.5f)
        arcTo(77.11f, 77.11f, 0.0f, false, false, 147.0f, 200.0f)
        close()
    }
private val pathData9 =
    PathData {
        moveTo(136.5f, 260.5f)
        arcToRelative(3.1f, 3.1f, 0.0f, false, false, -2.0f, 1.0f)
        curveToRelative(-1.0f, 1.0f, -3.0f, 5.0f, 0.0f, 5.0f)
        reflectiveCurveToRelative(4.0f, -1.0f, 4.0f, -3.0f)
        reflectiveCurveTo(137.5f, 260.5f, 136.5f, 260.5f)
        close()
        moveTo(133.5f, 270.5f)
        arcToRelative(4.33f, 4.33f, 0.0f, false, false, -2.0f, 4.0f)
        curveToRelative(0.0f, 3.0f, 1.0f, 6.0f, 2.0f, 12.0f)
        reflectiveCurveToRelative(0.67f, 14.0f, -1.67f, 15.0f)
        reflectiveCurveToRelative(6.67f, 0.0f, 6.67f, 0.0f)
        reflectiveCurveToRelative(4.0f, -16.0f, 0.0f, -25.0f)
        curveTo(138.5f, 276.5f, 136.5f, 269.5f, 133.5f, 270.5f)
        close()
    }
private val pathData11 =
    PathData {
        moveTo(195.5f, 245.5f)
        reflectiveCurveToRelative(8.0f, 5.0f, 6.0f, 18.0f)
        reflectiveCurveToRelative(-9.78f, 17.77f, -14.39f, 17.89f)
        curveToRelative(0.0f, 0.0f, 21.41f, 6.8f, 24.4f, -15.54f)
        curveTo(211.51f, 265.84f, 212.5f, 245.5f, 195.5f, 245.5f)
        close()
        moveTo(161.5f, 253.5f)
        reflectiveCurveToRelative(9.0f, 2.0f, 11.0f, 10.0f)
        reflectiveCurveToRelative(2.0f, 28.0f, 2.0f, 28.0f)
        verticalLineToRelative(12.0f)
        lineToRelative(22.0f, 1.0f)
        verticalLineToRelative(-19.0f)
        arcToRelative(4.38f, 4.38f, 0.0f, false, false, -1.0f, -3.0f)
        reflectiveCurveToRelative(-22.0f, -1.0f, -20.0f, -21.0f)
        curveToRelative(0.0f, 0.0f, 0.0f, -9.0f, 7.0f, -13.0f)
        arcToRelative(35.0f, 35.0f, 0.0f, false, false, -14.06f, -5.07f)
        lineToRelative(0.06f, 5.07f)
        arcTo(10.39f, 10.39f, 0.0f, false, true, 161.5f, 253.5f)
        close()
        moveTo(118.5f, 252.5f)
        reflectiveCurveToRelative(5.0f, -3.0f, 3.0f, 1.0f)
        reflectiveCurveToRelative(-5.0f, 5.0f, -6.0f, 11.0f)
        reflectiveCurveToRelative(4.0f, 11.0f, 4.0f, 11.0f)
        reflectiveCurveToRelative(2.0f, 2.0f, -1.0f, 2.0f)
        reflectiveCurveToRelative(-7.7f, -8.0f, -5.35f, -17.0f)
        arcTo(16.51f, 16.51f, 0.0f, false, true, 118.5f, 252.5f)
        close()
        moveTo(125.0f, 280.39f)
        reflectiveCurveToRelative(-4.48f, 3.61f, -3.22f, 21.11f)
        horizontalLineTo(125.0f)
        reflectiveCurveToRelative(-1.0f, -11.5f, 4.0f, -16.5f)
        lineToRelative(-1.58f, -5.08f)
        close()
        moveTo(202.0f, 281.28f)
        reflectiveCurveTo(209.0f, 293.0f, 209.0f, 302.0f)
        reflectiveCurveToRelative(4.79f, -1.37f, 4.79f, -1.37f)
        reflectiveCurveTo(215.0f, 286.0f, 206.0f, 278.0f)
        arcTo(6.19f, 6.19f, 0.0f, false, true, 202.0f, 281.28f)
        close()
        moveTo(158.67f, 199.17f)
        reflectiveCurveToRelative(0.83f, 11.33f, 1.83f, 18.33f)
        reflectiveCurveToRelative(2.0f, 15.0f, 2.0f, 20.0f)
        reflectiveCurveToRelative(0.33f, 13.93f, -1.33f, 16.0f)
        curveToRelative(0.0f, 0.0f, 4.33f, 0.0f, 7.33f, -6.0f)
        curveToRelative(0.0f, 0.0f, -3.0f, -26.0f, -3.0f, -33.0f)
        verticalLineToRelative(-15.0f)
        close()
    }
private val pathData26 =
    PathData {
        moveTo(211.55f, 285.39f)
        reflectiveCurveToRelative(0.73f, 7.21f, -11.66f, 6.16f)
        moveTo(141.34f, 249.41f)
        reflectiveCurveToRelative(3.16f, 11.09f, 16.16f, 11.09f)
        reflectiveCurveToRelative(19.07f, -10.0f, 19.0f, -15.0f)
        moveTo(144.5f, 301.5f)
        reflectiveCurveToRelative(2.0f, -12.0f, 1.0f, -18.0f)
        lineToRelative(23.0f, -1.0f)
        reflectiveCurveToRelative(1.0f, 17.0f, 0.0f, 19.0f)
        moveTo(114.92f, 286.75f)
        reflectiveCurveToRelative(3.58f, 4.75f, 11.58f, 2.75f)
        moveTo(166.0f, 211.5f)
        reflectiveCurveToRelative(-1.08f, 3.0f, -8.68f, 3.0f)
        reflectiveCurveToRelative(-10.48f, -1.37f, -10.48f, -1.37f)
        moveTo(166.0f, 223.5f)
        reflectiveCurveToRelative(-1.08f, 3.0f, -8.68f, 3.0f)
        reflectiveCurveToRelative(-10.48f, -1.37f, -10.48f, -1.37f)
        moveTo(147.5f, 238.5f)
        arcToRelative(15.39f, 15.39f, 0.0f, false, false, 8.0f, 2.0f)
        curveToRelative(5.0f, 0.0f, 11.32f, -1.58f, 12.16f, -4.29f)
    }
