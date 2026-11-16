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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.membershipManagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.EmojiPackCard
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEventAndMap
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.my_emoji_list_empty
import com.vitorpamplona.amethyst.commons.resources.my_emoji_list_title
import com.vitorpamplona.amethyst.commons.resources.remove_from_emoji_list
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiListEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.taggedEmojis

// Screen that lets the logged-in user inspect the contents of their kind 10030 selection
// (a.k.a. "My Emoji List") and remove individual packs from it. Tapping a pack navigates to a
// viewer: the owner's pack-editor screen for self-authored packs, otherwise the generic note
// thread view (which already knows how to render a kind 30030 `EmojiPackEvent`).
@Composable
fun MyEmojiListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(
        address = accountViewModel.account.emoji.getEmojiListAddress(),
    ) { selectionNote ->
        selectionNote?.let {
            MyEmojiListView(
                selectionNote = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun MyEmojiListView(
    selectionNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(caption = stringRes(Res.string.my_emoji_list_title), nav)
        },
    ) { contentPadding ->
        val packAddresses by observeNoteEventAndMap<EmojiListEvent, List<Address>>(
            selectionNote,
            accountViewModel,
        ) { event ->
            // The kind 10030 selection event can carry the same `a` tag more than once (e.g. a pack
            // added twice). The grid below keys items by `address.toValue()`, so duplicates would
            // throw an IllegalArgumentException ("Key ... was already used"). Address is a data
            // class over (kind, pubKeyHex, dTag) — the same fields toValue() encodes — so distinct()
            // dedupes on exactly the grid key.
            event?.emojiPacks()?.distinct() ?: emptyList()
        }

        Column(
            modifier =
                Modifier
                    .padding(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding(),
                    ).consumeWindowInsets(contentPadding)
                    .fillMaxHeight(),
        ) {
            MyEmojiListFeed(
                packAddresses = packAddresses,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun MyEmojiListFeed(
    packAddresses: List<Address>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (packAddresses.isEmpty()) {
        Text(
            text = stringRes(Res.string.my_emoji_list_empty),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center,
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        state = rememberLazyGridState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            packAddresses,
            key = { address -> address.toValue() },
        ) { address ->
            LoadAddressableNote(
                address = address,
            ) { packNote ->
                packNote?.let {
                    SelectedEmojiPackCard(
                        packNote = it,
                        modifier = Modifier.animateItem(),
                        accountViewModel = accountViewModel,
                        onOpen = {
                            val route =
                                if (accountViewModel.isLoggedUser(it.author)) {
                                    Route.EmojiPackView(it.dTag())
                                } else {
                                    Route.Note(it.idHex)
                                }
                            nav.nav(route)
                        },
                        onRemove = {
                            // Publishes a replacement kind 10030 without this pack's `a` tag.
                            // Downstream consumers (reaction menu + `:` autocomplete) refresh
                            // automatically because they're all subscribed to the same flow.
                            accountViewModel.removeEmojiPack(it)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedEmojiPackCard(
    packNote: AddressableNote,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val packEvent by observeNoteEvent<EmojiPackEvent>(packNote, accountViewModel)

    val title = packEvent?.titleOrName()?.takeIf { it.isNotBlank() } ?: packNote.dTag()
    val image = packEvent?.image()
    val emojiUrls =
        remember(packEvent) {
            packEvent?.taggedEmojis()?.map { it.url }.orEmpty()
        }
    val author =
        remember(packNote) {
            if (accountViewModel.isLoggedUser(packNote.author)) null else packNote.author?.toBestDisplayName()
        }

    // The remove button sits in the card's footer, beside the title: over the emoji sheet it
    // would cover the emojis, which fill the card's corners in a small pack.
    EmojiPackCard(
        title = title,
        emojiUrls = emojiUrls,
        coverImage = image,
        author = author,
        onClick = onOpen,
        modifier = modifier,
        action = {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    symbol = MaterialSymbols.Delete,
                    contentDescription = stringRes(Res.string.remove_from_emoji_list),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}
