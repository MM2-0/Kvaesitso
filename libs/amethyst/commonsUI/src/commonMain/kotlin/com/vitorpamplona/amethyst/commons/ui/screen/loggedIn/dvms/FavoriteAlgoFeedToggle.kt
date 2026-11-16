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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteAndMap
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_dvm_to_favorites
import com.vitorpamplona.amethyst.commons.resources.remove_dvm_from_favorites
import com.vitorpamplona.amethyst.commons.ui.components.ClickableBox
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.DvmContentDiscoveryRequestEvent

/**
 * Inline star toggle that follows / unfollows a NIP-90 content-discovery DVM.
 *
 * Uses [ClickableBox] (no [androidx.compose.material3.IconButton] padding) so it
 * fits in a `LeftPictureLayout` title row alongside other compact reactions
 * without inflating the row to 48dp.
 *
 * Hidden until the underlying [AppDefinitionEvent] loads and we can confirm the
 * DVM advertises kind 5300 — favouriting any other DVM type would only stall on
 * a 6300 reply that never comes.
 */
@Composable
fun FavoriteAlgoFeedToggle(
    appDefinitionNote: AddressableNote,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    iconSizeModifier: Modifier = Size20Modifier,
) {
    val supportsContentDiscovery by
        observeNoteAndMap(appDefinitionNote, accountViewModel) { note ->
            (note.event as? AppDefinitionEvent)?.includeKind(DvmContentDiscoveryRequestEvent.KIND) == true
        }

    if (!supportsContentDiscovery) return

    val favorites by accountViewModel.account.favoriteAlgoFeedsList.flow
        .collectAsStateWithLifecycle()

    val isFavorite = favorites.contains(appDefinitionNote.address)

    ClickableBox(
        modifier = modifier,
        onClick = {
            if (isFavorite) {
                accountViewModel.unfollowFavoriteAlgoFeed(appDefinitionNote.address)
            } else {
                accountViewModel.followFavoriteAlgoFeed(
                    AddressBookmark(
                        address = appDefinitionNote.address,
                        relayHint = appDefinitionNote.relayHintUrl(),
                    ),
                )
            }
        },
    ) {
        if (isFavorite) {
            Icon(
                symbol = MaterialSymbols.Star,
                contentDescription = stringRes(Res.string.remove_dvm_from_favorites),
                modifier = iconSizeModifier,
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                symbol = MaterialSymbols.StarBorder,
                contentDescription = stringRes(Res.string.add_dvm_to_favorites),
                modifier = iconSizeModifier,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
