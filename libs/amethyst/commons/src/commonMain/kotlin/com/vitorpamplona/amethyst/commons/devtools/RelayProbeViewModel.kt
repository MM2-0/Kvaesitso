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
package com.vitorpamplona.amethyst.commons.devtools

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.DONE_REASON_AUTH_REFUSED
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.DONE_REASON_EOSE
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** What one relay did with the probe's REQ. */
enum class RelayProbeStatus {
    WAITING,
    QUERYING,
    FOUND,
    EMPTY,
    AUTH_REQUIRED,
    REFUSED,
    OFFLINE,
    TIMEOUT,

    /** The app routes this relay over Tor and Tor is not up yet, so it was not dialed. */
    WAITING_FOR_TOR,
}

@Immutable
data class RelayProbeResult(
    val relay: NormalizedRelayUrl,
    val status: RelayProbeStatus,
    /** `created_at` of the newest matching event this relay served. */
    val newestAt: Long? = null,
    /** The relay's own words for a refusal or failure. */
    val message: String? = null,
)

enum class RelayProbePhase {
    IDLE,
    DISCOVERING,
    PROBING,
    DONE,
}

@Immutable
data class RelayProbeState(
    val phase: RelayProbePhase = RelayProbePhase.IDLE,
    /** One row per relay, keyed so each answer is an O(log n) update instead of a list copy. */
    val results: PersistentMap<NormalizedRelayUrl, RelayProbeResult> = persistentMapOf(),
    /** The newest matching event across every relay: the sample the screen shows. */
    val newest: Event? = null,
) {
    val total get() = results.size
    val finished get() = results.values.count { it.status != RelayProbeStatus.WAITING && it.status != RelayProbeStatus.QUERYING }
    val found get() = results.values.count { it.status == RelayProbeStatus.FOUND }
}

/**
 * "Where is this event": asks many relays for one id, one REQ per relay, and keeps what each one
 * did (found, none, refused, offline, timed out, waiting for Tor), the per-relay table
 * kinds.amethyst.social draws for a kind.
 *
 * Runs on a throwaway client from [clientBuilder] (Amethyst's crawl client: relay-authenticated,
 * blocked relays removed, events kept out of LocalCache) and closes it when done.
 */
