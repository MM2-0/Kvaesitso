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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nest_join
import com.vitorpamplona.amethyst.commons.resources.nest_lobby_host_label
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.types.MeetingSpaceClosedFlag
import com.vitorpamplona.amethyst.commons.ui.note.types.MeetingSpaceOpenFlag
import com.vitorpamplona.amethyst.commons.ui.note.types.MeetingSpacePlannedFlag
import com.vitorpamplona.amethyst.commons.ui.note.types.MeetingSpacePrivateFlag
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE

/**
 * Lobby card rendered in [com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.ChannelView]
 * for NIP-53 kind 30312 meeting-space events. Shows the room cover,
 * status, summary, host, speaker avatars, and a "Join nest" button.
 *
 * The lobby is intentionally thin: no audio session, no presence
 * event, no hand-raise. All of that lives behind the Join button so
 * the on-the-wire traffic only fires when the user actually wants to
 * be in the room.
 *
 * Hidden when the event has no `service` tag — those rooms are hosted
 * on non-nests servers we can't connect to.
 */
@Composable
fun NestJoinCard(
    baseChannel: LiveActivitiesChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(baseChannel.address) { addressableNote ->
        addressableNote ?: return@LoadAddressableNote
        val event = addressableNote.event as? MeetingSpaceEvent ?: return@LoadAddressableNote
        NestJoinCardContent(event, accountViewModel, nav)
    }
}

@Composable
private fun NestJoinCardContent(
    event: MeetingSpaceEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val serviceBase = event.service()
    val endpoint = event.endpoint()
    val roomId = event.address().dTag
    // Need the auth base, MoQ endpoint, and the room creator's pubkey to
    // mint a JWT scoped to this namespace. Skip rendering Join when any
    // are missing — those rooms aren't joinable on the audio plane.
    if (serviceBase.isNullOrBlank() || endpoint.isNullOrBlank() || roomId.isBlank()) return

    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val mutedTextColor = textColor.copy(alpha = 0.7f)

    val image = remember(event) { event.image() }
    val roomName = remember(event) { event.room() }
    val summary = remember(event) { event.summary() }
    val status = remember(event) { event.status() }
    val starts = remember(event) { event.starts() }
    val participants = remember(event) { event.participants() }
    // Host is whoever the kind:30312's `p`-tag explicitly marks `host`,
    // OR — when the host is implicit (no `p`-tag at all for them) — the
    // event author. Per EGG-07, the author is the host regardless of
    // whether they self-tag.
    val hostPubkey = event.pubKey
    val speakers =
        remember(participants, hostPubkey) {
            participants.filter { p ->
                p.pubKey != hostPubkey &&
                    (p.role.equals(ROLE.SPEAKER.code, true) || p.role.equals(ROLE.MODERATOR.code, true))
            }
        }
    val hostUser = remember(hostPubkey) { LocalCache.getOrCreateUser(hostPubkey) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!image.isNullOrBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop,
                )
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    roomName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(StdHorzSpacer)
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
                            MeetingSpacePlannedFlag(starts)
                        }

                        null -> {}
                    }
                }

                summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedTextColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClickableUserPicture(
                        baseUser = hostUser,
                        size = 36.dp,
                        accountViewModel = accountViewModel,
                        onClick = { user -> nav.nav(routeFor(user)) },
                    )
                    Spacer(StdHorzSpacer)
                    UsernameDisplay(
                        baseUser = hostUser,
                        weight = Modifier.weight(1f),
                        textColor = textColor,
                        accountViewModel = accountViewModel,
                    )
                    Spacer(StdHorzSpacer)
                    Text(
                        text = stringRes(Res.string.nest_lobby_host_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedTextColor,
                    )
                }

                if (speakers.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Cap visible speaker avatars so the row doesn't
                        // overflow on rooms with many speakers; the rest
                        // collapse into a "+N" label.
                        val cap = 5
                        val visible = remember(speakers, cap) { speakers.take(cap) }
                        visible.forEach { tag ->
                            ClickableUserPicture(
                                baseUserHex = tag.pubKey,
                                size = 24.dp,
                                accountViewModel = accountViewModel,
                                modifier = Modifier.padding(end = 4.dp),
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
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    JoinNestButton(
                        event = event,
                        nav = nav,
                    )
                }
            }
        }
    }
}

/**
 * "Join nest" entry button — navigates to [NestLobbyScreen] rather
 * than launching the audio activity directly. The lobby exposes the
 * cached chat plus an active composer, so a user who's just coming
 * back to read or chime in doesn't trigger a MoQ handshake or the
 * host's kind-30312 republish path. The actual room launch lives
 * behind the lobby's top-bar "Open" action.
 *
 * Renders nothing for events without a service / endpoint / d-tag —
 * those rooms can't be joined on the audio plane.
 *
 * [primaryColorOverride] lets the lobby card paint the button with
 * the room's themed primary color (`["c", hex, "primary"]`); other
 * call sites pass null and get the platform default.
 */
@Composable
fun JoinNestButton(
    event: MeetingSpaceEvent,
    nav: INav,
    primaryColorOverride: Color? = null,
) {
    val serviceBase = event.service()
    val endpoint = event.endpoint()
    val roomId = event.address().dTag
    if (serviceBase.isNullOrBlank() || endpoint.isNullOrBlank() || roomId.isBlank()) return

    val colors =
        if (primaryColorOverride != null) {
            androidx.compose.material3.ButtonDefaults
                .buttonColors(containerColor = primaryColorOverride)
        } else {
            androidx.compose.material3.ButtonDefaults
                .buttonColors()
        }

    val addressValue = event.address().toValue()
    Button(
        onClick = { nav.nav(Route.NestLobby(addressValue)) },
        colors = colors,
    ) {
        Text(stringRes(Res.string.nest_join))
    }
}
