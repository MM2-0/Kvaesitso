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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_group_badge_invite_only
import com.vitorpamplona.amethyst.commons.resources.relay_group_badge_private
import com.vitorpamplona.amethyst.commons.resources.relay_group_field_banner
import com.vitorpamplona.amethyst.commons.resources.relay_group_member_count
import com.vitorpamplona.amethyst.commons.resources.relay_group_open
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.LoadRelayGroupChannel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupCardWarmupSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupInviteLink

/**
 * Renders a NIP-29 group invite link (`<relay>'<groupId>[?code=<code>]`) as a full-width
 * card. It draws immediately with a stable layout — a robohash avatar seeded from the group
 * id, the id as a placeholder name, and the host relay — then fills in the real name, picture
 * and member count reactively as the relay-signed metadata arrives, WITHOUT changing the
 * card's structure or height. While on screen it also warms the group (live metadata +
 * recent messages/threads) so tapping it opens an already-populated screen.
 */
@Composable
fun RelayGroupCard(
    linkText: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val invite = remember(linkText) { GroupInviteLink.parse(linkText) }
    if (invite == null) {
        // Detection produced this, so it should always parse; degrade to a plain link if not.
        ClickableRelayGroupLink(linkText, nav)
        return
    }

    val groupId = remember(invite) { invite.toGroupId() }

    LoadRelayGroupChannel(groupId) { channel ->
        RelayGroupCardContent(channel, invite.code, accountViewModel, nav)
    }
}

@Composable
private fun RelayGroupCardContent(
    baseChannel: RelayGroupChannel,
    inviteCode: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RelayGroupCardWarmupSubscription(baseChannel, accountViewModel.dataSources().relayGroupCardWarmup, accountViewModel)

    // Recompose in place when the relay-signed metadata / roster changes.
    val channelState by baseChannel
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val channel = channelState.channel as? RelayGroupChannel ?: baseChannel

    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    val memberCount = channel.memberCount()
    val relayLabel = channel.groupId.relayUrl.displayUrl()

    // Closed (invite-only) is the more actionable signal to a prospective joiner than
    // private, so it wins when both are set. Null for a plain open group.
    val statusBadge =
        when {
            channel.isClosed() -> stringRes(Res.string.relay_group_badge_invite_only)
            channel.isPrivate() -> stringRes(Res.string.relay_group_badge_private)
            else -> null
        }

    // Shown only when the group actually has an about, so description-less groups stay
    // compact. It appears (a small one-time grow) once the relay-signed metadata loads.
    val description = channel.summary()?.takeIf { it.isNotBlank() }

    ElevatedCard(
        onClick = {
            nav.nav(
                Route.RelayGroup(channel.groupId.id, channel.groupId.relayUrl.url, inviteCode = inviteCode),
            )
        },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        // NIP-29 `banner`: a wide header strip across the top of the card, when the group has one.
        val banner = channel.bannerPicture()?.takeIf { it.isNotBlank() }
        if (banner != null && LocalDisplaySettings.current.showProfilePictures) {
            AsyncImage(
                model = banner,
                contentDescription = stringRes(Res.string.relay_group_field_banner),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RobohashFallbackAsyncImage(
                    robot = channel.groupId.id,
                    model = channel.profilePicture(),
                    contentDescription = channel.toBestDisplayName(),
                    modifier =
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
                    loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                    loadRobohash = LocalDisplaySettings.current.loadRobohash,
                    autoPlayGif = autoPlayGif,
                )

                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = channel.toBestDisplayName(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (statusBadge != null) {
                            StatusPill(statusBadge)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (memberCount > 0) {
                            Icon(
                                symbol = MaterialSymbols.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = pluralStringRes(Res.plurals.relay_group_member_count, memberCount, memberCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = relayLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }

                Icon(
                    symbol = MaterialSymbols.ChevronRight,
                    contentDescription = stringRes(Res.string.relay_group_open),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

/** A small tonal pill flagging group visibility/join policy (e.g. Private / Invite-only). */
@Composable
private fun StatusPill(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
