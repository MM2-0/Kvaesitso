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
package com.vitorpamplona.amethyst.commons.relayClient.nips

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipsOnNostr
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.concurrent.Volatile

/**
 * What a NIPs screen keeps live while it is open. Each is one relay subscription, shared by every
 * screen asking the same question ([id]); after the first EOSE a relay is only asked for what is
 * newer, so new NIPs, new versions and deletions keep arriving.
 */
sealed class NipsQueryState(
    override val account: Account,
) : AccountScopedQuery {
    abstract val id: String

    /** Every NIP: the NIPs screen. No time window: NIPs are few and long-lived. */
    class All(
        account: Account,
    ) : NipsQueryState(account) {
        override val id = "all"
    }

    /** The NIPs whose `k` tags define [kind]: the kind page and the "Defined by" row of an unknown event. */
    class DefiningKind(
        val kind: Int,
        account: Account,
    ) : NipsQueryState(account) {
        override val id = "k:$kind"
    }

    /** Recent events of [kind]: the kind page's sample. */
    class KindEvents(
        val kind: Int,
        account: Account,
    ) : NipsQueryState(account) {
        override val id = "kind:$kind"
    }

    /**
     * One NIP, watched for new versions while it is read. The relays it has been seen on are read
     * each time the filters are rebuilt, so ones it turns up on later are asked too.
     */
    class Watch(
        val note: AddressableNote,
        account: Account,
    ) : NipsQueryState(account) {
        val address: Address get() = note.address
        override val id = "a:" + note.address.toValue()
    }
}

class NipsFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<NipsQueryState>() {
    val group = listOf(NipsSubAssembler(client, ::allKeys))

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

/**
 * The relays asked include the outboxes of the cached NIPs' authors, so after an EOSE that brought in
 * a new author the filters are rebuilt to ask that author's outboxes too. Only then: rebuilding on
 * every EOSE would move that relay's `since` and send it a new REQ, which answers with another EOSE.
 */
class NipsSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<NipsQueryState>,
) : PerUniqueIdEoseManager<NipsQueryState, String>(client, allKeys) {
    @Volatile private var askedOutboxes: Set<NormalizedRelayUrl> = emptySet()

    override fun newEose(
        key: NipsQueryState,
        relayUrl: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>?,
    ) {
        super.newEose(key, relayUrl, time, filters)
        if (!askedOutboxes.containsAll(NipsOnNostr.authorOutboxes())) invalidateFilters()
    }

    override fun updateFilter(
        key: NipsQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val outboxes = NipsOnNostr.authorOutboxes()
        askedOutboxes = outboxes
        val relays = NipsOnNostr.accountDiscoveryRelays(key.account) + outboxes
        val all =
            when (key) {
                is NipsQueryState.Watch ->
                    relays + key.note.relays +
                        key.note.author
                            ?.outboxRelays()
                            .orEmpty()
                else -> relays
            }
        return nipsRelayFilters(NipsQuerySpec.of(key), all, since)
    }

    override fun id(key: NipsQueryState) = key.id
}

/** The REQ a [NipsQueryState] sends, apart from the relays and the per-relay `since`. */
class NipsQuerySpec(
    val purpose: SubPurpose,
    val kinds: List<Int>,
    val authors: List<String>? = null,
    val tags: Map<String, List<String>>? = null,
    val limit: Int,
) {
    companion object {
        const val ALL_LIMIT = 500
        const val DEFINING_LIMIT = 50
        const val KIND_EVENTS_LIMIT = 20

        fun of(key: NipsQueryState): NipsQuerySpec =
            when (key) {
                is NipsQueryState.All -> NipsQuerySpec(SubPurpose.TOPIC_FEED, listOf(NipTextEvent.KIND), limit = ALL_LIMIT)
                is NipsQueryState.DefiningKind -> NipsQuerySpec(SubPurpose.TOPIC_FEED, listOf(NipTextEvent.KIND), tags = mapOf("k" to listOf(key.kind.toString())), limit = DEFINING_LIMIT)
                is NipsQueryState.KindEvents -> NipsQuerySpec(SubPurpose.TOPIC_FEED, listOf(key.kind), limit = KIND_EVENTS_LIMIT)
                is NipsQueryState.Watch ->
                    NipsQuerySpec(
                        SubPurpose.THREAD,
                        listOf(key.address.kind),
                        authors = listOf(key.address.pubKeyHex),
                        tags = mapOf("d" to listOf(key.address.dTag)),
                        limit = 1,
                    )
            }
    }
}

/** One filter per relay for [spec], each from where that relay's last EOSE left off ([since]). */
fun nipsRelayFilters(
    spec: NipsQuerySpec,
    relays: Set<NormalizedRelayUrl>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> =
    relays.map { relay ->
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = spec.purpose,
                    kinds = spec.kinds,
                    authors = spec.authors,
                    tags = spec.tags,
                    limit = spec.limit,
                    since = since?.get(relay)?.time,
                ),
        )
    }
