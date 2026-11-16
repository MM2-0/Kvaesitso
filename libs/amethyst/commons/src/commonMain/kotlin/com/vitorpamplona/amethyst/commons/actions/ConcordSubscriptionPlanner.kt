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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.ConcordDissolution
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordRefounding
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * One plane to subscribe to: its stream address ([pubKeyHex]) and the relays it
 * may live on. [channelId] is null for a community's Control Plane and set for a
 * channel's Chat Plane.
 */
data class ConcordPlaneSub(
    val channelId: ConcordChannelId?,
    val pubKeyHex: String,
    val relays: Set<NormalizedRelayUrl>,
)

/**
 * Turns the account's joined-communities list into the per-plane relay
 * subscriptions that keep Concord Channels live — the Concord analog of NIP-29's
 * `RelayGroupMyJoinedGroupsFilterAssembler`.
 *
 * Because a Concord wrap's `p` tag is ephemeral, there is no single `#p=me`
 * subscription; instead each plane is fetched by its derived stream address
 * (`authors=[planePk]`). The "warm all joined planes" policy is encoded here:
 * every community's Control Plane is subscribed upfront ([controlPlaneSubs]), and
 * once its Control Plane folds, every channel's Chat Plane is subscribed
 * ([channelPlaneSubs]).
 */
object ConcordSubscriptionPlanner {
    /**
     * Control-plane subscriptions for every joined community (known from the entry alone) — at
     * the current epoch **plus** every prior epoch the account still holds a root for.
     *
     * The prior-epoch Control Planes are what a CORD-06 Refounding's compacted head is checked
     * against: the rotator picks which edition per entity survives the rotation, so without the
     * old planes a client has no memory of the versions it already folded and a rotator can roll
     * an entity backwards (restore a revoked role, clear a banlist) with genuine signatures. See
     * `ConcordCommunitySession.historicalControlPlaneAddresses`.
     */
    fun controlPlaneSubs(entries: List<ConcordCommunityListEntry>): List<ConcordPlaneSub> =
        entries.flatMap { e ->
            val communityId = e.id.hexToByteArray()
            val relays = normalize(e.relays)
            // The address is the held `control_pk` on a split epoch and the legacy derivation
            // otherwise (CORD-02 §5) — a member can never derive the former, so it is read off
            // the entry, per epoch, exactly as it was delivered.
            val cp = ConcordActions.controlPlaneKeysFor(e)
            val historical =
                // Losing-fork roots of a healed race are kept for their messages only (CORD-06 §3).
                ConcordRefounding
                    .canonicalHeldRoots(e.heldRoots)
                    .filter { it.epoch < e.rootEpoch }
                    .sortedByDescending { it.epoch }
                    .take(ConcordActions.MAX_BACKFILL_EPOCHS)
                    .mapNotNull { held ->
                        val keys = runCatching { ConcordActions.controlPlaneKeys(held.key.hexToByteArray(), communityId, held.epoch, held.controlPk, held.controlRoot) }.getOrNull() ?: return@mapNotNull null
                        ConcordPlaneSub(channelId = null, pubKeyHex = keys.address, relays = relays)
                    }
            listOf(ConcordPlaneSub(channelId = null, pubKeyHex = cp.address, relays = relays)) + historical
        }

