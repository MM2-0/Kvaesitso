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
package com.vitorpamplona.amethyst.commons.relayClient.event.watchers

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.pressstr.issue.MagazineIssueEvent
import com.vitorpamplona.quartz.experimental.pressstr.magazine.MagazineEvent
import com.vitorpamplona.quartz.experimental.videoCollaboration.VideoCollaborationEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.utils.mapOfSet

val RepliesAndReactionsToAddressesKinds1 =
    listOf(
        TextNoteEvent.KIND,
        ReactionEvent.KIND,
        RepostEvent.KIND,
        GenericRepostEvent.KIND,
        ReportEvent.KIND,
        ZapReceiptEvent.KIND,
        Bolt12ZapEvent.KIND,
        ZapPollEvent.KIND,
        CommentEvent.KIND,
        AttestationEvent.KIND,
        BadgeAwardEvent.KIND,
        // A NIP-CC found log points at the cache it claims with a lowercase `a`, which is how
        // every client writing them today does it. Without this the cache screen asks for
        // engagement on the listing and never for its finds, so a cache with logs sitting on a
        // relay we are already connected to still reads "No one has logged this cache yet".
        GeocacheFoundLogEvent.KIND,
    )

val PostsAndChatMessagesToAddresses =
    listOf(
        CommunityPostApprovalEvent.KIND,
        LiveActivitiesChatMessageEvent.KIND,
        // A collaborator's answer to being credited on a NIP-71 video. Only the `a` tag is
        // common to both shapes in the wild — divine-web keys the event by a random `d`, so
        // there is no coordinate to ask for and this `a` filter is the only way to see it.
        VideoCollaborationEvent.KIND,
    )

/**
 * A PressStr magazine's issues point at it with `a`, and nothing else lists them, so this is how a
 * magazine on screen finds its issues. Its own filter rather than a seat in the ones above: those
 * are shared with every address on screen and with live-stream chat, which would use up their
 * limit first, and only the magazine's own author can publish its issues.
 */
val MagazineIssueKindList = listOf(MagazineIssueEvent.KIND)

private val MAGAZINE_ADDRESS_PREFIX = "${MagazineEvent.KIND}:"

/**
 * NIP-22 keeps the conversation root in the **uppercase** `A` tag and only the direct
 * parent in lowercase `a`. A comment on a comment of an article therefore never carries
 * `a`=<address>, so an `a`-only engagement filter sees only the first level of that
 * 1111 thread — the rest used to appear only once ThreadScreen opened its own `A`
 * subscription.
 */
val RootScopedRepliesToAddressesKinds =
    listOf(
        CommentEvent.KIND,
    )

val DeletionKindList =
    listOf(
        DeletionRequestEvent.KIND,
    )

val TextNoteKindList = listOf(TextNoteEvent.KIND)

fun filterRepliesAndReactionsToAddresses(
    keys: List<AddressableNote>,
    since: SincePerRelayMap?,
    accountPubKey: HexKey? = null,
): List<RelayBasedFilter>? {
    if (keys.isEmpty()) return null

    val notesPerRelay =
        mapOfSet {
            keys.forEach {
                it.relayUrlsForReactions().forEach { relay ->
                    add(relay, it.address().toValue())
                }
            }
        }

    return notesPerRelay.flatMap {
        val since = since?.get(it.key)?.time
        val sortedList = it.value.sorted()
        val relay = it.key
        magazineIssuesFilter(relay, sortedList, since, accountPubKey) +
            listOf(
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.ENGAGEMENT,
                            accountPubKeys = listOfNotNull(accountPubKey),
                            kinds = RepliesAndReactionsToAddressesKinds1,
                            tags = mapOf("a" to sortedList),
                            since = since,
                            // Max amount of "replies" to download on a specific event.
                            limit = 1000,
                        ),
                ),
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.ENGAGEMENT,
                            accountPubKeys = listOfNotNull(accountPubKey),
                            kinds = PostsAndChatMessagesToAddresses,
                            tags = mapOf("a" to sortedList),
                            since = since,
                            // Max amount of "replies" to download on a specific event.
                            limit = 100,
                        ),
                ),
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.ENGAGEMENT,
                            accountPubKeys = listOfNotNull(accountPubKey),
                            kinds = DeletionKindList,
                            tags = mapOf("a" to sortedList),
                            since = since,
                            // Max amount of "replies" to download on a specific event.
                            limit = 10,
                        ),
                ),
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.ENGAGEMENT,
                            accountPubKeys = listOfNotNull(accountPubKey),
                            kinds = TextNoteKindList,
                            tags = mapOf("q" to sortedList),
                            since = since,
                            limit = 1000,
                        ),
                ),
                // Its own filter on purpose: tag names inside one filter are ANDed, so folding
                // "A" into the "a" filter above would only match comments carrying both.
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.ENGAGEMENT,
                            accountPubKeys = listOfNotNull(accountPubKey),
                            kinds = RootScopedRepliesToAddressesKinds,
                            tags = mapOf("A" to sortedList),
                            since = since,
                            // Matches the ceiling of the direct-reply filter above: this is the
                            // rest of the same thread, not a second conversation.
                            limit = 1000,
                        ),
                ),
            )
    }
}

private fun magazineIssuesFilter(
    relay: NormalizedRelayUrl,
    sortedAddresses: List<String>,
    since: Long?,
    accountPubKey: HexKey?,
): List<RelayBasedFilter> {
    val magazines = sortedAddresses.filter { it.startsWith(MAGAZINE_ADDRESS_PREFIX) }
    if (magazines.isEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ENGAGEMENT,
                    accountPubKeys = listOfNotNull(accountPubKey),
                    kinds = MagazineIssueKindList,
                    authors = magazines.mapNotNull { it.split(':').getOrNull(1) }.distinct(),
                    tags = mapOf("a" to magazines),
                    since = since,
                    // A run of a periodical, not a conversation: years of monthly issues fit.
                    limit = 200,
                ),
        ),
    )
}
