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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nest_chat_empty
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatroomMessageCompose
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.chat.NestEditFieldRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.chat.NestNewMessageViewModel
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * In-room chat panel. Renders the kind-1311 transcript that the
 * [NestViewModel] is collecting (`viewModel.chat`) using the same
 * per-message renderer that NIP-53 live-stream chat uses
 * ([ChatroomMessageCompose]).
 *
 * The composer is owned by a separate [NestNewMessageViewModel] —
 * scoped to *editing and sending* chat messages only — so we get
 * @-mention picker, file/image/video upload, reply preview, NIP-37
 * draft auto-save, emoji suggestions, content warnings, expiration,
 * geohash, and zap-split forwarding for the nest chat field. The
 * message list itself is still driven from the [NestViewModel] so
 * presence / reactions / chat all share a single lifecycle.
 *
 * Navigation: the room may have no Compose NavHost in scope (Android
 * hosts it in its own activity), so taps on a profile, quoted note,
 * hashtag or channel go to the [nav] the host hands in.
 */
@Composable
fun ColumnScope.NestChatPanel(
    event: MeetingSpaceEvent,
    roomNote: AddressableNote,
    viewModel: NestViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.chat.collectAsState()
    val scope = rememberCoroutineScope()

    val routeForLastRead = remember(event) { "NestChat/${event.address().toValue()}" }

    val nestScreenModel: NestNewMessageViewModel =
        rememberViewModel(key = "Nest/${event.address().toValue()}") { NestNewMessageViewModel() }
    nestScreenModel.init(accountViewModel)
    nestScreenModel.load(roomNote)

    val listState = rememberLazyListState()

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

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
        ) {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringRes(Res.string.nest_chat_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            } else {
                NestChatMessageList(
                    messages = messages,
                    listState = listState,
                    routeForLastRead = routeForLastRead,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onWantsToReply = nestScreenModel::reply,
                    onWantsToEditDraft = nestScreenModel::editFromDraft,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

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

@Composable
private fun NestChatMessageList(
    messages: List<Note>,
    routeForLastRead: String,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,
    ) {
        items(
            items = messages,
            key = { it.idHex },
        ) { note ->
            ChatroomMessageCompose(
                baseNote = note,
                routeForLastRead = routeForLastRead,
                accountViewModel = accountViewModel,
                nav = nav,
                onWantsToReply = onWantsToReply,
                onWantsToEditDraft = onWantsToEditDraft,
            )
        }
    }
}
