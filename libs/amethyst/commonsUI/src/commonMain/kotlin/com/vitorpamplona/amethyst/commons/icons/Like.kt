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
    Image(Like, null)
}

private var labelsLike: ImageVector? = null

public val Like: ImageVector
    get() =
        labelsLike ?: Builder(
            name = "Like",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f,
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = NonZero,
            ) {
                moveTo(12.0f, 21.638f)
                horizontalLineToRelative(-0.014f)
                curveTo(9.403f, 21.59f, 1.95f, 14.856f, 1.95f, 8.478f)
                curveToRelative(0.0f, -3.064f, 2.525f, -5.754f, 5.403f, -5.754f)
                curveToRelative(2.29f, 0.0f, 3.83f, 1.58f, 4.646f, 2.73f)
                curveToRelative(0.814f, -1.148f, 2.354f, -2.73f, 4.645f, -2.73f)
                curveToRelative(2.88f, 0.0f, 5.404f, 2.69f, 5.404f, 5.755f)
                curveToRelative(0.0f, 6.376f, -7.454f, 13.11f, -10.037f, 13.157f)
                horizontalLineTo(12.0f)
                close()
                moveTo(7.354f, 4.225f)
                curveToRelative(-2.08f, 0.0f, -3.903f, 1.988f, -3.903f, 4.255f)
                curveToRelative(0.0f, 5.74f, 7.034f, 11.596f, 8.55f, 11.658f)
                curveToRelative(1.518f, -0.062f, 8.55f, -5.917f, 8.55f, -11.658f)
                curveToRelative(0.0f, -2.267f, -1.823f, -4.255f, -3.903f, -4.255f)
                curveToRelative(-2.528f, 0.0f, -3.94f, 2.936f, -3.952f, 2.965f)
                curveToRelative(-0.23f, 0.562f, -1.156f, 0.562f, -1.387f, 0.0f)
                curveToRelative(-0.014f, -0.03f, -1.425f, -2.965f, -3.954f, -2.965f)
                close()
            }
        }.build()
            .also { labelsLike = it }
