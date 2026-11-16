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
package com.vitorpamplona.amethyst.commons.favorites

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.icons.symbols.rememberMaterialSymbolPainter
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.snapshot.NappletSnapshotEvent

/**
 * The icon for a favorite app: [iconModel] (e.g. a captured favicon the host app resolves) when given,
 * else its own manifest icon ([FavoriteApp.iconUrl]), else a type glyph (the napplet/nsite grid mark, or
 * the globe for a plain web URL). The glyph also backs the remote image as placeholder/error, so a missing
 * or failed icon degrades to it rather than to a blank.
 */
@Composable
fun FavoriteAppIcon(
    app: FavoriteApp,
    tint: Color,
    modifier: Modifier = Modifier,
    iconModel: Any? = null,
    // Applied to the drawn icon (image or its placeholder glyph), e.g. to desaturate it.
    colorFilter: ColorFilter? = null,
) {
    val symbol = if (app is FavoriteApp.NostrApp) MaterialSymbols.Apps else MaterialSymbols.Public
    val isNapplet = app is FavoriteApp.NostrApp && app.coordinate.substringBefore(':').toIntOrNull() in NAPPLET_KINDS
    val model = iconModel ?: app.iconUrl?.takeIf { !isNapplet && it.isNotBlank() }

    if (model == null) {
        Icon(symbol, contentDescription = null, modifier = modifier, tint = tint)
    } else {
        val glyph = rememberMaterialSymbolPainter(symbol, tint)
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(6.dp)),
            placeholder = glyph,
            error = glyph,
            fallback = glyph,
            colorFilter = colorFilter,
        )
    }
}

private val NAPPLET_KINDS = setOf(NappletSnapshotEvent.KIND, RootNappletEvent.KIND, NamedNappletEvent.KIND)