    /**
     * The off-channel planes every joined community subscribes to upfront (known
     * from the entry alone): the Guestbook Plane (membership motions) and the
     * next-epoch base-rekey address (so an inbound Refounding is received live,
     * CORD-06), the dissolution tombstone address (CORD-02 §9), and every held Private Channel's
     * next channel-rekey addresses (CORD-06 §2). All are kind-1059
     * wraps authored by their derived stream address.
     */
    fun auxiliaryPlaneSubs(entries: List<ConcordCommunityListEntry>): List<ConcordPlaneSub> =
        entries.flatMap { e ->
            val root = e.root.hexToByteArray()
            val communityId = e.id.hexToByteArray()
            val relays = normalize(e.relays)
            val guestbook = ConcordActions.guestbookPlane(root, communityId, e.rootEpoch)
            val nextRekey = ConcordActions.nextBaseRekeyPlane(root, communityId, e.rootEpoch)
            val dissolved = ConcordDissolution.planeKey(e.id)
            val sibling = ConcordActions.siblingBaseRekeyPlane(e)
            listOfNotNull(
                ConcordPlaneSub(channelId = null, pubKeyHex = guestbook.publicKeyHex, relays = relays),
                ConcordPlaneSub(channelId = null, pubKeyHex = nextRekey.publicKeyHex, relays = relays),
                ConcordPlaneSub(channelId = null, pubKeyHex = dissolved.publicKeyHex, relays = relays),
                // The current epoch's own rekey address, so a racing sibling can heal us (CORD-06 §3).
                sibling?.let { ConcordPlaneSub(channelId = null, pubKeyHex = it.publicKeyHex, relays = relays) },
            ) +
                // Each held Private Channel's next channel-rekey addresses (CORD-06 §2), so a rotation
                // that moves the key forward — or cuts us — is received live.
                ConcordPrivateChannels.watchKeys(e).keys.map { ConcordPlaneSub(channelId = null, pubKeyHex = it, relays = relays) }
        }

    /**
     * Chat-plane subscriptions for every live channel in a folded community [state] — at the current
     * epoch, plus each channel's plane at every prior epoch the account still holds a root for
     * ([ConcordCommunityListEntry.heldRoots]). A CORD-06 Refounding rotates the root per epoch, so the
     * pre-refounding history lives under those prior-epoch planes; subscribing to them is what lets the
     * client fetch messages older than the last Refounding instead of stopping at "All caught up".
     */
    fun channelPlaneSubs(
        entry: ConcordCommunityListEntry,
        state: ConcordCommunityState,
    ): List<ConcordPlaneSub> {
        val relays = normalize(entry.relays)
        // A Private Channel is subscribed on its own key's plane only, and not at all without a held
        // key (CORD-03 §1): never on the root-derived plane every member can read.
        val current =
            state.channels.values.mapNotNull { channel ->
                ConcordActions.currentChannelPlane(entry, channel.channelIdHex, channel.definition.private)?.let { plane ->
                    ConcordPlaneSub(
                        channelId = ConcordChannelId(entry.id, plane.channelIdHex),
                        pubKeyHex = plane.key.publicKeyHex,
                        relays = relays,
                    )
                }
            }
        val historical =
            state.channels.values.flatMap { channel ->
                ConcordActions.historicalChannelPlanes(entry, channel.channelIdHex, channel.definition.private).map { plane ->
                    ConcordPlaneSub(
                        channelId = ConcordChannelId(entry.id, plane.channelIdHex),
                        pubKeyHex = plane.key.publicKeyHex,
                        relays = relays,
                    )
                }
            }
        return current + historical
    }

