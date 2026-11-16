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
package com.vitorpamplona.amethyst.commons.buzz

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.buzz.AgentFleetAggregator
import com.vitorpamplona.amethyst.commons.model.buzz.AgentFleetMetrics
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.buzz.reconnectPoolAfterJoin
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.quartz.buzz.amTurnMetrics.AgentTurnMetricEvent
import com.vitorpamplona.quartz.buzz.amTurnMetrics.AgentTurnMetricPayload
import com.vitorpamplona.quartz.buzz.aoObserver.ObserverFrameEvent
import com.vitorpamplona.quartz.buzz.aoObserver.tags.FrameTag
import com.vitorpamplona.quartz.buzz.apPersonas.PersonaEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Backing ViewModel for the [AgentConsoleScreen] — the workspace owner's read-only
 * dashboard over their AI-agent fleet.
 *
 * It reads two Buzz kinds the owner authored or received:
 * - **Personas** ([PersonaEvent], `kind:30175`) — plaintext, authored by the owner.
 * - **Turn metrics** ([AgentTurnMetricEvent], `kind:44200`) — NIP-44 ciphertext an
 *   agent published to the owner (`p` tag = owner). Either party can decrypt; here the
 *   owner's [Account.signer] does. Decryption is cached by event id so a refresh that
 *   re-reads the cache never re-runs NIP-44 on an event it already opened.
 *
 * [refresh] fetches both kinds from the Buzz-dialect relays plus the owner's outbox set,
 * lets [LocalCache] consume them, then re-derives the aggregate via the pure
 * [AgentFleetAggregator] (which owns the cost/token semantics). The ViewModel is keyed by
 * the owner pubkey in the screen, so one instance survives navigation in and out.
 */
class AgentConsoleViewModel : ViewModel() {
    @Volatile private var account: Account? = null

    /** The community (relay) this console is scoped to — agents/telemetry live on community relays. */
    private var scopeRelay: NormalizedRelayUrl? = null

    /** event id -> decrypted payload (null = decryption failed; don't retry). */
    private val decryptCache = HashMap<HexKey, AgentTurnMetricPayload?>()
    private val refreshMutex = Mutex()

    private val _metrics = MutableStateFlow(AgentFleetMetrics.EMPTY)
    val metrics: StateFlow<AgentFleetMetrics> = _metrics.asStateFlow()

    private val _personas = MutableStateFlow<List<PersonaCard>>(emptyList())
    val personas: StateFlow<List<PersonaCard>> = _personas.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _observerFrames = MutableStateFlow<List<ObserverRow>>(emptyList())
    val observerFrames: StateFlow<List<ObserverRow>> = _observerFrames.asStateFlow()

    private var observerJob: Job? = null
    private var watchJob: Job? = null

    /** Dedups ephemeral frames across relays and re-emissions (accessed off multiple readers). */
    private val observerSeen = ConcurrentSet<HexKey>()

    /** Binds to [account] scoped to the community [relayUrl] — the console shows that community's fleet. */
    fun bind(
        account: Account,
        relayUrl: String,
    ) {
        if (this.account != null) return
        val relay = RelayUrlNormalizer.normalizeOrNull(relayUrl)
        this.scopeRelay = relay
        this.account = account
        relay?.let {
            val newlyJoined = account.buzzWorkspaces.join(it)
            viewModelScope.launch { account.relayAuthLedger.setDecision(it.url, RelayAuthDecision.ALLOW) }
            if (newlyJoined) reconnectPoolAfterJoin(account.client)
        }
        refresh()
    }

