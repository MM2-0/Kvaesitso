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
fun CustomHashTagIconsNostrPreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Nostr,
            ),
        contentDescription = "",
    )
}

public val CustomHashTagIcons.Nostr: ImageVector
    get() {
        if (customHashTagIconsNostr != null) {
            return customHashTagIconsNostr!!
        }
        customHashTagIconsNostr =
            Builder(
                name = "Nostr",
                defaultWidth = 256.0.dp,
                defaultHeight = 256.0.dp,
                viewportWidth = 256.0f,
                viewportHeight = 256.0f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF9d5aff)),
                    stroke = SolidColor(Color(0x00000000)),
                    strokeLineWidth = 0.488346f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(59.661f, 98.547f)
                    curveToRelative(-3.007f, -0.988f, -5.63f, -2.705f, -8.79f, -3.204f)
                    curveToRelative(-9.733f, -1.536f, -20.022f, 2.618f, -26.776f, 9.596f)
                    curveToRelative(-3.489f, 3.604f, -6.176f, 8.376f, -7.721f, 13.141f)
                    curveToRelative(-0.436f, 1.344f, -1.365f, 4.295f, 0.119f, 5.337f)
                    curveToRelative(2.359f, 1.655f, 8.252f, -3.372f, 11.426f, -2.895f)
                    curveToRelative(-1.501f, 3.387f, -3.098f, 7.468f, -2.907f, 11.232f)
                    curveToRelative(0.108f, 2.137f, 1.374f, 4.428f, 3.883f, 3.603f)
                    curveToRelative(4.417f, -1.452f, 6.135f, -7.225f, 11.232f, -7.998f)
                    curveToRelative(-0.81f, 2.489f, -4.209f, 10.816f, -0.821f, 12.632f)
                    curveToRelative(2.253f, 1.208f, 6.274f, -2.466f, 8.147f, -3.46f)
                    curveToRelative(5.717f, -3.037f, 13.492f, -5.564f, 20.022f, -5.725f)
                    curveToRelative(4.769f, -0.118f, 9.389f, 4.766f, 13.674f, 6.6f)
                    curveToRelative(9.014f, 3.857f, 18.45f, 5.866f, 27.836f, 8.51f)
                    curveToRelative(-5.549f, 8.157f, -12.991f, 15.022f, -20.999f, 20.758f)
                    curveToRelative(-3.104f, 2.223f, -6.98f, 2.751f, -9.663f, 5.658f)
                    curveToRelative(-3.017f, 3.269f, -3.081f, 8.023f, -5.478f, 11.675f)
                    curveToRelative(-7.682f, 11.702f, -15.816f, 23.585f, -24.366f, 34.671f)
                    curveToRelative(-4.329f, 5.614f, -12.746f, 3.903f, -16.651f, 10.259f)
                    curveToRelative(-2.622f, 4.27f, -0.247f, 6.045f, 3.904f, 4.881f)
                    curveToRelative(-2.736f, 4.934f, -6.406f, 10.19f, -6.222f, 16.115f)
                    curveToRelative(0.05f, 1.59f, 0.543f, 5.759f, 2.779f, 5.759f)
                    curveToRelative(2.164f, 0.0f, 4.639f, -7.04f, 5.847f, -8.689f)
                    curveToRelative(3.137f, -4.28f, 7.228f, -7.629f, 10.552f, -11.72f)
                    curveToRelative(3.062f, -3.768f, 5.291f, -8.248f, 8.111f, -12.209f)
                    curveToRelative(8.287f, -11.637f, 16.547f, -24.861f, 26.627f, -35.027f)
                    curveToRelative(2.386f, -2.407f, 6.005f, -2.902f, 8.361f, -5.527f)
                    curveToRelative(1.995f, -2.223f, 2.178f, -5.462f, 4.159f, -7.566f)
                    curveToRelative(2.812f, -2.986f, 6.83f, -5.276f, 10.107f, -7.734f)
                    curveToRelative(7.554f, -5.666f, 14.832f, -11.354f, 24.417f, -13.003f)
                    curveToRelative(-1.823f, 3.395f, -3.652f, 6.661f, -5.078f, 10.255f)
                    curveToRelative(-2.06f, 5.191f, -6.035f, 18.045f, 4.102f, 17.527f)
                    curveToRelative(3.787f, -0.193f, 6.345f, -3.143f, 9.767f, -4.321f)
                    curveToRelative(8.992f, -3.096f, 18.219f, -5.857f, 27.347f, -8.53f)
                    curveToRelative(3.83f, -1.122f, 8.137f, -3.336f, 12.206f, -3.164f)
                    curveToRelative(5.052f, 0.214f, 2.044f, 7.862f, 6.837f, 9.154f)
                    curveToRelative(2.993f, 0.806f, 2.123f, -4.518f, 2.444f, -6.272f)
                    horizontalLineToRelative(0.977f)
                    curveToRelative(1.795f, 3.46f, 5.276f, 6.668f, 8.79f, 8.382f)
                    curveToRelative(1.126f, 0.549f, 3.41f, 1.458f, 4.36f, 0.113f)
                    curveToRelative(1.265f, -1.789f, -1.49f, -5.133f, -2.407f, -6.542f)
                    curveToRelative(-3.31f, -5.087f, -6.927f, -14.399f, -13.185f, -16.287f)
                    curveToRelative(-5.581f, -1.684f, -12.344f, 1.201f, -17.58f, 2.939f)
                    curveToRelative(-10.983f, 3.644f, -22.076f, 7.259f, -33.208f, 10.418f)
                    curveToRelative(3.008f, -6.434f, 7.233f, -8.024f, 12.105f, -12.467f)
                    curveToRelative(1.697f, -1.548f, 2.297f, -4.404f, 4.545f, -5.307f)
                    curveToRelative(3.951f, -1.587f, 10.787f, -0.53f, 15.093f, -0.827f)
                    curveToRelative(7.661f, -0.528f, 15.199f, -2.598f, 21.976f, -6.198f)
                    curveToRelative(8.972f, -4.766f, 13.782f, -12.659f, 14.999f, -22.57f)
                    curveToRelative(0.42f, -3.42f, -1.094f, -8.668f, 0.703f, -11.694f)
                    curveToRelative(2.713f, -4.568f, 11.302f, -7.601f, 15.552f, -10.833f)
                    curveToRelative(9.871f, -7.51f, 17.983f, -19.995f, 18.538f, -32.656f)
                    curveToRelative(0.468f, -10.684f, -3.262f, -19.679f, -11.232f, -26.859f)
                    curveToRelative(-4.153f, -3.741f, -13.686f, -8.055f, -14.511f, -14.162f)
                    curveToRelative(-0.469f, -3.475f, 3.193f, -4.011f, 5.74f, -4.742f)
                    curveToRelative(6.568f, -1.886f, 13.618f, 0.286f, 20.022f, -1.607f)
                    verticalLineToRelative(-0.977f)
                    lineToRelative(-6.837f, -2.93f)
                    lineToRelative(6.837f, -0.488f)
                    verticalLineTo(12.598f)
                    curveTo(234.765f, 10.283f, 229.423f, 9.599f, 224.722f, 7.462f)
                    curveTo(216.79f, 3.856f, 206.396f, -5.601f, 200.733f, 7.226f)
                    curveToRelative(-8.514f, 19.283f, 8.555f, 30.24f, 20.552f, 41.592f)
                    curveToRelative(6.761f, 6.397f, 8.068f, 17.66f, 2.564f, 25.31f)
                    curveToRelative(-6.537f, 9.087f, -14.684f, 6.861f, -24.032f, 6.876f)
                    curveToRelative(-2.628f, 0.004f, -5.212f, 1.652f, -7.814f, 1.216f)
                    curveToRelative(-3.827f, -0.641f, -7.492f, -3.968f, -10.744f, -5.919f)
                    curveToRelative(-5.155f, -3.093f, -10.679f, -5.645f, -16.604f, -6.837f)
                    curveToRelative(-15.82f, -3.183f, -32.068f, -0.627f, -46.881f, 5.374f)
                    curveToRelative(-9.526f, 3.859f, -18.321f, 9.347f, -27.836f, 13.232f)
                    curveToRelative(-7.302f, 2.981f, -15.15f, 3.639f, -22.952f, 3.639f)
                    curveToRelative(-2.1f, 0.0f, -7.822f, -0.993f, -9.263f, 0.851f)
                    curveToRelative(-1.387f, 1.776f, 0.99f, 4.574f, 1.938f, 5.986f)
                    close()
                }
            }.build()
        return customHashTagIconsNostr!!
    }

private var customHashTagIconsNostr: ImageVector? = null
