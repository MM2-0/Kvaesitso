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
fun Eyes5RoundSmall() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    eyes5RoundSmall(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun eyes5RoundSmall(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor)
    builder.addPath(pathData3, fill = Black, stroke = Black, fillAlpha = 0.55f, strokeLineWidth = 0.75f)
    builder.addPath(pathData4, fill = Brown, stroke = Black, strokeLineWidth = 0.5f)
    builder.addPath(pathData5, fill = LightRed)
}

private val pathData1 =
    PathData {
        moveTo(112.71f, 130.0f)
        lineToRelative(-0.49f, 0.0f)
        curveToRelative(-2.46f, 0.0f, -13.35f, 0.38f, -14.22f, 11.0f)
        curveToRelative(-1.0f, 12.0f, 8.33f, 13.5f, 12.26f, 13.5f)
        reflectiveCurveToRelative(12.75f, -3.0f, 12.75f, -13.0f)
        curveTo(123.0f, 130.5f, 112.71f, 130.0f, 112.71f, 130.0f)
        close()
        moveTo(156.71f, 130.0f)
        lineToRelative(-0.49f, 0.0f)
        curveToRelative(-2.46f, 0.0f, -13.35f, 0.38f, -14.22f, 11.0f)
        curveToRelative(-1.0f, 12.0f, 8.33f, 13.5f, 12.26f, 13.5f)
        reflectiveCurveToRelative(12.75f, -3.0f, 12.75f, -13.0f)
        curveTo(167.0f, 130.5f, 156.71f, 130.0f, 156.71f, 130.0f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(112.71f, 130.0f)
        lineToRelative(-0.49f, 0.0f)
        curveToRelative(-2.46f, 0.0f, -13.35f, 0.38f, -14.22f, 11.0f)
        curveToRelative(-1.0f, 12.0f, 8.33f, 13.5f, 12.26f, 13.5f)
        reflectiveCurveToRelative(12.75f, -3.0f, 12.75f, -13.0f)
        curveTo(123.0f, 130.5f, 112.71f, 130.0f, 112.71f, 130.0f)
        close()
        moveTo(156.71f, 130.0f)
        lineToRelative(-0.49f, 0.0f)
        curveToRelative(-2.46f, 0.0f, -13.35f, 0.38f, -14.22f, 11.0f)
        curveToRelative(-1.0f, 12.0f, 8.33f, 13.5f, 12.26f, 13.5f)
        reflectiveCurveToRelative(12.75f, -3.0f, 12.75f, -13.0f)
        curveTo(167.0f, 130.5f, 156.71f, 130.0f, 156.71f, 130.0f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(111.23f, 130.5f)
        reflectiveCurveToRelative(-9.8f, 1.0f, -9.8f, 10.0f)
        reflectiveCurveToRelative(4.9f, 11.0f, 10.78f, 11.0f)
        arcToRelative(11.0f, 11.0f, 0.0f, false, false, 10.78f, -11.0f)
        curveTo(123.0f, 134.5f, 117.12f, 130.5f, 111.23f, 130.5f)
        close()
        moveTo(155.23f, 130.5f)
        reflectiveCurveToRelative(-9.8f, 1.0f, -9.8f, 10.0f)
        reflectiveCurveToRelative(4.9f, 11.0f, 10.78f, 11.0f)
        arcToRelative(11.0f, 11.0f, 0.0f, false, false, 10.78f, -11.0f)
        curveTo(167.0f, 134.5f, 161.12f, 130.5f, 155.23f, 130.5f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(110.26f, 140.5f)
        arcToRelative(1.96f, 3.0f, 0.0f, true, false, 3.92f, 0.0f)
        arcToRelative(1.96f, 3.0f, 0.0f, true, false, -3.92f, 0.0f)
        close()
        moveTo(154.26f, 140.5f)
        arcToRelative(1.96f, 3.0f, 0.0f, true, false, 3.92f, 0.0f)
        arcToRelative(1.96f, 3.0f, 0.0f, true, false, -3.92f, 0.0f)
        close()
    }