class RelayProbeViewModel(
    private val filter: Filter,
    private val seedRelays: Set<NormalizedRelayUrl>,
    private val clientBuilder: () -> INostrClient,
    private val canDial: (NormalizedRelayUrl) -> Boolean = { true },
) : ViewModel() {
    private val _state = MutableStateFlow(RelayProbeState())
    val state: StateFlow<RelayProbeState> = _state

    private var job: Job? = null

    fun start(includeDiscovered: Boolean) {
        job?.cancel()
        job =
            viewModelScope.launch(Dispatchers.IO) {
                val client = clientBuilder()
                try {
                    run(client, includeDiscovered)
                } finally {
                    client.close()
                }
            }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(phase = RelayProbePhase.DONE) }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private suspend fun run(
        client: INostrClient,
        includeDiscovered: Boolean,
    ) {
        _state.value = RelayProbeState(phase = RelayProbePhase.DISCOVERING)

        val relays =
            if (includeDiscovered) {
                seedRelays + discoverRelays(client)
            } else {
                seedRelays
            }

        _state.value =
            RelayProbeState(
                phase = RelayProbePhase.PROBING,
                results = relays.associateWith { RelayProbeResult(it, RelayProbeStatus.WAITING) }.toPersistentMap(),
            )

        val permits = Semaphore(PARALLEL_RELAYS)
        coroutineScope {
            relays.forEach { relay ->
                launch {
                    permits.withPermit { probe(client, relay) }
                }
            }
        }

        _state.update { it.copy(phase = RelayProbePhase.DONE) }
    }

    private suspend fun probe(
        client: INostrClient,
        relay: NormalizedRelayUrl,
    ) {
        // A socket the builder will not dial never reaches a terminal state, so asking would
        // only burn the whole deadline and then read as a dead relay.
        if (!canDial(relay)) {
            report(RelayProbeResult(relay, RelayProbeStatus.WAITING_FOR_TOR))
            return
        }
        report(RelayProbeResult(relay, RelayProbeStatus.QUERYING))

        var newest: Event? = null
        val result =
            try {
                withTimeoutOrNull(RELAY_DEADLINE_MS) {
                    client.fetchAllWithHooks(mapOf(relay to listOf(filter)), idleTimeoutMs = IDLE_MS) { _, event ->
                        if (newest == null || event.createdAt > newest!!.createdAt) newest = event
                        true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                report(RelayProbeResult(relay, RelayProbeStatus.OFFLINE, message = e.message))
                return
            }

        val reason = result?.doneReasons?.get(relay)
        val status =
            when {
                newest != null -> RelayProbeStatus.FOUND
                reason == null -> RelayProbeStatus.TIMEOUT
                reason == DONE_REASON_EOSE -> RelayProbeStatus.EMPTY
                reason.startsWith(DONE_REASON_AUTH_REFUSED) -> RelayProbeStatus.AUTH_REQUIRED
                reason.startsWith("closed") -> RelayProbeStatus.REFUSED
                else -> RelayProbeStatus.OFFLINE
            }
        val message = reason?.substringAfter(':', "")?.ifBlank { null }

        report(
            RelayProbeResult(
                relay = relay,
                status = status,
                newestAt = newest?.createdAt,
                message = message,
            ),
            newest,
        )
    }

    private fun report(
        result: RelayProbeResult,
        event: Event? = null,
    ) {
        _state.update { current ->
            val newest =
                if (event != null && (current.newest == null || event.createdAt > current.newest.createdAt)) event else current.newest
            // A probe cancelled by a restart may still answer; its relay may not be in this run.
            if (result.relay !in current.results) return@update current
            current.copy(
                results = current.results.putting(result.relay, result),
                newest = newest,
            )
        }
    }

    companion object {
        const val PARALLEL_RELAYS = 24
        const val IDLE_MS = 6_000L
        const val RELAY_DEADLINE_MS = 20_000L
        const val DISCOVERY_DEADLINE_MS = 30_000L

        val MONITOR_RELAYS =
            listOf("wss://relay.nostr.watch", "wss://relaypag.es", "wss://monitorlizard.nostr1.com")
                .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                .toSet()

        /**
         * Relays NIP-66 monitors reported on in the last day (kind 30166, one per relay in `d`).
         * Checked 2026-10-20: each of [MONITOR_RELAYS] served ~400 relays from 11-13 monitors.
         */
        suspend fun discoverRelays(client: INostrClient): Set<NormalizedRelayUrl> {
            val discovered = mutableSetOf<NormalizedRelayUrl>()
            val reports =
                Filter(
                    kinds = listOf(RelayDiscoveryEvent.KIND),
                    since = TimeUtils.oneDayAgo(),
                    limit = 2000,
                )
            withTimeoutOrNull(DISCOVERY_DEADLINE_MS) {
                client.fetchAllWithHooks(MONITOR_RELAYS.associateWith { listOf(reports) }, idleTimeoutMs = IDLE_MS) { _, event ->
                    event.tags
                        .firstOrNull { it.size > 1 && it[0] == "d" }
                        ?.get(1)
                        ?.let { RelayUrlNormalizer.normalizeOrNull(it) }
                        ?.let { discovered.add(it) }
                    true
                }
            }
            return discovered
        }

        /** Relays worth asking even when discovery is off: Amethyst's indexers, search and finder relays. */
        fun defaultSeeds(accountRelays: Set<NormalizedRelayUrl>): Set<NormalizedRelayUrl> = accountRelays + DefaultIndexerRelayList + DefaultSearchRelayList + Constants.eventFinderRelays

        fun forEvent(
            id: String,
            seeds: Set<NormalizedRelayUrl>,
            clientBuilder: () -> INostrClient,
            canDial: (NormalizedRelayUrl) -> Boolean,
        ) = RelayProbeViewModel(
            filter = Filter(ids = listOf(id)),
            seedRelays = seeds,
            clientBuilder = clientBuilder,
            canDial = canDial,
        )
    }
}
