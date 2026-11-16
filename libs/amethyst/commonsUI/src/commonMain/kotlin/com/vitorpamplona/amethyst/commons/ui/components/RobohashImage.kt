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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.icons.symbols.rememberMaterialSymbolPainter
import com.vitorpamplona.amethyst.commons.robohash.CachedRobohash
import com.vitorpamplona.amethyst.commons.ui.theme.isLight

/**
 * Displays a robohash image based on a seed string (typically a public key).
 * Falls back to a generic face icon if robohash is disabled.
 *
 * @param robot The seed string for generating the robohash (e.g., pubkey hex)
 * @param modifier Modifier for the image
 * @param contentDescription Accessibility description
 * @param loadRobohash Whether to load the robohash or show a placeholder
 */
@Composable
fun RobohashImage(
    robot: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    loadRobohash: Boolean = true,
) {
    if (loadRobohash) {
        Image(
            imageVector = CachedRobohash.get(robot, MaterialTheme.colorScheme.isLight),
            contentDescription = contentDescription,
            modifier = modifier,
        )
    } else {
        Image(
            painter = rememberMaterialSymbolPainter(MaterialSymbols.Face),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
            modifier = modifier,
        )
    }
}

/**
 * Displays a robohash image with configurable alignment and content scale.
 */
@Composable
fun RobohashImage(
    robot: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    colorFilter: ColorFilter? = null,
    loadRobohash: Boolean = true,
) {
    if (loadRobohash) {
        Image(
            painter = rememberVectorPainter(CachedRobohash.get(robot, MaterialTheme.colorScheme.isLight)),
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            colorFilter = colorFilter,
        )
    } else {
        Image(
            painter = rememberMaterialSymbolPainter(MaterialSymbols.Face),
            contentDescription = contentDescription,
            colorFilter = colorFilter ?: ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
        )
    }
}
