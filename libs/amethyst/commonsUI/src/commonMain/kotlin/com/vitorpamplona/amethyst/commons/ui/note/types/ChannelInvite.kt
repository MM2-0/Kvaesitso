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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.toMembershipNotice
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_to_messages
import com.vitorpamplona.amethyst.commons.resources.channel_invite_added_you
import com.vitorpamplona.amethyst.commons.resources.channel_invite_ignore
import com.vitorpamplona.amethyst.commons.resources.channel_invite_leave
import com.vitorpamplona.amethyst.commons.resources.channel_invite_unknown_actor
import com.vitorpamplona.amethyst.commons.resources.relay_group_badge_invite_only
import com.vitorpamplona.amethyst.commons.resources.relay_group_badge_private
import com.vitorpamplona.amethyst.commons.resources.relay_group_member_count
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ObserveAndDrawInnerUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size22dp
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

private val BannerHeight = 84.dp
private val BlockShape = RoundedCornerShape(12.dp)
private val RosterFaceSize = 19.dp

/**
 * The body of a "somebody added you to a channel" row (kind 44100).
 *
 * Only the body: the row itself is an ordinary [com.vitorpamplona.amethyst.commons.ui.note.NoteCompose], so the
 * author header, overflow menu, reaction bar, last-read background and click-through all come from the
 * same code every other notification uses.
 *
 * ### Why the body carries the identity
 *
 * A kind-44100 is signed by the **relay keypair** — the relay is reporting a membership change it made,
 * so it really is the author. That has a visible consequence: `NoteCompose` draws its header from
 * `note.author`, a relay keypair has no kind-0 and no NIP-05, so line one falls through
 * `UsernameDisplay` to a bare `npub1…` and line two (`ObserveDisplayNip05Status`) renders nothing at
 * all. The header therefore identifies nobody a reader recognises, and everything that says what this
 * row is about has to live down here: who added you, and what they added you to.
 */
