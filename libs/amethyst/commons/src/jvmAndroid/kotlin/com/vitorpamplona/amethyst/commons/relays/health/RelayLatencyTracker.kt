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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-relay rolling-window latency tracker.
 *
 * **Pairing rules** (matches the plan's table):
 *
 * | Trigger | Action |
 * |---------|--------|
 * | `onSent(EventCmd, success=true)` | pending.eventId[relay][id] = now |
 * | `onSent(ReqCmd,   success=true)` | pending.subId[relay][subId] = now; clear firstResultSeen |
 * | `onSent(CloseCmd, success=true)` | drop pending.subId[relay][subId] (no sample) |
 * | `onSent(_, success=false)` | ignore (websocket buffer was full / socket closing) |
 * | `OkMessage`     | match by eventId, push delta into OK_ACK |
 * | `EventMessage`  | if firstResultSeen[relay][subId] is clear, push delta into FIRST_RESULT |
 * | `EoseMessage`   | match by subId, push delta into EOSE |
 * | `ClosedMessage` | drop pending.subId (no sample — fast negative response) |
 * | `onConnected`   | push pingMillis into PING ring |
 * | `sweep(now)`    | TTL-expire pending entries (60 s OK / 300 s REQ) |
 * | `onDisconnected`| drop ALL pending for that relay (no TTL samples recorded) |
 *
 * **AUTH retries**: the second `onSent` for the same (relay, eventId) overwrites the timestamp,
 * so OK_ACK samples reflect the retry leg (not the AUTH round-trip). Intentional — matches the
 * user's mental model of "how fast was the actual publish".
 *
 * **Threading**: All public methods are non-suspending and safe to call from any thread.
 * Pending maps are [ConcurrentHashMap]s; samples are guarded by per-buffer `synchronized`.
 *
 * **Lifecycle**: This tracker owns no [kotlinx.coroutines.CoroutineScope]. The
 * [RelayHealthStore] is responsible for calling [sweep] periodically and reading [snapshot]
 * on its existing 60-second classifier tick.
 */
class RelayLatencyTracker(
    val ringCapacity: Int = LatencyRingBuffer.DEFAULT_CAPACITY,
    val maxPendingPerRelay: Int = DEFAULT_MAX_PENDING_PER_RELAY,
    val okTtlMs: Long = DEFAULT_OK_TTL_MS,
    val reqTtlMs: Long = DEFAULT_REQ_TTL_MS,
) : RelayLatencyProvider {
    // Per-relay pending maps. `Long` is `currentTimeMillis()` at the time of the send.
    private val pendingEventId = ConcurrentHashMap<NormalizedRelayUrl, MutableMap<String, Long>>()
    private val pendingSubId = ConcurrentHashMap<NormalizedRelayUrl, MutableMap<String, Long>>()

    // Tracks which sub-ids have already produced a FIRST_RESULT sample so subsequent
    // EVENT messages for the same sub-id short-circuit cheaply (no map lookup beyond
    // the per-relay set).
    private val firstResultSeen = ConcurrentHashMap<NormalizedRelayUrl, MutableSet<String>>()

    // samples[relay][metric] -> ring buffer of latency in ms.
    private val samples =
        ConcurrentHashMap<NormalizedRelayUrl, ConcurrentHashMap<LatencyMetric, LatencyRingBuffer>>()

    // ------ Recording API ------

    /**
     * Called from `RelayConnectionListener.onSent`. Only records when [success] is true (a
     * `false` means the websocket send buffer rejected the message — it never went out).
     */
    fun recordSent(
        relay: NormalizedRelayUrl,
        cmd: Command,
        success: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (!success) return
        when (cmd) {
            is EventCmd -> putPending(pendingEventId, relay, cmd.event.id, nowMs)
            is ReqCmd -> {
                putPending(pendingSubId, relay, cmd.subId, nowMs)
                firstResultSeen[relay]?.remove(cmd.subId)
            }
            is CloseCmd -> {
                pendingSubId[relay]?.remove(cmd.subId)
                firstResultSeen[relay]?.remove(cmd.subId)
            }
            else -> Unit
        }
    }

    /** Called from `RelayConnectionListener.onIncomingMessage`. */
    fun recordIncoming(
        relay: NormalizedRelayUrl,
        msg: Message,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        when (msg) {
            is OkMessage -> {
                val sentAt = pendingEventId[relay]?.remove(msg.eventId) ?: return
                ringFor(relay, LatencyMetric.OK_ACK).push((nowMs - sentAt).toInt().coerceAtLeast(0))
            }
            is EoseMessage -> {
                val sentAt = pendingSubId[relay]?.remove(msg.subId) ?: return
                ringFor(relay, LatencyMetric.EOSE).push((nowMs - sentAt).toInt().coerceAtLeast(0))
                firstResultSeen[relay]?.remove(msg.subId)
            }
            is EventMessage -> {
                val seenSet = firstResultSeen.computeIfAbsent(relay) { newConcurrentSet() }
                if (msg.subId in seenSet) return
                val sentAt = pendingSubId[relay]?.get(msg.subId) ?: return
                seenSet.add(msg.subId)
                ringFor(relay, LatencyMetric.FIRST_RESULT)
                    .push((nowMs - sentAt).toInt().coerceAtLeast(0))
            }
            is ClosedMessage -> {
                pendingSubId[relay]?.remove(msg.subId)
                firstResultSeen[relay]?.remove(msg.subId)
            }
            else -> Unit
        }
    }

    /** Called from `RelayConnectionListener.onConnected`. */
    fun recordPing(
        relay: NormalizedRelayUrl,
        pingMs: Int,
    ) {
        if (pingMs < 0) return
        ringFor(relay, LatencyMetric.PING).push(pingMs)
    }

    /** Called from `RelayConnectionListener.onDisconnected`. Drops all pending entries. */
    fun recordDisconnect(relay: NormalizedRelayUrl) {
        pendingEventId[relay]?.clear()
        pendingSubId[relay]?.clear()
        firstResultSeen[relay]?.clear()
    }

    // ------ TTL sweep ------

    /**
     * Expires pending entries older than the configured TTLs and records the TTL value as the
     * sample (per the brainstorm: "punish silent relays"). Idempotent and cheap.
     *
     * The per-relay pending maps are `Collections.synchronizedMap(LinkedHashMap)` — their
     * individual reads and writes are thread-safe, but iteration is NOT: per
     * `Collections.synchronizedMap` javadoc, the caller MUST hold the returned map's
     * monitor while iterating. Directly iterating triggers a
     * `ConcurrentModificationException` when a producer thread (network dispatcher)
     * mutates the map while the sweep is walking it — reliably reproduced on macOS
     * during any relay-add on Amethyst Desktop as of 2026-07-06.
     *
     * Fix: iterate under `synchronized(pending)` blocks so the network dispatcher
     * waits until sweep releases the monitor. The sweep is O(pending), typically
     * ~single-digit entries per relay, so the hold time is negligible.
     */
    override fun sweep(nowMs: Long) {
        // Per-relay pending maps are `Collections.synchronizedMap(LinkedHashMap)` — the
        // Javadoc requires holding the wrapper's monitor while iterating, or a concurrent
        // put/remove from putPending/recordSent/recordIncoming/recordDisconnect throws CME.
        for ((relay, pending) in pendingEventId) {
            synchronized(pending) {
                val it = pending.entries.iterator()
                while (it.hasNext()) {
                    val (_, sentAt) = it.next()
                    if (nowMs - sentAt >= okTtlMs) {
                        ringFor(relay, LatencyMetric.OK_ACK).push(okTtlMs.toInt())
                        it.remove()
                    }
                }
            }
        }
        for ((relay, pending) in pendingSubId) {
            synchronized(pending) {
                val it = pending.entries.iterator()
                while (it.hasNext()) {
                    val entry = it.next()
                    val sentAt = entry.value
                    if (nowMs - sentAt >= reqTtlMs) {
                        ringFor(relay, LatencyMetric.EOSE).push(reqTtlMs.toInt())
                        // Record a FIRST_RESULT TTL sample only if the relay never emitted any
                        // matching event for this sub-id.
                        val seenSet = firstResultSeen[relay]
                        if (seenSet == null || entry.key !in seenSet) {
                            ringFor(relay, LatencyMetric.FIRST_RESULT).push(reqTtlMs.toInt())
                        }
                        seenSet?.remove(entry.key)
                        it.remove()
                    }
                }
            }
        }
    }

    // ------ Snapshot ------

    /** Immutable snapshot of all tracked relays' current rolling-window medians. */
    override fun snapshot(): ImmutableMap<NormalizedRelayUrl, RelayLatencySnapshot> {
        if (samples.isEmpty()) return persistentMapOf()
        val out = HashMap<NormalizedRelayUrl, RelayLatencySnapshot>(samples.size)
        for ((relay, perMetric) in samples) {
            val builder = HashMap<LatencyMetric, MetricSample>(perMetric.size)
            for ((metric, ring) in perMetric) {
                val median = ring.snapshotMedian() ?: continue
                builder[metric] = MetricSample(p50Ms = median, count = ring.size)
            }
            if (builder.isNotEmpty()) {
                out[relay] = RelayLatencySnapshot(builder.toPersistentMap())
            }
        }
        return out.toPersistentMap()
    }

    /**
     * Raw per-relay per-metric sample arrays (chronological order). Used by the persistence
     * layer to encode the rings into the packed `lat_<url>` Preferences key.
     */
    override fun samplesForPersistence(): Map<NormalizedRelayUrl, Map<LatencyMetric, IntArray>> {
        if (samples.isEmpty()) return emptyMap()
        val out = HashMap<NormalizedRelayUrl, Map<LatencyMetric, IntArray>>(samples.size)
        for ((relay, perMetric) in samples) {
            val inner = HashMap<LatencyMetric, IntArray>(perMetric.size)
            for ((metric, ring) in perMetric) {
                val arr = ring.snapshotSamples()
                if (arr.isNotEmpty()) inner[metric] = arr
            }
            if (inner.isNotEmpty()) out[relay] = inner
        }
        return out
    }

    /**
     * Restore samples (typically from the persistence layer at app start). Overwrites any
     * existing buffers for the listed `(relay, metric)` pairs; other relays/metrics are
     * untouched.
     */
    override fun restoreSamples(saved: Map<NormalizedRelayUrl, Map<LatencyMetric, IntArray>>) {
        for ((relay, perMetric) in saved) {
            for ((metric, arr) in perMetric) {
                if (arr.isEmpty()) continue
                ringFor(relay, metric).restore(arr)
            }
        }
    }

    // ------ Internals ------

    private fun ringFor(
        relay: NormalizedRelayUrl,
        metric: LatencyMetric,
    ): LatencyRingBuffer =
        samples
            .computeIfAbsent(relay) { ConcurrentHashMap() }
            .computeIfAbsent(metric) { LatencyRingBuffer(ringCapacity) }

    private fun putPending(
        store: ConcurrentHashMap<NormalizedRelayUrl, MutableMap<String, Long>>,
        relay: NormalizedRelayUrl,
        key: String,
        nowMs: Long,
    ) {
        val perRelay = store.computeIfAbsent(relay) { synchronizedLinkedMap() }
        synchronized(perRelay) {
            // Cap protects against adversarial relays that open thousands of sub-ids — TTL
            // sweep is the primary expiry mechanism; this is a safety net.
            if (perRelay.size >= maxPendingPerRelay && key !in perRelay) {
                val first = perRelay.entries.iterator()
                if (first.hasNext()) {
                    first.next()
                    first.remove()
                }
            }
            perRelay[key] = nowMs
        }
    }

    private fun synchronizedLinkedMap(): MutableMap<String, Long> = java.util.Collections.synchronizedMap(java.util.LinkedHashMap<String, Long>(16, 0.75f, false))

    private fun newConcurrentSet(): MutableSet<String> = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    companion object {
        const val DEFAULT_MAX_PENDING_PER_RELAY: Int = 256
        const val DEFAULT_OK_TTL_MS: Long = 60_000L
        const val DEFAULT_REQ_TTL_MS: Long = 300_000L
    }
}
