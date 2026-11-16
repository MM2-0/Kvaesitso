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
package com.vitorpamplona.amethyst.commons.relayClient.polls.results

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent

/**
 * How many votes one relay may hand back for the open poll.
 *
 * The engagement filter that feeds the poll *card* asks for 100 events covering every reaction kind
 * at once, which a well-voted poll blows through — and the card has no way to know it did. The
 * results screen is the surface that claims to show every voter, so it asks for its own, far larger
 * page of nothing but kind-1018.
 */
private const val VOTE_LIMIT = 1000

/**
 * Every vote cast on [poll], from the relays a vote could plausibly be on.
 *
 * NIP-88 tells respondents to publish to the relays the poll itself nominates, and
 * [com.vitorpamplona.amethyst.commons.model.EventBroadcaster] obeys that on the way out — those relays are
 * usually neither the author's inbox nor where we happened to see the poll, so they have to be
 * asked explicitly or the tally silently under-counts.
 */
fun filterPollResponses(
    poll: Note,
    since: SincePerRelayMap?,
): List<RelayBasedFilter>? {
    val pollId = poll.event?.id ?: return null
    val relays = ((poll.event as? PollEvent)?.relays().orEmpty() + poll.relayUrlsForReactions()).toSet()
    if (relays.isEmpty()) return null

    return relays.map {
        RelayBasedFilter(
            relay = it,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.THREAD,
                    kinds = listOf(PollResponseEvent.KIND),
                    tags = mapOf("e" to listOf(pollId)),
                    limit = VOTE_LIMIT,
                    since = since?.get(it)?.time,
                ),
        )
    }
}
