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
    Image(Reply, null)
}

private var labelsReply: ImageVector? = null

public val Reply: ImageVector
    get() =
        labelsReply ?: Builder(
            name = "Reply",
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
                moveTo(14.046f, 2.242f)
                lineToRelative(-4.148f, -0.01f)
                horizontalLineToRelative(-0.002f)
                curveToRelative(-4.374f, 0.0f, -7.8f, 3.427f, -7.8f, 7.802f)
                curveToRelative(0.0f, 4.098f, 3.186f, 7.206f, 7.465f, 7.37f)
                verticalLineToRelative(3.828f)
                curveToRelative(0.0f, 0.108f, 0.044f, 0.286f, 0.12f, 0.403f)
                curveToRelative(0.142f, 0.225f, 0.384f, 0.347f, 0.632f, 0.347f)
                curveToRelative(0.138f, 0.0f, 0.277f, -0.038f, 0.402f, -0.118f)
                curveToRelative(0.264f, -0.168f, 6.473f, -4.14f, 8.088f, -5.506f)
                curveToRelative(1.902f, -1.61f, 3.04f, -3.97f, 3.043f, -6.312f)
                verticalLineToRelative(-0.017f)
                curveToRelative(-0.006f, -4.367f, -3.43f, -7.787f, -7.8f, -7.788f)
                close()
                moveTo(17.833f, 15.214f)
                curveToRelative(-1.134f, 0.96f, -4.862f, 3.405f, -6.772f, 4.643f)
                lineTo(11.061f, 16.67f)
                curveToRelative(0.0f, -0.414f, -0.335f, -0.75f, -0.75f, -0.75f)
                horizontalLineToRelative(-0.396f)
                curveToRelative(-3.66f, 0.0f, -6.318f, -2.476f, -6.318f, -5.886f)
                curveToRelative(0.0f, -3.534f, 2.768f, -6.302f, 6.3f, -6.302f)
                lineToRelative(4.147f, 0.01f)
                horizontalLineToRelative(0.002f)
                curveToRelative(3.532f, 0.0f, 6.3f, 2.766f, 6.302f, 6.296f)
                curveToRelative(-0.003f, 1.91f, -0.942f, 3.844f, -2.514f, 5.176f)
                close()
            }
        }.build()
            .also { labelsReply = it }
