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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.dal.relayGroupDiscoveryChannelFor
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.WarmNip11
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.toGroupConstraints
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_group_badge_invite_only
import com.vitorpamplona.amethyst.commons.resources.relay_group_badge_live
import com.vitorpamplona.amethyst.commons.resources.relay_group_badge_private
import com.vitorpamplona.amethyst.commons.resources.relay_group_browse_title
import com.vitorpamplona.amethyst.commons.resources.relay_group_favorite_relay
import com.vitorpamplona.amethyst.commons.resources.relay_group_member_count
import com.vitorpamplona.amethyst.commons.resources.relay_group_message_count_short_capped
import com.vitorpamplona.amethyst.commons.resources.relay_group_relay_group_count
import com.vitorpamplona.amethyst.commons.resources.select_list_to_filter
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.FeedFilterSpinner
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.UserDrawerSearchTopBar
import com.vitorpamplona.amethyst.commons.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupCardWarmupSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupsDiscoveryFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

/**
 * Discover NIP-29 groups across the relay set the top-bar filter resolves to. Built on the shared
 * feed stack exactly like the Git-repositories screen: a [FeedContentState]
 * ([com.vitorpamplona.amethyst.commons.viewmodels.AccountFeedContentStates.relayGroupsDiscoveryFeed])
 * fed by the per-type datasource, a [FeedFilterSpinner] persisting the selection to
 * `defaultRelayGroupsDiscoveryFollowList`, and a [RefresheableBox] + [RenderFeedContentState]. Rows
 * are the relay-signed 39000 metadata notes, rendered as joinable group cards.
 */
