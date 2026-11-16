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
package com.vitorpamplona.amethyst.commons.relayClient.user.watchers

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.forEachChunk
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.EOSEAccountFast
import com.vitorpamplona.quartz.buzz.agentProfiles.AgentProfileEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import com.vitorpamplona.quartz.nip39ExtIdentities.ExternalIdentitiesEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTargetsEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import com.vitorpamplona.quartz.utils.mapOfSet

val UserMetadataForKeyKinds =
    listOf(
        MetadataEvent.KIND,
        ExternalIdentitiesEvent.KIND,
        UserStatusEvent.KIND,
        AdvertisedRelayListEvent.KIND,
        DmRelayListEvent.KIND,
        KeyPackageRelayListEvent.KIND,
        PaymentTargetsEvent.KIND,
        Bolt12OfferListEvent.KIND,
        // NIP-61 nutzap-info. Telegraphs which mints + P2PK pubkey this
        // user accepts nutzaps at. Co-loaded with kind:0 so the zap-picker
        // can decide whether to show the Nutzap chip the moment a note's
        // author renders, with no extra round-trip. We do NOT co-load
        // kind:17375 — that's the user's private wallet (NIP-44 encrypted
        // to them); only the kind:10019 nutzap announcement is public.
        NutzapInfoEvent.KIND,
        // Buzz agent profile (kind:10100): marks the author as an agent and carries its metadata
        // and channel-add policy. Co-loaded with kind:0 so an agent is recognizable the moment it
        // renders. (Its owner comes from the NIP-OA `auth` tag on the kind:0 itself.)
        AgentProfileEvent.KIND,
    )

fun filterUserMetadataForKey(
    authors: Set<User>,
    relayHints: HintIndexer,
    indexRelays: Set<NormalizedRelayUrl>,
    cannotConnectRelays: Set<NormalizedRelayUrl>,
    since: EOSEAccountFast<User>,
    accountPubKey: HexKey? = null,
): List<RelayBasedFilter> {
    val perRelayUsers =
        mapOfSet {
            authors.forEach { key ->
                val outbox = key.outboxRelays()
                val relays =
                    when {
                        outbox == null ->
                            key.allUsedRelays() + relayHints.hintsForKey(key.pubkeyHex) + indexRelays
                        // Outbox is published but exhausted (every relay either EOSE'd
                        // or is known-unreachable) and metadata is still missing —
                        // widen to indexers so a misconfigured outbox doesn't strand
                        // the user's profile.
                        key.metadataOrNull()?.flow?.value == null &&
                            outbox.all { it in cannotConnectRelays || since.since(key)?.get(it) != null } ->
                            outbox + indexRelays
                        else -> outbox
                    }

                (relays - cannotConnectRelays).forEach {
                    add(it, key)
                }
            }
        }

    val filters = mutableListOf<RelayBasedFilter>()

    perRelayUsers.forEach { (relay, users) ->
        val firstTimers = mutableSetOf<HexKey>()
        val updates = mutableSetOf<HexKey>()

        var minimumTime: Long = Long.MAX_VALUE

        users.forEach { user ->
            val time = since.since(user)?.get(relay)?.time
            if (time == null || user.metadataOrNull()?.flow?.value == null) {
                firstTimers.add(user.pubkeyHex)
            } else {
                updates.add(user.pubkeyHex)
                if (time < minimumTime) {
                    minimumTime = time
                }
            }
        }

        // Chunked for the same reason as FilterMissingEvents and FeedMetadataCoordinator:
        // a relay that clamps `limit` answers only part of an oversized batch, silently.
        // One screen can ask for far more than a hundred authors at once — a thread renders
        // every reply row in one pass, and each row subscribes its own author.
        if (firstTimers.isNotEmpty()) {
            forEachChunk(firstTimers.sorted()) { chunk ->
                filters.add(
                    RelayBasedFilter(
                        relay = relay,
                        filter =
                            ExplainedFilter(
                                purpose = SubPurpose.PROFILE_METADATA,
                                accountPubKeys = listOfNotNull(accountPubKey),
                                kinds = UserMetadataForKeyKinds,
                                authors = chunk,
                            ),
                    ),
                )
            }
        }

        if (updates.isNotEmpty()) {
            // Every chunk keeps the group's earliest EOSE: splitting the authors must not
            // narrow the window any of them is asked about.
            forEachChunk(updates.sorted()) { chunk ->
                filters.add(
                    RelayBasedFilter(
                        relay = relay,
                        filter =
                            ExplainedFilter(
                                purpose = SubPurpose.PROFILE_METADATA,
                                accountPubKeys = listOfNotNull(accountPubKey),
                                kinds = UserMetadataForKeyKinds,
                                authors = chunk,
                                since = minimumTime,
                            ),
                    ),
                )
            }
        }
    }

    return filters
}
