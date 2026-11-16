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
package com.vitorpamplona.amethyst.commons.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.patrykandpatrick.vico.compose.common.VicoTheme
import com.patrykandpatrick.vico.compose.common.VicoTheme.CandlestickCartesianLayerColors

val chartLightColors =
    VicoTheme(
        candlestickCartesianLayerColors =
            CandlestickCartesianLayerColors(
                Color(0xff0ac285),
                Color(0xff000000),
                Color(0xffe8304f),
            ),
        columnCartesianLayerColors = listOf(Color(0xff3287ff), Color(0xff0ac285), Color(0xffffab02)),
        lineColor = Color(0xffbcbfc2),
        textColor = Color(0xff000000),
    )

val chartDarkColors =
    VicoTheme(
        candlestickCartesianLayerColors =
            CandlestickCartesianLayerColors(
                Color(0xff0ac285),
                Color(0xffffffff),
                Color(0xffe8304f),
            ),
        columnCartesianLayerColors = listOf(Color(0xff3287ff), Color(0xff0ac285), Color(0xffffab02)),
        lineColor = Color(0xff494c50),
        textColor = Color(0xffffffff),
    )

val ColorScheme.chartStyle: VicoTheme
    get() = if (isLight) chartLightColors else chartDarkColors
