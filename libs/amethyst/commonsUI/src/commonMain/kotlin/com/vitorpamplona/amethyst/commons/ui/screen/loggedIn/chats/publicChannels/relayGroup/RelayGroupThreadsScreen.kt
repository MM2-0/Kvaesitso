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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource.RelayGroupOpenThreadsHistorySubAssembler
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupMembership
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReplyCount
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_channel_archive
import com.vitorpamplona.amethyst.commons.resources.buzz_channel_unarchive
import com.vitorpamplona.amethyst.commons.resources.more_options
import com.vitorpamplona.amethyst.commons.resources.relay_group_thread_new
import com.vitorpamplona.amethyst.commons.resources.relay_group_thread_untitled
import com.vitorpamplona.amethyst.commons.resources.relay_group_threads_all_caught_up
import com.vitorpamplona.amethyst.commons.resources.relay_group_threads_empty
import com.vitorpamplona.amethyst.commons.resources.relay_group_threads_empty_read_only
import com.vitorpamplona.amethyst.commons.resources.relay_group_threads_loading_older
import com.vitorpamplona.amethyst.commons.resources.relay_group_threads_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupOpenThreadsHistorySubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupOpenThreadsSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.forum.ForumPostEvent
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_CHANNEL_TYPE_FORUM
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * A group's forum-style threads (kind 11) — the secondary content type kept out of
 * the kind-9 chat feed. Streams the group's threads + their comments via
 * [RelayGroupOpenThreadsSubscription]; tapping a thread opens the generic thread view
 * ([Route.Note]) with its comment tree. Members can start a new thread.
 */
@Composable
fun RelayGroupThreadsScreen(
    id: HexKey,
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) } ?: return
    val channelId = remember(id, relay) { GroupId(id, relay) }

    LoadRelayGroupChannel(channelId) { channel ->
        RelayGroupThreads(channel, accountViewModel, nav)
    }
}

/**
 * Whether *starting* a thread belongs on this channel, given its Buzz `t` [channelType] (null on a
 * relay that doesn't declare one) and whether its host speaks the Buzz dialect.
 *
 * On a Buzz relay the compose FAB writes a kind-45001 forum post, and Buzz only ever puts those in a
 * `t=forum` channel — its own client mounts the forum view for `channelType === "forum"` alone, so a
 * 45001 in a `t=stream` chat channel is a post nobody outside Amethyst can see. The relay itself
 * accepts it (nothing there gates 45001 by channel type), which is exactly why the client has to.
 *
 * A Buzz channel whose kind-39000 hasn't landed yet reads as null and is treated as not-a-forum: the
 * FAB appearing a moment later is a smaller error than publishing into the wrong channel.
 *
 * This gates writing only. Reading stays open on every channel — an existing thread is worth showing
 * wherever it came from — and non-Buzz relays are untouched, where the FAB writes a NIP-7D kind-11.
 */
fun canStartThreadHere(
    channelType: String?,
    isBuzzRelay: Boolean,
): Boolean = !isBuzzRelay || channelType == BUZZ_CHANNEL_TYPE_FORUM