    /**
     * Per-channel **preview / catch-up** filters for every channel of a folded community, so its
     * channel list and the Messages inbox fill in without the user opening each channel one by one.
     * One filter per channel (never collapsed like the live [channelPlaneSubs], where the relay caps
     * every channel's planes as one result and starves the quiet channels); each channel's authors
     * span every held epoch so it reaches across a CORD-06 Refounding, and only stored wraps
     * ([ConcordStreamEnvelope.KIND_WRAP]) count — the ephemeral 21059 is a typing heartbeat.
     *
     * How much to pull is per-channel, from [lastReadFor] (0 = never read):
     *  - **Read before** → everything since the last-read time (capped at [catchUpLimit]), so the
     *    unread badge is accurate and the missed messages are already in cache when the channel opens.
     *    The last-read value *is a message's `created_at`* (the read marker is stamped with the seen
     *    message's timestamp, not wall-clock), so the window is `since = lastRead - 1`: that
     *    re-includes the last-read message itself, guaranteeing a preview even for a fully caught-up
     *    channel — and since the unread count is a strict `created_at > lastRead`, that boundary
     *    message is not miscounted as unread.
     *  - **Never read** → the [previewLimit] newest wraps — enough for a preview and a rough sense of
     *    how busy the channel is (the unread badge counts what we pulled), without dragging a whole
     *    channel's backlog into a community the user has only glanced at. The full history still pages
     *    in on open (the backward history pager).
     *
     * The returned filters `groupByRelay` into one REQ per relay, one filter per channel.
     */
    fun channelPreviewFilters(
        entry: ConcordCommunityListEntry,
        state: ConcordCommunityState,
        lastReadFor: (channelIdHex: String) -> Long,
        catchUpLimit: Int = 50,
        previewLimit: Int = 10,
        accountPubKey: HexKey? = null,
    ): List<RelayBasedFilter> {
        val authorsByChannel = LinkedHashMap<ConcordChannelId, MutableSet<String>>()
        val relaysByChannel = HashMap<ConcordChannelId, Set<NormalizedRelayUrl>>()
        for (sub in channelPlaneSubs(entry, state)) {
            val channelId = sub.channelId ?: continue
            authorsByChannel.getOrPut(channelId) { LinkedHashSet() }.add(sub.pubKeyHex)
            relaysByChannel[channelId] = sub.relays
        }
        return authorsByChannel.flatMap { (channelId, authors) ->
            val lastRead = lastReadFor(channelId.channelId)
            val filter =
                if (lastRead > 0L) {
                    ExplainedFilter(
                        purpose = SubPurpose.COMMUNITY_CHATS,
                        purposeDetail = "concord community planes",
                        entityIds = listOf(entry.id),
                        accountPubKeys = listOfNotNull(accountPubKey),
                        kinds = listOf(ConcordStreamEnvelope.KIND_WRAP),
                        authors = authors.toList(),
                        // -1 so the last-read message (created_at == lastRead) is itself returned:
                        // a caught-up channel still gets a preview, and strict-> unread stays correct.
                        since = lastRead - 1,
                        limit = catchUpLimit,
                    )
                } else {
                    ExplainedFilter(
                        purpose = SubPurpose.COMMUNITY_CHATS,
                        purposeDetail = "concord community planes",
                        entityIds = listOf(entry.id),
                        accountPubKeys = listOfNotNull(accountPubKey),
                        kinds = listOf(ConcordStreamEnvelope.KIND_WRAP),
                        authors = authors.toList(),
                        limit = previewLimit,
                    )
                }
            (relaysByChannel[channelId] ?: emptySet()).map { relay -> RelayBasedFilter(relay = relay, filter = filter) }
        }
    }

    /**
     * Collapses [subs] into a `relay -> [filter]` map ready for a drain/subscribe.
     * All plane wraps are kind-1059 authored by the plane address, so each relay
     * gets one `{kinds:[1059], authors:[…all plane pks on it…]}` filter.
     */
    fun filtersByRelay(subs: List<ConcordPlaneSub>): Map<NormalizedRelayUrl, List<Filter>> {
        val authorsByRelay = HashMap<NormalizedRelayUrl, MutableList<String>>()
        for (sub in subs) {
            for (relay in sub.relays) authorsByRelay.getOrPut(relay) { ArrayList() }.add(sub.pubKeyHex)
        }
        return authorsByRelay.mapValues { (_, authors) -> listOf(ConcordActions.planeFilterFor(authors)) }
    }