    fun refresh() {
        val account = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // fetchFromRelays only primes LocalCache (no shared VM state); the serialized
                // reloadFromCache below takes the mutex itself, so a concurrent watch reload
                // can't race it on decryptCache / the derived StateFlows.
                fetchFromRelays(account)
                reloadFromCache(account)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * One-shot paged fetch of the owner's personas (authored by owner) and turn metrics
     * (addressed to the owner via the `p` tag) from every Buzz-dialect relay and the
     * owner's own outbox relays. Events land in [LocalCache] via the normal consume path;
     * this only primes the cache before [reloadFromCache] reads it back.
     */
    private suspend fun fetchFromRelays(account: Account) {
        val myPubkey = account.userProfile().pubkeyHex
        val relays = scopeRelay?.let { setOf(it) } ?: (BuzzRelayDialect.flow.value + account.outboxRelays.flow.value)
        if (relays.isEmpty()) return

        val filters =
            listOf(
                Filter(kinds = listOf(AgentTurnMetricEvent.KIND), tags = mapOf("p" to listOf(myPubkey))),
                Filter(kinds = listOf(PersonaEvent.KIND), authors = listOf(myPubkey)),
            )

        // The turn-metric read is `#p`-gated, so the Buzz relay requires NIP-42 auth — warm-auth
        // (pendingOnAuthRequired) so it authenticates on the `auth-required` CLOSED and retries.
        account.client.taggedAs(SubPurpose.RELAY_GROUPS).fetchAllWithHooks(
            filters = relays.associateWith { filters },
            idleTimeoutMs = 8_000,
            pendingOnAuthRequired = true,
        ) { _, _ -> false }
    }

    // Serialized: refresh() and every per-relay watch collector funnel through here, so the shared
    // decryptCache (a plain HashMap) and the derived StateFlows are only ever mutated single-threaded.
    private suspend fun reloadFromCache(account: Account) =
        refreshMutex.withLock {
            val myPubkey = account.userProfile().pubkeyHex
            val signer = account.signer

            _personas.value =
                LocalCache.addressables
                    .filter(PersonaEvent.KIND, myPubkey) { _, note -> note.event is PersonaEvent }
                    .mapNotNull { note ->
                        val event = note.event as? PersonaEvent ?: return@mapNotNull null
                        val content = event.personaOrNull()
                        PersonaCard(
                            slug = event.slug(),
                            displayName = content?.displayName ?: event.slug(),
                            model = content?.model,
                            runtime = content?.runtime,
                            provider = content?.provider,
                            systemPrompt = content?.systemPrompt,
                        )
                    }.sortedBy { it.displayName.lowercase() }

            val metricNotes =
                LocalCache.filter(
                    Filter(kinds = listOf(AgentTurnMetricEvent.KIND), tags = mapOf("p" to listOf(myPubkey))),
                )

            val decrypted =
                metricNotes.mapNotNull { note ->
                    val event = note.event as? AgentTurnMetricEvent ?: return@mapNotNull null
                    val payload =
                        if (decryptCache.containsKey(event.id)) {
                            decryptCache[event.id]
                        } else {
                            event.decryptOrNull(signer).also { decryptCache[event.id] = it }
                        } ?: return@mapNotNull null
                    val agent = event.agentPubKey() ?: event.pubKey
                    agent to payload
                }

            _metrics.value = AgentFleetAggregator.aggregate(decrypted)
        }

    /**
     * Opens a live subscription to the owner's ephemeral observer telemetry frames
     * ([ObserverFrameEvent], `kind:24200`, `p` = owner) across every Buzz-dialect relay,
     * decrypts the `frame:telemetry` bodies, and pushes them newest-first into
     * [observerFrames] (bounded to [MAX_OBSERVER_ROWS]). Idempotent; call [stopObserving]
     * to tear the subscription down. Observer frames are never stored by relays or
     * [LocalCache], so this live REQ is the only way to see them.
     */
    fun startObserving() {
        val account = account ?: return
        if (observerJob != null) return

        val relays = scopeRelay?.let { setOf(it) } ?: BuzzRelayDialect.flow.value
        if (relays.isEmpty()) return

        val signer = account.signer
        val myPubkey = account.userProfile().pubkeyHex
        val filter = Filter(kinds = listOf(ObserverFrameEvent.KIND), tags = mapOf("p" to listOf(myPubkey)))

        observerJob =
            viewModelScope.launch(Dispatchers.IO) {
                relays.forEach { relay ->
                    launch {
                        account.client.taggedAs(SubPurpose.RELAY_GROUPS).subscribeAsFlow(relay, filter).collect { events ->
                            // Observer frames are ephemeral and unbounded over a long session; keep the
                            // dedup set from growing without limit. We only render the newest ~200 rows,
                            // so dropping the seen-history occasionally risks at most a stale duplicate.
                            if (observerSeen.size() > MAX_OBSERVER_ROWS * 4) observerSeen.clear()
                            val fresh =
                                events
                                    .filterIsInstance<ObserverFrameEvent>()
                                    .filter { observerSeen.add(it.id) }
                            if (fresh.isEmpty()) return@collect

                            val rows =
                                fresh.mapNotNull { frame ->
                                    if (frame.frame() != FrameTag.TELEMETRY) return@mapNotNull null
                                    val payload = frame.decryptTelemetryOrNull(signer) ?: return@mapNotNull null
                                    ObserverRow(
                                        seq = payload.seq,
                                        timestamp = payload.timestamp,
                                        kind = payload.kind,
                                        agentPubKey = frame.agentPubKey() ?: frame.pubKey,
                                        sessionId = payload.sessionId,
                                        turnId = payload.turnId,
                                    )
                                }
                            if (rows.isNotEmpty()) {
                                _observerFrames.update { existing ->
                                    (rows + existing)
                                        .sortedByDescending { it.timestamp }
                                        .take(MAX_OBSERVER_ROWS)
                                }
                            }
                        }
                    }
                }
            }
    }

    fun stopObserving() {
        observerJob?.cancel()
        observerJob = null
    }

    /**
     * Keeps the Costs + Personas tabs live while the console is open: a persistent subscription for
     * the owner's turn metrics (44200, `#p` = me) and persona definitions (30175, authored by me) on
     * this community's relay, re-deriving both tabs from [LocalCache] whenever a batch arrives.
     *
     * This is what makes the console populate right after the NIP-42 auth handshake completes: the
     * one-shot [refresh] fetch can return before the authenticated retry actually delivers, so
     * without a live watch the tabs stay empty until a manual refresh. Idempotent; [stopWatching]
     * tears it down when the screen leaves.
     */
    fun startWatching() {
        val account = account ?: return
        if (watchJob != null) return

        val relays = scopeRelay?.let { setOf(it) } ?: (BuzzRelayDialect.flow.value + account.outboxRelays.flow.value)
        if (relays.isEmpty()) return

        val myPubkey = account.userProfile().pubkeyHex
        val filters =
            listOf(
                Filter(kinds = listOf(AgentTurnMetricEvent.KIND), tags = mapOf("p" to listOf(myPubkey))),
                Filter(kinds = listOf(PersonaEvent.KIND), authors = listOf(myPubkey)),
            )

        watchJob =
            viewModelScope.launch(Dispatchers.IO) {
                relays.forEach { relay ->
                    launch {
                        account.client.taggedAs(SubPurpose.RELAY_GROUPS).subscribeAsFlow(relay, filters).collect {
                            // The client's global listener has already consumed the batch into LocalCache;
                            // re-derive both tabs from it. reloadFromCache is idempotent + decrypt-cached.
                            reloadFromCache(account)
                        }
                    }
                }
            }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    override fun onCleared() {
        stopObserving()
        stopWatching()
    }

    /** One decrypted observer telemetry frame rendered on the Observer tab. */
    @Immutable
    data class ObserverRow(
        val seq: Long,
        val timestamp: String,
        val kind: String,
        val agentPubKey: HexKey,
        val sessionId: String?,
        val turnId: String?,
    )

    /** A persona rendered on the Personas tab; a flattened projection of [PersonaEvent]. */
    @Immutable
    data class PersonaCard(
        val slug: String,
        val displayName: String,
        val model: String?,
        val runtime: String?,
        val provider: String?,
        val systemPrompt: String?,
    )

    companion object {
        /** Ring size for the ephemeral observer stream — enough to scroll recent activity. */
        const val MAX_OBSERVER_ROWS = 200
    }
}
