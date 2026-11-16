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
package com.vitorpamplona.amethyst.commons.icons

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
private fun VectorPreview() {
    Image(Share, null)
}

private var labelsShare: ImageVector? = null

val Share: ImageVector
    get() =
        labelsShare ?: Builder(
            name = "Share",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f,
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero,
            ) {
                moveTo(17.53f, 7.47f)
                lineToRelative(-5.0f, -5.0f)
                curveToRelative(-0.293f, -0.293f, -0.768f, -0.293f, -1.06f, 0.0f)
                lineToRelative(-5.0f, 5.0f)
                curveToRelative(-0.294f, 0.293f, -0.294f, 0.768f, 0.0f, 1.06f)
                reflectiveCurveToRelative(0.767f, 0.294f, 1.06f, 0.0f)
                lineToRelative(3.72f, -3.72f)
                verticalLineTo(15.0f)
                curveToRelative(0.0f, 0.414f, 0.336f, 0.75f, 0.75f, 0.75f)
                reflectiveCurveToRelative(0.75f, -0.336f, 0.75f, -0.75f)
                verticalLineTo(4.81f)
                lineToRelative(3.72f, 3.72f)
                curveToRelative(0.146f, 0.147f, 0.338f, 0.22f, 0.53f, 0.22f)
                reflectiveCurveToRelative(0.384f, -0.072f, 0.53f, -0.22f)
                curveToRelative(0.293f, -0.293f, 0.293f, -0.767f, 0.0f, -1.06f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero,
            ) {
                moveTo(19.708f, 21.944f)
                horizontalLineTo(4.292f)
                curveTo(3.028f, 21.944f, 2.0f, 20.916f, 2.0f, 19.652f)
                verticalLineTo(14.0f)
                curveToRelative(0.0f, -0.414f, 0.336f, -0.75f, 0.75f, -0.75f)
                reflectiveCurveToRelative(0.75f, 0.336f, 0.75f, 0.75f)
                verticalLineToRelative(5.652f)
                curveToRelative(0.0f, 0.437f, 0.355f, 0.792f, 0.792f, 0.792f)
                horizontalLineToRelative(15.416f)
                curveToRelative(0.437f, 0.0f, 0.792f, -0.355f, 0.792f, -0.792f)
                verticalLineTo(14.0f)
                curveToRelative(0.0f, -0.414f, 0.336f, -0.75f, 0.75f, -0.75f)
                reflectiveCurveToRelative(0.75f, 0.336f, 0.75f, 0.75f)
                verticalLineToRelative(5.652f)
                curveToRelative(0.0f, 1.264f, -1.028f, 2.292f, -2.292f, 2.292f)
                close()
            }
        }.build()
            .also { labelsShare = it }
