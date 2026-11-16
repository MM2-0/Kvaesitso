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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.icons.symbols.rememberMaterialSymbolPainter
import com.vitorpamplona.amethyst.commons.robohash.CachedRobohash
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.theme.onBackgroundColorFilter

// iOS has no animated-avatar pipeline yet, so autoPlayGif is unused.
@Composable
internal actual fun AvatarImage(
    userHex: String,
    pictureUrl: String?,
    contentDescription: String?,
    modifier: Modifier,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    autoPlayGif: Boolean,
    onError: (() -> Unit)?,
) {
    if (pictureUrl != null && loadProfilePicture) {
        val fallbackPainter =
            if (loadRobohash) {
                rememberVectorPainter(image = CachedRobohash.get(userHex, MaterialTheme.colorScheme.isLight))
            } else {
                rememberMaterialSymbolPainter(MaterialSymbols.Face)
            }

        AsyncImage(
            model = pictureUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            placeholder = fallbackPainter,
            fallback = fallbackPainter,
            error = fallbackPainter,
            contentScale = ContentScale.Crop,
            onError = onError?.let { onError -> { onError() } },
        )
    } else if (loadRobohash) {
        Image(
            imageVector = CachedRobohash.get(userHex, MaterialTheme.colorScheme.isLight),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Image(
            painter = rememberMaterialSymbolPainter(MaterialSymbols.Face),
            contentDescription = contentDescription,
            colorFilter = MaterialTheme.colorScheme.onBackgroundColorFilter,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}
