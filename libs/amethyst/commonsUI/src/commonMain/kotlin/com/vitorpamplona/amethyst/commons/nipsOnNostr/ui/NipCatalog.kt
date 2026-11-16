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
package com.vitorpamplona.amethyst.commons.nipsOnNostr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipCatalogFilter
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipCatalogViewModel
import com.vitorpamplona.amethyst.commons.nipsOnNostr.OfficialNipEntry
import com.vitorpamplona.amethyst.commons.nipsOnNostr.ui.datasource.AllNipsSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip_catalog_all
import com.vitorpamplona.amethyst.commons.resources.nip_catalog_empty
import com.vitorpamplona.amethyst.commons.resources.nip_catalog_official
import com.vitorpamplona.amethyst.commons.resources.nip_catalog_official_failed
import com.vitorpamplona.amethyst.commons.resources.nip_catalog_on_nostr
import com.vitorpamplona.amethyst.commons.resources.nip_catalog_search
import com.vitorpamplona.amethyst.commons.resources.nip_number
import com.vitorpamplona.amethyst.commons.resources.nip_unrecommended
import com.vitorpamplona.amethyst.commons.resources.try_again
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SearchTopBar
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The NIPs screen, opened from the drawer or the bottom bar. The NIPs published on Nostr are the
 * account's NIPs feed (AccountFeedContentStates.nipsFeed), kept live by [AllNipsSubscription] while
 * the screen is open and rendered through the shared feed scaffolding (pull to refresh, the saved
 * scroll position); a mute rebuilds it from AccountFeedContentStates. The nips repo's index sits
 * beside them, and the search in the top bar filters both.
 */
@Composable
fun NipsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState = accountViewModel.feedStates.nipsFeed
    val viewModel = rememberViewModel(key = "NipCatalog") { NipCatalogViewModel(accountViewModel.httpClientBuilder) }
    val query by viewModel.query.collectAsStateWithLifecycle()

    WatchLifecycleAndUpdateModel(feedState)
    AllNipsSubscription(accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        // The search is the top bar, as in Settings: the list below filters as you type.
        topBar = {
            SearchTopBar(
                query = query,
                onQueryChange = { viewModel.query.value = it },
                onClear = { viewModel.query.value = "" },
                placeholder = stringRes(Res.string.nip_catalog_search),
                nav = nav,
            )
        },
        bottomBar = {
            AppBottomBar(Route.Nips, nav, accountViewModel) { route ->
                if (route == Route.Nips) feedState.sendToTop() else nav.navBottomBar(route)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(feedState, true) {
            SaveableFeedContentState(feedState, scrollStateKey = ScrollStateKeys.NIPS_SCREEN) { listState ->
                // One list whatever the feed's state: the nips repo's index and the chips belong to
                // the screen, not to the feed, so they don't cross-fade or reset as the feed loads.
                val (notes, loading) = feedNotes(feedState)
                NipCatalogList(viewModel, notes, loadingOnNostr = loading, listState, accountViewModel, nav)
            }
        }
    }
}

/**
 * The list: the filter chips, the NIPs published on Nostr (the feed's [notes], drawn as their feed
 * cards with author and reactions) and the nips repo's index (a line each, opening the in-app
 * reader). The search matches a number (`44`, `nip-7d`), words of the title, or a kind a NIP
 * defines; ranking the Nostr ones reads their text, so it runs off the main thread.
 */
@Composable
private fun NipCatalogList(
    viewModel: NipCatalogViewModel,
    notes: List<Note>,
    loadingOnNostr: Boolean,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val official by viewModel.official.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    // Until the ranking is done: the feed as it is when nothing narrows it, else nothing, so a search
    // doesn't flash the whole list first.
    val unfiltered = query.isBlank() && filter != NipCatalogFilter.OFFICIAL
    val onNostr by produceState(initialValue = if (unfiltered) notes else emptyList(), notes, query, filter) {
        value = withContext(Dispatchers.Default) { viewModel.onNostr(notes, query, filter) }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = rememberFeedContentPadding(FeedPadding)) {
        item(key = "filters") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NipCatalogFilter.entries.forEach { option ->
                    val selected = filter == option
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.filter.value = option },
                        label = {
                            Text(
                                stringRes(
                                    when (option) {
                                        NipCatalogFilter.ALL -> Res.string.nip_catalog_all
                                        NipCatalogFilter.OFFICIAL -> Res.string.nip_catalog_official
                                        NipCatalogFilter.ON_NOSTR -> Res.string.nip_catalog_on_nostr
                                    },
                                ),
                            )
                        },
                        leadingIcon =
                            if (selected) {
                                { Icon(MaterialSymbols.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                    )
                }
            }
        }

        // While searching, a section with no match is left out instead of drawn as an empty heading.
        val searching = query.isNotBlank()
        if (filter != NipCatalogFilter.OFFICIAL && !(searching && onNostr.isEmpty() && !loadingOnNostr)) {
            item(key = "nostr-title") { CatalogSection(stringRes(Res.string.nip_catalog_on_nostr), onNostr.size, loadingOnNostr) }
            items(onNostr, key = { "nostr-" + it.idHex }) { note ->
                NoteCompose(baseNote = note, quotesLeft = 1, accountViewModel = accountViewModel, nav = nav)
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        if (filter != NipCatalogFilter.ON_NOSTR && !(searching && official.entries.isEmpty() && !official.loading)) {
            item(key = "official-title") { CatalogSection(stringRes(Res.string.nip_catalog_official), official.entries.size, official.loading) }
            if (official.failed && official.entries.isEmpty()) {
                item(key = "official-failed") {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringRes(Res.string.nip_catalog_official_failed), color = MaterialTheme.colorScheme.grayText, modifier = Modifier.weight(1f))
                        Text(
                            stringRes(Res.string.try_again),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { viewModel.loadOfficial() }.padding(8.dp),
                        )
                    }
                }
            }
            items(official.entries, key = { "official-" + it.number }) { entry ->
                OfficialNipRow(entry) { nav.nav(Route.Nip(entry.number)) }
            }
        }

        if (searching && official.entries.isEmpty() && onNostr.isEmpty() && !official.loading && !loadingOnNostr) {
            item(key = "empty") {
                Text(
                    stringRes(Res.string.nip_catalog_empty),
                    color = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CatalogSection(
    title: String,
    count: Int,
    loading: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (count > 0) {
            Text(" · $count", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.grayText)
        }
        Spacer(Modifier.weight(1f))
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

/** `NIP-44  Encrypted Payloads (Versioned)`, struck through with its reason when unrecommended. */
@Composable
private fun OfficialNipRow(
    entry: OfficialNipEntry,
    onClick: () -> Unit,
) {
    val unrecommended = entry.unrecommended != null
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NipPill(
            label = stringRes(Res.string.nip_number, entry.number),
            container = if (unrecommended) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
            content = if (unrecommended) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
            bold = true,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (unrecommended) TextDecoration.LineThrough else null,
                color = if (unrecommended) MaterialTheme.colorScheme.grayText else MaterialTheme.colorScheme.onSurface,
            )
            if (unrecommended) {
                val reason = entry.unrecommended?.ifBlank { null }
                Text(
                    stringRes(Res.string.nip_unrecommended) + (reason?.let { ": $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(MaterialSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.grayText)
    }
}
