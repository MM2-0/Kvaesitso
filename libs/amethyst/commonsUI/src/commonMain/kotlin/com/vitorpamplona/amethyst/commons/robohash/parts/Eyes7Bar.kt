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
fun Eyes7Bar() {
    Image(
        painter =
            rememberVectorPainter(
                roboBuilder {
                    eyes7Bar(SolidColor(Color.Blue), this)
                },
            ),
        contentDescription = "",
    )
}

fun eyes7Bar(
    fgColor: SolidColor,
    builder: Builder,
) {
    builder.addPath(pathData1, fill = fgColor)
    builder.addPath(pathData2, fill = Black, stroke = Black, fillAlpha = 0.4f, strokeLineWidth = 1.0f)
    builder.addPath(pathData3, fill = Brown, stroke = Black, strokeLineWidth = 0.5f)
    builder.addPath(pathData4, fill = Brown, stroke = Black, strokeLineWidth = 0.75f)
}

private val pathData1 =
    PathData {
        moveTo(110.0f, 135.0f)
        reflectiveCurveToRelative(-1.5f, 6.5f, 0.5f, 6.5f)
        horizontalLineToRelative(45.0f)
        verticalLineToRelative(-6.0f)
        reflectiveCurveToRelative(0.0f, -2.0f, -3.0f, -2.0f)
        horizontalLineToRelative(-38.0f)
        curveTo(111.5f, 133.5f, 110.5f, 133.5f, 110.0f, 135.0f)
        close()
    }
private val pathData2 =
    PathData {
        moveTo(110.0f, 135.0f)
        reflectiveCurveToRelative(-1.5f, 6.5f, 0.5f, 6.5f)
        horizontalLineToRelative(45.0f)
        verticalLineToRelative(-6.0f)
        reflectiveCurveToRelative(0.0f, -2.0f, -3.0f, -2.0f)
        horizontalLineToRelative(-38.0f)
        curveTo(111.5f, 133.5f, 110.5f, 133.5f, 110.0f, 135.0f)
        close()
    }
private val pathData3 =
    PathData {
        moveTo(110.83f, 133.88f)
        arcToRelative(25.17f, 25.17f, 0.0f, false, false, 2.67f, 4.62f)
        curveToRelative(1.0f, 1.0f, 5.0f, 0.0f, 9.0f, 0.0f)
        horizontalLineToRelative(31.0f)
        reflectiveCurveToRelative(2.0f, -0.12f, 2.0f, 0.94f)
        verticalLineTo(135.5f)
        reflectiveCurveToRelative(0.0f, -2.0f, -3.0f, -2.0f)
        horizontalLineTo(113.31f)
        close()
    }
private val pathData4 =
    PathData {
        moveTo(113.5f, 138.5f)
        lineTo(109.9f, 141.21f)
    }
