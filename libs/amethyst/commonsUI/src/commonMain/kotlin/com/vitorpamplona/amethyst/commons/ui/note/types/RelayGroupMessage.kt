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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.nip29RelayGroups.ui.RelayNameChip
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.workspace.buzzParticipants
import com.vitorpamplona.quartz.buzz.workspace.isBuzzDm
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/**
 * Renders a Buzz relay-group message (a kind-9 [ChatEvent] or a kind-40002 stream message) as a
 * conversation row rather than a standalone post — the group/DM analog of [RenderChatMessage] /
 * [RenderChannelMessage]. Draws a [RelayGroupChannelHeader] (which for a DM titles by the other
 * participant) above the message body, so a Buzz DM on the Notifications tab reads like a message.
 *
 * The channel is taken from the note's gatherers, where [com.vitorpamplona.amethyst.commons.model.Channel.addNote]
 * records it on consume; without it (a stray message we never attached) we just render the body.
 */
@Composable
fun RenderRelayGroupMessage(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val channel = remember(note) { note.inGatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel } }

    channel?.let {
        RelayGroupChannelHeader(
            channel = it,
            modifier = MaterialTheme.colorScheme.replyModifier.padding(10.dp),
            accountViewModel = accountViewModel,
            nav = nav,
        )
        Spacer(modifier = StdVertSpacer)
    }

    // Body: kind-9 has its own renderer (its reply target is a `q`/`e` tag, not a NIP-10 thread);
    // everything else (kind-40002 stream messages) goes through the generic text renderer. Both pass
    // NONE so the header — not an inline reply-to preview — is the only chrome above the text.
    if (note.event is ChatEvent) {
        RenderChat(note, makeItShort, canPreview, quotesLeft, ReplyRenderType.NONE, backgroundColor, accountViewModel, nav)
    } else {
        RenderTextEvent(note, makeItShort, canPreview, quotesLeft, ReplyRenderType.NONE, backgroundColor, editState, accountViewModel, nav)
    }
}

/**
 * A compact, tappable header naming the Buzz group (or DM participant) a message belongs to, plus a
 * [RelayNameChip] naming its host relay — the same pairing the Messages row uses, and the NIP-29
 * analog of the [com.vitorpamplona.amethyst.commons.concord.ui.ConcordCommunityPill]
 * a Concord message wears on these same cards. A group id is only unique within its host relay, so
 * without the relay a notification from `#general` doesn't say *which* `#general` it came from. The
 * chip taps through to the relay's channel list, while the name/avatar open the room itself.
 */
@Composable
fun RelayGroupChannelHeader(
    channel: RelayGroupChannel,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val isDm = channel.event?.isBuzzDm() == true
    val dmOther =
        remember(channel) {
            if (isDm) channel.event?.buzzParticipants()?.firstOrNull { it != accountViewModel.userProfile().pubkeyHex } else null
        }

    Row(
        modifier = modifier.fillMaxWidth().clickable { nav.nav(routeFor(channel)) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RobohashFallbackAsyncImage(
            robot = channel.groupId.id,
            model = channel.profilePicture(),
            contentDescription = channel.toBestDisplayName(),
            modifier = Modifier.size(28.dp).clip(CircleShape),
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
        )

        if (dmOther != null) {
            RelayGroupDmName(dmOther, channel, accountViewModel, Modifier.weight(1f, fill = false))
        } else {
            Text(
                text = channel.toBestDisplayName(),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        RelayNameChip(
            label = channel.groupId.relayUrl.displayUrl(),
            onClick = { nav.nav(Route.RelayGroupServer(channel.groupId.relayUrl.url)) },
        )
    }
}

/** The other DM participant's display name (reactive), falling back to the channel name. */
@Composable
private fun RelayGroupDmName(
    otherPubkey: HexKey,
    channel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    modifier: Modifier,
) {
    val user = remember(otherPubkey) { LocalCache.getOrCreateUser(otherPubkey) }
    val name by observeUserName(user, accountViewModel)
    Text(
        text = name.ifBlank { channel.toBestDisplayName() },
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
