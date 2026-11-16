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
fun CustomHashTagIconsSkullPreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Skull,
            ),
        contentDescription = "",
    )
}

val CustomHashTagIcons.Skull: ImageVector
    get() {
        if (customHashTagIconsSkull != null) {
            return customHashTagIconsSkull!!
        }
        customHashTagIconsSkull =
            Builder(
                name = "Skull",
                defaultWidth = 521.0.dp,
                defaultHeight = 521.0.dp,
                viewportWidth = 521.0f,
                viewportHeight = 521.0f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFF100f0f)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(166.043f, 80.017f)
                    curveToRelative(-17.589f, -6.803f, -44.532f, 4.017f, -36.944f, 26.703f)
                    curveToRelative(2.102f, 23.626f, 5.968f, 47.166f, 3.466f, 70.986f)
                    curveToRelative(-2.736f, 24.018f, -32.144f, 35.563f, -36.128f, 61.121f)
                    curveToRelative(-17.98f, 47.592f, -10.437f, 101.14f, 7.34f, 147.588f)
                    curveToRelative(-5.494f, 19.929f, -10.791f, 48.409f, 11.267f, 61.19f)
                    curveToRelative(17.725f, 13.076f, 40.748f, 11.293f, 60.405f, 18.039f)
                    curveToRelative(20.504f, 12.317f, -10.44f, 42.989f, 15.691f, 51.945f)
                    curveToRelative(18.095f, 2.854f, 17.827f, 3.664f, 56.455f, 3.83f)
                    curveToRelative(23.285f, 0.371f, 51.137f, 0.264f, 66.031f, -3.178f)
                    curveToRelative(17.85f, -3.738f, 39.299f, -7.904f, 29.227f, -32.481f)
                    curveToRelative(0.752f, -28.566f, 30.293f, -42.617f, 54.417f, -48.439f)
                    curveToRelative(24.039f, -0.976f, 29.123f, -26.788f, 19.494f, -44.696f)
                    curveToRelative(-5.32f, -21.481f, 6.117f, -42.357f, 4.253f, -64.147f)
                    curveToRelative(9.219f, -22.321f, 5.234f, -47.011f, 3.786f, -70.404f)
                    curveToRelative(-3.119f, -31.072f, -19.988f, -60.885f, -47.911f, -76.091f)
                    curveToRelative(-19.16f, -14.769f, -41.795f, -26.32f, -51.946f, -49.919f)
                    curveToRelative(-13.211f, -19.23f, -10.244f, -46.183f, -25.23f, -63.732f)
                    curveToRelative(-12.877f, -9.258f, -34.33f, 6.801f, -32.652f, -16.298f)
                    curveToRelative(-4.238f, -18.497f, -22.619f, -63.827f, -43.895f, -35.805f)
                    curveToRelative(-0.798f, 10.205f, 6.997f, 46.603f, -3.089f, 20.067f)
                    curveToRelative(-8.914f, -17.607f, -6.239f, -23.9f, -23.011f, -35.866f)
                    curveToRelative(-33.287f, -3.665f, -17.661f, 22.157f, -29.248f, 36.91f)
                    curveToRelative(-11.198f, 9.777f, -1.801f, 29.625f, -1.778f, 42.677f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF29b34a)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(197.415f, 67.468f)
                    curveToRelative(10.404f, -9.938f, 32.503f, -7.129f, 19.032f, -25.224f)
                    curveToRelative(-10.229f, -16.227f, -7.149f, -34.882f, -28.123f, -37.024f)
                    curveToRelative(-13.71f, 16.666f, -8.256f, 27.409f, 3.864f, 41.459f)
                    curveToRelative(2.333f, 6.769f, 3.635f, 13.825f, 5.226f, 20.789f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF136c37)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(253.885f, 115.571f)
                    curveToRelative(2.506f, -17.598f, -3.283f, -35.978f, 9.784f, -50.514f)
                    curveToRelative(-6.679f, -17.003f, -8.374f, -38.331f, -21.776f, -51.311f)
                    curveToRelative(-23.222f, -3.057f, -6.865f, 32.18f, -7.339f, 44.922f)
                    curveToRelative(15.503f, 13.029f, 12.399f, 35.878f, 18.82f, 53.612f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF136c37)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(169.184f, 49.392f)
                    curveToRelative(-0.974f, 1.101f, -1.704f, 6.811f, 6.669f, 43.023f)
                    curveToRelative(6.056f, 18.193f, 12.642f, 46.965f, 24.448f, 57.302f)
                    curveToRelative(2.53f, -36.482f, -3.511f, -73.178f, -18.599f, -106.116f)
                    curveToRelative(-2.683f, -5.857f, -9.241f, 2.216f, -12.518f, 5.791f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF29b34a)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(204.735f, 104.068f)
                    curveToRelative(0.625f, -7.842f, 3.927f, -37.846f, 6.899f, -19.429f)
                    curveToRelative(-3.324f, 23.587f, -5.05f, 47.144f, -4.767f, 71.229f)
                    curveToRelative(-0.842f, 28.442f, -19.717f, -11.418f, -22.985f, -22.25f)
                    curveToRelative(-10.169f, -15.42f, -5.614f, -50.82f, -31.301f, -48.375f)
                    curveToRelative(-28.193f, -1.433f, -14.516f, 32.27f, -14.95f, 49.151f)
                    curveToRelative(3.377f, 16.605f, -2.993f, 44.197f, 2.46f, 54.009f)
                    curveToRelative(15.805f, -3.46f, -17.136f, 24.032f, -21.739f, 34.012f)
                    curveToRelative(-27.954f, 42.526f, -22.507f, 97.338f, -10.089f, 144.233f)
                    curveToRelative(1.337f, 14.758f, 3.425f, 18.522f, 17.299f, 14.775f)
                    curveToRelative(-10.154f, 11.186f, -29.782f, 28.985f, -13.96f, 47.862f)
                    curveToRelative(14.781f, 25.267f, 46.309f, 12.627f, 66.711f, 26.555f)
                    curveToRelative(16.958f, 5.175f, 5.17f, 30.138f, 22.723f, 23.089f)
                    curveToRelative(9.803f, 7.155f, 9.208f, 22.008f, 19.428f, 5.38f)
                    curveToRelative(19.77f, -21.954f, 28.472f, 35.563f, 35.96f, 6.99f)
                    curveToRelative(4.723f, -20.5f, 27.271f, -14.175f, 31.971f, 2.826f)
                    curveToRelative(3.478f, -14.209f, 17.353f, -16.833f, 26.143f, -5.229f)
                    curveToRelative(9.612f, -16.096f, 27.492f, -17.179f, 35.903f, -35.211f)
                    curveToRelative(12.955f, -16.257f, 34.621f, -16.627f, 51.515f, -25.791f)
                    curveToRelative(17.619f, -15.141f, 3.569f, -40.186f, -5.852f, -56.119f)
                    curveToRelative(17.858f, 12.186f, 9.98f, -26.685f, 16.119f, -38.692f)
                    curveToRelative(7.231f, -36.187f, 12.769f, -76.041f, -5.908f, -109.896f)
                    curveToRelative(-14.865f, -32.017f, -48.573f, -51.652f, -82.978f, -54.865f)
                    curveToRelative(-28.394f, -9.939f, 30.195f, -0.921f, 8.313f, -16.557f)
                    curveToRelative(-19.659f, -20.983f, -23.944f, -50.275f, -32.649f, -76.299f)
                    curveToRelative(-11.311f, -10.062f, -41.455f, -4.621f, -30.088f, 9.78f)
                    curveToRelative(-0.115f, 11.714f, -7.056f, 27.874f, -5.351f, 6.936f)
                    curveToRelative(1.89f, -16.451f, -3.848f, -11.097f, -1.641f, 1.757f)
                    curveToRelative(-0.075f, 15.923f, 3.317f, 46.987f, -3.507f, 53.766f)
                    curveToRelative(-11.535f, -23.034f, -15.183f, -49.22f, -20.725f, -74.156f)
                    curveToRelative(-11.236f, -22.76f, -44.567f, -8.538f, -33.969f, 21.585f)
                    curveToRelative(0.542f, 2.957f, 0.77f, 5.943f, 1.016f, 8.934f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF100f0f)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(141.992f, 137.532f)
                    curveToRelative(5.433f, -6.563f, 6.768f, -44.708f, -3.759f, -38.139f)
                    curveToRelative(-0.113f, 12.72f, 4.244f, 25.288f, 3.759f, 38.139f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF100f0f)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(190.095f, 178.763f)
                    curveToRelative(-23.353f, 4.429f, -48.582f, 19.127f, -56.055f, 42.868f)
                    curveToRelative(-2.024f, 30.081f, 23.868f, 1.519f, 32.26f, -8.593f)
                    curveToRelative(9.022f, -13.104f, 39.168f, -19.981f, 37.211f, -34.476f)
                    curveToRelative(-4.434f, -1.085f, -8.984f, -0.392f, -13.416f, 0.201f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFffffff)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(137.809f, 233.739f)
                    curveToRelative(18.974f, -10.812f, 30.284f, -32.523f, 50.837f, -42.381f)
                    curveToRelative(10.081f, -4.822f, 18.905f, -12.577f, 1.877f, -9.752f)
                    curveToRelative(-23.615f, 5.699f, -57.72f, 22.986f, -52.714f, 52.132f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF100f0f)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(176.501f, 382.232f)
                    curveToRelative(-10.891f, 10.084f, -47.394f, -14.781f, -33.154f, 10.003f)
                    curveToRelative(22.963f, 15.302f, 54.036f, 2.594f, 73.061f, -13.679f)
                    curveToRelative(25.349f, -20.634f, 34.476f, -66.262f, 5.31f, -87.733f)
                    curveToRelative(-28.868f, -23.834f, -81.683f, -12.071f, -90.905f, 26.166f)
                    curveToRelative(-5.558f, 28.613f, 14.865f, 63.502f, 45.688f, 65.243f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF100f0f)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(380.418f, 379.095f)
                    curveToRelative(-10.317f, 0.003f, -50.27f, 12.128f, -23.121f, 1.307f)
                    curveToRelative(34.329f, -13.642f, 44.659f, -68.986f, 10.941f, -89.151f)
                    curveToRelative(-29.709f, -20.531f, -81.334f, -7.617f, -88.304f, 30.567f)
                    curveToRelative(-5.964f, 36.873f, 22.869f, 76.762f, 61.704f, 76.249f)
                    curveToRelative(13.693f, -0.457f, 36.367f, -1.58f, 38.78f, -18.972f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFffffff)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(178.592f, 317.397f)
                    curveToRelative(-1.612f, 19.729f, 20.895f, -2.92f, 0.0f, 0.0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFffffff)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(333.36f, 318.443f)
                    curveToRelative(-9.346f, 14.816f, 15.03f, 7.845f, 3.202f, 1.021f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFffffff)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(182.775f, 346.677f)
                    curveToRelative(19.051f, 3.541f, 12.908f, -18.621f, -0.321f, -6.868f)
                    curveToRelative(-0.441f, 2.36f, -1.198f, 4.037f, 0.321f, 6.868f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFffffff)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(328.131f, 347.723f)
                    curveToRelative(18.676f, 9.029f, 16.713f, -14.754f, 1.534f, -5.831f)
                    lineToRelative(-0.715f, 1.921f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF100f0f)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(261.205f, 452.296f)
                    curveToRelative(-0.01f, -31.751f, -0.131f, -63.617f, -4.183f, -95.161f)
                    curveToRelative(-21.21f, 6.715f, -22.059f, 34.273f, -29.883f, 51.747f)
                    curveToRelative(-14.996f, 21.746f, 1.779f, 51.062f, 28.507f, 43.698f)
                    lineToRelative(2.754f, -0.175f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF100f0f)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(268.525f, 362.363f)
                    curveToRelative(-5.532f, 25.009f, 1.026f, 50.791f, 1.095f, 76.12f)
                    curveToRelative(2.316f, 33.709f, 45.428f, -4.749f, 27.671f, -24.311f)
                    curveToRelative(-9.589f, -16.836f, -13.047f, -39.454f, -28.766f, -51.809f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFf9a318)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(185.912f, 282.888f)
                    curveToRelative(2.812f, 7.364f, -1.009f, 19.985f, 6.798f, 23.39f)
                    curveToRelative(13.403f, 7.924f, 5.009f, 23.188f, 18.017f, 31.428f)
                    curveToRelative(3.286f, 15.59f, -20.209f, 16.404f, -9.522f, 32.471f)
                    curveToRelative(10.79f, 11.433f, 33.598f, -18.826f, 33.283f, -34.778f)
                    curveToRelative(4.946f, -24.56f, -15.221f, -46.819f, -38.787f, -50.423f)
                    curveToRelative(-3.238f, -0.808f, -6.508f, -1.478f, -9.789f, -2.088f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFf9a318)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(174.409f, 283.934f)
                    curveToRelative(-0.802f, 9.414f, 7.357f, 32.461f, 3.761f, 10.435f)
                    curveToRelative(-1.913f, -2.349f, 0.636f, -11.744f, -3.761f, -10.435f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFf9a318)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(316.628f, 378.049f)
                    curveToRelative(3.075f, -11.085f, 3.285f, -20.165f, -2.786f, -29.212f)
                    curveToRelative(-1.487f, -24.509f, 13.443f, -41.074f, 17.426f, -62.812f)
                    curveToRelative(-28.09f, 0.389f, -54.698f, 27.468f, -46.08f, 56.402f)
                    curveToRelative(3.617f, 16.059f, 15.22f, 30.985f, 31.439f, 35.622f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFf9a318)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(163.952f, 287.071f)
                    curveToRelative(-20.933f, 9.04f, -36.187f, 32.897f, -27.189f, 55.421f)
                    curveToRelative(6.503f, 19.036f, 24.512f, 34.534f, 44.967f, 35.557f)
                    curveToRelative(-4.624f, -20.378f, -19.128f, -36.549f, -18.624f, -60.584f)
                    curveToRelative(2.351f, -10.495f, 8.589f, -19.63f, 0.847f, -30.394f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFf9a318)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(340.68f, 287.071f)
                    curveToRelative(-4.457f, 8.035f, -2.168f, 32.414f, 0.122f, 9.871f)
                    curveToRelative(-2.314f, -1.879f, 6.217f, -11.529f, -0.122f, -9.871f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFf9a318)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(351.137f, 289.162f)
                    curveToRelative(-6.293f, 17.563f, 7.981f, 33.691f, 3.62f, 48.783f)
                    curveToRelative(15.158f, 21.457f, -25.383f, 21.09f, -17.214f, 43.241f)
                    curveToRelative(35.013f, -1.124f, 60.324f, -47.798f, 37.825f, -75.927f)
                    curveToRelative(-6.395f, -7.397f, -14.86f, -13.213f, -24.23f, -16.097f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFf9a318)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(188.004f, 358.18f)
                    curveToRelative(-1.683f, 13.577f, 8.332f, 26.695f, 3.23f, 7.21f)
                    curveToRelative(-1.151f, -2.128f, 0.632f, -8.445f, -3.23f, -7.21f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFf9a318)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(329.177f, 360.272f)
                    curveToRelative(-4.168f, 9.067f, -2.34f, 32.64f, 0.249f, 10.422f)
                    curveToRelative(-0.99f, -2.221f, 4.738f, -12.028f, -0.249f, -10.422f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF203d1b)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(318.72f, 509.811f)
                    curveToRelative(27.619f, 9.81f, 24.306f, -50.605f, 1.551f, -25.143f)
                    curveToRelative(-4.515f, 7.619f, -2.502f, 16.89f, -1.551f, 25.143f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF203d1b)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(206.827f, 511.902f)
                    curveToRelative(8.105f, -14.523f, -14.147f, -44.0f, -18.997f, -18.823f)
                    curveToRelative(-4.344f, 14.514f, 5.617f, 21.311f, 18.997f, 18.823f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF203d1b)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(248.656f, 517.131f)
                    curveToRelative(3.679f, -23.672f, -27.249f, -48.536f, -35.512f, -15.612f)
                    curveToRelative(-7.898f, 22.958f, 23.234f, 17.531f, 35.512f, 15.612f)
                    close()
                    moveTo(292.577f, 516.085f)
                    curveToRelative(26.315f, 9.481f, 28.204f, -26.173f, 7.336f, -31.631f)
                    curveToRelative(-11.741f, 5.503f, -7.375f, 21.531f, -7.336f, 31.631f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF203d1b)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(289.062f, 514.957f)
                    curveToRelative(-0.003f, -5.436f, -0.324f, -10.515f, -2.186f, -15.686f)
                    curveToRelative(-2.274f, -5.577f, -5.053f, -10.71f, -9.359f, -14.348f)
                    curveToRelative(-12.703f, -10.594f, -18.586f, 9.534f, -21.57f, 18.531f)
                    curveToRelative(-1.24f, 3.742f, -4.543f, 11.236f, -0.763f, 14.494f)
                    curveToRelative(2.449f, 2.11f, 6.787f, 2.152f, 9.827f, 2.222f)
                    curveToRelative(8.863f, 0.207f, 15.69f, -3.227f, 24.052f, -5.213f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF100f0f)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(183.928f, 13.894f)
                    curveToRelative(1.519f, 7.249f, 3.919f, 14.134f, 4.873f, 21.515f)
                    lineToRelative(2.074f, -0.267f)
                    curveToRelative(-0.953f, -7.414f, -1.329f, -14.808f, -4.873f, -21.515f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF100f0f)),
                    stroke = null,
                    strokeLineWidth = 1.04573f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(233.231f, 20.945f)
                    lineToRelative(1.046f, 16.732f)
                    curveToRelative(2.294f, -5.109f, 2.624f, -11.389f, 1.046f, -16.732f)
                    close()
                }
            }.build()
        return customHashTagIconsSkull!!
    }

private var customHashTagIconsSkull: ImageVector? = null
