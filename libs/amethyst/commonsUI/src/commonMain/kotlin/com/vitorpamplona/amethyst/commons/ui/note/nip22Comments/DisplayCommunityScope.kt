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
package com.vitorpamplona.amethyst.commons.ui.note.nip22Comments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.ShortCommunityHeaderNoActions
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address

/**
 * The parent-context card for a top-level NIP-72 community post.
 *
 * Such a post answers the community itself, so there is no parent *note* to render -- without
 * this the slot above the post is simply empty. [ShortCommunityHeaderNoActions] is reused because
 * it degrades gracefully: it falls back to the address' `d` tag while the definition event is
 * still in flight, and its `observeNoteEvent` is what asks the relays for that definition in the
 * first place.
 */
@Composable
fun DisplayCommunityScope(
    community: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val communityNote = remember(community) { LocalCache.getOrCreateAddressableNote(community) }

    ShortCommunityHeaderNoActions(
        baseNote = communityNote,
        accountViewModel = accountViewModel,
        nav = nav,
        modifier =
            MaterialTheme.colorScheme.replyModifier
                .clickable { nav.nav(Route.Community(community.kind, community.pubKeyHex, community.dTag)) }
                .padding(10.dp),
    )
}
