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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.buzz.BuzzDmListViewModel
import com.vitorpamplona.amethyst.commons.buzz.ui.PresenceDot
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.newestTimelineNote
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzCommunityMembership
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayImportViewModel
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupDeletions
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.isRelaySignedRelayGroup
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.looksLikeNonNip29Relay
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_channel_create_title
import com.vitorpamplona.amethyst.commons.resources.buzz_community_add_people
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_new
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_section_empty
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_see_all_count
import com.vitorpamplona.amethyst.commons.resources.buzz_dm_title
import com.vitorpamplona.amethyst.commons.resources.buzz_forum_create_title
import com.vitorpamplona.amethyst.commons.resources.buzz_import_loading
import com.vitorpamplona.amethyst.commons.resources.relay_group_channels_empty
import com.vitorpamplona.amethyst.commons.resources.relay_group_channels_not_nip29
import com.vitorpamplona.amethyst.commons.resources.relay_group_create_title
import com.vitorpamplona.amethyst.commons.resources.relay_group_member_count
import com.vitorpamplona.amethyst.commons.resources.relay_group_role_member
import com.vitorpamplona.amethyst.commons.resources.relay_group_section_archived
import com.vitorpamplona.amethyst.commons.resources.relay_group_section_channels
import com.vitorpamplona.amethyst.commons.resources.relay_group_section_forums
import com.vitorpamplona.amethyst.commons.resources.relay_tor_clearnet_action
import com.vitorpamplona.amethyst.commons.resources.relay_tor_clearnet_body
import com.vitorpamplona.amethyst.commons.resources.relay_tor_clearnet_title
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.TimeAgoLabels
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeAgoLabels
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoShort
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.BuzzAddPeopleDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.BuzzImportRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.BuzzWorkspaceOverflowMenu
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.HiddenDmHeader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.observeChatPreviewText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.buzzTimelinePreviewSummary
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupCardWarmupSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupsOnRelaySubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.amethyst.commons.util.sortedBySnapshot
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_CHANNEL_TYPE_DM
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_CHANNEL_TYPE_FORUM
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/** A first screen's worth of recent messages to prefetch per visible group card, ahead of a tap. */
private const val CHANNEL_LIST_WARMUP_LIMIT = 10

/**
 * How long this relay's socket must stay down before the Tor→clearnet escape hatch is offered, so a
 * slow first connect (or a reconnect) isn't mistaken for a relay that blocks Tor exits.
 */
private const val TOR_CLEARNET_HINT_DELAY_MS = 6_000L

/**
 * Whether to offer the Tor→clearnet escape hatch for this relay ([TorClearnetBanner]).
 *
 * The banner accuses *this relay* of blocking Tor exits and offers a privacy downgrade to work
 * around it, so it has to rule out every other explanation — and be able to deliver:
 *
 * - [usesTor]: this relay is *actually* routed over Tor right now (`TorRelayEvaluation.useTor`, the
 *   same predicate the pool dials with). Tor being enabled globally says nothing about one relay —
 *   onion, localhost and the per-role presets (trusted / DM / new) each decide independently.
 * - [torIsUp]: the SOCKS proxy is Active. While Tor is still bootstrapping *nothing* Tor-routed
 *   connects, which is Tor's problem (and its own dialog's), not this relay's.
 * - [trustingMovesToClearnet]: the action on offer — adding the relay to the kind-10089 Trusted list —
 *   would actually change its routing. Under a preset that keeps trusted relays on Tor it wouldn't,
 *   and the button would be inert.
 * - [disconnectedLongEnough]: the socket has been continuously down for [TOR_CLEARNET_HINT_DELAY_MS].
 *   A relay that answers is reachable by definition, and one that merely reconnects is not a relay
 *   that blocks Tor — only a sustained silence is.
 */
internal fun shouldOfferTorClearnetFallback(
    usesTor: Boolean,
    torIsUp: Boolean,
    trustingMovesToClearnet: Boolean,
    disconnectedLongEnough: Boolean,
): Boolean = usesTor && torIsUp && trustingMovesToClearnet && disconnectedLongEnough

/**
 * Bottom room the list leaves for the floating action button: a 56dp FAB + the Scaffold's 16dp margin
 * + slack, so the last row's overflow menu stays tappable instead of sitting under the FAB. Matches
 * the value `JobBoardScreen` already uses.
 */
private val FAB_CLEARANCE = 96.dp

/**
 * Lists every channel a relay hosts (its kind 39000-39003 directory), so the user
 * can browse and open channels on that relay. The relay's directory is streamed by
 * [RelayGroupsOnRelaySubscription] and consumed into per-group channels; this
 * screen reads them back for the relay and renders them. Each visible card also warms
 * its group's recent messages so opening a chat lands on cached content.
 */
