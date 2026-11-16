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
fun Mouth6Cell() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    mouth6Cell(SolidColor(Color.Yellow), this)
                },
            ),
        contentDescription = "",
    )
}

fun mouth6Cell(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.0f)
    builder.addPath(pathData5, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData7, fill = Brown, stroke = Black, strokeLineWidth = 0.5f)
}

private val pathData1 =
    PathData {
        moveTo(122.0f, 180.0f)
        reflectiveCurveToRelative(1.0f, 10.0f, 2.0f, 14.0f)
        reflectiveCurveToRelative(1.5f, 6.5f, 1.5f, 6.5f)
        reflectiveCurveToRelative(3.0f, 0.0f, 4.0f, -1.0f)
        reflectiveCurveToRelative(-2.0f, -17.0f, -2.0f, -18.0f)
        verticalLineToRelative(-2.0f)
        reflectiveCurveToRelative(-1.49f, 0.0f, -3.0f, 0.11f)
        arcTo(12.0f, 12.0f, 0.0f, false, false, 122.0f, 180.0f)
        close()
        moveTo(131.0f, 179.0f)
        reflectiveCurveToRelative(1.0f, 10.0f, 2.0f, 14.0f)
        reflectiveCurveToRelative(1.5f, 6.5f, 1.5f, 6.5f)
        reflectiveCurveToRelative(3.0f, 0.0f, 4.0f, -1.0f)
        reflectiveCurveToRelative(-2.0f, -17.0f, -2.0f, -18.0f)
        verticalLineToRelative(-2.0f)
        reflectiveCurveToRelative(-1.49f, 0.0f, -3.0f, 0.11f)
        arcTo(12.0f, 12.0f, 0.0f, false, false, 131.0f, 179.0f)
        close()
        moveTo(141.0f, 179.0f)
        reflectiveCurveToRelative(1.0f, 10.0f, 2.0f, 14.0f)
        reflectiveCurveToRelative(1.5f, 6.5f, 1.5f, 6.5f)
        reflectiveCurveToRelative(3.0f, 0.0f, 4.0f, -1.0f)
        reflectiveCurveToRelative(-2.0f, -17.0f, -2.0f, -18.0f)
        verticalLineToRelative(-2.0f)
        reflectiveCurveToRelative(-1.49f, 0.0f, -3.0f, 0.11f)
        arcTo(12.0f, 12.0f, 0.0f, false, false, 141.0f, 179.0f)
        close()
        moveTo(149.0f, 178.0f)
        reflectiveCurveToRelative(1.0f, 10.0f, 2.0f, 14.0f)
        reflectiveCurveToRelative(1.5f, 6.5f, 1.5f, 6.5f)
        reflectiveCurveToRelative(3.0f, 0.0f, 4.0f, -1.0f)
        reflectiveCurveToRelative(-2.0f, -17.0f, -2.0f, -18.0f)
        verticalLineToRelative(-2.0f)
        reflectiveCurveToRelative(-1.49f, 0.0f, -3.0f, 0.11f)
        arcTo(12.0f, 12.0f, 0.0f, false, false, 149.0f, 178.0f)
        close()
    }