@Composable
fun RenderChannelInvite(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Collected, not read as .value: the workspace set arrives asynchronously (a 13534 roster landing
    // after this row is first drawn), and a plain .value read is a one-shot snapshot that never
    // recomposes — so an invite drawn before its workspace was known would stay blank. This is what
    // Compose's StateFlowValueCalledInComposition lint flags.
    val workspaces by accountViewModel.account.buzzWorkspaces.flow
        .collectAsStateWithLifecycle()
    val notice = remember(note, workspaces) { note.toMembershipNotice(workspaces) } ?: return
    // A withdrawn membership is not an invite. The projection already excludes these before a card is
    // built; re-checked here because this renderer is reachable from any NoteCompose over a 44100.
    if (notice.removed) return

    val groupId = remember(notice) { GroupId(notice.channelId, notice.relay) }
    val baseChannel = remember(groupId) { LocalCache.getOrCreateRelayGroupChannel(groupId) }

    // Recompose in place as the relay-signed metadata and roster land, so name, picture, member count
    // and description fill in without the row being rebuilt. observeChannel also mounts the channel
    // subscription, which is what actually goes and fetches the kind-39000 for a group the viewer has
    // never opened — the common case for an invite.
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? RelayGroupChannel ?: baseChannel

    val actorUser = remember(notice.actor) { notice.actor?.let { LocalCache.getOrCreateUser(it) } }

    Column(Modifier.fillMaxWidth()) {
        // Who did this. The header above is an npub belonging to the relay, so without this the row
        // never names a person — and the actor is the whole difference between "I joined this" and "a
        // stranger put me here".
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (actorUser != null) {
                UserPicture(actorUser, Size22dp, accountViewModel = accountViewModel, nav = nav)
                UsernameDisplay(actorUser, Modifier.weight(1f, fill = false), accountViewModel = accountViewModel)
            } else {
                Text(
                    text = stringRes(Res.string.channel_invite_unknown_actor),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringRes(Res.string.channel_invite_added_you),
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ChannelBanner(channel, accountViewModel) {
            // Opening a group that is not on my kind-10009 yet is exactly what this route supports, so
            // the viewer can look at the channel before answering.
            nav.nav(Route.RelayGroup(channel.groupId.id, channel.groupId.relayUrl.url))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            // Leave is separate from Ignore on purpose: Ignore is a local display choice that leaves you
            // in the roster, Leave is the kind-9022 that actually removes you from the channel. It is
            // also the destructive one, so it stays the lightest of the three.
            TextButton(onClick = { accountViewModel.leaveChannelInvite(channel) }) {
                Text(stringRes(Res.string.channel_invite_leave), color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = { accountViewModel.dismissChannelInvite(notice.channelId) }) {
                Text(stringRes(Res.string.channel_invite_ignore))
            }
            // Accepting *is* `addRelayGroupToMessages`, the same call behind the channel top bar's
            // "Add to Messages", so it carries that label rather than a second word for one action.
            //
            // Compact rather than a stock Button: the default 40dp height with 24dp of horizontal
            // padding is a call-to-action size, and on a row that already offers two other choices it
            // dominated them. This keeps the fill — Accept is unmistakably primary — at roughly the
            // optical height of the text buttons beside it.
            Button(
                onClick = { accountViewModel.acceptChannelInvite(channel) },
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                modifier = Modifier.height(34.dp),
            ) {
                Text(stringRes(Res.string.add_to_messages), fontSize = 13.sp)
            }
        }
    }
}

/**
 * The channel as a cover block: its picture edge to edge, name reversed out over a scrim, visibility
 * badge, then roster and description.
 *
 * The picture is drawn *over* a gradient rather than falling back to one, so a channel with no
 * `picture` — and one whose picture fails to load — both land on the same stable colour instead of an
 * empty band. No branch, no placeholder state.
 */
@Composable
private fun ChannelBanner(
    channel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val name = channel.toBestDisplayName()
    val picture = channel.profilePicture()
    val description = channel.summary()?.takeIf { it.isNotBlank() }
    val memberCount = channel.memberCount()
    val gradient = remember(channel.groupId.id) { identityGradient(channel.groupId.id) }

    val badge =
        when {
            // Closed (invite-only) is the more actionable signal to somebody deciding whether to stay
            // than private is, so it wins when both are set.
            channel.isClosed() -> stringRes(Res.string.relay_group_badge_invite_only)
            channel.isPrivate() -> stringRes(Res.string.relay_group_badge_private)
            else -> null
        }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(BlockShape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(BannerHeight).background(gradient)) {
            if (picture != null) {
                AsyncImage(
                    model = picture,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Scrim only over the lower half, where the name sits — a full-height wash would mute the
            // picture the block exists to show.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.68f),
                        ),
                    ),
            )

            if (badge != null) {
                Text(
                    text = badge,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Text(
                text = name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
            ChannelRosterLine(channel, memberCount, accountViewModel)

            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * "3 people you follow · 24 members · relay.host".
 *
 * The faces are [RelayGroupChannel.participatingFollows], not an arbitrary slice of the roster: whether
 * anyone you already follow is in a channel says far more about whether you want to be there than three
 * strangers' avatars do. Falls back to the bare count when the answer is nobody.
 */
@Composable
private fun ChannelRosterLine(
    channel: RelayGroupChannel,
    memberCount: Int,
    accountViewModel: AccountViewModel,
) {
    val follows by accountViewModel.account.kind3FollowList.flow
        .collectAsStateWithLifecycle()

    val known =
        remember(channel, follows) {
            channel.participatingFollows(follows.authors).take(3)
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        known.forEach { pubkey ->
            key(pubkey) {
                FollowedMemberFace(pubkey, accountViewModel)
            }
        }

        // "24 members · relay.host". The count carries its unit through the same plural every other
        // group surface uses (the workspace channel list, discovery, the parent picker), because a bare
        // number sitting immediately after the faces reads as counting the faces — "3 people you
        // follow" — which is the one thing it does not mean.
        val host = channel.groupId.relayUrl.displayUrl()
        Text(
            text =
                if (memberCount > 0) {
                    pluralStringRes(Res.plurals.relay_group_member_count, memberCount, memberCount) + " · " + host
                } else {
                    host
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FollowedMemberFace(
    pubkey: HexKey,
    accountViewModel: AccountViewModel,
) {
    val user = remember(pubkey) { LocalCache.getOrCreateUser(pubkey) }

    // The plain inner picture rather than BaseUserPicture: everyone on this line is by definition
    // somebody the viewer follows, so the following badge BaseUserPicture stacks on top would be three
    // identical checkmarks at 19dp saying nothing.
    ObserveAndDrawInnerUserPicture(user, RosterFaceSize, accountViewModel)
}

/**
 * A stable two-stop gradient derived from the group id, so every channel keeps the same colour across
 * launches and devices without anything being stored. Hue is the only thing the id chooses;
 * saturation and lightness are fixed so no channel can land on something unreadable behind white text.
 */
private fun identityGradient(groupId: String): Brush {
    var hash = 0
    groupId.forEach { hash = it.code + ((hash shl 5) - hash) }
    val hue = ((hash % 360) + 360) % 360
    return Brush.linearGradient(
        listOf(
            Color.hsl(hue.toFloat(), 0.52f, 0.42f),
            Color.hsl(((hue + 42) % 360).toFloat(), 0.58f, 0.28f),
        ),
    )
}
