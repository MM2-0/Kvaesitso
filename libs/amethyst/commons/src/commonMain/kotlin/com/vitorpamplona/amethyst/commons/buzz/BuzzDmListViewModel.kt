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
package com.vitorpamplona.amethyst.commons.buzz

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvites
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmChannels
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmRegistry
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.buzz.membershipNoticeFilter
import com.vitorpamplona.amethyst.commons.model.buzz.membershipNotices
import com.vitorpamplona.amethyst.commons.model.buzz.reconnectPoolAfterJoin
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.RELAY_GROUP_METADATA_KINDS
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.util.logSignerErrors
import com.vitorpamplona.quartz.buzz.dvDmVisibility.DmVisibilityEvent
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.workspace.buzzParticipants
import com.vitorpamplona.quartz.buzz.workspace.isBuzzDm
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Backing ViewModel for [BuzzDmListScreen] — the user's Buzz direct-message inbox.
 *
 * A Buzz DM is a relay-authoritative NIP-29 group whose `h`/id is a relay-generated UUID, so the
 * message timeline reuses the whole relay-group chat stack; this ViewModel owns only *discovery*
 * and the inbox projection. Discovery mirrors how the deployed relay actually models DMs (it does
 * NOT emit a queryable kind-41001): the relay addresses each member a kind-44100 member-added
 * notification (`#p` = me, `h` = channel), and marks a channel a DM via the `t` tag on its
 * kind-39000 metadata (with the participants inlined as `p` tags). So it:
 * - fetches + live-subscribes 44100 (`#p` = me) across the joined Buzz relays to learn the
 *   channels the user is in;
 * - fetches each channel's directory (39000-39003) and keeps the ones whose metadata says
 *   `t` = `dm` — that same 39000 also carries the roster the shared chat composer's member gate
 *   needs, and the DM participants;
 * - subscribes the per-viewer [DmVisibilityEvent] (`kind:30622`) so a hidden DM (tracked in
 *   [BuzzDmRegistry]) moves from [rows] to [hiddenRows];
 * - projects the visible DMs into [rows] and the hidden ones into [hiddenRows], both sorted by
 *   last message time. Hidden DMs stay projected (rather than being dropped on the floor) so the
 *   inbox can offer them back — hiding is reversible, and a conversation with no way back is a
 *   conversation the user has lost.
 */
class BuzzDmListViewModel : ViewModel() {
    @Volatile private var account: Account? = null

    /** The community (relay) this inbox is scoped to — DMs are per-community. */
    private var scopeRelay: NormalizedRelayUrl? = null
    private val refreshMutex = Mutex()
    private var liveJob: Job? = null

    /** channelId -> relay it was discovered on (from the 44100 provenance). */
    private val memberChannels = ConcurrentMap<String, NormalizedRelayUrl>()

    private val _rows = MutableStateFlow<List<DmRow>>(emptyList())
    val rows: StateFlow<List<DmRow>> = _rows.asStateFlow()

    /** The DMs I hid (per the relay's 30622 snapshot), newest-first — offered back under "Hidden". */
    private val _hiddenRows = MutableStateFlow<List<DmRow>>(emptyList())
    val hiddenRows: StateFlow<List<DmRow>> = _hiddenRows.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** One inbox row: a materialized DM plus what the UI needs to render it. */
    @Immutable
    data class DmRow(
        val channelId: String,
        val relayUrl: NormalizedRelayUrl,
        /** All participants (from the 39000 metadata `p` tags), including me. */
        val allParticipants: List<HexKey>,
        /** Participants other than me — who the DM is "with". */
        val others: List<HexKey>,
        /** Newest message time (or 0 when the DM has no messages yet). */
        val lastActivity: Long,
    )

    private fun relays(): Set<NormalizedRelayUrl> =
        scopeRelay?.let { setOf(it) } ?: (
            account
                ?.buzzWorkspaces
                ?.flow
                ?.value
                .orEmpty() + BuzzRelayDialect.flow.value
        )

    /**
     * Binds to [account] scoped to the community [relayUrl]. Marks that relay a joined workspace and
     * pre-approves NIP-42 so the read-only `#p=me` DM discovery authenticates against it.
     */
    fun bind(
        account: Account,
        relayUrl: String,
    ) {
        if (this.account != null) return
        this.account = account
        // A malformed relay URL must not silently fall back to querying every joined workspace
        // (relays() does that when scopeRelay is null) — this screen is scoped to one community.
        val relay = RelayUrlNormalizer.normalizeOrNull(relayUrl) ?: return
        this.scopeRelay = relay

        val newlyJoined = account.buzzWorkspaces.join(relay)
        viewModelScope.launch { account.relayAuthLedger.setDecision(relay.url, RelayAuthDecision.ALLOW) }
        if (newlyJoined) reconnectPoolAfterJoin(account.client)

        // Paint from cache BEFORE any network work. [discoverMemberChannels] learns the channel ids
        // from a relay round-trip, so waiting on it left the Direct Messages section visibly empty
        // for about a second on every visit — even though the always-on [BuzzDmDiscovery] already
        // recorded those ids process-wide and [rebuildRows] reads nothing but caches. Seeding from
        // that registry makes the first frame the right frame; the refresh below still runs and
        // corrects anything stale.
        seedFromDiscovery(account, relay)

        refresh()
        startLive()
    }

