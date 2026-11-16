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
package com.vitorpamplona.amethyst.commons.ui.note.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextColor
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip72ModCommunities.communityAddress
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent

@Composable
fun DisplayFollowingCommunityInPost(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) { DisplayCommunity(baseNote, accountViewModel, nav) }
    }
}

@Composable
private fun DisplayCommunity(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val communityTag = note.event?.communityAddress() ?: return

    val communityNote = LocalCache.getOrCreateAddressableNote(communityTag)
    val communityState by observeNote(communityNote, accountViewModel)
    val label = communityShortLabel(communityTag, communityState.note.event as? CommunityDefinitionEvent)

    ClickableTextColor(
        label,
        linkColor = MaterialTheme.colorScheme.lessImportantLink,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
    ) {
        nav.nav {
            note.event?.communityAddress()?.let { addr ->
                Route.Community(addr.kind, addr.pubKeyHex, addr.dTag)
            }
        }
    }
}

private fun communityShortLabel(
    address: Address,
    definition: CommunityDefinitionEvent?,
): String {
    val raw = definition?.name()?.ifBlank { null } ?: address.dTag
    val shortened =
        if (raw.length > 12) {
            raw.take(12) + "..."
        } else {
            raw
        }
    return "/n/$shortened"
}
