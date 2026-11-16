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
fun Eyes4RoundSingle() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    eyes4RoundSingle(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun eyes4RoundSingle(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor)
    builder.addPath(pathData2, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 0.75f)
    builder.addPath(pathData3, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 0.75f)
    builder.addPath(pathData4, fill = Brown, stroke = Black, strokeLineWidth = 0.5f)
    builder.addPath(pathData5, fill = LightRed)
    builder.addPath(pathData6, fill = Brown, stroke = Black, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(131.5f, 119.5f)
        curveToRelative(-5.0f, 0.0f, -19.0f, 2.0f, -19.0f, 16.0f)
        curveToRelative(0.0f, 15.0f, 10.0f, 18.0f, 18.0f, 18.0f)
        reflectiveCurveToRelative(17.0f, -7.0f, 16.0f, -19.0f)
        reflectiveCurveTo(136.5f, 119.5f, 131.5f, 119.5f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(131.5f, 119.5f)
        curveToRelative(-5.0f, 0.0f, -19.0f, 2.0f, -19.0f, 16.0f)
        curveToRelative(0.0f, 15.0f, 10.0f, 18.0f, 18.0f, 18.0f)
        reflectiveCurveToRelative(17.0f, -7.0f, 16.0f, -19.0f)
        reflectiveCurveTo(136.5f, 119.5f, 131.5f, 119.5f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(132.0f, 124.0f)
        lineToRelative(-0.5f, 0.0f)
        curveToRelative(-2.51f, 0.0f, -13.61f, 0.38f, -14.5f, 11.0f)
        curveToRelative(-1.0f, 12.0f, 8.5f, 13.5f, 12.5f, 13.5f)
        reflectiveCurveToRelative(13.0f, -3.0f, 13.0f, -13.0f)
        curveTo(142.5f, 124.5f, 132.0f, 124.0f, 132.0f, 124.0f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(130.5f, 124.5f)
        reflectiveCurveToRelative(-10.0f, 1.0f, -10.0f, 10.0f)
        reflectiveCurveToRelative(5.0f, 11.0f, 11.0f, 11.0f)
        arcToRelative(11.1f, 11.1f, 0.0f, false, false, 11.0f, -11.0f)
        curveTo(142.5f, 128.5f, 136.5f, 124.5f, 130.5f, 124.5f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(129.5f, 134.5f)
        arcToRelative(2.0f, 3.0f, 0.0f, true, false, 4.0f, 0.0f)
        arcToRelative(2.0f, 3.0f, 0.0f, true, false, -4.0f, 0.0f)
        close()
    }
private val pathData6 =
    PathData {
        moveTo(147.5f, 133.5f)
        horizontalLineToRelative(6.0f)
        reflectiveCurveToRelative(1.0f, 0.0f, 0.0f, 1.0f)
        arcToRelative(7.69f, 7.69f, 0.0f, false, true, -3.0f, 2.0f)
        horizontalLineToRelative(-4.0f)
        verticalLineToRelative(-2.0f)
        close()
        moveTo(112.49f, 135.15f)
        arcToRelative(29.28f, 29.28f, 0.0f, false, true, -3.29f, 0.35f)
        curveToRelative(-1.1f, 0.0f, -1.7f, 1.0f, -2.2f, 2.0f)
        arcToRelative(0.79f, 0.79f, 0.0f, false, false, 0.2f, 0.57f)
        arcToRelative(1.56f, 1.56f, 0.0f, false, false, 0.94f, 0.36f)
        arcToRelative(18.66f, 18.66f, 0.0f, false, false, 2.15f, 0.07f)
        curveToRelative(2.2f, 0.0f, 2.32f, -0.35f, 2.32f, -0.35f)
        close()
    }
