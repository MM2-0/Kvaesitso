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
package com.vitorpamplona.amethyst.commons.relayClient.subscriptions

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap

/**
 * Debug-build tripwire: warns when a REQ or COUNT reaches the pool with a filter that never said
 * why it exists.
 *
 * Such a filter keeps its relays connected with nothing to show for it — the always-on notification
 * counts the relay, but no line of its breakdown names it. Tagging is a convention, and a new
 * `fetchAll(Filter(...))` slips past review easily, so this names the offender the first time it
 * runs instead of leaving it to surface as an unexplained relay count on someone's phone.
 *
 * Each producer is reported once, keyed by the shape of what it asks for (kinds and tag names), so an
 * assembler that re-sends its REQ every few seconds does not flood the log. The sub id cannot be the
 * key: the one-shot helpers (`fetchAll`, `count`, …) draw a fresh random one on every call, so it
 * would log every call and grow the set without bound. Never wired into release builds.
 */
class UntaggedFilterWarningClient(
    private val delegate: INostrClient,
) : INostrClient by delegate {
    private val reported = ConcurrentMap<String, Boolean>()

    override fun subscribe(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        listener: SubscriptionListener?,
    ) {
        check("REQ", subId, filters)
        delegate.subscribe(subId, filters, listener)
    }

    override fun count(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) {
        check("COUNT", subId, filters)
        delegate.count(subId, filters)
    }

    private fun check(
        verb: String,
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) {
        // The common case, checked without allocating: assemblers re-send their REQs constantly.
        if (filters.values.all { list -> list.all { it is ExplainedFilter } }) return
        val untagged = filters.values.flatten().filter { it !is ExplainedFilter }

        val kinds = untagged.flatMapTo(mutableSetOf()) { it.kinds.orEmpty() }.sorted()
        val tagNames = untagged.flatMapTo(mutableSetOf()) { it.tags?.keys.orEmpty() }.sorted()
        val key = "$verb $kinds $tagNames ${untagged.any { it.authors != null }}"
        if (reported.putIfAbsent(key, true) == null) {
            Log.w(TAG) { "$verb $subId sent ${untagged.size} filter(s) with no purpose, kinds=$kinds, tags=$tagNames, to ${filters.size} relay(s)" }
        }
    }

    companion object {
        private const val TAG = "UntaggedFilter"
    }
}
