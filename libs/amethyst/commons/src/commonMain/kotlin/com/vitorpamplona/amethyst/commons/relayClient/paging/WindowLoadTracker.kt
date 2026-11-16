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
package com.vitorpamplona.amethyst.commons.relayClient.paging

import com.vitorpamplona.amethyst.commons.util.ConcurrentSet
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tracks when one relay-subscription "window" has finished loading, so a caller (e.g. an auto-fill /
 * pagination loop) can wait for the WHOLE response instead of declaring victory on the first EOSE.
 *
 * A subscription fans a single REQ out to several relays. The first EOSE is a misleading "done"
 * signal: a fast but near-empty relay can EOSE in milliseconds while the relay that actually holds
 * the data is still connecting, stuck in an auth handshake, or busy streaming thousands of stored
 * events. An auto-fill loop driven by the first EOSE — or by a fixed wall-clock timeout — would
 * widen the window again mid-stream, before the events were even processed, re-issuing an ever-wider
 * REQ that re-downloads the whole history over and over.
 *
 * Completion is therefore **per-relay terminal-state** based, not wall-clock based. A relay is
 * *settled* once it answers with a terminal signal — an EOSE (stored backfill done), a CLOSED (it
 * rejected the REQ, e.g. `auth-required`), or a cannot-connect (it is unreachable). The load is done
 * when **every targeted relay has settled** ([settled] ⊇ [expected]). This is the only signal that
 * survives the real world: relays connect over a wide spread (tens of seconds on mobile), and some
 * answer only with CLOSED — a quiet-time heuristic fires in the gap between two relays connecting and
 * mistakes a half-loaded window for a finished one, which is exactly how a load reports "1 event"
 * when a hundred are still on the way.
 *
 * Two backstops cover misbehaving relays. If every relay we're still waiting on has at least been
 * *heard from* (any event, EOSE, CLOSED, or cannot-connect) but one streamed events without ever
 * sending EOSE, an [idleTimeout] of quiet completes the load — the "heard from" gate is what keeps
 * this from firing in a connection gap. And an [absoluteCap] is the final ceiling on a window that
 * somehow defeats the above.
 */
