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
package com.vitorpamplona.amethyst.commons.icons.brand

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Full-colour brand marks for the payment rails shown in the wallet UI. Not
 * tintable: each mark keeps its official colours, so render with
 * `tint = Color.Unspecified`.
 *
 * Sources (official brand assets):
 *  - NWC: the logo Wisp ships (barrydeen/wisp-ios, MIT, NwcLogo.imageset) - a
 *    yellow-to-orange bolt mark with a violet accent.
 *  - Cashu: cashubtc/cashu-ui `ui/icons/svg/cashu.svg` (MIT) - the pixel-art
 *    cashew.
 *  - CLINK: the mark extracted from https://clinkme.dev/clink-logo.svg (the
 *    wordmark paths are dropped) - yellow + violet C.
 */
object PaymentBrandIcons {
    val Nwc: ImageVector by lazy { buildNwc() }
    val Cashu: ImageVector by lazy { buildCashu() }
    val Clink: ImageVector by lazy { buildClink() }

    /** The pixel cashew's body tan, for Cashu-branded strokes and tints. */
    val CashuAccent: Color = Color(0xFFB89563)
}

private fun buildNwc(): ImageVector =
    ImageVector
        .Builder(
            name = "NwcLogo",
            defaultWidth = 32.dp,
            defaultHeight = 32.dp,
            viewportWidth = 256f,
            viewportHeight = 256f,
        ).apply {
            addPath(
                pathData =
                    addPathNodes(
                        "M110.605 31.0639C100.371 20.8691 83.7516 20.9782 73.5543 31.2091L7.5804 97.4141C-2.61818 107.646 -2.48842 123.974 7.74253 134.17L115.913 242.34C126.146 252.534 142.733 252.449 152.93 242.218L185.082 210.066C175.705 219.443 167.989 212.701 158.845 203.595L140.911 185.662C127.297 191.051 111.385 188.374 100.355 177.365L86.6891 163.699C86.2293 163.243 85.8745 162.767 85.6252 162.17C85.3759 161.572 85.2473 160.931 85.2467 160.284C85.2462 159.637 85.3737 158.995 85.622 158.398C85.8702 157.8 86.2593 157.293 86.7183 156.837L94.4518 149.103L77.6167 132.268C74.9814 129.638 74.5511 125.391 76.9077 122.522C79.6015 119.228 84.4857 119.053 87.4411 122.002L104.504 139.051L116.061 127.494L99.1857 110.661C96.5492 108.03 96.1201 103.784 98.4968 100.895C99.1272 100.128 99.9112 99.5006 100.798 99.0542C101.686 98.6077 102.656 98.3518 103.648 98.3028C104.64 98.2538 105.631 98.4129 106.558 98.7697C107.485 99.1266 108.327 99.6733 109.03 100.375L126.162 117.393L133.422 110.132C133.878 109.673 134.327 109.259 134.925 109.01C135.522 108.761 136.163 108.632 136.811 108.632C137.458 108.631 138.099 108.758 138.697 109.006C139.295 109.254 139.838 109.618 140.295 110.077L153.983 123.738C164.875 134.609 167.723 150.431 162.631 163.943L180.568 181.88C189.712 190.985 197.363 197.785 206.741 188.408L247.312 147.836C237.56 157.588 229.548 150.075 219.911 140.446L110.605 31.0639Z",
                    ),
                fill =
                    Brush.linearGradient(
                        colorStops = arrayOf(0f to Color(0xFFFFCA4A), 1f to Color(0xFFF7931A)),
                        start = Offset(123.656f, 10.4384f),
                        end = Offset(123.656f, 249.939f),
                    ),
            )
            addPath(
                pathData =
                    addPathNodes(
                        "M187.308 13.0273L152.82 47.4873L229.448 124.116C236.783 131.419 243.158 137.239 250.232 134.417C254.321 132.787 257.128 128.351 255.561 124.238C218.894 28.0253 218.879 28.0238 214.015 17.507C209.151 6.99014 195.471 4.76016 187.308 13.0273Z",
                    ),
                fill = SolidColor(Color(0xFF897FFF)),
            )
        }.build()

