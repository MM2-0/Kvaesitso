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

import com.vitorpamplona.amethyst.commons.relays.MutableTime
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcordSubscriptionPlannerTest {
    private val owner = NostrSignerInternal(KeyPair())

    @Test
    fun channelSubsAlsoCoverPriorEpochPlanesForHeldRoots() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val priorEpoch = 4L
            val priorRoot = KeyPair().pubKey
            val entry =
                com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    controlPk = community.controlPkHex,
                    controlRoot = community.controlRoot.toHexKey(),
                    heldRoots =
                        listOf(
                            com.vitorpamplona.quartz.concord.cord02Community
                                .HeldRoot(priorEpoch, priorRoot.toHexKey()),
                        ),
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )
            val state = ConcordActions.foldCommunity(community.genesisWraps, community.controlPlane, community.communityId, community.ownerPubKey)
            val subs = ConcordSubscriptionPlanner.channelPlaneSubs(entry, state)

            // Both the current-epoch and the prior-epoch #general planes are subscribed.
            val currentGeneral = ConcordActions.publicChannel(community.communityRoot, community.generalChannelId, community.rootEpoch).publicKeyHex
            val priorGeneral = ConcordActions.publicChannel(priorRoot, community.generalChannelId, priorEpoch).publicKeyHex
            assertTrue(subs.any { it.pubKeyHex == currentGeneral }, "current-epoch plane missing")
            assertTrue(subs.any { it.pubKeyHex == priorGeneral }, "prior-epoch plane missing")
            assertTrue(currentGeneral != priorGeneral) // a Refounding really does move the plane
        }

    @Test
    fun controlAndChannelSubsMatchDerivedAddresses() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val entry =
                com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    controlPk = community.controlPkHex,
                    controlRoot = community.controlRoot.toHexKey(),
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )

            // Control-plane sub address must equal the derived control plane pk.
            val controlSubs = ConcordSubscriptionPlanner.controlPlaneSubs(listOf(entry))
            assertEquals(1, controlSubs.size)
            assertEquals(community.controlPlane.address, controlSubs[0].pubKeyHex)
            assertTrue(controlSubs[0].channelId == null)

            // Channel-plane subs cover the folded #general channel.
            val state = ConcordActions.foldCommunity(community.genesisWraps, community.controlPlane, community.communityId, community.ownerPubKey)
            val channelSubs = ConcordSubscriptionPlanner.channelPlaneSubs(entry, state)
            val general = channelSubs.firstOrNull { it.channelId?.channelId == community.generalChannelIdHex }
            assertTrue(general != null)
            assertEquals(
                ConcordActions.publicChannel(community.communityRoot, community.generalChannelId, community.rootEpoch).publicKeyHex,
                general.pubKeyHex,
            )

            // filtersByRelay collapses to one kind-1059 author filter per relay.
            val filters = ConcordSubscriptionPlanner.filtersByRelay(controlSubs + channelSubs)
            assertEquals(1, filters.size) // single relay
            val filter = filters.values.first().first()
            assertEquals(listOf(1059), filter.kinds)
            assertTrue(filter.authors!!.contains(community.controlPlane.address))
            assertTrue(filter.authors!!.contains(general.pubKeyHex))
        }

    @Test
    fun channelPreviewFiltersAreOnePerChannelWithLimitOne() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val entry =
                com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    controlPk = community.controlPkHex,
                    controlRoot = community.controlRoot.toHexKey(),
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )
            val state = ConcordActions.foldCommunity(community.genesisWraps, community.controlPlane, community.communityId, community.ownerPubKey)

            // Never read (lastRead == 0) ⇒ the newest few wraps (previewLimit), no `since`.
            val previews = ConcordSubscriptionPlanner.channelPreviewFilters(entry, state, lastReadFor = { 0L }, previewLimit = 10)

            // One filter per channel (a fresh community has just #general) on the one community relay.
            assertEquals(1, previews.size)
            val preview = previews.first()
            assertEquals(10, preview.filter.limit) // a handful of recent messages, not the whole backlog
            assertNull(preview.filter.since) // never read ⇒ no catch-up window
            assertEquals(listOf(1059), preview.filter.kinds) // stored wraps only — not ephemeral 21059 typing
            val general = ConcordActions.publicChannel(community.communityRoot, community.generalChannelId, community.rootEpoch).publicKeyHex
            assertTrue(preview.filter.authors?.contains(general) == true, "no limit:1 preview for #general")
        }

    @Test
    fun channelPreviewFiltersCatchUpSinceLastReadWhenRead() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val entry =
                com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    controlPk = community.controlPkHex,
                    controlRoot = community.controlRoot.toHexKey(),
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )
            val state = ConcordActions.foldCommunity(community.genesisWraps, community.controlPlane, community.communityId, community.ownerPubKey)

            // Read before (lastRead > 0) ⇒ everything since, capped, so the unread badge is accurate.
            val lastRead = 1_700_000_000L
            val previews =
                ConcordSubscriptionPlanner.channelPreviewFilters(entry, state, lastReadFor = { lastRead }, catchUpLimit = 25)

            assertEquals(1, previews.size)
            val preview = previews.first()
            // `since = lastRead - 1` re-includes the last-read message (its created_at == lastRead),
            // so even a caught-up channel still yields a preview.
            assertEquals(lastRead - 1, preview.filter.since)
            assertEquals(25, preview.filter.limit) // bounded by catchUpLimit, not 1
        }

    @Test
    fun relayBasedFiltersCollapsePerRelayAndApplySince() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val entry =
                com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    controlPk = community.controlPkHex,
                    controlRoot = community.controlRoot.toHexKey(),
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )
            val subs = ConcordSubscriptionPlanner.controlPlaneSubs(listOf(entry))
            val relay = RelayUrlNormalizer.normalizeOrNull("wss://r.example")!!

            // One filter for the single relay, carrying the derived since. Live subscriptions ask for
            // both the stored wrap (1059) and the ephemeral wrap (21059) that carries typing heartbeats.
            val filters = ConcordSubscriptionPlanner.relayBasedFilters(subs, mutableMapOf(relay to MutableTime(1234L)))!!
            assertEquals(1, filters.size)
            assertEquals(relay, filters[0].relay)
            assertEquals(listOf(1059, 21059), filters[0].filter.kinds)
            assertEquals(1234L, filters[0].filter.since)
            assertTrue(filters[0].filter.authors!!.contains(community.controlPlane.address))

            // No planes resolve to a relay -> nothing to subscribe.
            assertNull(ConcordSubscriptionPlanner.relayBasedFilters(emptyList(), null))
        }

    @Test
    fun controlIsolatedFiltersKeepControlPlaneOffTheGuestbookAndChannelFilter() =
        runTest {
            // Regression: a Concord community whose Control Plane got collapsed into the same
            // `authors=[…]` filter as the Guestbook + channel planes folded only a fraction of its
            // channels — a chatty Guestbook crowded the channel-defining editions out of the relay's
            // per-filter cap (Soapbox showed 1 of 12 channels). The Control Plane must ride its own filter.
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val entry =
                com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    controlPk = community.controlPkHex,
                    controlRoot = community.controlRoot.toHexKey(),
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )
            val state = ConcordActions.foldCommunity(community.genesisWraps, community.controlPlane, community.communityId, community.ownerPubKey)

            val controlPk = community.controlPlane.address
            val guestbookPk = ConcordActions.guestbookPlane(community.communityRoot, community.communityId, community.rootEpoch).publicKeyHex
            val generalPk = ConcordActions.publicChannel(community.communityRoot, community.generalChannelId, community.rootEpoch).publicKeyHex

            val filters = ConcordSubscriptionPlanner.controlIsolatedFilters(listOf(entry), stateOf = { state }, since = null)

            // The filter carrying the Control Plane must NOT also carry the Guestbook or a channel plane.
            val controlFilter = filters.map { it.filter }.single { controlPk in it.authors.orEmpty() }
            assertTrue(guestbookPk !in controlFilter.authors.orEmpty(), "Guestbook leaked into the Control Plane filter")
            assertTrue(generalPk !in controlFilter.authors.orEmpty(), "a channel plane leaked into the Control Plane filter")

            // The Guestbook and the channel plane share the OTHER filter, and it must not carry Control.
            val otherFilter = filters.map { it.filter }.single { guestbookPk in it.authors.orEmpty() }
            assertTrue(generalPk in otherFilter.authors.orEmpty(), "channel plane missing from the non-control filter")
            assertTrue(controlPk !in otherFilter.authors.orEmpty(), "Control Plane leaked into the Guestbook/channel filter")
        }

    @Test
    fun aJustJoinedCommunityIsFetchedWithoutTheRelaysSinceCursor() =
        runTest {
            // Regression: joining a community on a relay that already carries another one asked for its
            // Control Plane `since` that relay's EOSE cursor, so the genesis editions (older than the
            // cursor) never arrived and the community showed "No channels yet" until an app restart.
            fun entryOf(c: NewConcordCommunity) =
                com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry(
                    id = c.communityIdHex,
                    owner = c.ownerPubKey,
                    ownerSalt = c.ownerSalt.toHexKey(),
                    root = c.communityRoot.toHexKey(),
                    rootEpoch = c.rootEpoch,
                    controlPk = c.controlPkHex,
                    relays = listOf("wss://r.example"),
                    name = "x",
                )
            val folded = ConcordCommunityFactory.create(owner, "Folded", createdAt = 1L, relays = listOf("wss://r.example"))
            val joined = ConcordCommunityFactory.create(owner, "Joined", createdAt = 1L, relays = listOf("wss://r.example"))
            val foldedEntry = entryOf(folded)
            val joinedEntry = entryOf(joined)
            val foldedState = ConcordActions.foldCommunity(folded.genesisWraps, folded.controlPlane, folded.communityId, folded.ownerPubKey)
            val relay = RelayUrlNormalizer.normalizeOrNull("wss://r.example")!!

            val filters =
                ConcordSubscriptionPlanner.controlIsolatedFilters(
                    listOf(foldedEntry, joinedEntry),
                    since = mutableMapOf(relay to MutableTime(1234L)),
                    stateOf = { if (it.id == foldedEntry.id) foldedState else null },
                )

            val joinedControl = filters.map { it.filter }.single { joined.controlPlane.address in it.authors.orEmpty() }
            assertNull(joinedControl.since, "an unfolded community's Control Plane must be fetched in full")
            val joinedGuestbook = ConcordActions.guestbookPlane(joined.communityRoot, joined.communityId, joined.rootEpoch).publicKeyHex
            assertNull(filters.map { it.filter }.single { joinedGuestbook in it.authors.orEmpty() }.since)

            // The already-folded community keeps its incremental cursor.
            val foldedControl = filters.map { it.filter }.single { folded.controlPlane.address in it.authors.orEmpty() }
            assertEquals(1234L, foldedControl.since)
        }
}
