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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_author_verified
import com.vitorpamplona.amethyst.commons.resources.podcast_role_cohost
import com.vitorpamplona.amethyst.commons.resources.podcast_role_editor
import com.vitorpamplona.amethyst.commons.resources.podcast_role_host
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipF4Podcasts.authored.AuthoredPodcastsEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.AuthorTag

/**
 * Renders a NIP-F4 podcast's claimed authors (`kind:10154` `p` tags, each a pubkey + role). The
 * claims are unverified — the show can name anyone — so each author is cross-checked against their
 * own counter-claim ([AuthoredPodcastsEvent], `kind:10064`): a verified check appears only when that
 * author's 10064 actually lists this podcast's pubkey. The 10064 is fetched + observed lazily via
 * [observeNoteEvent], so it arrives and flips the badge without extra wiring.
 */
@Composable
fun PodcastAuthors(
    podcastPubkey: HexKey,
    authors: List<AuthorTag>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Size5dp),
    ) {
        authors.forEach { author ->
            PodcastAuthorRow(podcastPubkey, author, accountViewModel, nav)
        }
    }
}

@Composable
private fun PodcastAuthorRow(
    podcastPubkey: HexKey,
    author: AuthorTag,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var user by remember(author.pubKey) { mutableStateOf(LocalCache.getUserIfExists(author.pubKey)) }
    if (user == null) {
        LaunchedEffect(author.pubKey) {
            user = LocalCache.checkGetOrCreateUser(author.pubKey)
        }
    }

    val authoredNote =
        remember(author.pubKey) {
            LocalCache.getOrCreateAddressableNote(AuthoredPodcastsEvent.createAddress(author.pubKey))
        }
    val authored by observeNoteEvent<AuthoredPodcastsEvent>(authoredNote, accountViewModel)
    val verified = authored?.authors(podcastPubkey) == true

    val loadedUser = user ?: return

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav(routeFor(loadedUser)) }
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClickableUserPicture(loadedUser, 28.dp, accountViewModel)
        Column(modifier = Modifier.weight(1f)) {
            UsernameDisplay(loadedUser, accountViewModel = accountViewModel)
            author.role?.let {
                Text(
                    text = roleLabel(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }
        if (verified) {
            Icon(
                symbol = MaterialSymbols.CheckCircle,
                contentDescription = stringRes(Res.string.podcast_author_verified),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun roleLabel(role: String): String =
    when (role) {
        AuthorTag.ROLE_HOST -> stringRes(Res.string.podcast_role_host)
        AuthorTag.ROLE_COHOST -> stringRes(Res.string.podcast_role_cohost)
        AuthorTag.ROLE_EDITOR -> stringRes(Res.string.podcast_role_editor)
        else -> role
    }
