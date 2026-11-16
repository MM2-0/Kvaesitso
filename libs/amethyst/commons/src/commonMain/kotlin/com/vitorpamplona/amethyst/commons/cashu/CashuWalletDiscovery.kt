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
package com.vitorpamplona.amethyst.commons.cashu

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlin.coroutines.cancellation.CancellationException

/**
 * Crawls *every* known relay for the user's NIP-60 wallet events.
 *
 * NIP-60 wallets (kind:17375) are portable across clients and replaceable, so a
 * user who created one in another app (cashu.me, Boardwalk, …) may have a valid
 * wallet sitting on relays Amethyst's own subscription never reads. Before the
 * "create a new wallet" path can clobber it, the find-my-wallet wizard runs this
 * discovery to pull in *all* distinct kind:17375 (and kind:10019 nutzap-config)
 * events authored by the account.
 *
 * Modeled on
 * [com.vitorpamplona.amethyst.commons.relays.eventsync.EventSync]:
 *  - queries up to [MAX_CONCURRENT_RELAYS] relays concurrently via a sliding
 *    [Semaphore] window inside a [supervisorScope];
 *  - paginates each relay individually through Quartz's [fetchAllPages];
 *  - runs on a **fresh** [INostrClient] (built by [clientBuilder]) so the crawled
 *    events never pollute the production [com.vitorpamplona.amethyst.commons.model.cache.LocalCache].
 *
 * Unlike EventSync this neither republishes nor mutates state — it only collects
 * and hands the distinct events back to the wizard for parsing/verification.
 */
@Stable
class CashuWalletDiscovery(
    private val pubKey: HexKey,
    private val relayDb: () -> List<NormalizedRelayUrl>,
    private val clientBuilder: () -> INostrClient,
    private val scope: CoroutineScope,
) {
    companion object {
        /** Maximum number of relays queried at the same time. */
        const val MAX_CONCURRENT_RELAYS = 50

        /** How long (ms) to wait for a single relay to reply per page before giving up. */
        const val RELAY_TIMEOUT_MS = 30_000L
    }

    sealed class State {
        object Idle : State()

        data class Crawling(
            val relaysCompleted: Int,
            val totalRelays: Int,
        ) : State()

        data class Done(
            val walletEvents: List<CashuWalletEvent>,
            val nutzapInfoEvents: List<NutzapInfoEvent>,
        ) : State()

        data class Error(
            val message: String,
        ) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    /** Begin (or restart) a crawl. No-op while one is already in flight. */
    fun start() {
        if (job?.isActive == true) return
        _state.value = State.Idle
        job = scope.launch(Dispatchers.IO) { crawl() }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private suspend fun crawl() {
        val relays = relayDb()
        if (relays.isEmpty()) {
            _state.value = State.Done(emptyList(), emptyList())
            return
        }

        // Distinct by event id — the same kind:17375 is found on many relays,
        // but a user with wallets created by different apps will have several
        // distinct ids. Keep them all; the wizard sorts/picks the newest.
        val wallets = MutableStateFlow<Map<HexKey, CashuWalletEvent>>(emptyMap())
        val nutzapInfos = MutableStateFlow<Map<HexKey, NutzapInfoEvent>>(emptyMap())
        val completed = MutableStateFlow(0)

        _state.value = State.Crawling(0, relays.size)

        // One filter covers both kinds, restricted to this account. fetchAllPages
        // pages with `until` cursors per relay, so even relays that cap returns
        // are walked back to the oldest event.
        val filters =
            listOf(
                ExplainedFilter(
                    kinds = listOf(CashuWalletEvent.KIND, NutzapInfoEvent.KIND),
                    authors = listOf(pubKey),
                    purpose = SubPurpose.WALLET,
                ),
            )

        try {
            clientBuilder().use { client ->
                client.crawlPool(
                    relays = relays,
                    filters = filters,
                    onEvent = { event ->
                        when (event) {
                            is CashuWalletEvent ->
                                if (event.pubKey == pubKey) wallets.update { if (event.id in it) it else it + (event.id to event) }
                            is NutzapInfoEvent ->
                                if (event.pubKey == pubKey) nutzapInfos.update { if (event.id in it) it else it + (event.id to event) }
                            else -> Unit
                        }
                    },
                    onRelayComplete = {
                        // Up to MAX_CONCURRENT_RELAYS relays finish at once: a coroutine preempted
                        // between the increment and the write would move the progress backwards.
                        val done = completed.updateAndGet { it + 1 }
                        _state.update { current ->
                            if (current is State.Crawling && current.relaysCompleted >= done) current else State.Crawling(done, relays.size)
                        }
                    },
                )
            }

            _state.value =
                State.Done(
                    walletEvents = wallets.value.values.sortedByDescending { it.createdAt },
                    nutzapInfoEvents = nutzapInfos.value.values.sortedByDescending { it.createdAt },
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("CashuWalletDiscovery", "Crawl failed", e)
            _state.value = State.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Sliding-window relay crawl: keeps up to [MAX_CONCURRENT_RELAYS] relays
     * paginating at once, starting the next as soon as one finishes. Same shape as
     * the shared `fetchAllPagesFromPool` accessory, but with one filter list for
     * every relay and collect-only (no per-relay tagging, no republish).
     */
    private suspend fun INostrClient.crawlPool(
        relays: List<NormalizedRelayUrl>,
        filters: List<Filter>,
        onEvent: (Event) -> Unit,
        onRelayComplete: (NormalizedRelayUrl) -> Unit,
    ) {
        val semaphore = Semaphore(MAX_CONCURRENT_RELAYS)
        supervisorScope {
            for (relay in relays) {
                if (!isActive) break
                semaphore.acquire()
                launch {
                    try {
                        // A single unreachable/slow relay must not abort the crawl.
                        runCatching {
                            fetchAllPages(
                                relay = relay,
                                filters = filters,
                                idleTimeoutMs = RELAY_TIMEOUT_MS,
                                onEvent = onEvent,
                            )
                        }.onFailure {
                            if (it is CancellationException) throw it
                            Log.w("CashuWalletDiscovery") { "Relay ${relay.url} failed: ${it.message}" }
                        }
                        onRelayComplete(relay)
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
    }
}
