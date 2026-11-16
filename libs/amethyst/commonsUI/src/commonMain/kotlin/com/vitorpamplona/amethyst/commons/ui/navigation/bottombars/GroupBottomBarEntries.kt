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
package com.vitorpamplona.amethyst.commons.ui.navigation.bottombars

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.ChannelState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.ChannelFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.concord_home_title
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.platform.rememberConcordImageModel
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The resolved presentation of a pinned chat/group [BottomBarEntry] — enough to render its avatar in
 * the bottom bar and its row in the settings picker, and to navigate when tapped. Resolved live from
 * the local cache, so the name/avatar fill in as the group's metadata arrives.
 */
@Immutable
data class GroupEntryDisplay(
    val label: String,
    val robotSeed: String,
    val model: String?,
    val route: Route,
)

/**
 * Observes a channel's metadata for a display row. [subscribe] gates the relay REQ:
 *  - the live bottom bar / rail passes true — a pinned group keeps a REQ open so its name/avatar
 *    refresh even if the group is never opened (bounded by the handful of pinned slots);
 *  - the settings picker passes false — it reads whatever metadata is already cached (filled by the
 *    chats/group screens) so expanding a category with many joined groups doesn't fan out into one
 *    subscription per row.
 */
@Composable
private fun observeChannelMetadata(
    channel: Channel,
    accountViewModel: AccountViewModel,
    subscribe: Boolean,
): State<ChannelState?> {
    if (subscribe) ChannelFinderFilterAssemblerSubscription(channel, accountViewModel)
    return channel
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
}

@Composable
fun rememberPublicChatEntryDisplay(
    entry: BottomBarEntry.PublicChat,
    accountViewModel: AccountViewModel,
    subscribe: Boolean = true,
): GroupEntryDisplay {
    val channel = remember(entry.channelId) { LocalCache.getOrCreatePublicChatChannel(entry.channelId) }
    val state by observeChannelMetadata(channel, accountViewModel, subscribe)
    val current = (state?.channel as? PublicChatChannel) ?: channel
    return GroupEntryDisplay(
        label = current.toBestDisplayName(),
        robotSeed = entry.channelId,
        model = current.profilePicture(),
        route = Route.PublicChatChannel(entry.channelId),
    )
}

@Composable
fun rememberRelayGroupEntryDisplay(
    entry: BottomBarEntry.RelayGroup,
    accountViewModel: AccountViewModel,
    subscribe: Boolean = true,
): GroupEntryDisplay {
    val relay = remember(entry.relayUrl) { RelayUrlNormalizer.normalizeOrNull(entry.relayUrl) }
    val channel = remember(entry.groupId, relay) { relay?.let { LocalCache.getOrCreateRelayGroupChannel(GroupId(entry.groupId, it)) } }
    // Always call the observer (with a null channel when the relay won't normalize) so the composable
    // call structure is unconditional; a null channel just yields a null state and the id fallback.
    val state by observeChannelMetadataOrNull(channel, accountViewModel, subscribe)
    val current = (state?.channel as? RelayGroupChannel) ?: channel

    // The name from the group's own kind-39000 metadata, once it has loaded.
    val metaName = current?.event?.name()?.ifBlank { null }
    // Otherwise the name the user's joined-groups list stored for this group (the NIP-51
    // ["group", id, relay, name] tag) — so the row reads as a name even before 39000 is fetched,
    // which the read-only settings picker never does.
    val joined by accountViewModel.account.relayGroupList.liveRelayGroupList
        .collectAsStateWithLifecycle()
    val tagName =
        remember(joined, entry) {
            joined.firstOrNull { it.groupId == entry.groupId && it.relayUrl == entry.relayUrl }?.name?.ifBlank { null }
        }

    return GroupEntryDisplay(
        label = metaName ?: tagName ?: entry.groupId,
        robotSeed = entry.groupId,
        model = current?.profilePicture(),
        route = Route.RelayGroup(entry.groupId, entry.relayUrl),
    )
}

/**
 * A pinned NIP-29 host relay ("server"). Its name/icon come from the relay's NIP-11 document (cached,
 * shared with the browse/server rows), falling back to the display host; tapping opens the relay's
 * home page listing every joined group on it.
 */
@Composable
fun rememberRelayServerEntryDisplay(
    entry: BottomBarEntry.RelayServer,
    accountViewModel: AccountViewModel,
): GroupEntryDisplay {
    val relay = remember(entry.relayUrl) { RelayUrlNormalizer.normalizeOrNull(entry.relayUrl) }
    val host = remember(relay, entry.relayUrl) { relay?.displayUrl() ?: entry.relayUrl }
    // NIP-11 is its own HTTP cache (not a relay REQ), so this is safe even for the read-only picker.
    val info = relay?.let { loadRelayInfo(it) }
    val name = info?.value?.name?.takeIf { it.isNotBlank() } ?: host
    return GroupEntryDisplay(
        label = name,
        robotSeed = entry.relayUrl,
        model = info?.value?.icon?.ifBlank { null },
        route = Route.RelayGroupServer(entry.relayUrl),
    )
}