    /**
     * Fills [memberChannels] from the app-wide [BuzzDmChannels] registry (scoped to this community's
     * relay) and projects the rows straight away, so the inbox renders from cache instead of after a
     * fetch. A no-op the first time a viewer ever opens a Buzz relay, when discovery genuinely has
     * nothing yet.
     */
    private fun seedFromDiscovery(
        account: Account,
        relay: NormalizedRelayUrl,
    ) {
        val known = BuzzDmChannels.channelsFor(account.userProfile().pubkeyHex)
        var seeded = false
        known.forEach { (channelId, discoveredOn) ->
            if (discoveredOn == relay) {
                memberChannels[channelId] = discoveredOn
                seeded = true
            }
        }
        if (seeded) rebuildRows(account)
    }

    fun refresh() {
        val account = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            refreshMutex.withLock {
                _isLoading.value = true
                try {
                    discoverMemberChannels(account)
                    fetchMetadata(account)
                    rebuildRows(account)
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Fetch kind-44100 (`#p` = me) + the visibility snapshot (30622) across the joined relays. These
     * reads are `#p`-gated so the Buzz relay requires NIP-42 auth — use the warm-auth fetch
     * (`pendingOnAuthRequired`) so it authenticates on the `auth-required` CLOSED and retries, rather
     * than returning empty (this is why the import lists channels but a plain fetch wouldn't).
     */
    private suspend fun discoverMemberChannels(account: Account) {
        val myPubkey = account.userProfile().pubkeyHex
        val relays = relays()
        if (relays.isEmpty()) return
        val filters =
            listOf(
                Filter(kinds = listOf(MemberAddedNotificationEvent.KIND), tags = mapOf("p" to listOf(myPubkey))),
                Filter(kinds = listOf(DmVisibilityEvent.KIND), tags = mapOf("p" to listOf(myPubkey))),
            )
        account.client.taggedAs(SubPurpose.RELAY_GROUPS).fetchAllWithHooks(
            filters = relays.associateWith { filters },
            idleTimeoutMs = 8_000,
            pendingOnAuthRequired = true,
        ) { relay, event ->
            (event as? MemberAddedNotificationEvent)?.channel()?.let { memberChannels[it] = relay }
            false
        }
    }

    /** Fetch the NIP-29 directory (39000-39003) of every discovered channel so its `t`/roster load. */
    private suspend fun fetchMetadata(account: Account) {
        val byRelay =
            memberChannels
                .asMap()
                .entries
                .groupBy({ it.value }, { it.key })
                .mapValues { (_, ids) -> listOf(Filter(kinds = RELAY_GROUP_METADATA_KINDS, tags = mapOf("d" to ids))) }
        if (byRelay.isEmpty()) return
        account.client.taggedAs(SubPurpose.RELAY_GROUPS).fetchAllWithHooks(filters = byRelay, idleTimeoutMs = 8_000, pendingOnAuthRequired = true) { _, _ -> false }
    }

    /**
     * Project the discovered DM channels (metadata `t` = `dm`) newest-first, split by my hidden set
     * (the relay's 30622 snapshot) into [rows] and [hiddenRows]. Both halves come from one pass so a
     * DM can only ever be in one of them.
     */
    private fun rebuildRows(account: Account) {
        val myPubkey = account.userProfile().pubkeyHex
        val hidden = BuzzDmRegistry.hiddenFor(myPubkey)
        val (hiddenDms, visibleDms) =
            memberChannels
                .asMap()
                .entries
                .mapNotNull { (channelId, relay) ->
                    val channel = LocalCache.getOrCreateRelayGroupChannel(GroupId(channelId, relay))
                    val metadata = channel.event ?: return@mapNotNull null
                    if (!metadata.isBuzzDm()) return@mapNotNull null
                    val participants = metadata.buzzParticipants()
                    DmRow(
                        channelId = channelId,
                        relayUrl = relay,
                        allParticipants = participants,
                        others = participants.filter { it != myPubkey },
                        lastActivity = lastActivityFor(channelId),
                    )
                }.sortedByDescending { it.lastActivity }
                .partition { it.channelId in hidden }
        _rows.value = visibleDms
        _hiddenRows.value = hiddenDms
    }

    /**
     * Take [row] off Messages with a kind-41012 hide command. Server-side and per-viewer: the relay
     * republishes my 30622 snapshot with this channel in it, which moves the row to [hiddenRows].
     * Membership is untouched — nobody else's inbox changes, and [addToMessages] brings it back.
     */
    fun removeFromMessages(row: DmRow) {
        val account = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            logSignerErrors("BuzzDmList") { account.relayGroups.hideBuzzDm(LocalCache.getOrCreateRelayGroupChannel(GroupId(row.channelId, row.relayUrl))) }
        }
    }

    /**
     * Put a hidden DM back on Messages. Buzz has no "unhide" command — re-opening the conversation is
     * the un-hide: a kind-41010 with the same participants resolves to the same canonical channel and
     * drops it from the 30622 hidden snapshot. A self-DM has no `others`, so send myself, which is
     * what the relay derived that channel from (and satisfies kind-41010's 1-8 participant rule).
     */
    fun addToMessages(row: DmRow) {
        val account = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val me = account.userProfile().pubkeyHex
            logSignerErrors("BuzzDmList") { account.relayGroups.openBuzzDm(row.relayUrl, row.others.ifEmpty { listOf(me) }) }
            // The relay's new 30622 normally arrives on the live subscription; refresh anyway so the
            // row returns even if this screen's socket missed the snapshot.
            refresh()
        }
    }

    /** Newest message `created_at` for [channelId] from [LocalCache], or 0 when the DM is empty. */
    private fun lastActivityFor(channelId: String): Long =
        LocalCache
            .filter(
                Filter(
                    kinds = listOf(ChatEvent.KIND, StreamMessageV2Event.KIND),
                    tags = mapOf("h" to listOf(channelId)),
                ),
            ).maxOfOrNull { it.createdAt() ?: 0L } ?: 0L

    /**
     * Re-projects the inbox as new DMs and hide changes arrive. Idempotent; torn down with the ViewModel.
     *
     * The 44100/30622 stream itself is **not** subscribed here. `bind` marks this community's relay a
     * joined workspace, which is exactly what
     * [com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.buzz.BuzzMembershipEoseManager]
     * keys its always-on `#p=me` subscription on — so opening this screen used to put a second, identical
     * REQ on the same relay. Observing [LocalCache] instead means the screen sees the same events at the
     * same time for free, and the relay sees one subscription.
     */
    private fun startLive() {
        val account = account ?: return
        if (liveJob != null) return
        val myPubkey = account.userProfile().pubkeyHex

        liveJob =
            viewModelScope.launch(Dispatchers.IO) {
                launch {
                    LocalCache.observeNotes(membershipNoticeFilter(myPubkey)).collect {
                        // The emission is only the signal; the notices come from a cache scan, because
                        // `observeNotes` cannot seed these kinds (see [membershipNotices]).
                        //
                        // Re-read the relay scope per pass rather than snapshotting it: a workspace
                        // joined while this screen is open should bring its channels with it.
                        val scoped = relays()
                        val workspaces = account.buzzWorkspaces.flow.value
                        val memberships =
                            BuzzChannelInvites
                                .currentMemberships(LocalCache.membershipNotices(myPubkey, workspaces))
                                .filterValues { it in scoped }
                        var changed = false
                        memberships.forEach { (channelId, relay) ->
                            // One atomic insert decides "new", so a concurrent seed of the same
                            // channel can't make this pass skip its row rebuild.
                            if (memberChannels.putIfAbsent(channelId, relay) == null) {
                                changed = true
                            } else {
                                memberChannels[channelId] = relay
                            }
                        }
                        // A kind-44101 takes the membership away: drop the row rather than leaving a
                        // conversation the relay no longer lets us read.
                        //
                        // Only channels the scan actually has a *removal* for. Anything else in
                        // `memberChannels` was put there by the seed or the one-shot fetch, which see
                        // relays this scan may not cover — treating "absent from this pass" as "gone"
                        // would let one pass wipe rows nothing withdrew.
                        val withdrawn =
                            LocalCache
                                .membershipNotices(myPubkey, workspaces)
                                .let { BuzzChannelInvites.latestPerChannel(it) }
                                .filterValues { it.removed }
                                .keys
                        val gone = memberChannels.asMap().keys.filter { it in withdrawn }
                        if (gone.isNotEmpty()) {
                            gone.forEach { memberChannels.remove(it) }
                            changed = true
                        }
                        if (changed) {
                            fetchMetadata(account)
                            rebuildRows(account)
                        }
                    }
                }
                // Re-project when my hidden set (30622) or the joined-relay set changes.
                launch {
                    combine(BuzzDmRegistry.hidden, account.buzzWorkspaces.flow, BuzzRelayDialect.flow) { _, _, _ -> }
                        .collect { rebuildRows(account) }
                }
            }
    }

    override fun onCleared() {
        liveJob?.cancel()
        liveJob = null
    }
}
