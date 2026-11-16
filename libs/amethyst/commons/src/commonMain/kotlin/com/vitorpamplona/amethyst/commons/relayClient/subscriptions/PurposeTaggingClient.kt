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

/**
 * Tags every filter that passes through it with [purpose], so the relay it opens can say why.
 *
 * Some subscriptions cannot carry an [ExplainedFilter] themselves: the filter is built inside
 * Quartz (the NIP-46 bunker, the ContextVM transport, NIP-47 helpers), which cannot depend on
 * `commons`, or it goes through a one-shot helper ([fetchAll], [fetchFirst], [fetchAllPages], …)
 * that takes a plain [Filter]. Without a purpose such a relay shows up as connected for no reason —
 * the always-on notification counted it, but no line in its breakdown did.
 *
 * Handing the job this client instead of the raw one tags it at the boundary. The one-shot
 * helpers are extension functions that route through [subscribe] / [count], so they are covered
 * too. A filter that already names a purpose keeps it: the builder knew better.
 */
class PurposeTaggingClient(
    private val delegate: INostrClient,
    private val purpose: SubPurpose,
    private val detail: String? = null,
) : INostrClient by delegate {
    override fun subscribe(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        listener: SubscriptionListener?,
    ) {
        delegate.subscribe(subId, filters.tagged(), listener)
    }

    override fun count(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) {
        delegate.count(subId, filters.tagged())
    }

    private fun Map<NormalizedRelayUrl, List<Filter>>.tagged(): Map<NormalizedRelayUrl, List<Filter>> {
        if (values.all { list -> list.all { it is ExplainedFilter } }) return this
        return mapValues { (_, list) -> list.map { it.taggedWith(purpose, detail) } }
    }
}

/** This filter as an [ExplainedFilter] for [purpose], unless it already names one. */
fun Filter.taggedWith(
    purpose: SubPurpose,
    detail: String? = null,
): Filter = this as? ExplainedFilter ?: ExplainedFilter.of(this, purpose, detail)

/** A view of this client whose subscriptions are all attributed to [purpose]. */
fun INostrClient.taggedAs(
    purpose: SubPurpose,
    detail: String? = null,
): INostrClient = PurposeTaggingClient(this, purpose, detail)