/** [observeChannelMetadata] tolerant of a null channel (unresolvable relay), so callers avoid an early return. */
@Composable
private fun observeChannelMetadataOrNull(
    channel: Channel?,
    accountViewModel: AccountViewModel,
    subscribe: Boolean,
): State<ChannelState?> {
    if (channel == null) return remember { mutableStateOf<ChannelState?>(null) }
    return observeChannelMetadata(channel, accountViewModel, subscribe)
}

@Composable
fun rememberConcordEntryDisplay(
    entry: BottomBarEntry.Concord,
    accountViewModel: AccountViewModel,
): GroupEntryDisplay {
    val account = accountViewModel.account
    // Recompute the folded metadata (name / icon) whenever a Control Plane folds.
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    val communities by account.concordChannelList.liveCommunities.collectAsStateWithLifecycle()

    val session = remember(entry.communityId, revision) { account.concordSessions.sessionFor(entry.communityId) }
    val state by (session?.state ?: remember { MutableStateFlow(null) }).collectAsStateWithLifecycle()
    val metadata = state.takeIf { revision >= 0 }?.metadata

    val fallbackName = remember(communities, entry.communityId) { communities.firstOrNull { it.id == entry.communityId }?.name?.ifBlank { null } }
    val label = metadata?.name?.takeIf { it.isNotBlank() } ?: fallbackName ?: stringRes(Res.string.concord_home_title)
    val model = rememberConcordImageModel(metadata?.icon, accountViewModel)

    return GroupEntryDisplay(
        label = label,
        robotSeed = entry.communityId,
        model = model,
        route = Route.ConcordServer(entry.communityId),
    )
}

/**
 * A pinned Concord channel inside a community. The label is the channel's folded name; the avatar is
 * the *community's* icon (so the tab reads as "this community's channel"), keyed by the channel id so
 * distinct channels still get distinct robohash fallbacks. Tapping opens that channel directly.
 */
@Composable
fun rememberConcordChannelEntryDisplay(
    entry: BottomBarEntry.ConcordChannel,
    accountViewModel: AccountViewModel,
): GroupEntryDisplay {
    val account = accountViewModel.account
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    val session = remember(entry.communityId, revision) { account.concordSessions.sessionFor(entry.communityId) }
    val state by (session?.state ?: remember { MutableStateFlow(null) }).collectAsStateWithLifecycle()

    val folded = state.takeIf { revision >= 0 }
    val channelName =
        folded
            ?.channels
            ?.get(entry.channelId)
            ?.definition
            ?.name
            ?.ifBlank { null }
    val label = channelName ?: entry.channelId.take(8)
    val model = rememberConcordImageModel(folded?.metadata?.icon, accountViewModel)

    return GroupEntryDisplay(
        label = label,
        robotSeed = entry.channelId,
        model = model,
        route = Route.Concord(entry.communityId, entry.channelId),
    )
}

/**
 * A pinned geohash location channel. Anonymous by design — no metadata REQ; the row is a location
 * pin robohash keyed by the cell, labelled with the geohash, opening the location chat.
 */
@Composable
fun rememberGeohashEntryDisplay(entry: BottomBarEntry.Geohash): GroupEntryDisplay =
    remember(entry.geohash) {
        GroupEntryDisplay(
            label = "#${entry.geohash}",
            robotSeed = entry.geohash,
            model = null,
            route = Route.GeohashChat(entry.geohash),
        )
    }

/**
 * Resolves any chat/group entry to its live display, or null for a non-group entry. [subscribe] is
 * forwarded to the channel observers: true (the default) keeps a relay REQ open — used by the live
 * bar/rail; false reads only cached metadata — used by the settings picker (see [observeChannelMetadata]).
 */
@Composable
fun rememberGroupEntryDisplay(
    entry: BottomBarEntry,
    accountViewModel: AccountViewModel,
    subscribe: Boolean = true,
): GroupEntryDisplay? =
    when (entry) {
        is BottomBarEntry.PublicChat -> rememberPublicChatEntryDisplay(entry, accountViewModel, subscribe)
        is BottomBarEntry.RelayGroup -> rememberRelayGroupEntryDisplay(entry, accountViewModel, subscribe)
        is BottomBarEntry.RelayServer -> rememberRelayServerEntryDisplay(entry, accountViewModel)
        is BottomBarEntry.Concord -> rememberConcordEntryDisplay(entry, accountViewModel)
        is BottomBarEntry.ConcordChannel -> rememberConcordChannelEntryDisplay(entry, accountViewModel)
        is BottomBarEntry.Geohash -> rememberGeohashEntryDisplay(entry)
        is BottomBarEntry.BuiltIn -> null
        is BottomBarEntry.Favorite -> null
    }

/** The circular avatar for a pinned chat/group, shared by the bottom bar and the settings picker. */
@Composable
fun GroupEntryAvatar(
    display: GroupEntryDisplay,
    size: Dp,
    accountViewModel: AccountViewModel,
) {
    RobohashFallbackAsyncImage(
        robot = display.robotSeed,
        model = display.model,
        contentDescription = display.label,
        modifier = Modifier.size(size).clip(CircleShape),
        loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
        loadRobohash = LocalDisplaySettings.current.loadRobohash,
        autoPlayGif = false,
    )
}