private fun buildCashu(): ImageVector =
    ImageVector
        .Builder(
            name = "CashuLogo",
            defaultWidth = 30.dp,
            defaultHeight = 34.dp,
            viewportWidth = 135f,
            viewportHeight = 153f,
        ).apply {
            addPath(
                pathData =
                    addPathNodes(
                        "m 18 0 v 9 h 18 V 8 0 h -17 z m 0 9 H 17 9 v 9 h 9 V 17 10 Z M 9 18 H 8 0 v 27 H 1 9 V 44 19 Z M 0 53 v 55 h 9 v -47 H 8 6 V 60 57 H 5 3 V 56 53 H 2 1 Z m 9 55 v 18 h 9 v -18 h -8 z m 9 18 v 9 h 9 v -9 h -8 z m 9 9 v 9 h 9 v -9 h -8 z m 9 9 v 9 h 81 v -9 h -80 z m 81 0 h 9 v -9 h -9 v 8 z",
                    ),
                fill = SolidColor(PaymentBrandIcons.CashuAccent),
            )
            addPath(
                pathData =
                    addPathNodes(
                        "m 36 0 v 9 h 45 V 8 0 h -44 z m 45 9 v 27 h 9 V 35 9 h -8 z m 0 27 h -9 v 9 h 9 V 44 37 Z M 63 64 v 8 h 9 v -8 h -8 z m 9 8 v 18 h 9 v -18 h -8 z m 9 18 v 9 h 36 v -9 h -35 z m 36 9 v 9 h 9 v -9 h -8 z m 9 9 v 27 h 9 v -27 h -8 z",
                    ),
                fill = SolidColor(Color(0xFFE2D2B3)),
            )
            addPath(
                pathData =
                    addPathNodes(
                        "m 18 9 v 9 H 17 9 v 27 h 18 v -27 h 9 V 17 9 H 35 19 Z M 9 61 v 47 h 9 v 18 h 9 v 9 h 9 v 9 h 81 v -9 h 9 v -9 h -54 v -9 h -9 v -9 h -9 v -9 h -18 v -18 h -9 v -17 h -17 v -3 z",
                    ),
                fill = SolidColor(Color(0xFFC5A77F)),
            )
            addPath(
                pathData =
                    addPathNodes(
                        "m 36 9 v 9 h -9 v 27 h 45 v -9 h 9 V 35 9 h -44 z m 9 40 v 6 h -2 v 3 h -3 v 3 h -4 v 3 h -9 v 17 h 9 v 18 h 18 v 9 h 9 v 9 h 9 v 9 h 54 v -18 h -27 v -9 h -18 v -9 h -9 v -18 h -9 v -8 h -5 v -3 h -3 v -4 h -4 v -8 h -5 z",
                    ),
                fill = SolidColor(Color(0xFFDBBF98)),
            )
            addPath(
                pathData =
                    addPathNodes(
                        "m 0 45 v 8 h 3 v 4 h 3 v 4 h 4 v 3 h 26 v -3 h 4 v -3 h 3 v -3 h 2 v -6 h 6 v 8 h 4 v 4 h 3 v 3 h 26 v -3 h 4 v -4 h 4 v -4 h 4 V 52 45 H 95 1 Z m 6 4 h 4 v 4 h 4 v -4 h 4 v 4 h 4 v 4 h 4 v 4 h -4 v -4 h -4 v 4 H 17 14 V 60 57 H 13 10 V 56 53 H 9 6 v -3 z m 8 8 h 4 v -4 h -4 v 3 z m 44 -8 h 4 v 4 h 4 v -4 h 4 v 4 h 4 v 4 h 4 v 4 h -4 v -4 h -4 v 4 h -4 v -4 h -4 v -4 h -4 v -3 z m 8 8 h 4 v -4 h -4 v 3 z",
                    ),
                fill = SolidColor(Color(0xFF000000)),
            )
            addPath(
                pathData =
                    addPathNodes(
                        "m 6 49 v 4 h 4 V 52 49 H 9 7 Z m 4 4 v 4 h 4 v -4 h -3 z m 4 0 h 4 v -4 h -4 v 3 z m 4 0 v 4 h 4 v -4 h -3 z m 4 4 v 4 h 4 v -4 h -3 z m -4 0 h -4 v 4 h 4 v -3 z m 40 -8 v 4 h 4 v -4 h -3 z m 4 4 v 4 h 4 v -4 h -3 z m 4 0 h 4 v -4 h -4 v 3 z m 4 0 v 4 h 4 v -4 h -3 z m 4 4 v 4 h 4 v -4 h -3 z m -4 0 h -4 v 4 h 4 v -3 z",
                    ),
                fill = SolidColor(Color(0xFFFFFFFF)),
            )
            addPath(
                pathData =
                    addPathNodes(
                        "m 99 99 v 9 h 18 v -9 h -17 z",
                    ),
                fill = SolidColor(Color(0xFFF7F8F3)),
            )
        }.build()

private fun buildClink(): ImageVector =
    ImageVector
        .Builder(
            name = "ClinkMark",
            defaultWidth = 28.dp,
            defaultHeight = 40.dp,
            viewportWidth = 69.9f,
            viewportHeight = 100f,
        ).apply {
            addPath(
                pathData =
                    addPathNodes(
                        "M7.94434 55.9229L26.2432 52.9229L13.252 76.1387C5.70852 69.9025 0.900462 60.4747 0.900391 49.9229C0.900391 31.1452 16.1227 15.9229 34.9004 15.9229C45.4646 15.9229 57.7758 9.96767 68.8604 0L7.94434 55.9229Z",
                    ),
                fill = SolidColor(Color(0xFFF7FF00)),
            )
            addPath(
                pathData =
                    addPathNodes(
                        "M55.7607 23.0732C63.7563 29.2942 68.9004 39.0076 68.9004 49.9229C68.9003 68.7004 53.678 83.9229 34.9004 83.9229C23.7075 83.9229 10.95 89.8965 0 99.8965C0 99.8965 0.0182273 99.8689 0.0556641 99.8174L59.9443 43.9229L42.1797 46.9229L55.7607 23.0732Z",
                    ),
                fill = SolidColor(Color(0xFF9B63FF)),
            )
        }.build()
