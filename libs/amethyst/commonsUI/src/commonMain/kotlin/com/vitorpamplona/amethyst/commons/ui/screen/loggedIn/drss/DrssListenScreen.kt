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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drss

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.drss.DrssTree
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drss_continue_listening
import com.vitorpamplona.amethyst.commons.resources.drss_listen_empty
import com.vitorpamplona.amethyst.commons.resources.drss_listen_no_river
import com.vitorpamplona.amethyst.commons.resources.drss_listen_title
import com.vitorpamplona.amethyst.commons.resources.drss_now_playing
import com.vitorpamplona.amethyst.commons.resources.drss_untitled
import com.vitorpamplona.amethyst.commons.resources.drss_up_next
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.DrssCover
import com.vitorpamplona.amethyst.commons.ui.note.types.DrssEpisodeAudio
import com.vitorpamplona.amethyst.commons.ui.note.types.DrssHeardBadge
import com.vitorpamplona.amethyst.commons.ui.note.types.episodeAudio
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.topNavFeedQueryState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

/** How many of the river's latest articles the queue looks through for episodes. */
private const val RIVER_SCAN_LIMIT = 300

/**
 * drss.io's listen page: one player over a queue of the episodes the user is part way through,
 * then the river's newest episodes they have not finished. The player moves to the next episode
 * in the queue when one ends, and saves progress in the same records drss reads.
 */
@Composable
fun DrssListenScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val drss = account.drss
    val river by drss.readList.collectAsStateWithLifecycle()
    val version by drss.version.collectAsStateWithLifecycle()
    val progressVersion by drss.progressVersion.collectAsStateWithLifecycle()

    if (river != null) DrssRiverEpisodesSubscription(accountViewModel)

    val continuing =
        remember(progressVersion) {
            drss
                .myProgress()
                .filter { (it.position() ?: 0) > 0 }
                .mapNotNull { progress -> progress.episode()?.let { Address(it.kind, it.pubKeyHex, it.dTag) } }
                .distinct()
                .map { LocalCache.getOrCreateAddressableNote(it) }
        }

    val authors = remember(river, version) { river?.let { DrssTree.authorsUnder(it, LocalCache) }.orEmpty() }
    val riverNotes by remember(authors) {
        if (authors.isEmpty()) {
            flowOf(emptyList())
        } else {
            LocalCache
                .observeNotes(Filter(kinds = listOf(LongFormContentEvent.KIND), authors = authors.toList(), limit = RIVER_SCAN_LIMIT))
                .flowOn(Dispatchers.Default)
        }
    }.collectAsStateWithLifecycle(emptyList())

    val upNext =
        remember(riverNotes, continuing, progressVersion) {
            val skip = continuing.mapTo(mutableSetOf()) { it.address }
            riverNotes
                .filterIsInstance<AddressableNote>()
                .filter { note ->
                    val event = note.event as? LongFormContentEvent
                    event != null && note.address !in skip && event.episodeAudio() != null && drss.progressOf(note.address)?.completedAt() == null
                }.sortedByDescending { it.event?.createdAt ?: 0 }
        }

    val queue = remember(continuing, upNext) { continuing + upNext }
    var playing by rememberSaveable { mutableStateOf<String?>(null) }
    var autoPlay by remember { mutableStateOf(false) }
    val current = remember(playing, queue) { playing?.let { id -> queue.firstOrNull { it.address.toValue() == id } ?: Address.parse(id)?.let { LocalCache.getOrCreateAddressableNote(it) } } }

    fun play(note: AddressableNote) {
        playing = note.address.toValue()
        autoPlay = true
    }

    // The list keeps its place when the player appears above it: bring the player into view.
    val listState = rememberLazyListState()
    LaunchedEffect(playing) { if (playing != null) listState.animateScrollToItem(0) }

    DrssScreenFrame(stringRes(Res.string.drss_listen_title), nav) {
        LazyColumn(Modifier.fillMaxWidth(), state = listState) {
            current?.let { note ->
                item(key = "player") {
                    DrssSectionTitle(stringRes(Res.string.drss_now_playing))
                    key(note.address) {
                        DrssNowPlaying(note, autoPlay, accountViewModel, nav) {
                            val at = queue.indexOfFirst { it.address == note.address }
                            val next = queue.drop(at + 1).firstOrNull { it.address != note.address }
                            if (next != null) play(next)
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 12.dp))
                }
            }
            if (continuing.isNotEmpty()) {
                item(key = "continueTitle") { DrssSectionTitle(stringRes(Res.string.drss_continue_listening)) }
                items(continuing, key = { "c" + it.address.toValue() }) { note ->
                    DrssQueueRow(note, note.address == current?.address, accountViewModel) { play(note) }
                }
            }
            if (upNext.isNotEmpty()) {
                item(key = "nextTitle") { DrssSectionTitle(stringRes(Res.string.drss_up_next)) }
                items(upNext, key = { "n" + it.address.toValue() }) { note ->
                    DrssQueueRow(note, note.address == current?.address, accountViewModel) { play(note) }
                }
            }
            if (queue.isEmpty()) {
                item(key = "empty") { DrssHint(stringRes(if (river == null) Res.string.drss_listen_no_river else Res.string.drss_listen_empty)) }
            }
        }
    }
}

/** Loads the river's articles from its feeds' outboxes while the screen is open. */
@Composable
private fun DrssRiverEpisodesSubscription(accountViewModel: AccountViewModel) {
    val state =
        remember(accountViewModel.account) {
            val account = accountViewModel.account
            accountViewModel.topNavFeedQueryState(account.drssRiverList, account.liveDrssRiverPerRelay)
        }
    LifecycleAwareKeyDataSourceSubscription(state, accountViewModel.dataSources().articles)
}

@Composable
private fun DrssSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun DrssNowPlaying(
    note: AddressableNote,
    autoPlay: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    onEnded: () -> Unit,
) {
    val event by observeNoteEvent<LongFormContentEvent>(note, accountViewModel)
    val episode = event ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.clickable { routeFor(note, accountViewModel.account)?.let(nav::nav) },
        ) {
            DrssCover(episode.image(), 56.dp, MaterialSymbols.Podcasts, accountViewModel)
            Column(Modifier.weight(1f)) {
                Text(
                    text = episode.title() ?: stringRes(Res.string.drss_untitled),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                DrssShowName(note, accountViewModel)
            }
        }
        DrssEpisodeAudio(note, episode, accountViewModel, autoPlay = autoPlay, onEnded = onEnded)
    }
}

@Composable
private fun DrssShowName(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
) {
    val show = note.author ?: return
    val name by observeUserName(show, accountViewModel)
    Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** An episode in the queue; [isCurrent] marks the one in the player. */
@Composable
internal fun DrssQueueRow(
    note: AddressableNote,
    isCurrent: Boolean,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val event by observeNoteEvent<LongFormContentEvent>(note, accountViewModel)
    val episode = event
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (isCurrent) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) else Modifier)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        DrssCover(episode?.image(), 44.dp, MaterialSymbols.Podcasts, accountViewModel)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = episode?.title() ?: stringRes(Res.string.drss_untitled),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            DrssShowName(note, accountViewModel)
            if (episode != null) DrssHeardBadge(note, episode, accountViewModel)
        }
    }
}