@Composable
private fun RelayGroupThreads(
    channel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Recent live tail + on-demand backward history, the Threads analog of the chat stack. Without the
    // pager a group with more threads than the relay's default result cap would silently hide the older ones.
    RelayGroupOpenThreadsSubscription(channel, accountViewModel.dataSources().relayGroupOpenThreads, accountViewModel)
    val historySource = accountViewModel.dataSources().relayGroupOpenThreadsHistory
    RelayGroupOpenThreadsHistorySubscription(channel.groupId, historySource, accountViewModel)

    val threads by channel.threads.collectAsStateWithLifecycle()
    val history = remember(historySource) { historySource.history }
    val loadingOlder by history.loadingMore.collectAsStateWithLifecycle()
    val status by history.status.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    RelayGroupThreadsPaging(threadCount = { threads.size }, listState = listState, history = history)

    // Hide the compose FAB where the relay would reject the kind-11: on membership-gated groups
    // that don't list me. Open Buzz channels accept any authenticated member. See [RelayGroupChannel.canPost].
    val isBuzz = remember(channel.groupId.relayUrl) { BuzzRelayDialect.isBuzz(channel.groupId.relayUrl) }
    val canPost =
        channel.canPost(accountViewModel.userProfile().pubkeyHex) &&
            canStartThreadHere(channel.event?.buzzChannelType(), isBuzz)

    Scaffold(
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    Column {
                        Text(
                            text = stringRes(Res.string.relay_group_threads_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = channel.toBestDisplayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    // The forum's per-item actions, moved off the community-list row into this screen's
                    // top-bar overflow: Pin/Unpin and the Add/Remove-from-Messages toggle, plus the
                    // admin-only Archive/Unarchive (so an archived forum can be brought back from here).
                    // Buzz-only, which every forum channel is.
                    if (isBuzz) {
                        var menuOpen by remember { mutableStateOf(false) }
                        val isAdmin = channel.membershipOf(accountViewModel.userProfile().pubkeyHex) == RelayGroupMembership.ADMIN
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                symbol = MaterialSymbols.MoreVert,
                                contentDescription = stringRes(Res.string.more_options),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            BuzzPinDropdownItem(channel.groupId, accountViewModel) { menuOpen = false }
                            RelayGroupMessagesDropdownItem(channel, accountViewModel) { menuOpen = false }
                            if (isAdmin) {
                                val archived = channel.isArchived()
                                DropdownMenuItem(
                                    text = { Text(stringRes(if (archived) Res.string.buzz_channel_unarchive else Res.string.buzz_channel_archive)) },
                                    onClick = {
                                        menuOpen = false
                                        accountViewModel.archiveRelayGroup(channel, !archived)
                                    },
                                )
                            }
                        }
                    }
                },
                // Hidden in a list/detail screen's detail pane, where there is nothing to go back to.
                nav = nav,
            )
        },
        floatingActionButton = {
            if (canPost) {
                FloatingActionButton(
                    onClick = {
                        // On a Buzz workspace, "new thread" is a Buzz forum post (kind 45001);
                        // vanilla NIP-29 relays use a kind-11 thread. Buzz relays reject
                        // unknown kinds, so a kind-11 thread would be refused there.
                        if (isBuzz) {
                            nav.nav(Route.BuzzForumPost(channel.groupId.id, channel.groupId.relayUrl.url))
                        } else {
                            nav.nav(
                                Route.NewShortNote(
                                    groupThreadId = channel.groupId.id,
                                    groupThreadRelayUrl = channel.groupId.relayUrl.url,
                                ),
                            )
                        }
                    },
                    shape = CircleShape,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Add,
                        contentDescription = stringRes(Res.string.relay_group_thread_new),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
    ) { padding ->
        if (threads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text =
                        if (canPost) {
                            stringRes(Res.string.relay_group_threads_empty)
                        } else {
                            stringRes(Res.string.relay_group_threads_empty_read_only)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.padding(padding)) {
                itemsIndexed(threads, key = { _, thread -> thread.idHex }) { index, thread ->
                    if (index > 0) {
                        HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    // A Buzz forum root (45001) opens the forum-thread detail (root + kind-45003 replies);
                    // a NIP-29 kind-11 thread opens the generic note view with its kind-1111 comment tree.
                    val open: () -> Unit = {
                        if (thread.event is ForumPostEvent) {
                            nav.nav(Route.BuzzForumThread(channel.groupId.id, channel.groupId.relayUrl.url, thread.idHex))
                        } else {
                            nav.nav(Route.Note(thread.idHex))
                        }
                    }
                    ThreadRow(thread, accountViewModel, nav, open)
                }
                item(key = "threads-history-footer") {
                    RelayGroupThreadsHistoryFooter(loadingOlder, status.exhausted)
                }
            }
        }
    }
}

/** How many threads to eagerly backfill on open before paging goes demand-driven, and the scroll lead. */
private const val RELAY_GROUP_THREADS_TARGET = 30
private const val RELAY_GROUP_THREADS_PREFETCH_AHEAD = 5

/**
 * Drives the Threads backward pager: eagerly backfill to a window on open (so a group with deep history
 * doesn't show just its last few threads), then page older content demand-driven as the list nears its end.
 * Mirrors the chat screen's `RelayGroupBackfillHistoryToWindow` + reach sentinels, on the plain thread list.
 */
@Composable
private fun RelayGroupThreadsPaging(
    threadCount: () -> Int,
    listState: LazyListState,
    history: RelayGroupOpenThreadsHistorySubAssembler,
) {
    LaunchedEffect(history) {
        combine(snapshotFlow { threadCount() }, history.loadingMore, history.status) { count, loading, s ->
            count < RELAY_GROUP_THREADS_TARGET && !loading && !s.exhausted
        }.distinctUntilChanged()
            .filter { it }
            .collect { history.advanceAll() }
    }
    LaunchedEffect(history, listState) {
        snapshotFlow {
            val last =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val total = threadCount()
            total > 0 && last >= total - RELAY_GROUP_THREADS_PREFETCH_AHEAD
        }.distinctUntilChanged()
            .filter { it }
            .collect {
                if (!history.status.value.exhausted && !history.loadingMore.value) history.advanceAll()
            }
    }
}

/** A quiet footer at the bottom of the thread list: what the pager is doing, or nothing when idle. */
@Composable
private fun RelayGroupThreadsHistoryFooter(
    loadingOlder: Boolean,
    exhausted: Boolean,
) {
    val text =
        when {
            loadingOlder -> stringRes(Res.string.relay_group_threads_loading_older)
            exhausted -> stringRes(Res.string.relay_group_threads_all_caught_up)
            else -> return
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

@Composable
private fun ThreadRow(
    thread: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClick: () -> Unit,
) {
    // Observe the reply count so a comment arriving on an already-listed thread bumps it live
    // (channel.threads only re-emits on add/remove of a thread).
    val replyCount by observeNoteReplyCount(thread, accountViewModel)
    val untitled = stringRes(Res.string.relay_group_thread_untitled)
    // Two thread shapes share this list: NIP-29 kind-11 threads (title + body) and Buzz forum
    // roots (kind 45001, body only — no title). For a forum post, surface the first line as the
    // heading and the rest as the preview so it reads like a titled thread.
    val title: String
    val preview: String
    when (val event = thread.event) {
        is ThreadEvent -> {
            title = event.title()?.takeIf { it.isNotBlank() } ?: untitled
            preview = event.content.replace('\n', ' ').trim()
        }
        is ForumPostEvent -> {
            val body = event.body().trim()
            title = body.substringBefore('\n').trim().ifEmpty { untitled }
            // Everything after the first line. Using substringAfter (not removePrefix on the trimmed
            // first line) avoids duplicating the first line when it had trailing spaces before the \n.
            preview = body.substringAfter('\n', "").replace('\n', ' ').trim()
        }
        else -> {
            title = untitled
            preview = ""
        }
    }
    val author = thread.author

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (author != null) {
            UserPicture(author, Size35dp, accountViewModel = accountViewModel, nav = nav)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (author != null) {
                    UsernameDisplay(author, accountViewModel = accountViewModel)
                }
                Icon(
                    symbol = MaterialSymbols.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "$replyCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
