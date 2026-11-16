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
import com.vitorpamplona.amethyst.commons.viewmodels.nip88Polls.PollLoadReport
import com.vitorpamplona.amethyst.commons.viewmodels.nip88Polls.PollResponseLoader
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.count
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip45Count.mergeCountResults
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent

/**
 * Asks the relays how many votes a poll should have, so the screen can say when it is showing fewer.
 *
 * Fetching the votes is **not** this class's job — that is
 * [com.vitorpamplona.amethyst.ui.screen.loggedIn.polls.results.datasources.PollResponsesFilterAssembler],
 * the same lifecycle-aware, deduplicated, EOSE-tracked subscription every other current-screen data
 * source uses. What is left here is the one question a subscription cannot answer: how many events
 * exist that we did not receive.
 *
 * See [mergeCountResults] for why a COUNT fan-out is never summed — relays mirror each other, so
 * adding their counts would multiply the poll.
 */
class RelayPollResponseLoader(
    private val client: INostrClient,
    private val pollNote: Note,
) : PollResponseLoader {
    companion object {
        /** Per-relay idle window for the COUNT. */
        const val TIMEOUT_MS = 20_000L
    }

    override suspend fun load(poll: PollEvent): PollLoadReport {
        val relays = responseRelays(poll)
        if (relays.isEmpty()) return PollLoadReport(null, approximate = false, relaysAsked = 0, relaysAnswered = 0)

        val filter =
            ExplainedFilter(
                kinds = listOf(PollResponseEvent.KIND),
                tags = mapOf("e" to listOf(poll.id)),
                purpose = SubPurpose.ENGAGEMENT,
                purposeDetail = "Poll results",
            )

        val results = client.count(relays.associateWith { listOf(filter) }, idleTimeoutMs = TIMEOUT_MS)
        val merged = mergeCountResults(results.values)

        return PollLoadReport(
            reported = merged?.count,
            // An estimate is approximate; so is a figure from only some of the relays we asked.
            approximate = (merged?.approximate ?: false) || results.size < relays.size,
            relaysAsked = relays.size,
            relaysAnswered = results.size,
        )
    }

    /**
     * Where a poll's votes live: the relays the poll itself nominates (NIP-88 tells respondents to
     * publish there, and [com.vitorpamplona.amethyst.commons.model.EventBroadcaster] obeys it), plus the
     * relays we would look at for any other engagement. Same set the subscription asks.
     */
    private fun responseRelays(poll: PollEvent): Set<NormalizedRelayUrl> = (poll.relays() + pollNote.relayUrlsForReactions()).toSet()
}
