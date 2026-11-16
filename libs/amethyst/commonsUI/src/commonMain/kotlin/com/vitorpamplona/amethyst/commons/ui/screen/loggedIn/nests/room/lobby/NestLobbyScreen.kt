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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.lobby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.nests.datasource.NestRoomFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.nests.room.activity.NestBridge
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nest_chat_empty
import com.vitorpamplona.amethyst.commons.resources.nest_lobby_host_label
import com.vitorpamplona.amethyst.commons.resources.nest_lobby_listeners_count
import com.vitorpamplona.amethyst.commons.resources.nest_lobby_no_listeners
import com.vitorpamplona.amethyst.commons.resources.nest_lobby_open_action
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.types.MeetingSpaceClosedFlag
import com.vitorpamplona.amethyst.commons.ui.note.types.MeetingSpaceOpenFlag
import com.vitorpamplona.amethyst.commons.ui.note.types.MeetingSpacePlannedFlag
import com.vitorpamplona.amethyst.commons.ui.note.types.MeetingSpacePrivateFlag
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatroomMessageCompose
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.chat.NestEditFieldRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.chat.NestNewMessageViewModel
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * In-app navigation lobby for a NIP-53 audio room (kind-30312). Sits
 * between the feed / share-link entry points and [com.vitorpamplona.amethyst
 * .ui.screen.loggedIn.nests.room.activity.NestActivity], so a user who's
 * just coming back to read the chat or check who's hosting doesn't
 * trigger a MoQ handshake or a host-side kind-30312 republish. The
 * top-bar "Open" action launches the audio activity.
 *
 * The lobby keeps the room's relay subscription
 * ([NestRoomFilterAssemblerSubscription]) warm so cached chat / presence
 * stay fresh, renders the kind-1311 transcript with the same
 * `LazyColumn(reverseLayout = true)` list the in-room screen uses, and
 * exposes the same composer (kind-1311 chat) — so users can chime in
 * without ever joining the audio plane. It does NOT open a
 * [com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel], does
 * NOT publish kind-10312 presence, and does NOT touch the audio pipeline.
 */
