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
package com.vitorpamplona.amethyst.commons.hashtags

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush.Companion.linearGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun CustomHashTagIconsAmethystPreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Amethyst,
            ),
        contentDescription = "",
    )
}

public val CustomHashTagIcons.Amethyst: ImageVector
    get() {
        if (customHashTagIconsAmethyst != null) {
            return customHashTagIconsAmethyst!!
        }
        customHashTagIconsAmethyst =
            Builder(
                name = "Amethyst",
                defaultWidth = 170.16.dp,
                defaultHeight = 186.2.dp,
                viewportWidth = 170.16f,
                viewportHeight = 186.2f,
            ).apply {
                path(
                    fill =
                        linearGradient(
                            0.0f to Color(0xFF652D80),
                            1.0f to Color(0xFF2598CF),
                            start = Offset(80.19f, 57.17f),
                            end = Offset(91.66f, 57.17f),
                        ),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(80.19f, 57.17f)
                    arcToRelative(5.74f, 5.64f, 0.0f, true, false, 11.48f, 0.0f)
                    arcToRelative(5.74f, 5.64f, 0.0f, true, false, -11.48f, 0.0f)
                    close()
                }
                path(
                    fill =
                        linearGradient(
                            0.0f to Color(0xFF652D80),
                            1.0f to Color(0xFF2598CF),
                            start = Offset(7.48f, 93.92f),
                            end = Offset(180f, 93.92f),
                        ),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(170.13f, 142.54f)
                    curveToRelative(-0.2f, -0.61f, -46.85f, -111.69f, -59.68f, -142.5f)
                    curveToRelative(-15.62f, 0.0f, -48.61f, 0.0f, -71.88f, 0.0f)
                    curveToRelative(-6.83f, 16.89f, -24.46f, 59.94f, -38.6f, 94.62f)
                    curveToRelative(7.75f, 18.49f, 14.71f, 35.1f, 20.08f, 47.91f)
                    lineToRelative(29.27f, 0.0f)
                    arcToRelative(2.0f, 2.0f, 0.0f, false, false, 1.87f, -2.35f)
                    curveToRelative(-0.25f, -5.91f, -16.34f, -27.39f, -11.54f, -50.63f)
                    curveToRelative(1.28f, -5.0f, 2.26f, -10.08f, 3.66f, -15.06f)
                    arcToRelative(175.77f, 175.77f, 0.0f, false, true, 13.44f, -34.8f)
                    curveToRelative(0.89f, -1.69f, 1.3f, -2.45f, 3.35f, -1.17f)
                    curveToRelative(4.37f, 2.72f, 9.25f, 2.56f, 14.11f, 2.21f)
                    curveToRelative(4.19f, -0.91f, 6.23f, -2.92f, 13.91f, -0.88f)
                    curveToRelative(1.52f, 0.0f, 3.0f, 0.0f, 4.55f, 0.14f)
                    curveToRelative(3.69f, 0.29f, 7.15f, 1.17f, 9.37f, 4.51f)
                    curveToRelative(2.42f, 3.65f, 2.81f, 7.78f, 2.42f, 12.0f)
                    curveToRelative(-0.59f, 6.31f, -0.17f, 12.19f, 5.17f, 16.64f)
                    arcToRelative(57.52f, 57.52f, 0.0f, false, false, 6.0f, 4.0f)
                    curveToRelative(2.65f, 1.7f, 5.85f, 2.44f, 8.12f, 4.8f)
                    curveToRelative(1.34f, 1.39f, 2.13f, 2.87f, 1.55f, 4.85f)
                    reflectiveCurveToRelative(-2.13f, 2.91f, -4.17f, 3.13f)
                    curveToRelative(-5.16f, 0.56f, -10.2f, -0.49f, -15.27f, -1.1f)
                    curveToRelative(-0.66f, -0.08f, -1.31f, -0.13f, -2.0f, -0.17f)
                    arcToRelative(11.47f, 11.47f, 0.0f, false, true, -3.81f, 0.13f)
                    lineToRelative(-1.19f, 0.0f)
                    arcToRelative(26.7f, 26.7f, 0.0f, false, false, -5.9f, 1.41f)
                    curveToRelative(-4.78f, 1.74f, -9.13f, 3.66f, -14.77f, 3.56f)
                    arcToRelative(4.32f, 4.32f, 0.0f, false, false, -2.05f, 0.89f)
                    curveToRelative(-4.42f, 3.93f, -7.08f, 8.89f, -4.87f, 16.14f)
                    curveToRelative(6.06f, 16.93f, 21.61f, 57.77f, 28.29f, 75.4f)
                    curveToRelative(-0.19f, -11.94f, -0.24f, -33.32f, -0.28f, -43.7f)
                    curveTo(125.96f, 142.56f, 166.87f, 142.54f, 170.13f, 142.54f)
                    close()
                }
            }.build()
        return customHashTagIconsAmethyst!!
    }

private var customHashTagIconsAmethyst: ImageVector? = null
