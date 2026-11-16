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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthPurposeDeriver
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class RelayAuthPurposeDeriverTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    private fun event(
        kind: Int,
        pTags: List<String> = emptyList(),
    ) = Event(
        id = "00".repeat(32),
        pubKey = "11".repeat(32),
        createdAt = 1_700_000_000L,
        kind = kind,
        tags = pTags.map { arrayOf("p", it) }.toTypedArray(),
        content = "",
        sig = "22".repeat(64),
    )

    @Test
    fun giftWrapBecomesSendDmToItsRecipient() {
        val purposes = RelayAuthPurposeDeriver.derive(listOf(event(GiftWrapEvent.KIND, listOf(alice))), emptyMap())

        assertEquals(1, purposes.size)
        assertEquals(AuthPurposeKind.SEND_DM, purposes[0].kind)
        assertEquals(setOf(alice), purposes[0].counterparties)
    }

    @Test
    fun nonGiftWrapWithPTagsBecomesNotifyInbox() {
        val purposes = RelayAuthPurposeDeriver.derive(listOf(event(1, listOf(alice, bob))), emptyMap())

        assertEquals(1, purposes.size)
        assertEquals(AuthPurposeKind.NOTIFY_INBOX, purposes[0].kind)
        assertEquals(setOf(alice, bob), purposes[0].counterparties)
    }

    @Test
    fun notifyExcludesTheEventsOwnAuthor() {
        val author = "11".repeat(32) // matches event()'s pubKey
        val purposes = RelayAuthPurposeDeriver.derive(listOf(event(1, listOf(author, alice))), emptyMap())

        assertEquals(1, purposes.size)
        assertEquals(AuthPurposeKind.NOTIFY_INBOX, purposes[0].kind)
        assertEquals(setOf(alice), purposes[0].counterparties)
    }

    @Test
    fun subscriptionAuthorsBecomeReadOutbox() {
        val purposes = RelayAuthPurposeDeriver.derive(emptyList(), mapOf("sub1" to listOf(Filter(authors = listOf(alice, bob)))))

        assertEquals(1, purposes.size)
        assertEquals(AuthPurposeKind.READ_OUTBOX, purposes[0].kind)
        assertEquals(setOf(alice, bob), purposes[0].counterparties)
    }

    @Test
    fun mixedPendingWorkYieldsAllPurposes() {
        val purposes =
            RelayAuthPurposeDeriver.derive(
                pendingEvents = listOf(event(GiftWrapEvent.KIND, listOf(alice)), event(1, listOf(bob))),
                activeFilters = mapOf("sub1" to listOf(Filter(authors = listOf(alice)))),
            )

        assertEquals(
            setOf(AuthPurposeKind.SEND_DM, AuthPurposeKind.NOTIFY_INBOX, AuthPurposeKind.READ_OUTBOX),
            purposes.map { it.kind }.toSet(),
        )
    }

    @Test
    fun noActivityYieldsNoPurposes() {
        assertEquals(emptyList<AuthPurpose>(), RelayAuthPurposeDeriver.derive(emptyList(), emptyMap()))
    }

    @Test
    fun unattributableActivityYieldsOtherAsSafetyNet() {
        // An event we can't attribute (no p tags, not a venue) still prompts rather than fail silently.
        val purposes = RelayAuthPurposeDeriver.derive(listOf(event(1)), emptyMap())
        assertEquals(listOf(AuthPurposeKind.OTHER), purposes.map { it.kind })
    }

    @Test
    fun channelMessageBecomesPostVenue() {
        val channelId = "e".repeat(64)
        val ev =
            Event(
                id = "00".repeat(32),
                pubKey = "11".repeat(32),
                createdAt = 1_700_000_000L,
                kind = 42,
                tags = arrayOf(arrayOf("e", channelId, "", "root")),
                content = "hi",
                sig = "22".repeat(64),
            )
        val purposes = RelayAuthPurposeDeriver.derive(listOf(ev), emptyMap())
        assertEquals(1, purposes.size)
        assertEquals(AuthPurposeKind.POST_VENUE, purposes[0].kind)
        assertEquals(setOf(channelId), purposes[0].venues)
    }

    @Test
    fun communityAndLiveSubscriptionsBecomeReadVenue() {
        val community = "34550:${"1".repeat(64)}:my-community"
        val live = "30311:${"2".repeat(64)}:my-stream"
        val purposes =
            RelayAuthPurposeDeriver.derive(
                emptyList(),
                mapOf("sub" to listOf(Filter(tags = mapOf("a" to listOf(community, live))))),
            )
        assertEquals(1, purposes.size)
        assertEquals(AuthPurposeKind.READ_VENUE, purposes[0].kind)
        assertEquals(setOf(community, live), purposes[0].venues)
    }

    // ---- the declared purpose wins over tag shape --------------------------------------------

    @Test
    fun readingMyOwnInboxIsMyInboxAndNotAnOutboundNotify() {
        // filterNotificationsToPubkey: #p = me, no authors. Matches no tag-shape rule, so this used
        // to contribute nothing and the dialog borrowed the label of whatever else shared the socket.
        val purposes =
            RelayAuthPurposeDeriver.derive(
                emptyList(),
                mapOf(
                    "sub" to
                        listOf(
                            ExplainedFilter(
                                kinds = listOf(1, 7, 9735),
                                tags = mapOf("p" to listOf(alice)),
                                purpose = SubPurpose.NOTIFICATIONS,
                            ),
                        ),
                ),
            )

        assertEquals(listOf(AuthPurposeKind.MY_INBOX), purposes.map { it.kind })
        // Nobody is being notified, so no counterparty may be claimed.
        assertEquals(emptySet<String>(), purposes[0].counterparties)
    }

    @Test
    fun readingEngagementOnNotesIsAThreadNotAVenue() {
        // ReactionsFilterAssembler fetches likes/zaps with #e against NOTE ids, which is
        // shape-identical to reading a NIP-28 channel — this is what produced "Open 3f8a12c9?".
        val noteId = "f".repeat(64)
        val purposes =
            RelayAuthPurposeDeriver.derive(
                emptyList(),
                mapOf(
                    "sub" to
                        listOf(
                            ExplainedFilter(
                                kinds = listOf(7, 9735),
                                tags = mapOf("e" to listOf(noteId)),
                                purpose = SubPurpose.ENGAGEMENT,
                            ),
                        ),
                ),
            )

        assertEquals(listOf(AuthPurposeKind.THREAD), purposes.map { it.kind })
        // The note id must not leak through as a "venue" the UI would then try to name as a room.
        assertEquals(emptySet<String>(), purposes[0].venues)
    }

    @Test
    fun aDeclaredVenueReadPrefersTheEntityIdsTheAssemblerNamed() {
        val channelId = "c".repeat(64)
        val purposes =
            RelayAuthPurposeDeriver.derive(
                emptyList(),
                mapOf(
                    "sub" to
                        listOf(
                            ExplainedFilter(
                                tags = mapOf("e" to listOf(channelId, "d".repeat(64))),
                                purpose = SubPurpose.PUBLIC_CHATS,
                                entityIds = listOf(channelId),
                            ),
                        ),
                ),
            )

        assertEquals(listOf(AuthPurposeKind.READ_VENUE), purposes.map { it.kind })
        assertEquals(setOf(channelId), purposes[0].venues)
    }

    @Test
    fun aPurposeThatSaysNothingAboutIdentityFallsBackToTagShape() {
        // SEARCH maps to no auth purpose, so the authors on the filter still drive the answer.
        val purposes =
            RelayAuthPurposeDeriver.derive(
                emptyList(),
                mapOf("sub" to listOf(ExplainedFilter(authors = listOf(alice), purpose = SubPurpose.SEARCH))),
            )

        assertEquals(listOf(AuthPurposeKind.READ_OUTBOX), purposes.map { it.kind })
        assertEquals(setOf(alice), purposes[0].counterparties)
    }

    // ---- rooms whose traffic doesn't look like a room -----------------------------------------

    @Test
    fun postingIntoARelayGroupIsAVenuePostAndNotANotification() {
        // A NIP-29 chat message is `#h`-scoped; the mention it carries would otherwise make this read
        // as "delivering a notification to alice" instead of "posting into the group".
        val groupId = "abcd1234"
        val ev =
            Event(
                id = "00".repeat(32),
                pubKey = "11".repeat(32),
                createdAt = 1_700_000_000L,
                kind = 9,
                tags = arrayOf(arrayOf("h", groupId), arrayOf("p", alice)),
                content = "hi",
                sig = "22".repeat(64),
            )

        val purposes = RelayAuthPurposeDeriver.derive(listOf(ev), emptyMap())

        assertEquals(listOf(AuthPurposeKind.POST_VENUE), purposes.map { it.kind })
        assertEquals(setOf(groupId), purposes[0].venues)
    }

    @Test
    fun aMarmotGroupMessageIsNotTreatedAsARoomWeCanName() {
        // MLS carries its group id in an `h` tag exactly like NIP-29, but the id is an opaque MLS
        // value with no metadata event behind it — and it is 64-hex, so a POST_VENUE on it would have
        // the label get-or-create a phantom public chat. Stays on the unattributed safety net.
        val ev =
            Event(
                id = "00".repeat(32),
                pubKey = "11".repeat(32),
                createdAt = 1_700_000_000L,
                kind = 445,
                tags = arrayOf(arrayOf("h", "d".repeat(64))),
                content = "",
                sig = "22".repeat(64),
            )

        val purposes = RelayAuthPurposeDeriver.derive(listOf(ev), emptyMap())

        assertEquals(listOf(AuthPurposeKind.OTHER), purposes.map { it.kind })
    }

    @Test
    fun aPendingEventThatIsNotAWrapIsNeverAskedAboutPlanes() {
        // The plane lookup walks every joined community on every pending event; only a stream wrap
        // can belong to a plane, so only a wrap may pay for it.
        var asked = 0
        RelayAuthPurposeDeriver.derive(
            pendingEvents = listOf(event(1, listOf(alice)), event(GiftWrapEvent.KIND, listOf(bob))),
            activeFilters = emptyMap(),
            venueForPlaneAuthor = {
                asked++
                null
            },
        )

        assertEquals(1, asked)
    }

    @Test
    fun postingIntoAConcordChannelIsAVenuePostAndNotADmToItsThrowawayPTag() {
        // A Concord plane wrap is kind 1059 signed by the plane's stream key and `p`-tagged to a fresh
        // random pubkey. On tag shape alone it is a gift wrap, so the prompt used to offer to "send a
        // message" to a key that belongs to nobody and will never be seen again.
        val planeAddress = "9".repeat(64)
        val communityId = "c".repeat(64)
        val throwaway = "e".repeat(64)
        val wrap =
            Event(
                id = "00".repeat(32),
                pubKey = planeAddress,
                createdAt = 1_700_000_000L,
                kind = GiftWrapEvent.KIND,
                tags = arrayOf(arrayOf("p", throwaway)),
                content = "",
                sig = "22".repeat(64),
            )

        val purposes =
            RelayAuthPurposeDeriver.derive(
                pendingEvents = listOf(wrap),
                activeFilters = emptyMap(),
                venueForPlaneAuthor = { if (it == planeAddress) communityId else null },
            )

        assertEquals(listOf(AuthPurposeKind.POST_VENUE), purposes.map { it.kind })
        assertEquals(setOf(communityId), purposes[0].venues)
        assertEquals(emptySet<String>(), purposes[0].counterparties)
    }

    @Test
    fun aGiftWrapFromAnUnknownAuthorIsStillADm() {
        // The plane lookup must not swallow real NIP-17 traffic: an author we don't recognize as a
        // plane keeps the gift-wrap reading.
        val purposes =
            RelayAuthPurposeDeriver.derive(
                pendingEvents = listOf(event(GiftWrapEvent.KIND, listOf(alice))),
                activeFilters = emptyMap(),
                venueForPlaneAuthor = { null },
            )

        assertEquals(listOf(AuthPurposeKind.SEND_DM), purposes.map { it.kind })
        assertEquals(setOf(alice), purposes[0].counterparties)
    }

    @Test
    fun readingMyInboxAndSendingADmAreBothReported() {
        val purposes =
            RelayAuthPurposeDeriver.derive(
                pendingEvents = listOf(event(GiftWrapEvent.KIND, listOf(bob))),
                activeFilters =
                    mapOf(
                        "sub" to
                            listOf(
                                ExplainedFilter(tags = mapOf("p" to listOf(alice)), purpose = SubPurpose.NOTIFICATIONS),
                            ),
                    ),
            )

        assertEquals(
            setOf(AuthPurposeKind.SEND_DM, AuthPurposeKind.MY_INBOX),
            purposes.map { it.kind }.toSet(),
        )
    }

    @Test
    fun venuePurposeNamingNoVenueFallsBackToItsAuthors() {
        // Concord's bundle fetch: tagged as a community read, but it names a link signer, not a venue.
        val filter = ExplainedFilter(kinds = listOf(33313), authors = listOf(alice), purpose = SubPurpose.COMMUNITY_CHATS)

        val purposes = RelayAuthPurposeDeriver.derive(emptyList(), mapOf("sub" to listOf(filter)))

        assertEquals(listOf(AuthPurposeKind.READ_OUTBOX), purposes.map { it.kind })
        assertEquals(setOf(alice), purposes[0].counterparties)
    }

    @Test
    fun venuePurposeWithNothingToNameStillPrompts() {
        // Buzz's warm-auth read: `#p` = me on a group relay. Tagging it must not turn a prompt into
        // a silent deny — with no venue and no author it is unattributed, exactly as a plain filter.
        val filter = ExplainedFilter(kinds = listOf(44100), tags = mapOf("p" to listOf(alice)), purpose = SubPurpose.RELAY_GROUPS)

        val purposes = RelayAuthPurposeDeriver.derive(emptyList(), mapOf("sub" to listOf(filter)))

        assertEquals(listOf(AuthPurposeKind.OTHER), purposes.map { it.kind })
    }
}