    /**
     * The live-subscription filters for every joined community, with the **Control Plane kept in
     * its own filter**, apart from the Guestbook / next-epoch rekey / channel planes.
     *
     * This is the anti-starvation split. The Control Plane is what folds the channel *list* (and
     * roster/roles); collapsing it into one `authors=[…every plane…]` filter lets a chatty plane —
     * the Guestbook can carry dozens of membership wraps — crowd its editions out of a relay's
     * per-filter result cap (measured ~100/filter on relay.dreamith.to), so only a fraction of the
     * channels fold (the "Soapbox shows 1 of 12 channels" bug). A dedicated filter gives the Control
     * Plane an isolated per-filter budget, so the whole plane folds no matter how busy the Guestbook
     * is. Mirrors Armada's plane-sweep design (one filter per plane scope). Both groups ride the same
     * per-relay REQ downstream (groupByRelay), so no extra socket/subscription is opened.
     *
     * [stateOf] supplies each entry's folded state (null → its channels aren't known yet, so only its
     * Control/aux planes are subscribed). [since] is the per-relay EOSE cursor, applied to every filter.
     */
    fun controlIsolatedFilters(
        entries: List<ConcordCommunityListEntry>,
        since: SincePerRelayMap?,
        accountPubKey: HexKey? = null,
        stateOf: (ConcordCommunityListEntry) -> ConcordCommunityState?,
    ): List<RelayBasedFilter> {
        // A community whose Control Plane hasn't folded yet (just joined, or its editions never
        // arrived) is fetched without the relay's `since`: that cursor was set by the OTHER
        // communities on the relay, and applying it here skips every edition older than it — the
        // genesis included — so the community showed no channels until a restart dropped the cursor.
        val (folded, unfolded) = entries.partition { stateOf(it) != null }

        val otherSubs = ArrayList<ConcordPlaneSub>()
        otherSubs += auxiliaryPlaneSubs(folded)
        for (entry in folded) {
            otherSubs += channelPlaneSubs(entry, stateOf(entry) ?: continue)
        }

        return relayBasedFilters(controlPlaneSubs(folded), since, accountPubKey).orEmpty() +
            relayBasedFilters(otherSubs, since, accountPubKey).orEmpty() +
            relayBasedFilters(controlPlaneSubs(unfolded), null, accountPubKey).orEmpty() +
            relayBasedFilters(auxiliaryPlaneSubs(unfolded), null, accountPubKey).orEmpty()
    }

    /**
     * Collapses [subs] into one [RelayBasedFilter] per host relay for a live
     * subscription: each relay gets a single `{kinds:[1059], authors:[…all plane
     * pks on it…], since}` filter, with [since] applied per relay from the EOSE
     * map. Returns null when no plane resolves to a relay (nothing to subscribe).
     *
     * This is the assembler-facing shape (what a `PerUniqueIdEoseManager` returns);
     * [filtersByRelay] is the one-shot drain shape (no `since`).
     */
    fun relayBasedFilters(
        subs: List<ConcordPlaneSub>,
        since: SincePerRelayMap?,
        accountPubKey: HexKey? = null,
    ): List<RelayBasedFilter>? {
        val authorsByRelay = HashMap<NormalizedRelayUrl, MutableSet<String>>()
        // Which communities each relay is being asked about. Collapsing planes into one filter per
        // relay is what makes the subscription screen unable to name them otherwise — the plane
        // pubkeys are stream keys, not something a user can recognise.
        val communitiesByRelay = HashMap<NormalizedRelayUrl, MutableSet<String>>()
        for (sub in subs) {
            for (relay in sub.relays) {
                authorsByRelay.getOrPut(relay) { HashSet() }.add(sub.pubKeyHex)
                sub.channelId?.let { communitiesByRelay.getOrPut(relay) { HashSet() }.add(it.communityId) }
            }
        }
        if (authorsByRelay.isEmpty()) return null

        return authorsByRelay.map { (relay, authors) ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.COMMUNITY_CHATS,
                        purposeDetail = "concord live planes",
                        entityIds = communitiesByRelay[relay]?.sorted(),
                        accountPubKeys = listOfNotNull(accountPubKey),
                        // Stored plane wraps (1059) plus ephemeral ones (21059) — the latter carry the
                        // live-only typing heartbeats a relay broadcasts but never stores.
                        kinds = listOf(ConcordStreamEnvelope.KIND_WRAP, ConcordStreamEnvelope.KIND_WRAP_EPHEMERAL),
                        authors = authors.toList(),
                        since = since?.get(relay)?.time,
                    ),
            )
        }
    }

    private fun normalize(urls: List<String>): Set<NormalizedRelayUrl> = urls.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
}
