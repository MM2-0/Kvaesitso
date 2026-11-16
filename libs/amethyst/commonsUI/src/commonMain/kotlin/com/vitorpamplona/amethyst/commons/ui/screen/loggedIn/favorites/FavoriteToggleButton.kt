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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.favorites

import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.favorite_app_add
import com.vitorpamplona.amethyst.commons.resources.favorite_app_remove
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A star toggle that pins/unpins an nsite or napplet (a [FavoriteApp.NostrApp]) by its addressable
 * [coordinate], in the platform's favorite apps.
 */
@Composable
fun FavoriteToggleButton(
    coordinate: String,
    label: String,
    iconUrl: String? = null,
) {
    val favoriteApps = LocalAppServices.current.favoriteApps
    val id = "nostr:$coordinate"
    // Only this card's answer: a change to the favorites recomposes the card it is about, not every
    // napplet and site card on screen.
    val isFavorite by remember(favoriteApps, id) {
        favoriteApps.favorites.map { apps -> apps.any { it.id == id } }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = favoriteApps.favorites.value.any { it.id == id })

    IconButton(
        onClick = {
            if (isFavorite) {
                favoriteApps.remove(id)
            } else {
                favoriteApps.add(FavoriteApp.NostrApp(coordinate, label, TimeUtils.nowMillis(), iconUrl))
            }
        },
    ) {
        Icon(
            if (isFavorite) MaterialSymbols.Star else MaterialSymbols.StarBorder,
            contentDescription = stringRes(if (isFavorite) Res.string.favorite_app_remove else Res.string.favorite_app_add),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}
