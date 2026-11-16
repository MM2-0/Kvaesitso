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
package com.vitorpamplona.amethyst.commons.relays.health

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/**
 * Per-account, durable record of relay liveness used to drive the "unhealthy relay" review UI.
 *
 * Records are fed in from the quartz relay listener (via [recordIncoming] / [recordConnect])
 * and the user's list-membership StateFlow (via [setListMembership]). The classifier runs:
 *  - whenever inputs change, OR
 *  - once every [TICK_SECONDS] so snoozes expire and lastSeenAny stays fresh.
 *
 * Persistence writes are debounced [PERSIST_DEBOUNCE_MS]ms.
 *
 * Lifecycle: the host (Android Account wiring / Desktop App() scope) is responsible
 * for calling [close] when switching accounts so the internal scope cancels.
 */
class RelayHealthStore(
    private val persistence: RelayHealthPersistence,
    private val torEnabledProvider: () -> Boolean = { false },
    parentScope: CoroutineScope? = null,
    // Dispatcher used for both classification and persistence I/O. `prefs.flush()` is
    // blocking, so this must not be `Dispatchers.Default`: a blocked worker there takes
    // one of only nCPU threads away from every other CPU-bound coroutine in the app.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // Optional latency tracker (jvmAndroidMain in the current build — Desktop wires it,
    // Android picks up later). When null, [latencySnapshots] stays empty and [slowRelays]
    // never flags anything.
    private val latencyTracker: RelayLatencyProvider? = null,
    // NIP-11 info supplier, used by the slow-relay classifier to skip auth/payment-required
    // relays. Read on every reclassify tick, so the supplier can return a live snapshot.
    private val nip11Provider: () -> ImmutableMap<NormalizedRelayUrl, Nip11RelayInformation?> = { persistentMapOf() },
    // Per-relay auth-completed lookup. Used by the classifier alongside [nip11Provider] —
    // a NIP-11 auth-required relay only participates in the cohort once auth is complete.
    private val authProvider: (NormalizedRelayUrl) -> Boolean = { true },
    // Per-relay NIP-11 lookup, when [nip11Provider] has no full map to give.
    private val nip11ForRelay: (NormalizedRelayUrl) -> Nip11RelayInformation? = { null },
    // Relays reached through Tor: their times measure the circuit, not the relay, so they stay out
    // of the slow comparison. Per relay, unlike [torEnabledProvider], because with Tor on only some
    // relays (onion, DM, money) go through it.
    private val torRoutedProvider: (NormalizedRelayUrl) -> Boolean = { false },
) {
    companion object {
        const val PERSIST_DEBOUNCE_MS: Long = 5_000L
        const val TICK_SECONDS: Long = 60L
    }

    private val scope: CoroutineScope =
        parentScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)

    private val ownsScope = parentScope == null

    private val state =
        MutableStateFlow(
            persistence.load().let { loaded ->
                // First-time observation: stamp firstScanAt now so newcomer-grace starts ticking.
                if (loaded.firstScanAt == 0L) {
                    loaded.copy(firstScanAt = TimeUtils.now())
                } else {
                    loaded
                }
            },
        )

    private val listMembership = MutableStateFlow<Map<NormalizedRelayUrl, Set<RelayListKind>>>(emptyMap())

    private val _unhealthy = MutableStateFlow<PersistentList<UnhealthyRelay>>(persistentListOf())
    val unhealthy: StateFlow<PersistentList<UnhealthyRelay>> = _unhealthy.asStateFlow()

    private val _latencySnapshots =
        MutableStateFlow<ImmutableMap<NormalizedRelayUrl, RelayLatencySnapshot>>(persistentMapOf())

    /** Per-relay rolling-window latency snapshot, updated on every 60 s tick. Empty when no tracker. */
    val latencySnapshots: StateFlow<ImmutableMap<NormalizedRelayUrl, RelayLatencySnapshot>> =
        _latencySnapshots.asStateFlow()

    /**
     * Relays classified as "slow" — derived from [latencySnapshots] + the NIP-11 / auth providers.
     * Re-runs whenever [latencySnapshots] emits (every 60 s tick when a tracker is wired).
     *
     * `SharingStarted.Eagerly` so the value is fresh whenever the UI reads `.value` without
     * an active collector — matches how callers consume [unhealthy].
     */
    val slowRelays: StateFlow<ImmutableMap<NormalizedRelayUrl, SlowReason>> =
        _latencySnapshots
            .map { snaps ->
                if (latencyTracker == null || snaps.isEmpty()) {
                    persistentMapOf<NormalizedRelayUrl, SlowReason>()
                } else {
                    val compared = snaps.filterKeys { !torRoutedProvider(it) }.toImmutableMap()
                    classifySlowRelays(
                        snapshots = compared,
                        nip11 = nip11Provider().ifEmpty { compared.keys.associateWith { nip11ForRelay(it) }.toImmutableMap() },
                        torEnabled = torEnabledProvider(),
                        authStatus = authProvider,
                    )
                }
            }.stateIn(scope, SharingStarted.Eagerly, persistentMapOf())

    @Volatile private var persistJob: Job? = null

    @Volatile private var tickJob: Job? = null

    @Volatile private var closed = false

    init {
        // Restore persisted latency samples into the tracker (if both wired).
        if (latencyTracker != null) {
            val loadedLatency = state.value.latencySamples
            if (loadedLatency.isNotEmpty()) {
                latencyTracker.restoreSamples(loadedLatency)
                _latencySnapshots.value = latencyTracker.snapshot()
            }
        }

        // Persist the firstScanAt seed if we just stamped it.
        schedulePersist()

        tickJob =
            scope.launch {
                while (true) {
                    reclassify()
                    delay(TICK_SECONDS * 1_000)
                }
            }
    }

    /** Called from RelayConnectionListener.onIncomingMessage. Non-suspending. */
    fun recordIncoming(
        url: NormalizedRelayUrl,
        atSeconds: Long = TimeUtils.now(),
    ) {
        var changed = false
        state.update { s ->
            val now = atSeconds
            val rec = s.records[url] ?: RelayHealthRecord()
            // Bump only on real progress to avoid noise.
            if (rec.lastIncomingAt >= now) return@update s
            changed = true
            val newRec = rec.copy(lastIncomingAt = now)
            s.copy(
                records = s.records + (url to newRec),
                lastSeenAny = maxOf(s.lastSeenAny, now),
            )
        }
        if (changed) {
            reclassifyAsync()
            schedulePersist()
        }
    }

    /** Called from RelayConnectionListener.onConnected. Non-suspending. */
    fun recordConnect(
        url: NormalizedRelayUrl,
        atSeconds: Long = TimeUtils.now(),
    ) {
        var changed = false
        state.update { s ->
            val now = atSeconds
            val rec = s.records[url] ?: RelayHealthRecord()
            if (rec.lastConnectAt >= now) return@update s
            changed = true
            val newRec = rec.copy(lastConnectAt = now)
            s.copy(
                records = s.records + (url to newRec),
                lastSeenAny = maxOf(s.lastSeenAny, now),
            )
        }
        if (changed) {
            reclassifyAsync()
            schedulePersist()
        }
    }

    /** Called by the UI when the user's monitored relay lists change. */
    fun setListMembership(membership: Map<NormalizedRelayUrl, Set<RelayListKind>>) {
        listMembership.value = membership
        reclassifyAsync()
    }

    /** Per-relay snooze (default 7d). */
    fun snooze(
        url: NormalizedRelayUrl,
        untilSeconds: Long = TimeUtils.now() + RELAY_HEALTH_THRESHOLD_SECONDS,
    ) {
        state.update { s ->
            val rec = s.records[url] ?: RelayHealthRecord()
            s.copy(records = s.records + (url to rec.copy(snoozedUntil = untilSeconds)))
        }
        reclassifyAsync()
        schedulePersist()
    }

    /** Snooze every relay currently flagged. */
    fun snoozeAllCurrent(untilSeconds: Long = TimeUtils.now() + RELAY_HEALTH_THRESHOLD_SECONDS) {
        val urls = _unhealthy.value.map { it.url }
        if (urls.isEmpty()) return
        state.update { s ->
            val updates =
                urls.associateWith { url ->
                    (s.records[url] ?: RelayHealthRecord()).copy(snoozedUntil = untilSeconds)
                }
            s.copy(records = s.records + updates)
        }
        reclassifyAsync()
        schedulePersist()
    }

    /** Run once at app start. Equivalent to a `recordIncoming`-driven reclassification. */
    fun scanNow() {
        reclassifyAsync()
    }

    @Volatile private var reclassifyJob: Job? = null

    // Called for every message a relay sends, so one pending run covers them all; the latency sweep
    // and snapshot wait for the minute tick, the only thing that publishes them.
    private fun reclassifyAsync() {
        if (reclassifyJob?.isActive == true) return
        reclassifyJob = scope.launch { reclassify(sweepLatency = false) }
    }

    private suspend fun reclassify(sweepLatency: Boolean = true) {
        val s = state.value
        val membership = listMembership.value
        val torEnabled = torEnabledProvider()
        val now = TimeUtils.now()

        // Allow lastSeenAny to "look offline" only if there are records at all.
        val effectiveLastSeenAny = if (s.lastSeenAny == 0L) now else s.lastSeenAny

        val flagged =
            withContext(ioDispatcher) {
                // Sweep + snapshot the latency tracker inside the same tick (one timer, not two).
                if (latencyTracker != null && sweepLatency) {
                    latencyTracker.sweep(TimeUtils.nowMillis())
                    val snap = latencyTracker.snapshot()
                    // Only emit if anything changed structurally — keeps strong-skipping happy
                    // on the dashboard rows when the per-tick snapshot is value-equal to the
                    // previous one (e.g. quiet period, no new samples).
                    if (snap != _latencySnapshots.value) {
                        _latencySnapshots.value = snap
                    }
                }
                classifyRelayHealth(
                    records = s.records,
                    listMembership = membership,
                    firstScanAt = s.firstScanAt,
                    lastSeenAny = effectiveLastSeenAny,
                    torEnabled = torEnabled,
                    now = now,
                )
            }
        _unhealthy.value = flagged
    }

    // At most one write per PERSIST_DEBOUNCE_MS: a pending write is not pushed back by new traffic,
    // which on a busy client would have postponed it until a quiet gap that might never come.
    private fun schedulePersist() {
        if (closed) return
        if (persistJob?.isActive == true) return
        persistJob =
            scope.launch {
                delay(PERSIST_DEBOUNCE_MS)
                withContext(ioDispatcher) {
                    runCatching { persistence.save(snapshotForPersist()) }
                }
            }
    }

    /**
     * Bundles the timestamp/snooze state with the tracker's current sample arrays. Read at
     * save time so we don't have to mirror per-event sample updates into `state` (which
     * would break StateFlow equality with IntArrays).
     */
    private fun snapshotForPersist(): RelayHealthSnapshot {
        val timestamps = state.value
        val latency = latencyTracker?.samplesForPersistence().orEmpty()
        return if (latency.isEmpty()) timestamps else timestamps.copy(latencySamples = latency)
    }

    /**
     * Tear down internal jobs and fire the final persist off-thread. Safe to call from
     * the composition / Main thread: the blocking I/O is dispatched to [ioDispatcher]
     * on a detached, self-cancelling scope so the last debounce window isn't lost when
     * the parent composition scope is about to cancel.
     */
    fun close() {
        if (closed) return
        closed = true
        persistJob?.cancel()
        tickJob?.cancel()
        val finalSnapshot = snapshotForPersist()
        val flushScope = CoroutineScope(SupervisorJob() + ioDispatcher)
        flushScope.launch {
            try {
                runCatching { persistence.save(finalSnapshot) }
            } finally {
                flushScope.cancel()
            }
        }
        if (ownsScope) scope.cancel()
    }
}