private val pathData5 =
    PathData {
        moveTo(122.0f, 180.0f)
        reflectiveCurveToRelative(1.0f, 10.0f, 2.0f, 14.0f)
        reflectiveCurveToRelative(1.5f, 6.5f, 1.5f, 6.5f)
        reflectiveCurveToRelative(3.0f, 0.0f, 4.0f, -1.0f)
        reflectiveCurveToRelative(-2.0f, -17.0f, -2.0f, -18.0f)
        verticalLineToRelative(-2.0f)
        reflectiveCurveToRelative(-1.49f, 0.0f, -3.0f, 0.11f)
        arcTo(12.0f, 12.0f, 0.0f, false, false, 122.0f, 180.0f)
        close()
        moveTo(131.0f, 179.0f)
        reflectiveCurveToRelative(1.0f, 10.0f, 2.0f, 14.0f)
        reflectiveCurveToRelative(1.5f, 6.5f, 1.5f, 6.5f)
        reflectiveCurveToRelative(3.0f, 0.0f, 4.0f, -1.0f)
        reflectiveCurveToRelative(-2.0f, -17.0f, -2.0f, -18.0f)
        verticalLineToRelative(-2.0f)
        reflectiveCurveToRelative(-1.49f, 0.0f, -3.0f, 0.11f)
        arcTo(12.0f, 12.0f, 0.0f, false, false, 131.0f, 179.0f)
        close()
        moveTo(141.0f, 179.0f)
        reflectiveCurveToRelative(1.0f, 10.0f, 2.0f, 14.0f)
        reflectiveCurveToRelative(1.5f, 6.5f, 1.5f, 6.5f)
        reflectiveCurveToRelative(3.0f, 0.0f, 4.0f, -1.0f)
        reflectiveCurveToRelative(-2.0f, -17.0f, -2.0f, -18.0f)
        verticalLineToRelative(-2.0f)
        reflectiveCurveToRelative(-1.49f, 0.0f, -3.0f, 0.11f)
        arcTo(12.0f, 12.0f, 0.0f, false, false, 141.0f, 179.0f)
        close()
        moveTo(149.0f, 178.0f)
        reflectiveCurveToRelative(1.0f, 10.0f, 2.0f, 14.0f)
        reflectiveCurveToRelative(1.5f, 6.5f, 1.5f, 6.5f)
        reflectiveCurveToRelative(3.0f, 0.0f, 4.0f, -1.0f)
        reflectiveCurveToRelative(-2.0f, -17.0f, -2.0f, -18.0f)
        verticalLineToRelative(-2.0f)
        reflectiveCurveToRelative(-1.49f, 0.0f, -3.0f, 0.11f)
        arcTo(12.0f, 12.0f, 0.0f, false, false, 149.0f, 178.0f)
        close()
    }

private val pathData7 =
    PathData {
        moveTo(124.5f, 180.5f)
        reflectiveCurveToRelative(1.0f, 11.0f, 2.0f, 14.0f)
        arcToRelative(15.77f, 15.77f, 0.0f, false, true, 1.0f, 4.0f)
        reflectiveCurveToRelative(2.34f, -0.11f, 2.17f, 0.44f)
        arcToRelative(31.0f, 31.0f, 0.0f, false, false, -0.82f, -8.63f)
        arcToRelative(59.0f, 59.0f, 0.0f, false, true, -1.35f, -10.07f)
        verticalLineToRelative(-0.75f)
        horizontalLineToRelative(-3.0f)
        close()
        moveTo(125.85f, 200.49f)
        lineTo(127.5f, 198.5f)
        moveTo(133.5f, 179.5f)
        reflectiveCurveToRelative(1.0f, 11.0f, 2.0f, 14.0f)
        arcToRelative(15.77f, 15.77f, 0.0f, false, true, 1.0f, 4.0f)
        reflectiveCurveToRelative(2.34f, -0.11f, 2.17f, 0.44f)
        arcToRelative(31.0f, 31.0f, 0.0f, false, false, -0.82f, -8.63f)
        arcToRelative(59.0f, 59.0f, 0.0f, false, true, -1.35f, -10.07f)
        verticalLineToRelative(-0.75f)
        horizontalLineToRelative(-3.0f)
        close()
        moveTo(134.85f, 199.49f)
        lineTo(136.5f, 197.5f)
        moveTo(143.5f, 179.5f)
        reflectiveCurveToRelative(1.0f, 11.0f, 2.0f, 14.0f)
        arcToRelative(15.77f, 15.77f, 0.0f, false, true, 1.0f, 4.0f)
        reflectiveCurveToRelative(2.34f, -0.11f, 2.17f, 0.44f)
        arcToRelative(31.0f, 31.0f, 0.0f, false, false, -0.82f, -8.63f)
        arcToRelative(59.0f, 59.0f, 0.0f, false, true, -1.35f, -10.07f)
        verticalLineToRelative(-0.75f)
        horizontalLineToRelative(-3.0f)
        close()
        moveTo(144.85f, 199.49f)
        lineTo(146.5f, 197.5f)
        moveTo(151.5f, 178.5f)
        reflectiveCurveToRelative(1.0f, 11.0f, 2.0f, 14.0f)
        arcToRelative(15.77f, 15.77f, 0.0f, false, true, 1.0f, 4.0f)
        reflectiveCurveToRelative(2.34f, -0.11f, 2.17f, 0.44f)
        arcToRelative(31.0f, 31.0f, 0.0f, false, false, -0.82f, -8.63f)
        arcToRelative(59.0f, 59.0f, 0.0f, false, true, -1.35f, -10.07f)
        verticalLineToRelative(-0.75f)
        horizontalLineToRelative(-3.0f)
        close()
        moveTo(152.85f, 198.49f)
        lineTo(154.5f, 196.5f)
    }
