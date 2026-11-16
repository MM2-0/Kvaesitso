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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nsites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nsite_none_found
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nsites.datasource.NsitesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent

/**
 * Lists the NIP-5A static-site manifests currently in the local cache (kinds 15128/35128). These are
 * replaceable/addressable events, so the screen observes the **AddressableNote** store ([observeNotes],
 * which scans `LocalCache.addressables`): exactly one note per address — the latest version — keyed by
 * the stable address ([com.vitorpamplona.amethyst.commons.model.Note.idHex]) and auto-updating in place as
 * newer versions arrive. Mirrors the nApplets browse screen: a follow-list filter in the top bar and
 * each row rendered through the shared [NoteCompose] — the same path the main feed uses for these
 * events — so it gets the author header, the
 * [com.vitorpamplona.amethyst.commons.ui.note.StaticWebsiteCard] (with its Open button), and the
 * standard reaction bar for free.
 */
@Composable
fun NsitesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Pull nSite manifests from the user's relays into LocalCache while this screen is open.
    NsitesFilterAssemblerSubscription(accountViewModel)

    val nsites by remember {
        LocalCache.observeNotes(
            Filter(kinds = listOf(RootSiteEvent.KIND, NamedSiteEvent.KIND)),
        )
    }.collectAsStateWithLifecycle(emptyList())

    val followFilter by accountViewModel.account.liveNsitesFollowLists
        .collectAsStateWithLifecycle()

    val visible =
        remember(nsites, followFilter) {
            nsites.filter { note ->
                val author = note.event?.pubKey ?: return@filter false
                // Covers "Mine" too: the shared author-matcher now resolves Mine to the user's own
                // pubkey, so matchAuthor already narrows to the user.
                followFilter.matchAuthor(author)
            }
        }

    Scaffold(
        topBar = { NsitesTopBar(accountViewModel, nav) },
        bottomBar = {
            AppBottomBar(Route.Nsites, nav, accountViewModel) { route ->
                if (route != Route.Nsites) nav.navBottomBar(route)
            }
        },
    ) { padding ->
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringRes(Res.string.nsite_none_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(visible, key = { it.idHex }) { note ->
                    NoteCompose(
                        baseNote = note,
                        modifier = Modifier.fillMaxWidth(),
                        quotesLeft = 3,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
