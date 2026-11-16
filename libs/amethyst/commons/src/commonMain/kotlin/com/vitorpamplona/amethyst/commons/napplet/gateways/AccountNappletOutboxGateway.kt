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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.NappletOutboxGateway
import com.vitorpamplona.amethyst.commons.napplet.OutboxDelivery
import com.vitorpamplona.amethyst.commons.napplet.OutboxEvent
import com.vitorpamplona.amethyst.commons.napplet.OutboxRead
import com.vitorpamplona.amethyst.commons.napplet.OutboxRelayPlan
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishStream
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull

/**
 * NAP-OUTBOX for a logged-in [account]: reads go to each author's NIP-65 write relays, as the app's
 * own feeds do; a publish goes to the user's write relays, the read relays of the authors the napplet
 * names, and the relays it asks for once checked. Missing relay lists are fetched from the user's
 * indexer relays. Every event is verified before it is returned and handed over as signed.
 *
 * Unlike NAP-RELAY, nothing is decrypted here: the napplet chooses where its reads go, so a read that
 * decrypted the user's messages could send them anywhere in its next filter. The cache's unwrapped
 * direct messages (rumors, which carry no signature) never verify, so they never leave either. A
 * napplet that needs a plaintext asks the signer, where the user decides.
 */
class AccountNappletOutboxGateway(
    private val account: Account,
) : NappletOutboxGateway {
    private val client get() = account.client.taggedAs(SubPurpose.ADD_ONS, "Napplet")

    /** Routing that may spend up to [relayListBudgetMs] learning relay lists it lacks. */
    private fun routing(relayListBudgetMs: Long = RELAY_LIST_TIMEOUT_MS) =
        NappletOutboxRouting(
            relayListOf = { author, write ->
                account.cache.getUserIfExists(author)?.let { if (write) it.outboxRelays() else it.inboxRelays() }
            },
            fetchRelayLists = { authors -> fetchRelayLists(authors, relayListBudgetMs) },
            fallback = { account.homeRelays.flow.value },
            user = account.pubKey,
            userRelays = { write -> if (write) account.homeRelays.flow.value else account.notificationRelays.flow.value },
        )

    override suspend fun getEvent(
        eventId: HexKey,
        author: HexKey?,
        relayHints: List<String>,
        timeoutMs: Long?,
    ): OutboxRead {
        account.cache.getNoteIfExists(eventId)?.event?.let { cached ->
            if (cached.verify()) return OutboxRead(listOf(OutboxEvent(cached, emptyList())))
        }
        val deadline = Deadline(timeoutMs)
        val route = routing(deadline.forRelayLists()).route(listOfNotNull(author), write = true)
        val relays = route.relays + NappletOutboxRouting.checkedHints(relayHints)
        return read(relays, listOf(Filter(ids = listOf(eventId), limit = 1)), deadline, route.missing.isNotEmpty())
    }

    override suspend fun query(
        filters: List<Filter>,
        authors: List<HexKey>,
        relayHints: List<String>,
        timeoutMs: Long?,
    ): OutboxRead {
        if (filters.isEmpty()) return OutboxRead(emptyList())
        val deadline = Deadline(timeoutMs)
        val route = routing(deadline.forRelayLists()).route(authorsOf(filters, authors), write = true)
        val relays = route.relays + NappletOutboxRouting.checkedHints(relayHints)
        val fromRelays = read(relays, filters, deadline, route.missing.isNotEmpty())
        val seen = fromRelays.events.mapTo(HashSet()) { it.event.id }
        // Newest first and cut to what a read returns before anything is verified: a broad filter can
        // match much of the cache.
        val fromCache =
            filters
                .flatMap { filter ->
                    account.cache
                        .filter(filter)
                        .mapNotNull { it.event }
                        .sortedByDescending { it.createdAt }
                        .take(filter.limit?.coerceIn(0, NappletOutboxGateway.MAX_RESULTS) ?: NappletOutboxGateway.MAX_RESULTS)
                }.filter { seen.add(it.id) && it.verify() }
                .map { OutboxEvent(it, emptyList()) }
        return OutboxRead((fromRelays.events + fromCache).sortedByDescending { it.event.createdAt }, fromRelays.incomplete)
    }

    override suspend fun subscriptionRelays(
        filters: List<Filter>,
        authors: List<HexKey>,
        relayHints: List<String>,
    ): Set<NormalizedRelayUrl> = routing().route(authorsOf(filters, authors), write = true).relays + NappletOutboxRouting.checkedHints(relayHints)

    override suspend fun publish(
        event: Event,
        toOutbox: Boolean,
        toInboxes: List<HexKey>,
        relays: List<String>,
    ): OutboxDelivery {
        val inboxes = if (toInboxes.isEmpty()) null else routing().route(toInboxes, write = false)
        val targets = LinkedHashSet<NormalizedRelayUrl>()
        if (toOutbox) targets += account.outboxRelays.flow.value
        inboxes?.let { targets += it.relays }
        targets += NappletOutboxRouting.checkedHints(relays)
        if (targets.isEmpty()) return OutboxDelivery(emptyMap(), inboxes?.missing.orEmpty())

        val answers =
            runCatchingCancellable {
                NappletOutboxPublish().publish(targets) { relay, answered ->
                    client.publishStream(relay, flowOf(event), idleTimeoutMs = NappletOutboxPublish.CAP_MS) { _, result -> answered(result.accepted) }
                }
            }.getOrDefault(emptyMap())
        // A relay that never answered didn't take it.
        return OutboxDelivery(targets.associateWith { answers[it] ?: false }, inboxes?.missing.orEmpty())
    }

    override suspend fun plan(
        authors: List<HexKey>,
        write: Boolean,
    ): OutboxRelayPlan = routing().route(authors, write).plan()

    /**
     * Reads [filters] from [relays], verifying each event and noting every relay that had it. Stops at
     * the [deadline] with what arrived by then, marked incomplete: the napplet waits on its own clock.
     */
    private suspend fun read(
        relays: Set<NormalizedRelayUrl>,
        filters: List<Filter>,
        deadline: Deadline,
        missingLists: Boolean,
    ): OutboxRead {
        if (relays.isEmpty()) return OutboxRead(emptyList(), incomplete = missingLists)
        val found = LinkedHashMap<String, Pair<Event, MutableList<String>>>()
        val left = deadline.remaining()
        val result =
            runCatchingCancellable {
                withTimeoutOrNull(left) {
                    client.fetchAllWithHooks(relays.associateWith { filters }, idleTimeoutMs = left) { relay, event ->
                        val known = found[event.id]
                        if (known != null) {
                            // Only a relay with the very event it verified counts as having it: a junk
                            // signature on a known id would put any relay on its hints.
                            if (known.first.sig == event.sig && relay.url !in known.second) known.second += relay.url
                        } else if (event.verify()) {
                            found[event.id] = event to mutableListOf(relay.url)
                        }
                        // Kept here, not in the fetch's own result.
                        false
                    }
                }
            }.getOrNull()
        val events = found.values.map { (event, hints) -> OutboxEvent(event, hints) }
        val incomplete = missingLists || result == null || result.stalled.isNotEmpty() || result.dead.isNotEmpty()
        return OutboxRead(events, incomplete)
    }

    /** Fetches the kind 10002 lists of [authors] from the indexer relays into the cache, for at most [budgetMs]. */
    private suspend fun fetchRelayLists(
        authors: List<HexKey>,
        budgetMs: Long,
    ) {
        val indexers =
            account.indexerRelayList.flow.value
                .ifEmpty { account.homeRelays.flow.value }
        if (indexers.isEmpty()) return
        val filter = Filter(kinds = listOf(AdvertisedRelayListEvent.KIND), authors = authors)
        runCatchingCancellable {
            withTimeoutOrNull(budgetMs) {
                client.fetchAllWithHooks(indexers.associateWith { listOf(filter) }, idleTimeoutMs = budgetMs) { relay, event ->
                    account.cache.justConsume(event, relay, wasVerified = false)
                    false
                }
            }
        }
    }

    private fun authorsOf(
        filters: List<Filter>,
        authors: List<HexKey>,
    ): List<HexKey> = (filters.flatMap { it.authors.orEmpty() } + authors).distinct()

    /**
     * A napplet's `timeoutMs` as one budget for the whole request: learning missing relay lists may
     * take part of it, and reading has the rest.
     */
    private class Deadline(
        timeoutMs: Long?,
    ) {
        private val budget = (timeoutMs ?: DEFAULT_TIMEOUT_MS).coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        private val endsAt = TimeUtils.nowMillis() + budget

        fun forRelayLists(): Long = minOf(RELAY_LIST_TIMEOUT_MS, budget * 2 / 5)

        fun remaining(): Long = (endsAt - TimeUtils.nowMillis()).coerceAtLeast(MIN_READ_MS)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
        const val MIN_TIMEOUT_MS = 1_000L
        const val MAX_TIMEOUT_MS = 15_000L
        const val MIN_READ_MS = 500L
        const val RELAY_LIST_TIMEOUT_MS = 4_000L
    }
}
