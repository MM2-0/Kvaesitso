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
package com.vitorpamplona.amethyst.commons.relayClient.communities

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent

val CommunityPostKinds =
    listOf(
        TextNoteEvent.KIND,
        CommentEvent.KIND,
        RepostEvent.KIND,
        GenericRepostEvent.KIND,
        ClassifiedsEvent.KIND,
        LongFormContentEvent.KIND,
        CommunityPostApprovalEvent.KIND,
        AttestationEvent.KIND,
    )

fun filterCommunityPosts(
    relay: NormalizedRelayUrl,
    community: CommunityDefinitionEvent,
    since: Long?,
): RelayBasedFilter =
    RelayBasedFilter(
        relay = relay,
        filter =
            ExplainedFilter(
                purpose = SubPurpose.COMMUNITY_FEED,
                authors = community.moderatorKeys(),
                tags = mapOf("a" to listOf(community.addressTag())),
                kinds = CommunityPostKinds,
                limit = 500,
                since = since,
            ),
    )

fun filterCommunityPostsFromModerators(
    relay: NormalizedRelayUrl,
    authors: Set<HexKey>,
    community: CommunityDefinitionEvent,
    since: Long?,
): RelayBasedFilter =
    RelayBasedFilter(
        relay = relay,
        filter =
            ExplainedFilter(
                purpose = SubPurpose.COMMUNITY_FEED,
                authors = authors.sorted(),
                tags = mapOf("a" to listOf(community.addressTag())),
                kinds = CommunityPostKinds,
                limit = 100,
                since = since,
            ),
    )

fun filterCommunityPostsFromEverybody(
    relay: NormalizedRelayUrl,
    community: CommunityDefinitionEvent,
    since: Long?,
): RelayBasedFilter =
    RelayBasedFilter(
        relay = relay,
        filter =
            ExplainedFilter(
                purpose = SubPurpose.COMMUNITY_FEED,
                tags = mapOf("a" to listOf(community.addressTag())),
                kinds = CommunityPostKinds,
                limit = 100,
                since = since,
            ),
    )
