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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_follow_set_to_favorites
import com.vitorpamplona.amethyst.commons.resources.remove_follow_set_from_favorites
import com.vitorpamplona.amethyst.commons.ui.components.ClickableBox
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark

/**
 * Star toggle that adds a kind:30000 follow set (anyone's) to the account's NIP-51 kind 10021
 * favorite follow sets, which puts it in the top-nav feed picker; tapping again removes it.
 */
@Composable
fun FavoriteFollowSetToggle(
    followSetNote: AddressableNote,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    iconSizeModifier: Modifier = Size20Modifier,
) {
    if (!accountViewModel.isWriteable()) return

    val favorites by accountViewModel.account.favoriteFollowSetsList.flow
        .collectAsStateWithLifecycle()

    val isFavorite = favorites.contains(followSetNote.address)

    ClickableBox(
        modifier = modifier,
        onClick = {
            if (isFavorite) {
                accountViewModel.unfollowFavoriteFollowSet(followSetNote.address)
            } else {
                accountViewModel.followFavoriteFollowSet(
                    AddressBookmark(
                        address = followSetNote.address,
                        relayHint = followSetNote.relayHintUrl(),
                    ),
                )
            }
        },
    ) {
        if (isFavorite) {
            Icon(
                symbol = MaterialSymbols.Star,
                contentDescription = stringRes(Res.string.remove_follow_set_from_favorites),
                modifier = iconSizeModifier,
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                symbol = MaterialSymbols.StarBorder,
                contentDescription = stringRes(Res.string.add_follow_set_to_favorites),
                modifier = iconSizeModifier,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
