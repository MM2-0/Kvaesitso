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
package com.vitorpamplona.amethyst.commons.relayClient.auth

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * The rooms an account joined that the NIP-51 venue lists don't describe: NIP-29 relay groups (the
 * kind-10009 list) and Concord communities (the kind-13302 list).
 *
 * Both are venues in the "…it's my relay, or a room I joined" sense, and both are invisible to every
 * other signal in the auth path — a NIP-29 group's content is `#h`-scoped and never names the user,
 * and a Concord plane is authored by and addressed to derived stream keys — so this is what feeds
 * them to [RelayAuthPermissionLedger] and [RelayAuthFirstParty]. Pure, so the id/url matching is
 * testable without an [com.vitorpamplona.amethyst.commons.model.Account].
 *
 * Everything here is per account, deliberately: these lists come off *this* account's own list events,
 * so one account's rooms can never grant another account's identity away. That rules out the
 * process-wide joined sets (Buzz workspaces) — they carry no account, so folding them in here would
 * auto-authenticate every logged-in account on a workspace only one of them joined.
 */
object RelayAuthVenues {
    /**
     * The relays these rooms live on. A NIP-29 group id is only meaningful together with its host, so
     * the [GroupId] carries an already-normalized url; a Concord entry stores raw url strings written
     * by whoever created the community, so those are normalized here — comparing them verbatim
     * against a challenge's relay url is what makes a trailing slash or a `wss://` case difference
     * quietly drop the whole community.
     */
    fun hostRelays(
        joinedGroups: Set<GroupId>,
        joinedCommunities: List<ConcordCommunityListEntry>,
    ): Set<NormalizedRelayUrl> =
        buildSet {
            joinedGroups.mapTo(this) { it.relayUrl }
            joinedCommunities.forEach { entry ->
                entry.relays.forEach { url -> RelayUrlNormalizer.normalizeOrNull(url)?.let(::add) }
            }
        }

    /**
     * True when [venueId], as served by [relayUrl], names one of these rooms. These are the ids the
     * subscription assemblers declare as their filters' entity ids, so this is what turns a
     * `READ_VENUE`/`POST_VENUE` on a joined room into a trusted venue.
     *
     * A NIP-29 group id is matched **together with its host**: ids are scoped to their relay and are
     * routinely generic (`_` is the spec's relay-wide group), so an id alone would let a group we
     * merely browsed on some other relay pass for one we joined. A Concord community id is a 64-hex
     * derived value that names exactly one community wherever it is served, so it matches on its own
     * — and its relays are covered by [hostRelays] anyway.
     */
    fun isJoinedRoom(
        venueId: String,
        relayUrl: NormalizedRelayUrl?,
        joinedGroups: Set<GroupId>,
        joinedCommunities: List<ConcordCommunityListEntry>,
    ): Boolean =
        (relayUrl != null && GroupId(venueId, relayUrl) in joinedGroups) ||
            joinedCommunities.any { it.id == venueId }
}
