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
import androidx.compose.ui.graphics.StrokeCap.Companion.Round
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.commons.icons.materialIcon
import androidx.compose.ui.graphics.StrokeJoin.Companion.Round as RoundJoin

@Preview
@Composable
fun CustomHashTagIconsCashuPreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Cashu,
            ),
        contentDescription = "",
    )
}

// Monochrome, tintable "deez nuts" cashew + pixel-shades mark. The cashew body
// is a smooth rounded outline (1.2 stroke weight, matching the shared Zap
// outline icon) so it tints with the surrounding content colour like a Material
// Symbol; the pixel "deal-with-it" sunglasses stay a solid fill, enlarged so
// they overhang the slimmed body for a bolder look.
public val CustomHashTagIcons.Cashu: ImageVector
    get() {
        if (customHashTagIconsCashu != null) {
            return customHashTagIconsCashu!!
        }
        customHashTagIconsCashu =
            materialIcon(name = "Cashu") {
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.2f,
                    strokeLineCap = Round,
                    strokeLineJoin = RoundJoin,
                ) {
                    moveTo(14.02f, 2.02f)
                    curveTo(14.14f, 2.16f, 14.00f, 2.39f, 14.11f, 2.51f)
                    curveTo(14.22f, 2.64f, 14.55f, 2.67f, 14.67f, 2.76f)
                    curveTo(14.79f, 2.85f, 14.78f, 2.65f, 14.83f, 3.06f)
                    curveTo(14.88f, 3.47f, 14.98f, 4.70f, 14.97f, 5.24f)
                    curveTo(14.96f, 5.78f, 14.89f, 6.07f, 14.76f, 6.30f)
                    curveTo(14.63f, 6.52f, 14.31f, 6.38f, 14.17f, 6.59f)
                    curveTo(14.04f, 6.81f, 14.07f, 7.37f, 13.95f, 7.58f)
                    curveTo(13.83f, 7.79f, 13.57f, 7.75f, 13.46f, 7.84f)
                    curveTo(13.36f, 7.93f, 13.36f, 7.87f, 13.32f, 8.13f)
                    curveTo(13.27f, 8.39f, 13.18f, 8.89f, 13.18f, 9.39f)
                    curveTo(13.18f, 9.89f, 13.19f, 10.73f, 13.31f, 11.11f)
                    curveTo(13.43f, 11.48f, 13.76f, 11.33f, 13.89f, 11.65f)
                    curveTo(14.01f, 11.98f, 14.00f, 12.74f, 14.07f, 13.04f)
                    curveTo(14.13f, 13.34f, 14.14f, 13.33f, 14.29f, 13.45f)
                    curveTo(14.44f, 13.58f, 14.57f, 13.69f, 14.95f, 13.79f)
                    curveTo(15.34f, 13.89f, 16.29f, 13.95f, 16.60f, 14.03f)
                    curveTo(16.91f, 14.11f, 16.75f, 14.09f, 16.82f, 14.25f)
                    curveTo(16.89f, 14.41f, 16.86f, 14.82f, 17.01f, 14.99f)
                    curveTo(17.16f, 15.17f, 17.58f, 15.12f, 17.72f, 15.31f)
                    curveTo(17.87f, 15.51f, 17.78f, 16.00f, 17.90f, 16.19f)
                    curveTo(18.01f, 16.38f, 18.29f, 16.32f, 18.41f, 16.45f)
                    curveTo(18.53f, 16.57f, 18.59f, 16.45f, 18.62f, 16.96f)
                    curveTo(18.66f, 17.47f, 18.66f, 18.98f, 18.62f, 19.48f)
                    curveTo(18.59f, 19.99f, 18.54f, 19.87f, 18.42f, 20.00f)
                    curveTo(18.30f, 20.13f, 18.02f, 20.07f, 17.91f, 20.25f)
                    curveTo(17.79f, 20.43f, 17.88f, 20.90f, 17.74f, 21.09f)
                    curveTo(17.59f, 21.28f, 17.17f, 21.25f, 17.02f, 21.40f)
                    curveTo(16.88f, 21.54f, 16.96f, 21.83f, 16.87f, 21.97f)
                    curveTo(16.79f, 22.10f, 17.07f, 22.13f, 16.52f, 22.21f)
                    curveTo(15.97f, 22.30f, 14.66f, 22.48f, 13.58f, 22.50f)
                    curveTo(12.50f, 22.52f, 10.73f, 22.38f, 10.04f, 22.31f)
                    curveTo(9.36f, 22.24f, 9.60f, 22.25f, 9.46f, 22.10f)
                    curveTo(9.31f, 21.95f, 9.34f, 21.55f, 9.18f, 21.40f)
                    curveTo(9.03f, 21.24f, 8.67f, 21.37f, 8.51f, 21.17f)
                    curveTo(8.35f, 20.96f, 8.38f, 20.38f, 8.21f, 20.16f)
                    curveTo(8.03f, 19.94f, 7.63f, 20.05f, 7.47f, 19.86f)
                    curveTo(7.32f, 19.67f, 7.41f, 19.20f, 7.30f, 19.03f)
                    curveTo(7.19f, 18.85f, 6.91f, 18.90f, 6.79f, 18.80f)
                    curveTo(6.67f, 18.70f, 6.66f, 18.82f, 6.58f, 18.45f)
                    curveTo(6.51f, 18.08f, 6.47f, 16.97f, 6.35f, 16.59f)
                    curveTo(6.22f, 16.21f, 5.96f, 16.53f, 5.84f, 16.17f)
                    curveTo(5.71f, 15.80f, 5.66f, 15.30f, 5.61f, 14.42f)
                    curveTo(5.55f, 13.55f, 5.49f, 12.59f, 5.51f, 10.94f)
                    curveTo(5.54f, 9.30f, 5.67f, 5.72f, 5.75f, 4.56f)
                    curveTo(5.82f, 3.40f, 5.84f, 4.11f, 5.95f, 3.98f)
                    curveTo(6.06f, 3.85f, 6.30f, 3.97f, 6.41f, 3.78f)
                    curveTo(6.53f, 3.58f, 6.51f, 3.02f, 6.64f, 2.82f)
                    curveTo(6.78f, 2.62f, 7.11f, 2.72f, 7.23f, 2.59f)
                    curveTo(7.35f, 2.45f, 7.29f, 2.15f, 7.37f, 2.01f)
                    curveTo(7.44f, 1.88f, 7.42f, 1.85f, 7.68f, 1.77f)
                    curveTo(7.94f, 1.69f, 8.23f, 1.58f, 8.93f, 1.54f)
                    curveTo(9.62f, 1.49f, 11.09f, 1.48f, 11.84f, 1.50f)
                    curveTo(12.58f, 1.52f, 13.03f, 1.59f, 13.40f, 1.67f)
                    curveTo(13.76f, 1.76f, 13.90f, 1.88f, 14.02f, 2.02f)
                    close()
                }
                path(fill = SolidColor(Color.Black)) {
                    moveTo(2.18f, 6.90f)
                    lineTo(2.18f, 8.55f)
                    lineTo(2.81f, 8.55f)
                    lineTo(2.81f, 9.50f)
                    lineTo(3.34f, 9.50f)
                    lineTo(3.34f, 10.39f)
                    lineTo(4.00f, 10.39f)
                    lineTo(4.00f, 11.08f)
                    lineTo(8.37f, 11.08f)
                    lineTo(8.37f, 10.37f)
                    lineTo(9.02f, 10.37f)
                    lineTo(9.02f, 9.48f)
                    lineTo(9.68f, 9.48f)
                    lineTo(9.68f, 7.60f)
                    lineTo(10.83f, 7.60f)
                    lineTo(10.83f, 9.46f)
                    lineTo(11.50f, 9.46f)
                    lineTo(11.50f, 10.34f)
                    lineTo(12.01f, 10.34f)
                    lineTo(12.01f, 11.04f)
                    lineTo(16.39f, 11.04f)
                    lineTo(16.39f, 10.36f)
                    lineTo(17.02f, 10.36f)
                    lineTo(17.02f, 9.47f)
                    lineTo(17.70f, 9.47f)
                    lineTo(17.70f, 8.54f)
                    lineTo(18.32f, 8.54f)
                    lineTo(18.32f, 6.93f)
                    close()
                    moveTo(12.11f, 7.58f)
                    lineTo(12.72f, 7.58f)
                    lineTo(12.72f, 8.49f)
                    lineTo(13.40f, 8.50f)
                    lineTo(13.40f, 9.40f)
                    lineTo(14.01f, 9.40f)
                    lineTo(14.01f, 8.48f)
                    lineTo(13.41f, 8.48f)
                    lineTo(13.41f, 7.58f)
                    lineTo(14.03f, 7.58f)
                    lineTo(14.03f, 8.49f)
                    lineTo(14.60f, 8.49f)
                    lineTo(14.61f, 9.41f)
                    lineTo(15.20f, 9.42f)
                    lineTo(15.20f, 10.28f)
                    lineTo(14.60f, 10.28f)
                    lineTo(14.61f, 9.41f)
                    lineTo(14.03f, 9.41f)
                    lineTo(14.03f, 10.24f)
                    lineTo(13.41f, 10.24f)
                    lineTo(13.41f, 9.42f)
                    lineTo(12.72f, 9.41f)
                    lineTo(12.72f, 8.50f)
                    lineTo(12.11f, 8.49f)
                    close()
                    moveTo(3.33f, 7.70f)
                    lineTo(3.94f, 7.70f)
                    lineTo(3.94f, 8.61f)
                    lineTo(4.63f, 8.62f)
                    lineTo(4.63f, 9.52f)
                    lineTo(5.24f, 9.52f)
                    lineTo(5.24f, 8.60f)
                    lineTo(4.63f, 8.60f)
                    lineTo(4.63f, 7.70f)
                    lineTo(5.25f, 7.70f)
                    lineTo(5.25f, 8.61f)
                    lineTo(5.83f, 8.61f)
                    lineTo(5.83f, 9.54f)
                    lineTo(6.43f, 9.54f)
                    lineTo(6.43f, 10.40f)
                    lineTo(5.83f, 10.40f)
                    lineTo(5.83f, 9.54f)
                    lineTo(5.25f, 9.54f)
                    lineTo(5.25f, 10.37f)
                    lineTo(4.63f, 10.37f)
                    lineTo(4.63f, 9.54f)
                    lineTo(3.94f, 9.53f)
                    lineTo(3.94f, 8.62f)
                    lineTo(3.33f, 8.61f)
                    close()
                }
            }
        return customHashTagIconsCashu!!
    }

private var customHashTagIconsCashu: ImageVector? = null