@Composable
fun RelayGroupDiscoveryScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RelayGroupDiscoveryScreen(
        feedContentState = accountViewModel.feedStates.relayGroupsDiscoveryFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun RelayGroupDiscoveryScreen(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedContentState)
    WatchAccountForRelayGroupDiscovery(feedContentState, accountViewModel)
    RelayGroupsDiscoveryFilterAssemblerSubscription(accountViewModel)
    // The joined groups' metadata + rosters are kept live by the always-on state sub (mounted at
    // LoggedInPage), so the "My Groups" filter can list them without a per-screen subscription.

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = { RelayGroupsDiscoveryTopBar(accountViewModel, nav) },
        bottomBar = {
            AppBottomBar(Route.RelayGroups, nav, accountViewModel) { route ->
                if (route == Route.RelayGroups) feedContentState.sendToTop() else nav.navBottomBar(route)
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                FloatingActionButton(
                    onClick = { nav.nav(Route.RelayGroupBrowse) },
                    shape = CircleShape,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Link,
                        contentDescription = stringRes(Res.string.relay_group_browse_title),
                    )
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(feedContentState, true) {
            SaveableFeedContentState(feedContentState, scrollStateKey = ScrollStateKeys.RELAY_GROUPS_DISCOVERY_SCREEN) { listState ->
                RenderFeedContentState(
                    feedContentState = feedContentState,
                    accountViewModel = accountViewModel,
                    listState = listState,
                    nav = nav,
                    routeForLastRead = null,
                    onLoaded = { loaded ->
                        val items by loaded.feed.collectAsStateWithLifecycle()
                        // Collect the kind-3 follow set once for the whole list; each row highlights
                        // the members it contains ("people you follow who are in here").
                        val follows by accountViewModel.account.kind3FollowList.flow
                            .collectAsStateWithLifecycle()
                        // Relay Rail: instead of a flat list of tall cards, cluster the groups by their
                        // host relay. Each relay becomes a slim header + a block of thin inbox-style
                        // rows. Resolving a note to its channel is a cache lookup (no subscription), so
                        // bucketing the whole feed here is cheap; the per-row live state (metadata,
                        // message preview) still streams lazily inside each row as it scrolls on.
                        val entries = remember(items.list) { toRelayRailEntries(items.list) }
                        LazyColumn(
                            contentPadding = rememberFeedContentPadding(FeedPadding),
                            modifier = Modifier.fillMaxWidth(),
                            state = listState,
                        ) {
                            itemsIndexed(
                                entries,
                                key = { _, entry -> entry.key },
                                contentType = { _, entry -> entry::class },
                            ) { _, entry ->
                                when (entry) {
                                    is RelayRailEntry.Header ->
                                        RelayRailHeader(entry.relay, entry.groupCount, Modifier.animateItem(), accountViewModel, nav)
                                    is RelayRailEntry.GroupRow ->
                                        RelayGroupRailRow(entry.note, entry.isFirst, entry.isLast, Modifier.animateItem(), follows.authors, accountViewModel, nav)
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Root top bar for the Relay Groups tab — the shared drawer/search bar every top-level feed uses,
 * with the feed-filter spinner as its title (mirrors GitRepositoriesTopBar). The browse-a-relay
 * action lives on the FAB, not here, so the bar stays consistent with the other screens.
 */
@Composable
private fun RelayGroupsDiscoveryTopBar(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // No seed: this lists groups across relays, so there is no one `group:` id to carry. A
    // single room's own top bar seeds it.
    UserDrawerSearchTopBar(accountViewModel, nav, null) {
        val selectedFilter by accountViewModel.account.settings.defaultRelayGroupsDiscoveryFollowList
            .collectAsStateWithLifecycle()
        val options by accountViewModel.feedStates.feedListOptions.relayGroupsDiscoveryRoutes
            .collectAsStateWithLifecycle()

        FeedFilterSpinner(
            placeholderCode = selectedFilter,
            explainer = stringRes(Res.string.select_list_to_filter),
            options = options,
            onSelect = { accountViewModel.account.settings.changeDefaultRelayGroupsDiscoveryFollowList(it.code) },
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
private fun WatchAccountForRelayGroupDiscovery(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listName by accountViewModel.account.settings.defaultRelayGroupsDiscoveryFollowList
        .collectAsStateWithLifecycle()
    val perRelay by accountViewModel.account.liveRelayGroupsDiscoveryFollowListsPerRelay
        .collectAsStateWithLifecycle()
    // The "My Groups" filter reads the kind-10009 joined list, so re-scan when it changes
    // (join/leave) — otherwise a newly-joined group wouldn't appear until another refresh.
    val joinedGroups by accountViewModel.account.relayGroupList.liveRelayGroupList
        .collectAsStateWithLifecycle()
    val joinedServers by accountViewModel.account.relayGroupList.liveRelayGroupServers
        .collectAsStateWithLifecycle()
    val favoriteRelays by accountViewModel.account.favoriteRelayList.flow
        .collectAsStateWithLifecycle()

    // Discovery only shows groups whose 39000 is signed by the host relay's own key (NIP-29's
    // authority — the NIP-11 `self` pubkey; general relays instead carry stray user-published 39000s
    // that can't be joined). The feed reads that from each relay's cached NIP-11, so warm every
    // candidate relay (in parallel — WarmNip11) and re-invalidate as each doc lands, otherwise a
    // relay whose NIP-11 resolves after its 39000s would stay hidden until a manual refresh.
    //
    // The follow-list filter sets resolve relays via the outbox model, but a group's 39000 lives on
    // its HOST relay. In All-Follows the subassembler probes those host relays (joined kind-10009 +
    // favorited kind-10012) for follow rosters, so warm them here too — otherwise their groups get
    // fetched but the self-key gate reads an unwarmed (empty, self=null) NIP-11 and hides them until
    // the user bounces through Global (whose relay set happens to include them).
    val candidateRelays =
        remember(perRelay, joinedServers, favoriteRelays) {
            buildSet {
                addAll(perRelay.toGroupConstraints().keys)
                joinedServers.forEach { server -> RelayUrlNormalizer.normalizeOrNull(server)?.let(::add) }
                addAll(favoriteRelays)
            }
        }
    var nip11Version by remember { mutableIntStateOf(0) }
    WarmNip11(candidateRelays) { nip11Version++ }

    LaunchedEffect(listName, perRelay, joinedGroups) {
        feedContentState.checkKeysInvalidateDataAndSendToTop()
    }

    // A NIP-11 doc resolving doesn't change the feed key (the self-key test lives in the filter's
    // match, not the key), so force a re-filter directly as each one lands.
    LaunchedEffect(nip11Version) {
        feedContentState.invalidateData()
    }
}

/**
 * One flattened entry in the Relay Rail list: either a relay [Header] that opens a cluster, or a
 * [GroupRow] for one group inside the cluster just above it. [isFirst]/[isLast] mark a row's
 * position in its relay block so it can round the right corners and skip the top divider.
 */
private sealed interface RelayRailEntry {
    val key: String

    data class Header(
        val relay: NormalizedRelayUrl,
        val groupCount: Int,
    ) : RelayRailEntry {
        override val key get() = "relay:" + relay.url
    }

    data class GroupRow(
        val note: Note,
        val isFirst: Boolean,
        val isLast: Boolean,
    ) : RelayRailEntry {
        override val key get() = note.idHex
    }
}

/**
 * Bucket the flat discovery feed by each group's host relay, preserving the relay's first-seen
 * order and the feed order within it, then flatten into header + row entries. Resolving a note to
 * its channel is a cache lookup ([relayGroupDiscoveryChannelFor]) with no subscription, so doing it
 * for the whole list here is cheap; the live per-row state still streams lazily as rows scroll on.
 */
private fun toRelayRailEntries(notes: List<Note>): List<RelayRailEntry> {
    val buckets = LinkedHashMap<NormalizedRelayUrl, MutableList<Note>>()
    notes.forEach { note ->
        val relay = relayGroupDiscoveryChannelFor(note)?.groupId?.relayUrl ?: return@forEach
        buckets.getOrPut(relay) { ArrayList() }.add(note)
    }
    val entries = ArrayList<RelayRailEntry>(buckets.size + notes.size)
    buckets.forEach { (relay, groupNotes) ->
        entries.add(RelayRailEntry.Header(relay, groupNotes.size))
        groupNotes.forEachIndexed { i, note ->
            entries.add(RelayRailEntry.GroupRow(note, isFirst = i == 0, isLast = i == groupNotes.lastIndex))
        }
    }
    return entries
}

/**
 * The slim header that owns a relay's cluster: the relay's NIP-11 icon + name, how many groups it
 * hosts, and the favorite star. Tapping the name area opens that relay's full group list
 * ([Route.RelayGroupServer]); the star is a separate tap target that toggles the relay in the
 * kind-10012 relay-feeds list (favoriting it, and every group it hosts, under the relay filter).
 */
@Composable
private fun RelayRailHeader(
    relay: NormalizedRelayUrl,
    groupCount: Int,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val info = loadRelayInfo(relay)
    val host = relay.displayUrl()
    val name = info.value.name?.takeIf { it.isNotBlank() } ?: host
    val favoriteRelays by accountViewModel.account.favoriteRelayList.flow
        .collectAsStateWithLifecycle()
    val isFavorite = relay in favoriteRelays

    Row(
        modifier = modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { nav.nav(Route.RelayGroupServer(relay.url)) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RenderRelayIcon(
                displayUrl = host,
                iconUrl = info.value.icon,
                loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                loadRobohash = LocalDisplaySettings.current.loadRobohash,
                pingInMs = 0,
                iconModifier = Modifier.size(Size25dp).clip(CircleShape),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (name != host) {
                    Text(
                        text = host,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = pluralStringRes(Res.plurals.relay_group_relay_group_count, groupCount, groupCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            symbol = if (isFavorite) MaterialSymbols.Star else MaterialSymbols.StarBorder,
            contentDescription = stringRes(Res.string.relay_group_favorite_relay),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .clip(CircleShape)
                    .clickable {
                        if (isFavorite) accountViewModel.unfollowRelayFeed(relay) else accountViewModel.followRelayFeed(relay)
                    }.padding(6.dp)
                    .size(18.dp),
        )
    }
}

/**
 * Resolves the 39000 metadata note to its live [RelayGroupChannel] and renders it as a thin
 * inbox-style row inside its relay's block. Recomposes in place as the relay-signed metadata /
 * roster / message preview streams in. While on screen it warms the group's recent content
 * (contentOnly) so tapping it opens an already-populated screen; the warm-up tears down as the row
 * scrolls off. The card's old `about` description is intentionally dropped — the last message and
 * activity signals below say what's happening far better than a static blurb.
 */
@Composable
private fun RelayGroupRailRow(
    note: Note,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier,
    follows: Set<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val baseChannel = remember(note) { relayGroupDiscoveryChannelFor(note) } ?: return

    RelayGroupCardWarmupSubscription(baseChannel, accountViewModel.dataSources().relayGroupCardWarmup, accountViewModel, contentOnly = true)

    val channelState by baseChannel
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val channel = channelState.channel as? RelayGroupChannel ?: baseChannel

    // The warm-up prefetch loads the group's recent kind-9 chats into the channel's note cache and
    // re-emits this flow as they land, so the last-message line, its timestamp, and the activity
    // count fill in (and refresh whenever the row reloads). It's a recent-activity snapshot, not a
    // live ticker: the always-on live chat tail is joined-groups-only, and a non-member generally
    // isn't streamed a discovery group's new messages.
    val notesState by baseChannel
        .flow()
        .notes.stateFlow
        .collectAsStateWithLifecycle()
    val messageCount = notesState.channel.notes.size()
    val lastNote = notesState.channel.lastNote

    val participatingFollows = remember(channelState, follows) { channel.participatingFollows(follows) }

    val blockColor = MaterialTheme.colorScheme.surfaceContainerLow
    val shape =
        RoundedCornerShape(
            topStart = if (isFirst) 14.dp else 0.dp,
            topEnd = if (isFirst) 14.dp else 0.dp,
            bottomStart = if (isLast) 14.dp else 0.dp,
            bottomEnd = if (isLast) 14.dp else 0.dp,
        )

    Column(modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        Surface(
            onClick = { nav.nav(routeFor(channel)) },
            shape = shape,
            color = blockColor,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                if (!isFirst) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 63.dp),
                    )
                }
                RelayGroupRailRowContent(
                    channel = channel,
                    lastNote = lastNote,
                    messageCount = messageCount,
                    participatingFollows = participatingFollows,
                    blockColor = blockColor,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
private fun RelayGroupRailRowContent(
    channel: RelayGroupChannel,
    lastNote: Note?,
    messageCount: Int,
    participatingFollows: List<HexKey>,
    blockColor: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        RobohashFallbackAsyncImage(
            robot = channel.groupId.id,
            model = channel.profilePicture(),
            contentDescription = channel.toBestDisplayName(),
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
            autoPlayGif = autoPlayGif,
        )

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = channel.toBestDisplayName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // When the group last had activity — the honest recency signal on a discovery screen,
                // where a non-joined group's chat isn't a live stream. Recomputed as newer messages
                // load, so it tracks whatever the preview shows.
                lastNote?.createdAt()?.let { ts ->
                    Text(
                        text = timeAgo(ts, prefix = ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            RelayGroupPreviewLine(channel, lastNote, accountViewModel)
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            RelayGroupStatusPill(channel)
            if (participatingFollows.isNotEmpty()) {
                CompactFollowFaces(participatingFollows, blockColor, accountViewModel, nav)
            } else if (messageCount > 0) {
                MessageActivityBadge(messageCount)
            }
        }
    }
}

/**
 * The line under a group name: the newest message as "author: text" — the live "what's happening"
 * signal that replaces the old static description. Author names resolve reactively (hex → profile
 * name). Before any message has streamed in, falls back to the member count so the row isn't bare.
 */
@Composable
private fun RelayGroupPreviewLine(
    channel: RelayGroupChannel,
    lastNote: Note?,
    accountViewModel: AccountViewModel,
) {
    val event = lastNote?.event
    val author = lastNote?.author
    val preview: String =
        if (event != null && author != null) {
            val authorName by observeUserName(author, accountViewModel)
            val body = event.content.take(80)
            if (body.isBlank()) authorName else "$authorName: $body"
        } else if (event != null) {
            event.content.take(80)
        } else {
            val memberCount = channel.memberCount()
            if (memberCount > 0) {
                pluralStringRes(Res.plurals.relay_group_member_count, memberCount, memberCount)
            } else {
                return
            }
        }
    Text(
        text = preview,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The top badge on a row's right edge. A live audio room (NIP-53 / LiveKit) wins with a green
 * pulsing "LIVE"; otherwise invite-only, then private. Open groups show nothing here so the row
 * stays quiet.
 */
@Composable
private fun RelayGroupStatusPill(channel: RelayGroupChannel) {
    when {
        channel.hasLivekit() ->
            Surface(shape = RoundedCornerShape(6.dp), color = RelayGroupLiveColor.copy(alpha = 0.16f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(RelayGroupLiveColor))
                    Text(
                        text = stringRes(Res.string.relay_group_badge_live),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RelayGroupLiveColor,
                    )
                }
            }
        // `closed` alone doesn't mean invite-only on Buzz (it stamps every channel); only badge it
        // where membership is actually required to participate.
        channel.requiresMembershipToPost() && channel.isClosed() ->
            TonalTextPill(stringRes(Res.string.relay_group_badge_invite_only))
        channel.isPrivate() ->
            TonalTextPill(stringRes(Res.string.relay_group_badge_private))
        else -> {}
    }
}

/** A small amber tonal status pill (invite-only / private). */
@Composable
private fun TonalTextPill(label: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.warningColor.copy(alpha = 0.16f)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.warningColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * The loaded-message counter, rendered as a neutral chat-icon + count stat ("50+" when the preview
 * page is full, [DISCOVERY_MESSAGE_CAP] tracking that limit; the exact count otherwise). Deliberately
 * NOT the accent color and NOT a filled pill — a violet badge here read as an unread indicator, which
 * this is not; it's an at-a-glance activity signal, so it uses the muted onSurfaceVariant tone.
 */
@Composable
private fun MessageActivityBadge(messageCount: Int) {
    val label =
        if (messageCount >= DISCOVERY_MESSAGE_CAP) {
            stringRes(Res.string.relay_group_message_count_short_capped, DISCOVERY_MESSAGE_CAP)
        } else {
            messageCount.toString()
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Chat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Overlapping avatars (up to three) of the people I follow who are already in this group — the
 * strongest join signal, so it takes the row's right edge over the message counter when present.
 * Faces border in the row's own background so they cut cleanly out of the block.
 */
@Composable
private fun CompactFollowFaces(
    follows: List<HexKey>,
    blockColor: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
        follows.take(3).forEach { pubkey ->
            UserPicture(
                userHex = pubkey,
                size = Size20dp,
                pictureModifier = Modifier.border(1.5.dp, blockColor, CircleShape),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

/**
 * Display cap for the loaded-message counter — kept in step with the discovery preview's fetch
 * limit (RELAY_GROUP_WARMUP_LIMIT), so a chat that returns the full page reads as "50+".
 */
private const val DISCOVERY_MESSAGE_CAP = 50

/** Live-audio accent (green) for the row's LIVE pill — reads on both light and dark grounds. */
private val RelayGroupLiveColor = Color(0xFF17B978)
