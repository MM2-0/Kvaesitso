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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
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
fun CustomHashTagIconsMatePreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Mate,
            ),
        contentDescription = "",
    )
}

public val CustomHashTagIcons.Mate: ImageVector
    get() {
        if (customHashTagIconsMate != null) {
            return customHashTagIconsMate!!
        }
        customHashTagIconsMate =
            Builder(
                name = "Mate",
                defaultWidth = 800.0.dp,
                defaultHeight = 800.0.dp,
                viewportWidth = 128.0f,
                viewportHeight = 128.0f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF865c52)),
                    stroke = null,
                    strokeLineWidth = 1.05494f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(31.448f, 53.887f)
                    curveToRelative(0.0f, 0.0f, -12.564f, 15.55f, -11.963f, 32.693f)
                    curveToRelative(0.601f, 17.143f, 10.37f, 40.868f, 44.856f, 41.66f)
                    curveTo(98.827f, 129.041f, 112.024f, 99.44f, 107.203f, 78.805f)
                    curveTo(102.308f, 57.843f, 91.653f, 51.092f, 91.653f, 51.092f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFb0b0b0)),
                    stroke = null,
                    strokeLineWidth = 1.05494f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(30.625f, 55.037f)
                    curveToRelative(0.0f, 0.0f, 0.084f, -1.783f, 0.77f, -3.945f)
                    curveToRelative(0.622f, -1.983f, 1.793f, -3.977f, 1.793f, -3.977f)
                    curveToRelative(24.589f, 17.122f, 31.545f, 15.636f, 56.059f, 5.349f)
                    curveToRelative(1.192f, -0.433f, 1.502f, -7.396f, 1.502f, -7.396f)
                    curveToRelative(0.0f, 0.0f, 3.077f, 3.619f, 3.889f, 6.605f)
                    curveToRelative(0.475f, 1.73f, 0.454f, 3.756f, 0.454f, 3.756f)
                    curveToRelative(0.0f, 0.0f, 0.696f, 6.161f, -11.362f, 13.704f)
                    curveTo(71.609f, 76.706f, 50.036f, 75.018f, 39.592f, 67.401f)
                    curveTo(31.585f, 61.546f, 30.625f, 55.037f, 30.625f, 55.037f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFe0e0e0)),
                    stroke = null,
                    strokeLineWidth = 1.05494f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(32.692f, 49.889f)
                    curveTo(32.503f, 58.867f, 48.77f, 66.547f, 62.094f, 66.631f)
                    curveTo(79.943f, 66.736f, 92.813f, 58.223f, 92.813f, 50.079f)
                    curveTo(87.333f, 24.888f, 34.137f, 29.554f, 32.692f, 49.889f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF858585)),
                    stroke = null,
                    strokeLineWidth = 1.05494f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(79.447f, 42.188f)
                    curveToRelative(-4.954f, -6.746f, -36.696f, -8.061f, -36.955f, 5.591f)
                    curveToRelative(0.19f, 7.3f, 9.642f, 8.302f, 9.642f, 8.302f)
                    lineToRelative(24.38f, -3.745f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF96a520)),
                    stroke = null,
                    strokeLineWidth = 1.05494f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(75.47f, 47.589f)
                    curveToRelative(0.0f, 0.0f, -3.597f, -3.123f, -13.746f, -3.123f)
                    curveToRelative(-10.149f, 0.0f, -15.729f, 3.407f, -16.204f, 6.825f)
                    curveToRelative(-0.274f, 1.973f, 0.285f, 2.469f, 0.285f, 2.469f)
                    curveToRelative(0.0f, 0.0f, 5.686f, 4.357f, 17.438f, 4.167f)
                    curveToRelative(11.752f, -0.19f, 13.461f, -2.279f, 13.461f, -2.279f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFe0e0e0)),
                    stroke = null,
                    strokeLineWidth = 1.05494f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(69.594f, 58.582f)
                    curveToRelative(0.0f, 0.0f, 15.824f, -30.519f, 17.248f, -32.028f)
                    curveTo(88.267f, 25.045f, 111.567f, 0.039f, 111.567f, 0.039f)
                    lineToRelative(3.597f, 3.218f)
                    lineToRelative(-6.537f, 7.473f)
                    curveToRelative(0.0f, 0.0f, -17.628f, 19.527f, -17.913f, 20.371f)
                    curveToRelative(-0.285f, 0.854f, -9.568f, 19.801f, -9.948f, 20.466f)
                    curveToRelative(-0.38f, 0.665f, -2.089f, 7.3f, -2.469f, 7.395f)
                    curveToRelative(-0.369f, 0.095f, -8.703f, -0.38f, -8.703f, -0.38f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFb0b0b0)),
                    stroke = null,
                    strokeLineWidth = 1.05494f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(76.135f, 56.018f)
                    curveToRelative(0.0f, 0.0f, 13.081f, -25.392f, 13.841f, -26.627f)
                    curveTo(90.735f, 28.157f, 114.51f, 2.645f, 114.51f, 2.645f)
                    lineToRelative(1.793f, 1.593f)
                    curveToRelative(0.0f, 0.0f, -22.973f, 25.798f, -23.67f, 26.758f)
                    curveToRelative(-0.992f, 1.361f, -11.921f, 22.776f, -11.921f, 22.776f)
                    curveToRelative(0.0f, 0.0f, -1.076f, 0.77f, -1.888f, 1.192f)
                    curveToRelative(-1.477f, 0.76f, -2.69f, 1.055f, -2.69f, 1.055f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFb0b0b0)),
                    stroke = null,
                    strokeLineWidth = 1.05494f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(95.841f, 15.277f)
                    curveToRelative(-0.475f, -0.105f, -3.882f, 3.407f, -3.692f, 3.977f)
                    curveToRelative(0.19f, 0.57f, 5.486f, 5.57f, 6.34f, 5.855f)
                    curveToRelative(0.854f, 0.285f, 3.692f, -3.313f, 3.787f, -3.692f)
                    curveToRelative(0.095f, -0.38f, -5.581f, -5.95f, -6.435f, -6.14f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFffffff)),
                    stroke = null,
                    strokeLineWidth = 1.05494f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(95.651f, 16.88f)
                    curveToRelative(-1.15f, 0.0f, -1.582f, 0.992f, -1.614f, 1.994f)
                    curveToRelative(-0.032f, 1.139f, 0.475f, 1.994f, 1.614f, 1.994f)
                    curveToRelative(1.139f, 0.0f, 1.614f, -1.139f, 1.614f, -2.089f)
                    curveToRelative(0.0f, -0.949f, -0.57f, -1.899f, -1.614f, -1.899f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFb78859)),
                    stroke = null,
                    strokeLineWidth = 1.05494f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(30.762f, 67.285f)
                    curveToRelative(-1.435f, -0.116f, -5.939f, 7.131f, -6.171f, 15.286f)
                    curveToRelative(-0.243f, 8.155f, 1.857f, 14.147f, 3.355f, 14.326f)
                    curveToRelative(1.983f, 0.243f, 3.123f, -6.351f, 5.697f, -11.509f)
                    curveToRelative(2.553f, -5.116f, 6.773f, -8.513f, 6.709f, -10.075f)
                    curveToRelative(-0.063f, -1.561f, -3.239f, -2.88f, -5.275f, -4.557f)
                    curveToRelative(-2.036f, -1.667f, -3.397f, -3.386f, -4.315f, -3.471f)
                    close()
                }
            }.build()
        return customHashTagIconsMate!!
    }

private var customHashTagIconsMate: ImageVector? = null