@Composable
fun NestLobbyScreen(
    addressValue: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val address = remember(addressValue) { Address.parse(addressValue) } ?: return
    LoadAddressableNote(address) { addressableNote ->
        addressableNote ?: return@LoadAddressableNote
        val event = addressableNote.event as? MeetingSpaceEvent ?: return@LoadAddressableNote
        NestLobbyContent(event, addressableNote, accountViewModel, nav)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NestLobbyContent(
    event: MeetingSpaceEvent,
    addressableNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val roomATag = remember(event) { event.address().toValue() }

    // Keep cached chat / presence warm while the user is on the lobby.
    // No MoQ session is opened — that's gated behind the Open action.
    NestRoomFilterAssemblerSubscription(addressableNote, accountViewModel)

    // Composer for kind-1311 chat. Same widget the in-room screen uses,
    // so the user gets @-mention picker, file uploads, draft auto-save,
    // emoji suggestions, and reply preview without joining audio.
    val nestScreenModel: NestNewMessageViewModel =
        rememberViewModel(key = "NestLobby/$roomATag") { NestNewMessageViewModel() }
    nestScreenModel.init(accountViewModel)
    nestScreenModel.load(addressableNote)

    val messages by produceState(initialValue = emptyList<Note>(), roomATag) {
        val filter =
            Filter(
                kinds = listOf(LiveActivitiesChatMessageEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            )
        LocalCache.observeNotes(filter).collect { notes ->
            value = notes.sortedByDescending { it.createdAt() ?: 0L }.take(LOBBY_CHAT_LIMIT)
        }
    }

    val routeForLastRead = remember(roomATag) { "NestLobbyChat/$roomATag" }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-stick to the newest message. With reverseLayout=true, item 0
    // is the bottom of the viewport (newest). When a new message is
    // prepended, LazyColumn preserves visual position by shifting
    // firstVisibleItemIndex from 0 → 1, leaving the new message just
    // below the viewport. If the user was at (or near) the bottom,
    // scroll back to 0 so the new message becomes visible. If they're
    // reading older history, leave them where they are.
    LaunchedEffect(messages.firstOrNull()?.idHex) {
        if (listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            ShorterTopAppBar(
                title = {
                    Text(
                        text = event.room().orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (nav.canPop()) {
                        IconButton(nav::popBack) {
                            ArrowBackIcon()
                        }
                    }
                },
                actions = {
                    OpenNestRoomAction(
                        event = event,
                        accountViewModel = accountViewModel,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePaddingSafe(),
        ) {
            RoomHeader(event, accountViewModel, nav)
            CachedListenerCount(roomATag)

            // Reverse-layout LazyColumn keyed by note id, same shape as
            // NestFullScreen's chat list. The lobby reads cached
            // LocalCache notes instead of an active NestViewModel.chat.
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                reverseLayout = true,
            ) {
                if (messages.isEmpty()) {
                    item("chat-empty") {
                        Text(
                            text = stringRes(Res.string.nest_chat_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                items(
                    items = messages,
                    key = { it.idHex },
                ) { note ->
                    ChatroomMessageCompose(
                        baseNote = note,
                        routeForLastRead = routeForLastRead,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onWantsToReply = nestScreenModel::reply,
                        onWantsToEditDraft = nestScreenModel::editFromDraft,
                    )
                }
            }

            // The composer sits above the 3-button nav when the keyboard
            // is closed and above the keyboard when it's open. The chat
            // list above is free to scroll behind the navigation bar.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                NestEditFieldRow(
                    nestScreenModel = nestScreenModel,
                    accountViewModel = accountViewModel,
                    onSendNewMessage = {
                        scope.launch {
                            delay(100)
                            listState.animateScrollToItem(0)
                        }
                    },
                    nav = nav,
                )
            }
        }
    }
}

@Composable
private fun OpenNestRoomAction(
    event: MeetingSpaceEvent,
    accountViewModel: AccountViewModel,
) {
    val serviceBase = event.service()
    val endpoint = event.endpoint()
    val roomId = event.address().dTag
    if (serviceBase.isNullOrBlank() || endpoint.isNullOrBlank() || roomId.isBlank()) return

    val appPlatform = LocalAppPlatform.current
    // Filled button so the primary CTA is unmistakable; the actions
    // Row in the AppBar already centers its children vertically, but
    // we trim the default end padding so the button sits flush with
    // the bar's trailing edge.
    Button(
        onClick = {
            NestBridge.set(accountViewModel)
            appPlatform.openNestRoom(event.address().toValue())
        },
        contentPadding = ButtonDefaults.ContentPadding,
        modifier = Modifier.padding(end = 8.dp),
    ) {
        Text(stringRes(Res.string.nest_lobby_open_action))
    }
}

@Composable
private fun RoomHeader(
    event: MeetingSpaceEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val image = remember(event) { event.image() }
    val summary = remember(event) { event.summary() }
    val status = remember(event) { event.status() }
    val starts = remember(event) { event.starts() }
    val hostPubkey = event.pubKey
    val participants = remember(event) { event.participants() }
    val speakers =
        remember(participants, hostPubkey) {
            participants.filter { p ->
                p.pubKey != hostPubkey &&
                    (p.role.equals(ROLE.SPEAKER.code, true) || p.role.equals(ROLE.MODERATOR.code, true))
            }
        }
    val hostUser = remember(hostPubkey) { LocalCache.getOrCreateUser(hostPubkey) }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop,
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (status) {
                    StatusTag.STATUS.LIVE -> {
                        MeetingSpaceOpenFlag()
                    }

                    StatusTag.STATUS.PRIVATE -> {
                        MeetingSpacePrivateFlag()
                    }

                    StatusTag.STATUS.ENDED -> {
                        MeetingSpaceClosedFlag()
                    }

                    StatusTag.STATUS.PLANNED -> {
                        MeetingSpacePlannedFlag(starts, horizontalAlignment = Alignment.Start)
                    }

                    null -> {}
                }
            }

            if (!summary.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                ClickableUserPicture(
                    baseUser = hostUser,
                    size = 40.dp,
                    accountViewModel = accountViewModel,
                    onClick = { user -> nav.nav(routeFor(user)) },
                )
                Spacer(StdHorzSpacer)
                UsernameDisplay(
                    baseUser = hostUser,
                    weight = Modifier.weight(1f),
                    accountViewModel = accountViewModel,
                )
                Spacer(StdHorzSpacer)
                Text(
                    text = stringRes(Res.string.nest_lobby_host_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (speakers.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val cap = 8
                    val visible = remember(speakers) { speakers.take(cap) }
                    visible.forEach { tag ->
                        ClickableUserPicture(
                            baseUserHex = tag.pubKey,
                            size = 28.dp,
                            accountViewModel = accountViewModel,
                            onClick = { hex ->
                                nav.nav(
                                    Route.Profile(hex),
                                )
                            },
                        )
                    }
                    if (speakers.size > visible.size) {
                        Text(
                            text = "+${speakers.size - visible.size}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Live listener count from cached kind-10312 presence events. Mirrors
 * the staleness window used inside the room (PRESENCE_STALE_THRESHOLD_SEC
 * in NestRoomEventCollectors): a peer that's missed one heartbeat plus a
 * 5-min "still here" window stops counting.
 */
@Composable
private fun CachedListenerCount(roomATag: String) {
    var count by remember(roomATag) { mutableIntStateOf(0) }
    LaunchedEffect(roomATag) {
        val filter =
            Filter(
                kinds = listOf(MeetingRoomPresenceEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            )
        LocalCache.observeEvents<MeetingRoomPresenceEvent>(filter).collect { events ->
            // Latest event per pubkey within the staleness window.
            val cutoff = TimeUtils.now() - LISTENER_FRESHNESS_SEC
            count =
                events
                    .groupBy { it.pubKey }
                    .values
                    .count { perAuthor -> perAuthor.maxOf { it.createdAt } >= cutoff }
        }
    }
    Text(
        text =
            if (count == 0) {
                stringRes(Res.string.nest_lobby_no_listeners)
            } else {
                stringRes(Res.string.nest_lobby_listeners_count, count)
            },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private const val LOBBY_CHAT_LIMIT = 50

// 6 minutes — matches PRESENCE_STALE_THRESHOLD_SEC in NestRoomEventCollectors.
private const val LISTENER_FRESHNESS_SEC: Long = 6L * 60L
