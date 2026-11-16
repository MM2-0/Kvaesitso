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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun CustomHashTagIconsGamestrPreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Gamestr,
            ),
        contentDescription = "",
    )
}

val CustomHashTagIcons.Gamestr: ImageVector
    get() {
        if (customHashTagIconsGamestr != null) {
            return customHashTagIconsGamestr!!
        }
        customHashTagIconsGamestr =
            Builder(
                name = "Gamestr",
                defaultWidth = 512.0.dp,
                defaultHeight = 512.0.dp,
                viewportWidth = 512.0f,
                viewportHeight = 512.0f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFFED5564)),
                ) {
                    moveTo(511.53f, 309.68f)
                    curveToRelative(-2.48f, -42.44f, -15.15f, -98.23f, -37.64f, -165.84f)
                    curveToRelative(-0.48f, -1.42f, -1.25f, -2.73f, -2.25f, -3.84f)
                    lineToRelative(-37.11f, -40.55f)
                    curveToRelative(-2.02f, -2.21f, -4.88f, -3.47f, -7.86f, -3.47f)
                    horizontalLineToRelative(-42.67f)
                    curveToRelative(-1.66f, 0.0f, -3.28f, 0.39f, -4.77f, 1.13f)
                    lineToRelative(-19.08f, 9.52f)
                    horizontalLineTo(151.84f)
                    lineToRelative(-19.09f, -9.52f)
                    curveToRelative(-1.47f, -0.73f, -3.11f, -1.13f, -4.76f, -1.13f)
                    horizontalLineTo(85.33f)
                    curveToRelative(-3.0f, 0.0f, -5.86f, 1.26f, -7.88f, 3.47f)
                    lineToRelative(-37.09f, 40.55f)
                    curveToRelative(-1.02f, 1.11f, -1.78f, 2.41f, -2.25f, 3.84f)
                    curveTo(15.61f, 211.45f, 2.94f, 267.25f, 0.46f, 309.68f)
                    curveToRelative(-2.16f, 36.75f, 3.31f, 64.5f, 16.26f, 82.44f)
                    curveToRelative(11.27f, 15.62f, 28.09f, 23.87f, 48.69f, 23.87f)
                    curveToRelative(14.97f, 0.0f, 28.55f, -6.81f, 40.37f, -20.28f)
                    curveToRelative(8.58f, -9.78f, 16.26f, -23.16f, 22.83f, -39.8f)
                    curveToRelative(7.19f, -18.17f, 11.47f, -36.17f, 13.59f, -46.58f)
                    horizontalLineToRelative(227.58f)
                    curveToRelative(2.13f, 10.41f, 6.41f, 28.41f, 13.59f, 46.58f)
                    curveToRelative(6.56f, 16.64f, 14.25f, 30.02f, 22.83f, 39.8f)
                    curveToRelative(11.83f, 13.47f, 25.42f, 20.28f, 40.38f, 20.28f)
                    curveToRelative(20.59f, 0.0f, 37.44f, -8.25f, 48.7f, -23.87f)
                    curveTo(508.22f, 374.18f, 513.68f, 346.43f, 511.53f, 309.68f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFE6E9ED)),
                ) {
                    moveTo(412.88f, 178.2f)
                    curveToRelative(-4.17f, 4.17f, -10.92f, 4.17f, -15.08f, 0.0f)
                    curveToRelative(-4.17f, -4.16f, -4.17f, -10.91f, 0.0f, -15.09f)
                    curveToRelative(4.16f, -4.16f, 10.9f, -4.16f, 15.08f, 0.0f)
                    curveTo(417.05f, 167.29f, 417.05f, 174.04f, 412.88f, 178.2f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFE6E9ED)),
                ) {
                    moveTo(412.88f, 220.87f)
                    curveToRelative(-4.17f, 4.16f, -10.92f, 4.16f, -15.08f, 0.0f)
                    curveToRelative(-4.17f, -4.16f, -4.17f, -10.92f, 0.0f, -15.09f)
                    curveToRelative(4.16f, -4.16f, 10.9f, -4.16f, 15.08f, 0.0f)
                    curveTo(417.05f, 209.95f, 417.05f, 216.71f, 412.88f, 220.87f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFE6E9ED)),
                ) {
                    moveTo(434.2f, 199.54f)
                    curveToRelative(-4.16f, 4.16f, -10.91f, 4.16f, -15.08f, 0.0f)
                    reflectiveCurveToRelative(-4.17f, -10.91f, 0.0f, -15.09f)
                    curveToRelative(4.17f, -4.16f, 10.92f, -4.16f, 15.08f, 0.0f)
                    curveTo(438.38f, 188.62f, 438.38f, 195.37f, 434.2f, 199.54f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFE6E9ED)),
                ) {
                    moveTo(391.55f, 199.54f)
                    curveToRelative(-4.17f, 4.16f, -10.92f, 4.16f, -15.09f, 0.0f)
                    curveToRelative(-4.16f, -4.16f, -4.16f, -10.91f, 0.0f, -15.09f)
                    curveToRelative(4.17f, -4.16f, 10.92f, -4.16f, 15.09f, 0.0f)
                    curveTo(395.7f, 188.62f, 395.7f, 195.37f, 391.55f, 199.54f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF656D78)),
                ) {
                    moveTo(224.0f, 277.34f)
                    curveToRelative(0.0f, 23.56f, -19.11f, 42.65f, -42.67f, 42.65f)
                    reflectiveCurveToRelative(-42.67f, -19.09f, -42.67f, -42.65f)
                    curveToRelative(0.0f, -23.58f, 19.11f, -42.68f, 42.67f, -42.68f)
                    reflectiveCurveTo(224.0f, 253.76f, 224.0f, 277.34f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF434A54)),
                ) {
                    moveTo(181.33f, 224.0f)
                    curveToRelative(-29.41f, 0.0f, -53.34f, 23.92f, -53.34f, 53.34f)
                    curveToRelative(0.0f, 29.4f, 23.94f, 53.33f, 53.34f, 53.33f)
                    reflectiveCurveToRelative(53.33f, -23.92f, 53.33f, -53.33f)
                    curveTo(234.65f, 247.92f, 210.73f, 224.0f, 181.33f, 224.0f)
                    close()
                    moveTo(181.33f, 309.34f)
                    curveToRelative(-17.64f, 0.0f, -32.0f, -14.38f, -32.0f, -32.0f)
                    curveToRelative(0.0f, -17.66f, 14.36f, -32.01f, 32.0f, -32.01f)
                    reflectiveCurveToRelative(32.0f, 14.35f, 32.0f, 32.01f)
                    curveTo(213.33f, 294.96f, 198.97f, 309.34f, 181.33f, 309.34f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF656D78)),
                ) {
                    moveTo(373.33f, 277.34f)
                    curveToRelative(0.0f, 23.56f, -19.09f, 42.65f, -42.67f, 42.65f)
                    curveToRelative(-23.56f, 0.0f, -42.65f, -19.09f, -42.65f, -42.65f)
                    curveToRelative(0.0f, -23.58f, 19.09f, -42.68f, 42.65f, -42.68f)
                    curveTo(354.24f, 234.66f, 373.33f, 253.76f, 373.33f, 277.34f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF434A54)),
                ) {
                    moveTo(330.66f, 224.0f)
                    curveToRelative(-29.41f, 0.0f, -53.33f, 23.92f, -53.33f, 53.34f)
                    curveToRelative(0.0f, 29.4f, 23.92f, 53.33f, 53.33f, 53.33f)
                    curveToRelative(29.42f, 0.0f, 53.34f, -23.92f, 53.34f, -53.33f)
                    curveTo(384.0f, 247.92f, 360.08f, 224.0f, 330.66f, 224.0f)
                    close()
                    moveTo(330.66f, 309.34f)
                    curveToRelative(-17.64f, 0.0f, -32.0f, -14.38f, -32.0f, -32.0f)
                    curveToRelative(0.0f, -17.66f, 14.36f, -32.01f, 32.0f, -32.01f)
                    curveToRelative(17.66f, 0.0f, 32.01f, 14.35f, 32.01f, 32.01f)
                    curveTo(362.67f, 294.96f, 348.31f, 309.34f, 330.66f, 309.34f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFF5F7FA)),
                ) {
                    moveTo(106.66f, 224.0f)
                    curveToRelative(-5.89f, 0.0f, -10.67f, -4.77f, -10.67f, -10.67f)
                    verticalLineToRelative(-42.66f)
                    curveToRelative(0.0f, -5.89f, 4.78f, -10.66f, 10.67f, -10.66f)
                    reflectiveCurveToRelative(10.67f, 4.77f, 10.67f, 10.66f)
                    verticalLineToRelative(42.66f)
                    curveTo(117.33f, 219.23f, 112.55f, 224.0f, 106.66f, 224.0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFE6E9ED)),
                ) {
                    moveTo(127.99f, 202.66f)
                    horizontalLineTo(85.33f)
                    curveToRelative(-5.89f, 0.0f, -10.67f, -4.77f, -10.67f, -10.66f)
                    reflectiveCurveToRelative(4.78f, -10.67f, 10.67f, -10.67f)
                    horizontalLineToRelative(42.65f)
                    curveToRelative(5.89f, 0.0f, 10.67f, 4.78f, 10.67f, 10.67f)
                    curveTo(138.66f, 197.89f, 133.88f, 202.66f, 127.99f, 202.66f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFFFCE54)),
                ) {
                    moveTo(181.32f, 106.64f)
                    horizontalLineToRelative(149.33f)
                    verticalLineToRelative(42.69f)
                    horizontalLineToRelative(-149.33f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF434A54)),
                ) {
                    moveTo(495.28f, 392.12f)
                    curveToRelative(8.92f, -12.36f, 14.28f, -29.38f, 16.06f, -50.78f)
                    curveToRelative(-0.03f, 0.0f, -59.73f, 43.54f, -117.26f, 36.28f)
                    curveToRelative(0.0f, 0.0f, 0.05f, 0.41f, 0.17f, 1.11f)
                    curveToRelative(3.75f, 6.5f, 7.75f, 12.17f, 11.95f, 16.98f)
                    curveToRelative(11.83f, 13.47f, 25.42f, 20.28f, 40.38f, 20.28f)
                    curveTo(467.17f, 415.99f, 484.01f, 407.74f, 495.28f, 392.12f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF434A54)),
                ) {
                    moveTo(16.72f, 392.12f)
                    curveToRelative(-8.92f, -12.36f, -14.3f, -29.38f, -16.06f, -50.78f)
                    curveToRelative(0.0f, 0.0f, 59.72f, 43.54f, 117.26f, 36.28f)
                    curveToRelative(0.0f, 0.0f, -0.06f, 0.41f, -0.17f, 1.11f)
                    curveToRelative(-3.77f, 6.5f, -7.75f, 12.17f, -11.97f, 16.98f)
                    curveToRelative(-11.83f, 13.47f, -25.41f, 20.28f, -40.37f, 20.28f)
                    curveTo(44.82f, 415.99f, 27.99f, 407.74f, 16.72f, 392.12f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF434A54)),
                ) {
                    moveTo(70.88f, 106.64f)
                    horizontalLineToRelative(80.97f)
                    lineToRelative(-19.09f, -9.52f)
                    curveToRelative(-1.47f, -0.73f, -3.11f, -1.13f, -4.76f, -1.13f)
                    horizontalLineTo(85.33f)
                    curveToRelative(-3.0f, 0.0f, -5.86f, 1.26f, -7.88f, 3.47f)
                    lineTo(70.88f, 106.64f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF434A54)),
                ) {
                    moveTo(441.13f, 106.64f)
                    horizontalLineToRelative(-80.95f)
                    lineToRelative(19.08f, -9.52f)
                    curveToRelative(1.48f, -0.73f, 3.11f, -1.13f, 4.77f, -1.13f)
                    horizontalLineToRelative(42.67f)
                    curveToRelative(2.98f, 0.0f, 5.84f, 1.26f, 7.86f, 3.47f)
                    lineTo(441.13f, 106.64f)
                    close()
                }
            }.build()
        return customHashTagIconsGamestr!!
    }

private var customHashTagIconsGamestr: ImageVector? = null
