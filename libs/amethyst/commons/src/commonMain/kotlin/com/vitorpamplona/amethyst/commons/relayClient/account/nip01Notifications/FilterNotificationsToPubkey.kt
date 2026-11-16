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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.review.GitReviewEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcResponseEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/**
 * Kinds that notify me about activity on MY messages inside a NIP-29 group — reactions, replies
 * (chat + NIP-22 comments), poll responses, reposts, zaps and reports. Scoped by `#h` to my joined
 * groups + `#p` to me. These live on the group's host relay, not my inbox relays.
 */
val GroupNotificationKinds =
    listOf(
        ReactionEvent.KIND,
        ChatEvent.KIND,
        CommentEvent.KIND,
        PollEvent.KIND,
        PollResponseEvent.KIND,
        RepostEvent.KIND,
        GenericRepostEvent.KIND,
        ZapReceiptEvent.KIND,
        Bolt12ZapEvent.KIND,
        ReportEvent.KIND,
    )

val SummaryKinds =
    listOf(
        TextNoteEvent.KIND,
        ReactionEvent.KIND,
        RepostEvent.KIND,
        GenericRepostEvent.KIND,
        ZapReceiptEvent.KIND,
        OnchainZapEvent.KIND,
        Bolt12ZapEvent.KIND,
    )

val NotificationsPerKeyKinds =
    listOf(
        ReportEvent.KIND,
        NwcResponseEvent.KIND,
        ChannelMessageEvent.KIND,
        EphemeralChatEvent.KIND,
        BadgeAwardEvent.KIND,
        ZapPollEvent.KIND,
        PollEvent.KIND,
        PollResponseEvent.KIND,
        PublicMessageEvent.KIND,
    )

val NotificationsPerKeyKinds2 =
    listOf(
        GitReplyEvent.KIND,
        GitIssueEvent.KIND,
        GitPatchEvent.KIND,
        GitPullRequestEvent.KIND,
        GitPullRequestUpdateEvent.KIND,
        // NIP-34 status events (1630/1631/1632/1633): opened, applied/merged,
        // closed, drafted. The status author p-tags every prior participant of
        // the target patch/PR/issue, so a `#p`=me subscription surfaces
        // "someone merged/closed a thread I'm on" without a repo-scoped query.
        GitStatusOpenEvent.KIND,
        GitStatusAppliedEvent.KIND,
        GitStatusClosedEvent.KIND,
        GitStatusDraftEvent.KIND,
        // NIP-34 reviews (7321) p-tag the reviewed patch's or PR's author.
        GitReviewEvent.KIND,
        HighlightEvent.KIND,
        CommentEvent.KIND,
        CalendarDateSlotEvent.KIND,
        CalendarTimeSlotEvent.KIND,
        CalendarRSVPEvent.KIND,
        InteractiveStoryPrologueEvent.KIND,
        InteractiveStorySceneEvent.KIND,
        LiveChessGameAcceptEvent.KIND,
        LiveChessMoveEvent.KIND,
    )

/** The `K` (root kind) values of comments on issues, patches and pull requests. */
val GitRootKindTags = listOf(GitIssueEvent.KIND.toString(), GitPatchEvent.KIND.toString(), GitPullRequestEvent.KIND.toString())

val NotificationsPerKeyKinds3 =
    listOf(
        AttestationRequestEvent.KIND,
        AttestorRecommendationEvent.KIND,
    )

/**
 * Every kind the notifications feed cares about on the user's inbox relays, flattened into one list.
 * The live-tail query ([filterNotificationsToPubkey] / [filterSummaryNotificationsToPubkey]) splits these
 * across several filters with different per-kind limits; backward history paging instead asks ONE filter
 * per relay so the single until+limit cursor stays gap-proof (an empty page truly means "nothing older").
 */
val AllNotificationKinds =
    (SummaryKinds + NotificationsPerKeyKinds + NotificationsPerKeyKinds2 + NotificationsPerKeyKinds3).distinct()

/**
 * One backward-paging page of notifications on an inbox relay: the N newest events tagging me
 * ([AllNotificationKinds], `#p` = me) strictly older than [until]. A single filter (not the live query's
 * split) so the [BackwardRelayPager][com.vitorpamplona.amethyst.commons.relayClient.paging.BackwardRelayPager]
 * cursor tracking the oldest delivered `created_at` can't skip a band that a per-kind sub-limit capped.
 */
fun filterNotificationsHistoryToPubkey(
    relay: NormalizedRelayUrl,
    pubkey: HexKey?,
    until: Long,
    limit: Int,
): List<RelayBasedFilter> {
    if (pubkey.isNullOrEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.NOTIFICATIONS,
                    accountPubKeys = listOfNotNull(pubkey),
                    kinds = AllNotificationKinds,
                    tags = mapOf("p" to listOf(pubkey)),
                    limit = limit,
                    until = until,
                ),
        ),
    )
}

