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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * The app's wallet mark: a filled billfold with a clasp tab, standing for every
 * payment rail the wallet screen holds (on-chain, lightning, cashu) rather than
 * any single one. Carried at the original 512 grid so the theme tint supplies
 * the color; the weight comes from the fill, standing beside the weight-300
 * Material Symbols in the navigation.
 *
 * Source: SVG Repo "wallet" (https://www.svgrepo.com/svg/197743/wallet), CC0.
 */
private val WALLET_BODY =
    "M486.4,128h-51.2V25.6c0-14.14-11.46-25.6-25.6-25.6H76.8C34.449,0,0,34.449,0,76.8v358.4C0,477.619,34.381,512,76.8,512h409.6c14.14,0,25.6-11.46,25.6-25.6V153.6C512,139.46,500.54,128,486.4,128z M76.8,25.6h332.8V128H76.8c-28.279,0-51.2-22.921-51.2-51.2S48.521,25.6,76.8,25.6z M486.4,486.4H76.8c-28.228,0-51.2-22.972-51.2-51.2V133.623C39.219,145.903,57.062,153.6,76.8,153.6h409.6V486.4z"

private val WALLET_CLASP =
    "M409.6,268.8c-28.279,0-51.2,22.921-51.2,51.2c0,28.279,22.921,51.2,51.2,51.2c28.279,0,51.2-22.921,51.2-51.2C460.8,291.721,437.879,268.8,409.6,268.8z M409.6,345.6c-14.114,0-25.6-11.486-25.6-25.6s11.486-25.6,25.6-25.6s25.6,11.486,25.6,25.6S423.714,345.6,409.6,345.6z"

val WalletIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "Wallet",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 512f,
            viewportHeight = 512f,
        ).apply {
            // The source art is full-bleed on its 512 grid, ~1.2dp walls.
            // Shrunk to the ~20dp optical box the sibling glyphs occupy, the
            // fill walls alone fall to ~1.0dp; the stroke lifts the weight to
            // match the row.
            group(
                scaleX = 0.8333f,
                scaleY = 0.8333f,
                pivotX = 256f,
                pivotY = 256f,
            ) {
                listOf(WALLET_BODY, WALLET_CLASP).forEach { data ->
                    addPath(
                        pathData = addPathNodes(data),
                        fill = SolidColor(Color.Black),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 12f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
        }.build()
}