class WindowLoadTracker(
    // Short label for the DMPagination logs (e.g. "giftwrap", "rooms.nip04", "convo.nip04").
    private val name: String = "dm",
    private val idleTimeout: Duration = 3.seconds,
    private val absoluteCap: Duration = 5.minutes,
) {
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // Relays the current REQ was sent to. Volatile: written on IO (updateFilter), read on the
    // listener threads and the watchdog.
    @Volatile
    private var expected: Set<NormalizedRelayUrl> = emptySet()

    // Relays that have produced any signal at all (event / EOSE / CLOSED / cannot-connect). The idle
    // backstop only arms once this covers [expected], so a still-connecting relay can't be skipped.
    private val heardFrom = ConcurrentSet<NormalizedRelayUrl>()

    // Relays that reached a terminal signal (EOSE / CLOSED / cannot-connect). When this covers
    // [expected] the stored backfill is complete on every relay and the load is done.
    private val settled = ConcurrentSet<NormalizedRelayUrl>()

    // Guards the window state below, as the JVM monitor behind @Synchronized did. Reentrant: tick()
    // and setExpectedRelays() call finish() while holding it.
    private val lock = KmpLock()

    private var watchdog: Job? = null

    // Incremented on every (re)start so a stale watchdog that wakes right as a new load begins
    // recognizes it has been superseded and bows out instead of completing the new window.
    private var generation = 0

    // Wall-clock of the last signal for the current window; the idle backstop completes the window
    // once this stops advancing for [idleTimeout]. Volatile so the hot per-event path stays lock-free.
    @Volatile
    private var lastActivityMs = 0L

    /** Begins a fresh window load: clears the per-relay sets, raises [loading], and arms the watchdog. */
    fun startLoading(scope: CoroutineScope) {
        lock.withLock {
            val gen = ++generation
            expected = emptySet()
            heardFrom.clear()
            settled.clear()
            lastActivityMs = TimeUtils.nowMillis()
            val wasLoading = _loading.value
            _loading.value = true
            Log.d(TAG) { "[$name] load start" + if (!wasLoading) "" else " (restart)" }
            watchdog?.cancel()
            watchdog =
                scope.launch {
                    val deadline = TimeUtils.nowMillis() + absoluteCap.inWholeMilliseconds
                    while (isActive) {
                        delay(IDLE_CHECK_MS)
                        if (!tick(gen, TimeUtils.nowMillis(), deadline)) break
                    }
                }
        }
    }

    // One watchdog poll. Returns false (stop polling) when this watchdog has been superseded by a
    // newer load, the window already finished, or a completion deadline is reached. Synchronized so
    // the generation/loading checks and the completion are atomic against startLoading/finish.
    private fun tick(
        gen: Int,
        now: Long,
        deadline: Long,
    ): Boolean {
        lock.withLock {
            if (gen != generation || !_loading.value) return false
            if (expected.isNotEmpty()) {
                // Once every relay has reached a terminal signal, nothing more is coming for this round.
                if (expected.all { it in settled }) {
                    finish("settled")
                    return false
                }
                // Idle backstop: every relay we're still waiting on has at least streamed something (so this
                // isn't a connection gap) and the stream has gone quiet. Settled relays don't count.
                val stillWaiting = expected.filterNot { settled.contains(it) }
                if (stillWaiting.all { heardFrom.contains(it) } && now - lastActivityMs >= idleTimeout.inWholeMilliseconds) {
                    finish("idle")
                    return false
                }
            }
            if (now >= deadline) {
                finish("cap")
                return false
            }
            return true
        }
    }

    /** Records which relays the current REQ was sent to. Completes immediately if there are none. */
    fun setExpectedRelays(relays: Set<NormalizedRelayUrl>) {
        lock.withLock {
            expected = relays
            if (relays.isEmpty()) {
                finish("no relays")
            } else if (relays.all { it in settled }) {
                finish("all relays")
            }
        }
    }

    /** A non-terminal sign of life from [relay] (a stored or live event). Keeps the idle timer alive. */
    fun onRelayEvent(relay: NormalizedRelayUrl) {
        heardFrom.add(relay)
        lastActivityMs = TimeUtils.nowMillis()
    }

    /**
     * A terminal signal from [relay] — EOSE, CLOSED, or cannot-connect. Once every expected relay has
     * settled the stored backfill is complete and the load finishes.
     */
    fun onRelaySettled(relay: NormalizedRelayUrl) {
        lock.withLock {
            lastActivityMs = TimeUtils.nowMillis()
            heardFrom.add(relay)
            settled.add(relay)
            if (expected.isNotEmpty() && expected.all { it in settled }) finish("all relays")
        }
    }

    // Idempotent: only the first call after a load actually completes (and logs); later calls no-op.
    private fun finish(reason: String) {
        lock.withLock {
            if (!_loading.value) return
            watchdog?.cancel()
            watchdog = null
            Log.d(TAG) { "[$name] load done: $reason" }
            _loading.value = false
        }
    }

    companion object {
        private const val TAG = "DMPagination"
        private const val IDLE_CHECK_MS = 500L
    }
}

/**
 * Builds the standard [SubscriptionListener] that feeds this tracker. Every event (stored backfill
 * included) is a non-terminal sign of life from its relay; an EOSE, CLOSED, or cannot-connect settles
 * that relay. [forward] carries the EOSE / live-event signal so the owning EOSE manager can record the
 * relay's timestamp (its usual `newEose`).
 */
fun WindowLoadTracker.trackingListener(forward: (NormalizedRelayUrl, List<Filter>?) -> Unit): SubscriptionListener =
    object : SubscriptionListener {
        override fun onEose(
            relay: NormalizedRelayUrl,
            forFilters: List<Filter>?,
        ) {
            onRelaySettled(relay)
            forward(relay, forFilters)
        }

        override suspend fun onEvent(
            event: Event,
            isLive: Boolean,
            relay: NormalizedRelayUrl,
            forFilters: List<Filter>?,
        ) {
            onRelayEvent(relay)
            if (isLive) {
                forward(relay, forFilters)
            }
        }

        override fun onClosed(
            message: String,
            relay: NormalizedRelayUrl,
            forFilters: List<Filter>?,
        ) {
            onRelaySettled(relay)
        }

        override fun onCannotConnect(
            relay: NormalizedRelayUrl,
            message: String,
            forFilters: List<Filter>?,
        ) {
            onRelaySettled(relay)
        }
    }