/**
 * One backward-paging page of NIP-29 group-activity notifications on a group's host relay:
 * [GroupNotificationKinds] tagging me (`#p`) inside my joined groups (`#h`), strictly older than [until].
 */
fun filterGroupNotificationsHistoryToPubkey(
    relay: NormalizedRelayUrl,
    pubkey: HexKey?,
    groupIds: List<String>,
    until: Long,
    limit: Int,
): List<RelayBasedFilter> {
    if (pubkey.isNullOrEmpty() || groupIds.isEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.NOTIFICATIONS,
                    accountPubKeys = listOfNotNull(pubkey),
                    kinds = GroupNotificationKinds,
                    tags = mapOf("p" to listOf(pubkey), "h" to groupIds),
                    limit = limit,
                    until = until,
                ),
        ),
    )
}

fun filterSummaryNotificationsToPubkeys(
    relay: NormalizedRelayUrl,
    pubkeys: List<HexKey>,
    since: Long?,
): List<RelayBasedFilter> {
    if (pubkeys.isEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.NOTIFICATIONS,
                    accountPubKeys = pubkeys,
                    kinds = SummaryKinds,
                    tags = mapOf("p" to pubkeys),
                    limit = 2000,
                    since = since,
                ),
        ),
    )
}

fun filterNotificationsToPubkeys(
    relay: NormalizedRelayUrl,
    pubkeys: List<HexKey>,
    since: Long?,
): List<RelayBasedFilter> {
    if (pubkeys.isEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.NOTIFICATIONS,
                    accountPubKeys = pubkeys,
                    kinds = NotificationsPerKeyKinds,
                    tags = mapOf("p" to pubkeys),
                    limit = 500,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.NOTIFICATIONS,
                    accountPubKeys = pubkeys,
                    kinds = NotificationsPerKeyKinds2,
                    tags = mapOf("p" to pubkeys),
                    limit = 200,
                    since = since,
                ),
        ),
        // NIP-22 comments anywhere under my issue, patch or PR. A reply to someone else's comment
        // names me only as the root author (uppercase `P`), so the `#p` filters above never see it.
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.NOTIFICATIONS,
                    accountPubKeys = pubkeys,
                    kinds = listOf(CommentEvent.KIND),
                    tags = mapOf("P" to pubkeys, "K" to GitRootKindTags),
                    limit = 100,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.NOTIFICATIONS,
                    accountPubKeys = pubkeys,
                    kinds = NotificationsPerKeyKinds3,
                    tags = mapOf("p" to pubkeys),
                    limit = 10,
                    since = since,
                ),
        ),
    )
}

/**
 * Notifications for activity on my messages inside joined NIP-29 groups, queried on the group's
 * HOST relay (where the content lives) scoped by `#h` to those groups and `#p` to me. Without this,
 * a reaction/reply to my group message is only fetched when I open the group, never surfacing in
 * the notifications feed.
 */
fun filterGroupNotificationsToPubkey(
    relay: NormalizedRelayUrl,
    pubkey: HexKey?,
    groupIds: List<String>,
    since: Long?,
): List<RelayBasedFilter> {
    if (pubkey.isNullOrEmpty() || groupIds.isEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.NOTIFICATIONS,
                    accountPubKeys = listOfNotNull(pubkey),
                    kinds = GroupNotificationKinds,
                    tags = mapOf("p" to listOf(pubkey), "h" to groupIds),
                    limit = 200,
                    since = since,
                ),
        ),
    )
}

fun filterJustTheLatestNotificationsToPubkeyFromRandomRelays(
    relay: NormalizedRelayUrl,
    pubkey: HexKey?,
    since: Long?,
): List<RelayBasedFilter> {
    if (pubkey.isNullOrEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.NOTIFICATIONS,
                    accountPubKeys = listOfNotNull(pubkey),
                    kinds = SummaryKinds,
                    tags = mapOf("p" to listOf(pubkey)),
                    limit = 20,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.NOTIFICATIONS,
                    accountPubKeys = listOfNotNull(pubkey),
                    kinds = NotificationsPerKeyKinds,
                    tags = mapOf("p" to listOf(pubkey)),
                    limit = 20,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.NOTIFICATIONS,
                    accountPubKeys = listOfNotNull(pubkey),
                    kinds = NotificationsPerKeyKinds2,
                    tags = mapOf("p" to listOf(pubkey)),
                    limit = 10,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.NOTIFICATIONS,
                    accountPubKeys = listOfNotNull(pubkey),
                    kinds = NotificationsPerKeyKinds3,
                    tags = mapOf("p" to listOf(pubkey)),
                    limit = 2,
                    since = since,
                ),
        ),
    )
}
