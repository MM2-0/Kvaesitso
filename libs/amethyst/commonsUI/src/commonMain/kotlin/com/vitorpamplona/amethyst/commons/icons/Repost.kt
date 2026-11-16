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
    Image(Repost, null)
}

private var labelsRepost: ImageVector? = null

public val Repost: ImageVector
    get() =
        labelsRepost ?: Builder(
            name = "Repost",
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
                moveTo(23.77f, 15.67f)
                curveToRelative(-0.292f, -0.293f, -0.767f, -0.293f, -1.06f, 0.0f)
                lineToRelative(-2.22f, 2.22f)
                lineTo(20.49f, 7.65f)
                curveToRelative(0.0f, -2.068f, -1.683f, -3.75f, -3.75f, -3.75f)
                horizontalLineToRelative(-5.85f)
                curveToRelative(-0.414f, 0.0f, -0.75f, 0.336f, -0.75f, 0.75f)
                reflectiveCurveToRelative(0.336f, 0.75f, 0.75f, 0.75f)
                horizontalLineToRelative(5.85f)
                curveToRelative(1.24f, 0.0f, 2.25f, 1.01f, 2.25f, 2.25f)
                verticalLineToRelative(10.24f)
                lineToRelative(-2.22f, -2.22f)
                curveToRelative(-0.293f, -0.293f, -0.768f, -0.293f, -1.06f, 0.0f)
                reflectiveCurveToRelative(-0.294f, 0.768f, 0.0f, 1.06f)
                lineToRelative(3.5f, 3.5f)
                curveToRelative(0.145f, 0.147f, 0.337f, 0.22f, 0.53f, 0.22f)
                reflectiveCurveToRelative(0.383f, -0.072f, 0.53f, -0.22f)
                lineToRelative(3.5f, -3.5f)
                curveToRelative(0.294f, -0.292f, 0.294f, -0.767f, 0.0f, -1.06f)
                close()
                moveTo(13.11f, 18.95f)
                lineTo(7.26f, 18.95f)
                curveToRelative(-1.24f, 0.0f, -2.25f, -1.01f, -2.25f, -2.25f)
                lineTo(5.01f, 6.46f)
                lineToRelative(2.22f, 2.22f)
                curveToRelative(0.148f, 0.147f, 0.34f, 0.22f, 0.532f, 0.22f)
                reflectiveCurveToRelative(0.384f, -0.073f, 0.53f, -0.22f)
                curveToRelative(0.293f, -0.293f, 0.293f, -0.768f, 0.0f, -1.06f)
                lineToRelative(-3.5f, -3.5f)
                curveToRelative(-0.293f, -0.294f, -0.768f, -0.294f, -1.06f, 0.0f)
                lineToRelative(-3.5f, 3.5f)
                curveToRelative(-0.294f, 0.292f, -0.294f, 0.767f, 0.0f, 1.06f)
                reflectiveCurveToRelative(0.767f, 0.293f, 1.06f, 0.0f)
                lineToRelative(2.22f, -2.22f)
                lineTo(3.512f, 16.7f)
                curveToRelative(0.0f, 2.068f, 1.683f, 3.75f, 3.75f, 3.75f)
                horizontalLineToRelative(5.85f)
                curveToRelative(0.414f, 0.0f, 0.75f, -0.336f, 0.75f, -0.75f)
                reflectiveCurveToRelative(-0.337f, -0.75f, -0.75f, -0.75f)
                close()
            }
        }.build()
            .also { labelsRepost = it }
