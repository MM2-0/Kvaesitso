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
package com.vitorpamplona.amethyst.commons.nip64Chess.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * CBurnett chess piece vectors from Lichess
 * License: GPLv2+
 * Original author: Colin M.L. Burnett (Wikimedia user Cburnett)
 */
object ChessPieceVectors {
    val WhiteKing: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "WhiteKing",
                defaultWidth = 45.dp,
                defaultHeight = 45.dp,
                viewportWidth = 45f,
                viewportHeight = 45f,
            ).apply {
                // Cross on top
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Miter,
                ) {
                    moveTo(22.5f, 11.63f)
                    verticalLineTo(6f)
                    moveTo(20f, 8f)
                    horizontalLineTo(25f)
                }
                // Head/crown
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                ) {
                    moveTo(22.5f, 25f)
                    reflectiveCurveTo(27f, 17.5f, 25.5f, 14.5f)
                    curveTo(25.5f, 14.5f, 24.5f, 12f, 22.5f, 12f)
                    reflectiveCurveTo(19.5f, 14.5f, 19.5f, 14.5f)
                    curveTo(18f, 17.5f, 22.5f, 25f, 22.5f, 25f)
                }
                // Body
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(11.5f, 37f)
                    curveTo(17f, 40.5f, 27f, 40.5f, 32.5f, 37f)
                    verticalLineTo(30f)
                    reflectiveCurveTo(41.5f, 25.5f, 38.5f, 19.5f)
                    curveTo(34.5f, 13f, 25f, 16f, 22.5f, 23.5f)
                    verticalLineTo(27f)
                    verticalLineTo(23.5f)
                    curveTo(19f, 16f, 9.5f, 13f, 5.5f, 19.5f)
                    curveTo(2.5f, 25.5f, 10.5f, 29.5f, 10.5f, 29.5f)
                    verticalLineTo(37f)
                    close()
                }
                // Horizontal lines
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(11.5f, 30f)
                    curveTo(17f, 27f, 27f, 27f, 32.5f, 30f)
                    moveTo(11.5f, 33.5f)
                    curveTo(17f, 30.5f, 27f, 30.5f, 32.5f, 33.5f)
                    moveTo(11.5f, 37f)
                    curveTo(17f, 34f, 27f, 34f, 32.5f, 37f)
                }
            }.build()
    }

    val BlackKing: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "BlackKing",
                defaultWidth = 45.dp,
                defaultHeight = 45.dp,
                viewportWidth = 45f,
                viewportHeight = 45f,
            ).apply {
                // Cross on top
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Miter,
                ) {
                    moveTo(22.5f, 11.6f)
                    verticalLineTo(6f)
                    moveTo(20f, 8f)
                    horizontalLineTo(25f)
                }
                // Head/crown
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                ) {
                    moveTo(22.5f, 25f)
                    reflectiveCurveTo(27f, 17.5f, 25.5f, 14.5f)
                    curveTo(25.5f, 14.5f, 24.5f, 12f, 22.5f, 12f)
                    reflectiveCurveTo(19.5f, 14.5f, 19.5f, 14.5f)
                    curveTo(18f, 17.5f, 22.5f, 25f, 22.5f, 25f)
                }
                // Body
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(11.5f, 37f)
                    curveTo(17f, 40.5f, 27f, 40.5f, 32.5f, 37f)
                    verticalLineTo(30f)
                    reflectiveCurveTo(41.5f, 25.5f, 38.5f, 19.5f)
                    curveTo(34.5f, 13f, 25f, 16f, 22.5f, 23.5f)
                    verticalLineTo(27f)
                    verticalLineTo(23.5f)
                    curveTo(19f, 16f, 9.5f, 13f, 5.5f, 19.5f)
                    curveTo(2.5f, 25.5f, 10.5f, 29.5f, 10.5f, 29.5f)
                    verticalLineTo(37f)
                    close()
                }
                // Inner highlight lines
                path(
                    fill = null,
                    stroke = SolidColor(Color(0xFFECECEC)),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(32f, 29.5f)
                    reflectiveCurveTo(40.5f, 25.5f, 38f, 19.8f)
                    curveTo(34.1f, 14f, 25f, 18f, 22.5f, 24.6f)
                    verticalLineTo(26.7f)
                    verticalLineTo(24.6f)
                    curveTo(20f, 18f, 9.9f, 14f, 7f, 19.9f)
                    curveTo(4.5f, 25.5f, 11.8f, 28.9f, 11.8f, 28.9f)
                }
                path(
                    fill = null,
                    stroke = SolidColor(Color(0xFFECECEC)),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(11.5f, 30f)
                    curveTo(17f, 27f, 27f, 27f, 32.5f, 30f)
                    moveTo(11.5f, 33.5f)
                    curveTo(17f, 30.5f, 27f, 30.5f, 32.5f, 33.5f)
                    moveTo(11.5f, 37f)
                    curveTo(17f, 34f, 27f, 34f, 32.5f, 37f)
                }
            }.build()
    }

    val WhiteQueen: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "WhiteQueen",
                defaultWidth = 45.dp,
                defaultHeight = 45.dp,
                viewportWidth = 45f,
                viewportHeight = 45f,
            ).apply {
                // Crown circles
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    // Left circle
                    moveTo(8f, 12f)
                    arcTo(2f, 2f, 0f, true, true, 4f, 12f)
                    arcTo(2f, 2f, 0f, true, true, 8f, 12f)
                    close()
                    // Center-left circle
                    moveTo(16f, 8.5f)
                    arcTo(2f, 2f, 0f, true, true, 12f, 8.5f)
                    arcTo(2f, 2f, 0f, true, true, 16f, 8.5f)
                    close()
                    // Center circle
                    moveTo(24.5f, 7.5f)
                    arcTo(2f, 2f, 0f, true, true, 20.5f, 7.5f)
                    arcTo(2f, 2f, 0f, true, true, 24.5f, 7.5f)
                    close()
                    // Center-right circle
                    moveTo(33f, 9f)
                    arcTo(2f, 2f, 0f, true, true, 29f, 9f)
                    arcTo(2f, 2f, 0f, true, true, 33f, 9f)
                    close()
                    // Right circle
                    moveTo(41f, 12f)
                    arcTo(2f, 2f, 0f, true, true, 37f, 12f)
                    arcTo(2f, 2f, 0f, true, true, 41f, 12f)
                    close()
                }
                // Crown body
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(9f, 26f)
                    curveTo(17.5f, 24.5f, 30f, 24.5f, 36f, 26f)
                    lineTo(38f, 14f)
                    lineTo(31f, 25f)
                    verticalLineTo(11f)
                    lineTo(25.5f, 24.5f)
                    lineTo(22.5f, 9.5f)
                    lineTo(19.5f, 24.5f)
                    lineTo(14f, 11f)
                    verticalLineTo(25f)
                    lineTo(7f, 14f)
                    lineTo(9f, 26f)
                    close()
                }
                // Lower body
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(9f, 26f)
                    curveTo(9f, 28f, 10.5f, 28f, 11.5f, 30f)
                    curveTo(12.5f, 31.5f, 12.5f, 31f, 12f, 33.5f)
                    curveTo(10.5f, 34.5f, 10.5f, 36f, 10.5f, 36f)
                    curveTo(9f, 37.5f, 11f, 38.5f, 11f, 38.5f)
                    curveTo(17.5f, 39.5f, 27.5f, 39.5f, 34f, 38.5f)
                    curveTo(34f, 38.5f, 35.5f, 37.5f, 34f, 36f)
                    curveTo(34f, 36f, 34.5f, 34.5f, 33f, 33.5f)
                    curveTo(32.5f, 31f, 32.5f, 31.5f, 33.5f, 30f)
                    curveTo(34.5f, 28f, 36f, 28f, 36f, 26f)
                    curveTo(27.5f, 24.5f, 17.5f, 24.5f, 9f, 26f)
                    close()
                }
                // Inner lines
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(11.5f, 30f)
                    curveTo(15f, 29f, 30f, 29f, 33.5f, 30f)
                    moveTo(12f, 33.5f)
                    curveTo(18f, 32.5f, 27f, 32.5f, 33f, 33.5f)
                }
            }.build()
    }

    val BlackQueen: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "BlackQueen",
                defaultWidth = 45.dp,
                defaultHeight = 45.dp,
                viewportWidth = 45f,
                viewportHeight = 45f,
            ).apply {
                // Crown circles (no stroke for fill)
                path(
                    fill = SolidColor(Color.Black),
                    stroke = null,
                ) {
                    // Circles at the top, r = 2.75 around (6,12) (14,9) (22.5,8) (31,9) (39,12):
                    // each drawn as two half arcs from its left edge, so the centre is where it says.
                    moveTo(3.25f, 12f)
                    arcTo(2.75f, 2.75f, 0f, false, true, 8.75f, 12f)
                    arcTo(2.75f, 2.75f, 0f, false, true, 3.25f, 12f)
                    close()
                    moveTo(11.25f, 9f)
                    arcTo(2.75f, 2.75f, 0f, false, true, 16.75f, 9f)
                    arcTo(2.75f, 2.75f, 0f, false, true, 11.25f, 9f)
                    close()
                    moveTo(19.75f, 8f)
                    arcTo(2.75f, 2.75f, 0f, false, true, 25.25f, 8f)
                    arcTo(2.75f, 2.75f, 0f, false, true, 19.75f, 8f)
                    close()
                    moveTo(28.25f, 9f)
                    arcTo(2.75f, 2.75f, 0f, false, true, 33.75f, 9f)
                    arcTo(2.75f, 2.75f, 0f, false, true, 28.25f, 9f)
                    close()
                    moveTo(36.25f, 12f)
                    arcTo(2.75f, 2.75f, 0f, false, true, 41.75f, 12f)
                    arcTo(2.75f, 2.75f, 0f, false, true, 36.25f, 12f)
                    close()
                }
                // Crown body
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(9f, 26f)
                    curveTo(17.5f, 24.5f, 30f, 24.5f, 36f, 26f)
                    lineTo(38.5f, 13.5f)
                    lineTo(31f, 25f)
                    lineTo(30.7f, 10.9f)
                    lineTo(25.5f, 24.5f)
                    lineTo(22.5f, 10f)
                    lineTo(19.5f, 24.5f)
                    lineTo(14.3f, 10.9f)
                    lineTo(14f, 25f)
                    lineTo(6.5f, 13.5f)
                    lineTo(9f, 26f)
                    close()
                }
                // Lower body
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(9f, 26f)
                    curveTo(9f, 28f, 10.5f, 28f, 11.5f, 30f)
                    curveTo(12.5f, 31.5f, 12.5f, 31f, 12f, 33.5f)
                    curveTo(10.5f, 34.5f, 10.5f, 36f, 10.5f, 36f)
                    curveTo(9f, 37.5f, 11f, 38.5f, 11f, 38.5f)
                    curveTo(17.5f, 39.5f, 27.5f, 39.5f, 34f, 38.5f)
                    curveTo(34f, 38.5f, 35.5f, 37.5f, 34f, 36f)
                    curveTo(34f, 36f, 34.5f, 34.5f, 33f, 33.5f)
                    curveTo(32.5f, 31f, 32.5f, 31.5f, 33.5f, 30f)
                    curveTo(34.5f, 28f, 36f, 28f, 36f, 26f)
                    curveTo(27.5f, 24.5f, 17.5f, 24.5f, 9f, 26f)
                    close()
                }
                // Bottom line
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(11f, 38.5f)
                    arcTo(35f, 35f, 0f, false, false, 34f, 38.5f)
                }
                // Inner highlight lines
                path(
                    fill = null,
                    stroke = SolidColor(Color(0xFFECECEC)),
                    strokeLineWidth = 1f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(11f, 29f)
                    arcTo(35f, 35f, 0f, false, true, 34f, 29f)
                    moveTo(12.5f, 31.5f)
                    horizontalLineTo(32.5f)
                    moveTo(11.5f, 34.5f)
                    arcTo(35f, 35f, 0f, false, false, 33.5f, 34.5f)
                    moveTo(10.5f, 37.5f)
                    arcTo(35f, 35f, 0f, false, false, 34.5f, 37.5f)
                }
            }.build()
    }

    val WhiteRook: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "WhiteRook",
                defaultWidth = 45.dp,
                defaultHeight = 45.dp,
                viewportWidth = 45f,
                viewportHeight = 45f,
            ).apply {
                // Base
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(9f, 39f)
                    horizontalLineTo(36f)
                    verticalLineTo(36f)
                    horizontalLineTo(9f)
                    verticalLineTo(39f)
                    close()
                }
                // Lower platform
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(12f, 36f)
                    verticalLineTo(32f)
                    horizontalLineTo(33f)
                    verticalLineTo(36f)
                    horizontalLineTo(12f)
                    close()
                }
                // Top battlements
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(11f, 14f)
                    verticalLineTo(9f)
                    horizontalLineTo(15f)
                    verticalLineTo(11f)
                    horizontalLineTo(20f)
                    verticalLineTo(9f)
                    horizontalLineTo(25f)
                    verticalLineTo(11f)
                    horizontalLineTo(30f)
                    verticalLineTo(9f)
                    horizontalLineTo(34f)
                    verticalLineTo(14f)
                }
                // Top slope
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(34f, 14f)
                    lineTo(31f, 17f)
                    horizontalLineTo(14f)
                    lineTo(11f, 14f)
                }
                // Body
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                ) {
                    moveTo(31f, 17f)
                    verticalLineTo(29.5f)
                    horizontalLineTo(14f)
                    verticalLineTo(17f)
                }
                // Bottom slope
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(31f, 29.5f)
                    lineTo(32.5f, 32f)
                    horizontalLineTo(12.5f)
                    lineTo(14f, 29.5f)
                }
                // Top line
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Miter,
                ) {
                    moveTo(11f, 14f)
                    horizontalLineTo(34f)
                }
            }.build()
    }

    val BlackRook: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "BlackRook",
                defaultWidth = 45.dp,
                defaultHeight = 45.dp,
                viewportWidth = 45f,
                viewportHeight = 45f,
            ).apply {
                // Base
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(9f, 39f)
                    horizontalLineTo(36f)
                    verticalLineTo(36f)
                    horizontalLineTo(9f)
                    verticalLineTo(39f)
                    close()
                }
                // Lower slope
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(12.5f, 32f)
                    lineTo(14f, 29.5f)
                    horizontalLineTo(31f)
                    lineTo(32.5f, 32f)
                    horizontalLineTo(12.5f)
                    close()
                }
                // Lower platform
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(12f, 36f)
                    verticalLineTo(32f)
                    horizontalLineTo(33f)
                    verticalLineTo(36f)
                    horizontalLineTo(12f)
                    close()
                }
                // Body
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                ) {
                    moveTo(14f, 29.5f)
                    verticalLineTo(16.5f)
                    horizontalLineTo(31f)
                    verticalLineTo(29.5f)
                    horizontalLineTo(14f)
                    close()
                }
                // Top slope
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(14f, 16.5f)
                    lineTo(11f, 14f)
                    horizontalLineTo(34f)
                    lineTo(31f, 16.5f)
                    horizontalLineTo(14f)
                    close()
                }
                // Top battlements
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(11f, 14f)
                    verticalLineTo(9f)
                    horizontalLineTo(15f)
                    verticalLineTo(11f)
                    horizontalLineTo(20f)
                    verticalLineTo(9f)
                    horizontalLineTo(25f)
                    verticalLineTo(11f)
                    horizontalLineTo(30f)
                    verticalLineTo(9f)
                    horizontalLineTo(34f)
                    verticalLineTo(14f)
                    horizontalLineTo(11f)
                    close()
                }
                // Inner highlight lines
                path(
                    fill = null,
                    stroke = SolidColor(Color(0xFFECECEC)),
                    strokeLineWidth = 1f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Miter,
                ) {
                    moveTo(12f, 35.5f)
                    horizontalLineTo(33f)
                    moveTo(13f, 31.5f)
                    horizontalLineTo(32f)
                    moveTo(14f, 29.5f)
                    horizontalLineTo(31f)
                    moveTo(14f, 16.5f)
                    horizontalLineTo(31f)
                    moveTo(11f, 14f)
                    horizontalLineTo(34f)
                }
            }.build()
    }

    val WhiteBishop: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "WhiteBishop",
                defaultWidth = 45.dp,
                defaultHeight = 45.dp,
                viewportWidth = 45f,
                viewportHeight = 45f,
            ).apply {
                // Base
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(9f, 36f)
                    curveTo(12.39f, 35.03f, 19.11f, 36.43f, 22.5f, 34f)
                    curveTo(25.89f, 36.43f, 32.61f, 35.03f, 36f, 36f)
                    curveTo(36f, 36f, 37.65f, 36.54f, 39f, 38f)
                    curveTo(38.32f, 38.97f, 37.35f, 38.99f, 36f, 38.5f)
                    curveTo(32.61f, 37.53f, 25.89f, 38.96f, 22.5f, 37.5f)
                    curveTo(19.11f, 38.96f, 12.39f, 37.53f, 9f, 38.5f)
                    curveTo(7.65f, 38.99f, 6.68f, 38.97f, 6f, 38f)
                    curveTo(7.35f, 36.06f, 9f, 36f, 9f, 36f)
                    close()
                }
                // Body
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(15f, 32f)
                    curveTo(17.5f, 34.5f, 27.5f, 34.5f, 30f, 32f)
                    curveTo(30.5f, 30.5f, 30f, 30f, 30f, 30f)
                    curveTo(30f, 27.5f, 27.5f, 26f, 27.5f, 26f)
                    curveTo(33f, 24.5f, 33.5f, 14.5f, 22.5f, 10.5f)
                    curveTo(11.5f, 14.5f, 12f, 24.5f, 17.5f, 26f)
                    curveTo(17.5f, 26f, 15f, 27.5f, 15f, 30f)
                    curveTo(15f, 30f, 14.5f, 30.5f, 15f, 32f)
                    close()
                }
                // Head circle
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(25f, 8f)
                    arcTo(2.5f, 2.5f, 0f, true, true, 20f, 8f)
                    arcTo(2.5f, 2.5f, 0f, true, true, 25f, 8f)
                    close()
                }
                // Inner lines
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Miter,
                ) {
                    moveTo(17.5f, 26f)
                    horizontalLineTo(27.5f)
                    moveTo(15f, 30f)
                    horizontalLineTo(30f)
                    moveTo(22.5f, 15.5f)
                    verticalLineTo(20.5f)
                    moveTo(20f, 18f)
                    horizontalLineTo(25f)
                }
            }.build()
    }

    val BlackBishop: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "BlackBishop",
                defaultWidth = 45.dp,
                defaultHeight = 45.dp,
                viewportWidth = 45f,
                viewportHeight = 45f,
            ).apply {
                // Base
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(9f, 36f)
                    curveTo(12.4f, 35f, 19.1f, 36.4f, 22.5f, 34f)
                    curveTo(25.9f, 36.4f, 32.6f, 35f, 36f, 36f)
                    curveTo(36f, 36f, 37.6f, 36.5f, 39f, 38f)
                    curveTo(38.3f, 39f, 37.4f, 39f, 36f, 38.5f)
                    curveTo(32.6f, 37.5f, 25.9f, 39f, 22.5f, 37.5f)
                    curveTo(19.1f, 39f, 12.4f, 37.5f, 9f, 38.5f)
                    curveTo(7.6f, 39f, 6.7f, 39f, 6f, 38f)
                    curveTo(7.4f, 36f, 9f, 36f, 9f, 36f)
                    close()
                }
                // Body
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(15f, 32f)
                    curveTo(17.5f, 34.5f, 27.5f, 34.5f, 30f, 32f)
                    curveTo(30.5f, 30.5f, 30f, 30f, 30f, 30f)
                    curveTo(30f, 27.5f, 27.5f, 26f, 27.5f, 26f)
                    curveTo(33f, 24.5f, 33.5f, 14.5f, 22.5f, 10.5f)
                    curveTo(11.5f, 14.5f, 12f, 24.5f, 17.5f, 26f)
                    curveTo(17.5f, 26f, 15f, 27.5f, 15f, 30f)
                    curveTo(15f, 30f, 14.5f, 30.5f, 15f, 32f)
                    close()
                }
                // Head circle
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(25f, 8f)
                    arcTo(2.5f, 2.5f, 0f, true, true, 20f, 8f)
                    arcTo(2.5f, 2.5f, 0f, true, true, 25f, 8f)
                    close()
                }
                // Inner highlight lines
                path(
                    fill = null,
                    stroke = SolidColor(Color(0xFFECECEC)),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Miter,
                ) {
                    moveTo(17.5f, 26f)
                    horizontalLineTo(27.5f)
                    moveTo(15f, 30f)
                    horizontalLineTo(30f)
                    moveTo(22.5f, 15.5f)
                    verticalLineTo(20.5f)
                    moveTo(20f, 18f)
                    horizontalLineTo(25f)
                }
            }.build()
    }

    val WhiteKnight: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "WhiteKnight",
                defaultWidth = 45.dp,
                defaultHeight = 45.dp,
                viewportWidth = 45f,
                viewportHeight = 45f,
            ).apply {
                // Body
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(22f, 10f)
                    curveTo(32.5f, 11f, 38.5f, 18f, 38f, 39f)
                    horizontalLineTo(15f)
                    curveTo(15f, 30f, 25f, 32.5f, 23f, 18f)
                }
                // Head/mane
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(24f, 18f)
                    curveTo(24.38f, 20.91f, 18.45f, 25.37f, 16f, 27f)
                    curveTo(13f, 29f, 13.18f, 31.34f, 11f, 31f)
                    curveTo(9.958f, 30.06f, 12.41f, 27.96f, 11f, 28f)
                    curveTo(10f, 28f, 11.19f, 29.23f, 10f, 30f)
                    curveTo(9f, 30f, 5.997f, 31f, 6f, 26f)
                    curveTo(6f, 24f, 12f, 14f, 12f, 14f)
                    reflectiveCurveTo(13.89f, 12.1f, 14f, 10.5f)
                    curveTo(13.27f, 9.506f, 13.5f, 8.5f, 13.5f, 7.5f)
                    curveTo(14.5f, 6.5f, 16.5f, 10f, 16.5f, 10f)
                    horizontalLineTo(18.5f)
                    reflectiveCurveTo(19.28f, 8.008f, 21f, 7f)
                    curveTo(22f, 7f, 22f, 10f, 22f, 10f)
                }
                // Eye and nostril
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    // Eye
                    moveTo(9.5f, 25.5f)
                    arcTo(0.5f, 0.5f, 0f, true, true, 8.5f, 25.5f)
                    arcTo(0.5f, 0.5f, 0f, true, true, 9.5f, 25.5f)
                    close()
                    // Nostril
                    moveTo(14.933f, 15.75f)
                    arcTo(0.5f, 1.5f, 30f, true, true, 14.067f, 15.25f)
                    arcTo(0.5f, 1.5f, 30f, true, true, 14.933f, 15.75f)
                    close()
                }
            }.build()
    }

    val BlackKnight: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "BlackKnight",
                defaultWidth = 45.dp,
                defaultHeight = 45.dp,
                viewportWidth = 45f,
                viewportHeight = 45f,
            ).apply {
                // Body
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(22f, 10f)
                    curveTo(32.5f, 11f, 38.5f, 18f, 38f, 39f)
                    horizontalLineTo(15f)
                    curveTo(15f, 30f, 25f, 32.5f, 23f, 18f)
                }
                // Head/mane
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(24f, 18f)
                    curveTo(24.38f, 20.91f, 18.45f, 25.37f, 16f, 27f)
                    curveTo(13f, 29f, 13.18f, 31.34f, 11f, 31f)
                    curveTo(9.96f, 30.06f, 12.41f, 27.96f, 11f, 28f)
                    curveTo(10f, 28f, 11.19f, 29.23f, 10f, 30f)
                    curveTo(9f, 30f, 6f, 31f, 6f, 26f)
                    curveTo(6f, 24f, 12f, 14f, 12f, 14f)
                    reflectiveCurveTo(13.89f, 12.1f, 14f, 10.5f)
                    curveTo(13.27f, 9.5f, 13.5f, 8.5f, 13.5f, 7.5f)
                    curveTo(14.5f, 6.5f, 16.5f, 10f, 16.5f, 10f)
                    horizontalLineTo(18.5f)
                    reflectiveCurveTo(19.28f, 8f, 21f, 7f)
                    curveTo(22f, 7f, 22f, 10f, 22f, 10f)
                }
                // Eye and nostril (highlights)
                path(
                    fill = SolidColor(Color(0xFFECECEC)),
                    stroke = SolidColor(Color(0xFFECECEC)),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    // Eye
                    moveTo(9.5f, 25.5f)
                    arcTo(0.5f, 0.5f, 0f, true, true, 8.5f, 25.5f)
                    arcTo(0.5f, 0.5f, 0f, true, true, 9.5f, 25.5f)
                    close()
                    // Nostril
                    moveTo(14.93f, 15.75f)
                    arcTo(0.5f, 1.5f, 30f, true, true, 14.07f, 15.25f)
                    arcTo(0.5f, 1.5f, 30f, true, true, 14.93f, 15.75f)
                    close()
                }
                // Highlight on body edge
                path(
                    fill = SolidColor(Color(0xFFECECEC)),
                    stroke = null,
                ) {
                    moveTo(24.55f, 10.4f)
                    lineTo(24.1f, 11.85f)
                    lineTo(24.6f, 12f)
                    curveTo(27.75f, 13f, 30.25f, 14.49f, 32.5f, 18.75f)
                    reflectiveCurveTo(35.75f, 29.06f, 35.25f, 39f)
                    lineTo(35.2f, 39.5f)
                    horizontalLineTo(37.45f)
                    lineTo(37.5f, 39f)
                    curveTo(38f, 28.94f, 36.62f, 22.15f, 34.25f, 17.66f)
                    curveTo(31.88f, 13.17f, 28.46f, 11.02f, 25.06f, 10.5f)
                    lineTo(24.55f, 10.4f)
                    close()
                }
            }.build()
    }

    val WhitePawn: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "WhitePawn",
                defaultWidth = 45.dp,
                defaultHeight = 45.dp,
                viewportWidth = 45f,
                viewportHeight = 45f,
            ).apply {
                path(
                    fill = SolidColor(Color.White),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(22.5f, 9f)
                    curveTo(20.29f, 9f, 18.5f, 10.79f, 18.5f, 13f)
                    curveTo(18.5f, 13.89f, 18.79f, 14.71f, 19.28f, 15.38f)
                    curveTo(17.33f, 16.5f, 16f, 18.59f, 16f, 21f)
                    curveTo(16f, 23.03f, 16.94f, 24.84f, 18.41f, 26.03f)
                    curveTo(15.41f, 27.09f, 11f, 31.58f, 11f, 39.5f)
                    horizontalLineTo(34f)
                    curveTo(34f, 31.58f, 29.59f, 27.09f, 26.59f, 26.03f)
                    curveTo(28.06f, 24.84f, 29f, 23.03f, 29f, 21f)
                    curveTo(29f, 18.59f, 27.67f, 16.5f, 25.72f, 15.38f)
                    curveTo(26.21f, 14.71f, 26.5f, 13.89f, 26.5f, 13f)
                    curveTo(26.5f, 10.79f, 24.71f, 9f, 22.5f, 9f)
                    close()
                }
            }.build()
    }

    val BlackPawn: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "BlackPawn",
                defaultWidth = 45.dp,
                defaultHeight = 45.dp,
                viewportWidth = 45f,
                viewportHeight = 45f,
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(22.5f, 9f)
                    arcTo(4f, 4f, 0f, false, false, 19.28f, 15.38f)
                    arcTo(6.48f, 6.48f, 0f, false, false, 18.41f, 26.03f)
                    curveTo(15.41f, 27.09f, 11f, 31.58f, 11f, 39.5f)
                    horizontalLineTo(34f)
                    curveTo(34f, 31.58f, 29.59f, 27.09f, 26.59f, 26.03f)
                    arcTo(6.46f, 6.46f, 0f, false, false, 25.72f, 15.38f)
                    arcTo(4.01f, 4.01f, 0f, false, false, 22.5f, 9f)
                    close()
                }
            }.build()
    }
}
