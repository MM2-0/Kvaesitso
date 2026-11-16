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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.stage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nest_hand_raise_approve
import com.vitorpamplona.amethyst.commons.resources.nest_hand_raise_queue_title
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.participants.RoomParticipantActions
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.RoomPresence
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE

/**
 * Host-only queue of audience members whose latest kind-10312
 * presence has `hand=1` AND who aren't already a host / moderator /
 * speaker (they wouldn't have raised their hand if they could
 * already speak). Each row has an "Approve" button that promotes
 * the user to SPEAKER via [RoomParticipantActions.setRole].
 *
 * Hidden when the queue is empty so it doesn't take up vertical
 * space on a quiet room.
 *
 * Visibility gating to host-only is the caller's responsibility.
 */
@Composable
fun HandRaiseQueueSection(
    event: MeetingSpaceEvent,
    viewModel: NestViewModel,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onLongPressParticipant: ((String) -> Unit)? = null,
) {
    val presences by viewModel.presences.collectAsState()
    // Memoize the on-stage gate so a heartbeat that doesn't change
    // the kind-30312 doesn't re-build the set every recompose. The
    // event reference is stable across recompositions until the host
    // republishes; presence updates run through the second remember.
    val onStageKeys =
        remember(event) {
            event
                .participants()
                .filter { it.canSpeak() }
                .map { it.pubKey }
                .toSet()
        }
    val hands =
        remember(presences, onStageKeys) {
            presences.values
                .filter { it.handRaised && it.pubkey !in onStageKeys }
                .sortedBy { it.updatedAtSec }
        }

    if (hands.isEmpty()) return

    Column(modifier = modifier.fillMaxSize().padding(top = 12.dp)) {
        Text(
            text = stringRes(Res.string.nest_hand_raise_queue_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // LazyColumn so a flood of raised hands doesn't render every
        // row every recompose. Stable key by pubkey lets Compose
        // animate row entry/exit instead of teardown-rebuild.
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
            items(items = hands, key = { it.pubkey }) { hand ->
                HandRaiseRow(
                    hand = hand,
                    accountViewModel = accountViewModel,
                    onApprove = {
                        // launchSigner runs on viewModelScope (+ Dispatchers.IO)
                        // and surfaces signer errors as toasts. Composition-scoped
                        // alternatives get cancelled when this row leaves the tree:
                        // approving flips `canSpeak()` true, the hand is filtered
                        // out, and if it was the last hand the section disposes
                        // entirely — killing the in-flight sign(...) before it ran.
                        accountViewModel.launchSigner {
                            val template = RoomParticipantActions.setRole(event, hand.pubkey, ROLE.SPEAKER)
                            template?.let { accountViewModel.account.signAndComputeBroadcast(it) }
                        }
                    },
                    // Tap or long-press on the avatar opens the
                    // per-participant sheet — without this, a host
                    // had to switch tabs to Audience to view profile
                    // or kick a hand-raiser they didn't want to approve.
                    onOpenParticipantSheet = onLongPressParticipant,
                )
            }
        }
    }
}

@Composable
private fun HandRaiseRow(
    hand: RoomPresence,
    accountViewModel: AccountViewModel,
    onApprove: () -> Unit,
    onOpenParticipantSheet: ((String) -> Unit)? = null,
) {
    val user = remember(hand.pubkey) { LocalCache.getOrCreateUser(hand.pubkey) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClickableUserPicture(
            baseUserHex = hand.pubkey,
            size = Size35dp,
            accountViewModel = accountViewModel,
            onClick = onOpenParticipantSheet,
            onLongClick = onOpenParticipantSheet,
        )
        UsernameDisplay(
            baseUser = user,
            weight = Modifier.weight(1f),
            accountViewModel = accountViewModel,
        )
        Spacer(Modifier.width(4.dp))
        Button(onClick = onApprove) {
            Text(stringRes(Res.string.nest_hand_raise_approve))
        }
    }
}