@Composable
fun RelayGroupChannelListScreen(
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) } ?: return

    RelayGroupsOnRelaySubscription(relay, accountViewModel.dataSources().relayGroupsOnRelay, accountViewModel)

    // Trust state drives the Tor→clearnet hint below AND a NIP-11 re-fetch: marking a relay Trusted
    // moves it off Tor onto clearnet, so a NIP-11 doc that failed over Tor must be re-fetched — its
    // cached error would otherwise keep the relay's `self` unknown for the whole TTL.
    val trustedRelays by accountViewModel.account.trustedRelayList.flow
        .collectAsStateWithLifecycle()
    val isTrusted = relay in trustedRelays

    // Warm the relay's NIP-11 so we can tell its genuine (relay-signed) groups from stray
    // user-published 39000s that a non-NIP-29 relay may also be storing. Re-keyed on trust so a move
    // to clearnet re-fetches over the new transport instead of serving the cached over-Tor failure.
    val nip11Cache = LocalCache.appHost.nip11Cache
    val relayInfo by produceState(nip11Cache.getFromCache(relay), relay, isTrusted) {
        if (isTrusted) nip11Cache.invalidate(relay)
        nip11Cache.loadRelayInfo(relay, onInfo = { value = it }, onError = { _, _, _ -> })
    }

    // Re-read the relay's channels whenever a group-metadata (kind 39000) event lands in
    // the cache — driven by LocalCache.observeEvents rather than a timer, so the list
    // updates as directory events arrive with no polling. The initial value is sorted too
    // so the first frame doesn't reshuffle when the first emission arrives.
    // remember the seed so the per-relay cache scan + sort runs once (on first composition / relay
    // change), not on every recomposition — produceState evaluates its initialValue argument eagerly.
    val initialChannels = remember(relay) { LocalCache.getRelayGroupChannelsOnRelay(relay).sortedBySnapshot { it.toBestDisplayName().lowercase() } }
    val allChannels by produceState(initialValue = initialChannels, relay) {
        LocalCache
            .observeEvents<GroupMetadataEvent>(Filter(kinds = listOf(GroupMetadataEvent.KIND)))
            .collectLatest {
                // Off the main thread, and only the newest: a relay streaming its directory emits
                // in bursts, and each rescan walks every cached channel and sorts by display name.
                value =
                    withContext(Dispatchers.Default) {
                        LocalCache.getRelayGroupChannelsOnRelay(relay).sortedBySnapshot { it.toBestDisplayName().lowercase() }
                    }
            }
    }

    // Channels this device has deleted (kind-9008). A delete is terminal — the relay drops the group —
    // but our cached 39000 (and a Buzz relay's stale re-announced 44100) would keep it in the list, so
    // filter them out everywhere below. Collected as a StateFlow so a delete removes the row live.
    val deletedChannels by RelayGroupDeletions.flow.collectAsStateWithLifecycle()

    // Prefer the relay's own genuine, relay-signed groups (39000 author == the NIP-11 `self`).
    // Recomputes as the NIP-11 doc resolves so real groups fill in and fakes stay hidden. But if
    // NIP-11 is unreachable (e.g. a Cloudflare-fronted relay that resets the plain HTTP GET while
    // still serving events over the socket), fall back to the dominant 39000 signer on this relay as
    // its de-facto signer — so its relay-signed groups still show while a stray user-published 39000
    // (a different author) stays filtered.
    val channels =
        remember(allChannels, relayInfo, deletedChannels) {
            val nip11Known = relayInfo.self != null || relayInfo.supported_nips != null
            val signed =
                if (nip11Known) {
                    allChannels.filter { isRelaySignedRelayGroup(it, relayInfo) }
                } else {
                    val dominantSigner =
                        allChannels
                            .mapNotNull { it.event?.pubKey }
                            .groupingBy { it }
                            .eachCount()
                            .maxByOrNull { it.value }
                            ?.key
                    if (dominantSigner != null) allChannels.filter { it.event?.pubKey == dominantSigner } else allChannels
                }
            signed.filterNot { it.groupId.toKey() in deletedChannels }
        }

    // Buzz relays expose no public group directory (membership is server-side), so `channels` above
    // stays empty for them. When this is a Buzz relay, fold in the membership-scoped channels
    // (kind-44100) so browsing the relay lists the channels you already belong to — each addable to
    // your kind-10009 list (so it then shows in Messages / Relay Groups). Same screen, one Browse.
    val isBuzz = BuzzRelayDialect.isBuzz(relay) || relayInfo.software?.contains("buzz", ignoreCase = true) == true

    // A Buzz relay lets any community member create a channel/forum (the creator becomes its owner),
    // and the relay rejects a non-member's kind-9007. We don't hard-gate the "+" on membership: the
    // NIP-43 roster (kind 13534) isn't fetched on this screen, so gating on it hid the "+" even from
    // admins. Instead we offer it on any Buzz community and let the relay enforce — the same approach
    // as the workspace overflow menu (Add people / Invite), whose own doc notes "any member sees them,
    // the relay only serves the owner/admin ones."
    val myPubkey = accountViewModel.account.signer.pubKey
    // Resolved once for the list, not per DM row.
    val timeLabels = rememberTimeAgoLabels()

    val buzzVm: BuzzRelayImportViewModel = rememberViewModel(key = "BuzzImport-${relay.url}") { BuzzRelayImportViewModel() }
    LaunchedEffect(relay, isBuzz) { if (isBuzz) buzzVm.bind(accountViewModel.account, relay.url) }
    val buzzChannels by buzzVm.channels.collectAsStateWithLifecycle()
    val buzzAdded by buzzVm.added.collectAsStateWithLifecycle()
    val buzzStatus by buzzVm.status.collectAsStateWithLifecycle()

    // This community's recent Direct Messages, shown inline below the channels (like Buzz's own
    // sidebar) instead of behind a separate drawer entry. Only mounted for Buzz relays.
    val dmVm: BuzzDmListViewModel = rememberViewModel(key = "BuzzDmInline-${relay.url}") { BuzzDmListViewModel() }
    LaunchedEffect(relay, isBuzz) { if (isBuzz) dmVm.bind(accountViewModel.account, relay.url) }
    val dmRows by dmVm.rows.collectAsStateWithLifecycle()
    val hiddenDmRows by dmVm.hiddenRows.collectAsStateWithLifecycle()
    // DMs I took off Messages, parked behind a collapsed "Hidden (N)" tail below the section. They
    // live here and not only on the full inbox screen because that screen sits behind a "see all"
    // row that never appears until a community has more DMs than fit inline — so without this, a
    // hidden DM in a small workspace would have no way back at all.
    var showHiddenDms by remember { mutableStateOf(false) }

    // A Buzz workspace's channels come in three flavours, distinguished by the relay-signed 39000
    // `channel_type`: chat "stream" channels, "forum" channels (threaded posts), and "dm" channels
    // (private — they belong in the Direct Messages section, never the channel list). Split the
    // membership set accordingly; look each id up in the full per-relay channel set (which also
    // carries the directory 39000s) so the split reacts as metadata lands. Union in the directory
    // ids so nothing the old flat list showed disappears.
    val channelsById = remember(allChannels) { allChannels.associateBy { it.groupId.id } }
    val buzzGroupIds =
        remember(buzzChannels, channels, deletedChannels) {
            val seen = LinkedHashSet<String>()
            // `channels` is already delete-filtered; also drop deleted ids from the membership-scoped
            // `buzzChannels` (kind-44100), which the relay can keep re-announcing after a delete.
            (buzzChannels + channels.map { it.groupId })
                .filterNot { it.toKey() in deletedChannels }
                .filter { seen.add(it.id) }
        }

    fun buzzTypeOf(groupId: GroupId): String? = channelsById[groupId.id]?.event?.buzzChannelType()

    // Starred channels float to the top of their section, then alphabetical.
    //
    // The name is the tie-break on purpose: [buzzGroupIds] is in *arrival* order (membership ids as
    // the ViewModel emitted them, then directory ids), so sorting on `starred` alone — a stable sort
    // over a boolean — left the underlying order at the mercy of whatever landed first. The list
    // visibly reshuffled in the second after opening, and came back differently each visit. Ordering
    // by a property of the channel instead makes the first frame the final order; a channel whose
    // 39000 hasn't arrived sorts by its id until the name lands.
    val starred by accountViewModel.account.buzzChannelStars.flow
        .collectAsStateWithLifecycle()

    fun buzzSortKey(groupId: GroupId): String = channelsById[groupId.id]?.toBestDisplayName()?.lowercase() ?: groupId.id

    // Archived channels (relay-signed `archived` tag on the 39000) drop out of their normal section and
    // gather in a collapsed "Archived" tail — the same hide-from-the-sidebar behavior the Buzz client
    // has. They stay reachable there so an admin can open one and Unarchive it from the top bar.
    fun isArchived(groupId: GroupId): Boolean = channelsById[groupId.id]?.isArchived() == true

    val buzzChatChannels =
        remember(buzzGroupIds, channelsById, starred) {
            buzzGroupIds
                .filter { buzzTypeOf(it).let { t -> t != BUZZ_CHANNEL_TYPE_FORUM && t != BUZZ_CHANNEL_TYPE_DM } && !isArchived(it) }
                .sortedWith(compareByDescending<GroupId> { it.id in starred }.thenBy { buzzSortKey(it) })
        }
    val buzzForumChannels =
        remember(buzzGroupIds, channelsById, starred) {
            buzzGroupIds
                .filter { buzzTypeOf(it) == BUZZ_CHANNEL_TYPE_FORUM && !isArchived(it) }
                .sortedWith(compareByDescending<GroupId> { it.id in starred }.thenBy { buzzSortKey(it) })
        }
    // Every archived non-DM channel (chat + forum together), newest section at the bottom.
    val buzzArchivedChannels =
        remember(buzzGroupIds, channelsById) {
            buzzGroupIds
                .filter { buzzTypeOf(it) != BUZZ_CHANNEL_TYPE_DM && isArchived(it) }
                .sortedBy { buzzSortKey(it) }
        }

    // Which sections the user has collapsed (session-scoped). Keyed by section id below. Archived
    // starts collapsed — it's the out-of-the-way tail, expanded only when someone goes looking.
    var collapsedSections by remember { mutableStateOf(setOf("archived")) }

    fun toggleSection(key: String) {
        collapsedSections = if (key in collapsedSections) collapsedSections - key else collapsedSections + key
    }

    // Community add-member (kind-9030). Offered to members of a Buzz workspace; the relay enforces
    // the owner/admin requirement (a non-admin's command is simply rejected), since our NIP-43
    // roster read drops roles and can't gate precisely.
    var showAddPeople by remember { mutableStateOf(false) }

    // Tor-failure escape hatch: a Cloudflare-fronted (or otherwise Tor-hostile) relay times out over
    // Tor. Offer to reach it over clearnet — which adds it to the kind-10089 Trusted Relay List
    // (connected over clearnet even while Tor stays on for everything else).
    //
    // Every input below is a live signal, because a grace timer on its own says nothing about the
    // relay: the banner used to fire on `torType != OFF && !onion && !trusted` plus a 6s delay, so on
    // a working, answering relay it appeared after six seconds and never went away — the socket state
    // was never consulted, and neither was whether this relay is Tor-routed at all (Tor being *on*
    // doesn't mean this relay goes through it; the per-role presets decide).
    val torEvaluation =
        accountViewModel.host.torRelayEvaluation
            .collectAsStateWithLifecycle()
    // The same predicate the relay pool itself dials with, so the banner can't claim Tor for a relay
    // the app is reaching over clearnet (onion / localhost / trusted-off-Tor are all folded in here).
    val usesTor by remember(relay) { derivedStateOf { torEvaluation.value.useTor(relay) } }

    // While Tor is bootstrapping every Tor-routed relay is silent — that's Tor's own failure (and its
    // own dialog), so don't let it read as "this relay blocks Tor exits".
    val torIsUp by LocalAppServices.current.torBootstrapped
        .collectAsStateWithLifecycle(false)

    // The offer adds the relay to the kind-10089 Trusted list, which only moves it to clearnet while
    // trusted relays are *off* Tor. Under the Small-Payloads / Full-Privacy presets they are on Tor,
    // so the button would add an entry and change no routing at all — don't offer what can't help.
    val trustingMovesToClearnet by remember { derivedStateOf { !torEvaluation.value.torSettings.trustedRelaysViaTor } }

    // Global flow (any relay's connect/disconnect re-emits), so derive this relay's boolean.
    val connectedRelays =
        accountViewModel.account.client
            .connectedRelaysFlow()
            .collectAsStateWithLifecycle()
    val isConnected by remember(relay) { derivedStateOf { relay in connectedRelays.value } }

    // Debounced on the *connection* rather than armed once per screen: the countdown restarts every
    // time the socket drops and clears the moment it comes back, so a reconnect can't flash the
    // banner, and circuits that die mid-session still surface it. Keying the wait on cached content
    // instead would have hidden the offer exactly when a working relay went dark with a full screen.
    var disconnectedLongEnough by remember(relay) { mutableStateOf(false) }
    LaunchedEffect(relay, isConnected) {
        if (isConnected) {
            disconnectedLongEnough = false
        } else {
            delay(TOR_CLEARNET_HINT_DELAY_MS)
            disconnectedLongEnough = true
        }
    }
    val showTorHint = shouldOfferTorClearnetFallback(usesTor, torIsUp, trustingMovesToClearnet, disconnectedLongEnough)

    // A pinned relay works both as a pushed detail (from the drawer or another screen) and as a
    // bottom-nav tab. Read once here (it is @Composable): the back arrow shows only when pushed;
    // as a bottom-nav root the bar below takes its place and the arrow hides.
    val selfRoute = remember(relay) { Route.RelayGroupServer(relay.url) }

    Scaffold(
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    Text(
                        text = relay.displayUrl(),
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                },
                nav = nav,
                actions = {
                    if (isBuzz) {
                        BuzzWorkspaceOverflowMenu(
                            relay = relay,
                            accountViewModel = accountViewModel,
                            onAddPeople = { showAddPeople = true },
                            onOpenAgentConsole = { nav.nav(Route.AgentConsole(relay.url)) },
                            // Only offer "Add all" when some discovered channel isn't in the user's
                            // list yet — hidden once everything is already added.
                            onAddAll = if (buzzChatChannels.any { it.id !in buzzAdded }) ({ buzzVm.addAll() }) else null,
                        )
                    }
                },
            )
        },
        bottomBar = {
            // Hidden on in-app pushes (AppBottomBar renders only when nav.showsBottomBar()),
            // so a pinned NIP-29 relay works both as a pushed detail and as a bottom-nav tab.
            AppBottomBar(selfRoute, nav, accountViewModel) { route ->
                if (route != selfRoute) nav.navBottomBar(route)
            }
        },
        floatingActionButton = {
            // A Buzz community creates channels/forums from the per-section "+" in their labels (like
            // Direct Messages), so no FAB there. A vanilla NIP-29 relay is a flat directory with no
            // sections, so it keeps the FAB to create a group.
            if (!isBuzz) {
                FloatingActionButton(onClick = { nav.nav(Route.RelayGroupCreate(relay.url)) }, shape = CircleShape) {
                    Icon(
                        symbol = MaterialSymbols.Add,
                        contentDescription = stringRes(Res.string.relay_group_create_title),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
    ) { padding ->
        // A Buzz relay is a community, so always render its sectioned list (Channels, Forums, Direct
        // Messages, Agent Console) even before anything loads, rather than the generic "empty" text.
        if (channels.isEmpty() && !isBuzz) {
            // An empty directory on a relay whose NIP-11 says it doesn't run NIP-29 is almost
            // certainly the wrong relay, not a young one — say so instead of the generic empty text.
            val notNip29 = looksLikeNonNip29Relay(relayInfo)
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (showTorHint) {
                    TorClearnetBanner(
                        relayName = relay.displayUrl(),
                        onUseClearnet = {
                            accountViewModel.launchSigner { accountViewModel.account.saveTrustedRelayList((trustedRelays + relay).toList()) }
                        },
                    )
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (notNip29) stringRes(Res.string.relay_group_channels_not_nip29) else stringRes(Res.string.relay_group_channels_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (notNip29) MaterialTheme.colorScheme.warningColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
        } else {
            // The Scaffold's `padding` carries the top/bottom bars but deliberately not the FAB — a FAB
            // overlays content by design, so clearing it is the list's job. As contentPadding (not a
            // modifier) so rows scroll *through* that strip and only come to rest clear of it; the
            // modifier form would shrink the viewport and leave the FAB floating over dead space.
            // Only the vanilla NIP-29 path has a FAB now; a Buzz community creates from its section
            // headers, so it needs no bottom clearance.
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(bottom = if (isBuzz) 0.dp else FAB_CLEARANCE),
            ) {
                if (showTorHint) {
                    item(key = "tor-hint") {
                        TorClearnetBanner(
                            relayName = relay.displayUrl(),
                            onUseClearnet = {
                                accountViewModel.launchSigner { accountViewModel.account.saveTrustedRelayList((trustedRelays + relay).toList()) }
                            },
                        )
                    }
                }

                if (isBuzz) {
                    // While the membership fetch is still running and nothing has loaded, show a
                    // "Loading…" line. The old "you're not a member — accept the invite in the browser"
                    // empty text is gone: the section labels below now each carry a "+" to create a
                    // channel/forum, so an empty community is a starting point, not a dead end.
                    val noChannelsYet = buzzChatChannels.isEmpty() && buzzForumChannels.isEmpty()
                    if (noChannelsYet && buzzStatus is BuzzRelayImportViewModel.Status.Loading) {
                        item(key = "buzz-loading") {
                            Text(
                                text = stringRes(Res.string.buzz_import_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    }

                    // -- CHANNELS -- The label carries a "+" to create a channel (the community's FAB
                    // moved here, like Direct Messages). Add-all lives in the top-bar overflow menu.
                    // The header always shows so the "+" is available even before any channel loads;
                    // the collapse toggle is offered only when there's something to collapse.
                    run {
                        val channelsCollapsed = "channels" in collapsedSections
                        item(key = "sec-channels") {
                            RelayGroupSectionHeader(
                                title = stringRes(Res.string.relay_group_section_channels),
                                collapsed = channelsCollapsed,
                                onToggle = if (buzzChatChannels.isNotEmpty()) ({ toggleSection("channels") }) else null,
                            ) {
                                SectionAddButton(stringRes(Res.string.buzz_channel_create_title)) {
                                    nav.nav(Route.RelayGroupCreate(relay.url))
                                }
                            }
                        }
                        if (buzzChatChannels.isNotEmpty() && !channelsCollapsed) {
                            itemsIndexed(buzzChatChannels, key = { _, it -> "chat-${it.id}" }) { index, groupId ->
                                RowHairline(index)
                                BuzzImportRow(
                                    groupId = groupId,
                                    accountViewModel = accountViewModel,
                                    onOpen = { nav.nav(Route.RelayGroup(groupId.id, relay.url)) },
                                    isStarred = groupId.id in starred,
                                )
                            }
                        }
                    }

                    // -- FORUMS -- Same treatment: an always-visible label with a "+" that starts the
                    // create flow on a forum channel (threaded posts) instead of a chat one.
                    run {
                        val forumsCollapsed = "forums" in collapsedSections
                        item(key = "sec-forums") {
                            RelayGroupSectionHeader(
                                title = stringRes(Res.string.relay_group_section_forums),
                                collapsed = forumsCollapsed,
                                onToggle = if (buzzForumChannels.isNotEmpty()) ({ toggleSection("forums") }) else null,
                            ) {
                                SectionAddButton(stringRes(Res.string.buzz_forum_create_title)) {
                                    nav.nav(Route.RelayGroupCreate(relay.url, isForum = true))
                                }
                            }
                        }
                        if (buzzForumChannels.isNotEmpty() && !forumsCollapsed) {
                            itemsIndexed(buzzForumChannels, key = { _, it -> "forum-${it.id}" }) { index, groupId ->
                                RowHairline(index)
                                BuzzImportRow(
                                    groupId = groupId,
                                    accountViewModel = accountViewModel,
                                    // A forum channel's primary content is its threads (kind-45001 posts), not a
                                    // kind-9 chat, so open the forum/threads view directly instead of the chat.
                                    onOpen = { nav.nav(Route.RelayGroupThreads(groupId.id, relay.url)) },
                                    isStarred = groupId.id in starred,
                                    // Forum posts live in a separate thread store, not the chat notes the
                                    // activity preview reads — so don't warm a kind-9 sub that returns nothing.
                                    showActivityPreview = false,
                                )
                            }
                        }
                    }

                    // -- ARCHIVED -- Channels the relay has archived (chat + forum), tucked into a
                    // collapsed tail. Opening one and using the top-bar Unarchive brings it back.
                    if (buzzArchivedChannels.isNotEmpty()) {
                        val archivedCollapsed = "archived" in collapsedSections
                        item(key = "sec-archived") {
                            RelayGroupSectionHeader(
                                title = stringRes(Res.string.relay_group_section_archived),
                                collapsed = archivedCollapsed,
                                onToggle = { toggleSection("archived") },
                            )
                        }
                        if (!archivedCollapsed) {
                            itemsIndexed(buzzArchivedChannels, key = { _, it -> "archived-${it.id}" }) { index, groupId ->
                                RowHairline(index)
                                val isForum = buzzTypeOf(groupId) == BUZZ_CHANNEL_TYPE_FORUM
                                BuzzImportRow(
                                    groupId = groupId,
                                    accountViewModel = accountViewModel,
                                    onOpen = {
                                        if (isForum) {
                                            nav.nav(Route.RelayGroupThreads(groupId.id, relay.url))
                                        } else {
                                            nav.nav(Route.RelayGroup(groupId.id, relay.url))
                                        }
                                    },
                                    isStarred = groupId.id in starred,
                                    showActivityPreview = !isForum,
                                )
                            }
                        }
                    }

                    // -- DIRECT MESSAGES -- (this community's private conversations, most recent first)
                    item(key = "sec-dms") {
                        RelayGroupSectionHeader(title = stringRes(Res.string.buzz_dm_title)) {
                            IconButton(onClick = { nav.nav(Route.BuzzNewDm(relay.url)) }) {
                                Icon(
                                    symbol = MaterialSymbols.Add,
                                    contentDescription = stringRes(Res.string.buzz_dm_new),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                    if (dmRows.isEmpty()) {
                        item(key = "dm-empty") {
                            Text(
                                text = stringRes(Res.string.buzz_dm_section_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    } else {
                        val shown = dmRows.take(INLINE_DM_LIMIT)
                        itemsIndexed(shown, key = { _, it -> "dm-${it.channelId}" }) { index, row ->
                            RowHairline(index)
                            BuzzDmInlineRow(
                                row = row,
                                myPubkey = myPubkey,
                                timeLabels = timeLabels,
                                isHidden = false,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            ) {
                                nav.nav(Route.RelayGroup(row.channelId, row.relayUrl.url))
                            }
                        }
                        if (dmRows.size > INLINE_DM_LIMIT) {
                            val extra = dmRows.size - INLINE_DM_LIMIT
                            item(key = "dm-see-all") {
                                SeeAllRow(pluralStringRes(Res.plurals.buzz_dm_see_all_count, extra, extra)) {
                                    nav.nav(Route.BuzzDmList(relay.url))
                                }
                            }
                        }
                    }
                    if (hiddenDmRows.isNotEmpty()) {
                        item(key = "dm-hidden-header") {
                            HiddenDmHeader(
                                count = hiddenDmRows.size,
                                expanded = showHiddenDms,
                                onToggle = { showHiddenDms = !showHiddenDms },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        if (showHiddenDms) {
                            itemsIndexed(hiddenDmRows, key = { _, it -> "dm-hidden-${it.channelId}" }) { index, row ->
                                RowHairline(index)
                                BuzzDmInlineRow(
                                    row = row,
                                    myPubkey = myPubkey,
                                    timeLabels = timeLabels,
                                    isHidden = true,
                                    accountViewModel = accountViewModel,
                                    nav = nav,
                                ) {
                                    nav.nav(Route.RelayGroup(row.channelId, row.relayUrl.url))
                                }
                            }
                        }
                    }
                    // Agent Console now lives in the community's top-bar overflow menu, not a footer card.
                } else {
                    // Vanilla NIP-29 relay: flat channel directory (no forums/DMs/console).
                    itemsIndexed(channels, key = { _, channel -> channel.groupId.id }) { index, channel ->
                        RowHairline(index)
                        RelayGroupChannelRow(channel, myPubkey, accountViewModel) { nav.nav(routeFor(channel)) }
                    }
                }
            }
        }
    }

    if (showAddPeople) {
        BuzzAddPeopleDialog(
            title = stringRes(Res.string.buzz_community_add_people),
            accountViewModel = accountViewModel,
            isAlreadyIn = { BuzzCommunityMembership.isMember(relay, it) },
            onAdd = { accountViewModel.addCommunityMember(relay, it) },
            onDismiss = { showAddPeople = false },
        )
    }
}

/**
 * The hairline separating two adjacent rows within a section. Drawn *before* row [index], and never
 * before the first one, so a section's own header keeps providing the separation at its boundaries.
 */
@Composable
private fun RowHairline(index: Int) {
    if (index > 0) {
        HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A first screen's worth of a community's DMs shown inline; the rest live behind the See-all row. */
private const val INLINE_DM_LIMIT = 6

/**
 * Shown on the relay screen when a relay won't load over Tor (e.g. a Cloudflare-fronted relay that
 * blocks Tor exits). Offers to reach it over clearnet, which adds it to the Trusted Relay List.
 */
@Composable
private fun TorClearnetBanner(
    relayName: String,
    onUseClearnet: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringRes(Res.string.relay_tor_clearnet_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = stringRes(Res.string.relay_tor_clearnet_body, relayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.size(10.dp))
            FilledTonalButton(onClick = onUseClearnet, modifier = Modifier.align(Alignment.End)) {
                Text(stringRes(Res.string.relay_tor_clearnet_action))
            }
        }
    }
}

/**
 * A section divider label ("Channels", "Forums", "Direct messages") with an optional trailing
 * action (an Add-all button, a New-message icon) — the modern equivalent of the old flat headers.
 */
@Composable
private fun RelayGroupSectionHeader(
    title: String,
    collapsed: Boolean = false,
    onToggle: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
                .padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onToggle != null) {
            // A single chevron rotated 90° when expanded, so no extra glyph is needed.
            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).rotate(if (collapsed) 0f else 90f),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * The trailing "+" for a section label (Channels / Forums), matching the Direct Messages header's
 * New-message icon: a primary-tinted Add glyph that creates a new item of that section's type.
 */
@Composable
private fun SectionAddButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            symbol = MaterialSymbols.Add,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * One inline Direct-Message conversation row inside the community view: the counterpart's avatar +
 * name (or a "+N" cluster label for a group DM), a preview of the last message, and a compact
 * last-activity time. The channel's recent content is warmed while the row is visible so the preview
 * fills in ahead of a tap. Tapping opens the DM as its relay-group chat; the Add/Remove-from-Messages
 * (hide/unhide) action lives in that chat screen's top-bar overflow, not on this row.
 *
 * [isHidden] renders the row faded — a hidden DM is a live conversation the viewer merely parked, so
 * it stays openable and reversible from the opened conversation.
 */
@Composable
private fun BuzzDmInlineRow(
    row: BuzzDmListViewModel.DmRow,
    myPubkey: HexKey,
    timeLabels: TimeAgoLabels,
    isHidden: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClick: () -> Unit,
) {
    val others = row.others.ifEmpty { listOf(myPubkey) }
    val leadHex = others.first()
    val leadUser = remember(leadHex) { LocalCache.getOrCreateUser(leadHex) }
    val leadName by observeUserName(leadUser, accountViewModel)
    val label = if (others.size > 1) "$leadName +${others.size - 1}" else leadName

    val account = accountViewModel.account
    val groupId = remember(row.channelId, row.relayUrl) { GroupId(row.channelId, row.relayUrl) }
    val channel = remember(groupId) { LocalCache.getOrCreateRelayGroupChannel(groupId) }
    // Warm a screen's worth of recent DM messages while visible, so the preview isn't blank until opened.
    RelayGroupCardWarmupSubscription(
        channel,
        accountViewModel.dataSources().relayGroupCardWarmup,
        accountViewModel,
        contentOnly = true,
        contentLimit = CHANNEL_LIST_WARMUP_LIMIT,
    )
    val notesState by channel
        .flow()
        .notes.stateFlow
        .collectAsStateWithLifecycle()
    val lastNote = remember(notesState) { channel.newestTimelineNote(account) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
                .alpha(if (isHidden) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Only show a presence dot for a 1:1 DM — a cluster avatar can't carry one peer's status.
        Box {
            UserPicture(leadHex, 44.dp, accountViewModel = accountViewModel, nav = nav)
            if (others.size == 1) {
                PresenceDot(leadHex, Modifier.align(Alignment.BottomEnd), ringColor = MaterialTheme.colorScheme.surface)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BuzzDmPreviewLine(lastNote, accountViewModel)
        }
        if (row.lastActivity > 0) {
            Text(
                text = timeAgoShort(row.lastActivity, timeLabels),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The last-message preview under a DM row: "author: snippet", or nothing before any message loads. */
@Composable
private fun BuzzDmPreviewLine(
    lastNote: Note?,
    accountViewModel: AccountViewModel,
) {
    val event = lastNote?.event ?: return
    val author = lastNote.author
    val summary = buzzTimelinePreviewSummary(event, accountViewModel)
    val preview: String =
        when {
            summary != null -> summary
            author != null -> {
                val authorName by observeUserName(author, accountViewModel)
                val body = observeChatPreviewText(lastNote, 80)
                if (body.isBlank()) authorName else "$authorName: $body"
            }
            else -> observeChatPreviewText(lastNote, 80)
        }
    Text(
        preview,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The "See all N conversations" row that opens the full per-community DM inbox. */
@Composable
private fun SeeAllRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(symbol = MaterialSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RelayGroupChannelRow(
    channel: RelayGroupChannel,
    myPubkey: String,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    val joined = channel.membershipOf(myPubkey).isMember()
    val memberCount = channel.memberCount()

    // Anticipate a tap: while this row is on-screen, prefetch a first screen's worth of recent
    // messages for its group (content only — the directory subscription already streams metadata),
    // so opening the chat lands on cached content instead of a blank load. Bounded to visible rows
    // by the LazyColumn, and released as they scroll off.
    RelayGroupCardWarmupSubscription(
        channel,
        accountViewModel.dataSources().relayGroupCardWarmup,
        accountViewModel,
        contentOnly = true,
        contentLimit = CHANNEL_LIST_WARMUP_LIMIT,
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RobohashFallbackAsyncImage(
            robot = channel.groupId.id,
            model = channel.profilePicture(),
            contentDescription = channel.toBestDisplayName(),
            modifier = Modifier.size(40.dp).clip(CircleShape),
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
            autoPlayGif = autoPlayGif,
        )

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (channel.isPrivate()) {
                    Icon(
                        symbol = MaterialSymbols.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = channel.toBestDisplayName(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val subtitle =
                channel.summary()?.takeIf { it.isNotBlank() }
                    ?: if (memberCount > 0) {
                        pluralStringRes(Res.plurals.relay_group_member_count, memberCount, memberCount)
                    } else {
                        null
                    }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (joined) {
            Box(Modifier.size(20.dp).clip(CircleShape)) {
                Icon(
                    symbol = MaterialSymbols.Check,
                    contentDescription = stringRes(Res.string.relay_group_role_member),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
