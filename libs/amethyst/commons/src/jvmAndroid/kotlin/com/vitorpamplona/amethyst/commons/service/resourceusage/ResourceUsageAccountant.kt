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
package com.vitorpamplona.amethyst.commons.service.resourceusage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory hot path for usage counters. [add] is called from network threads
 * per relay frame / HTTP response chunk, so it must be allocation-light and
 * lock-free: a ConcurrentHashMap of AtomicLongs, drained into
 * [ResourceUsageStore] by a debounced flush (at most one write per
 * [flushDebounceMs] while traffic flows; nothing scheduled when idle).
 *
 * AtomicLong (not LongAdder) because draining must be loss-free: getAndSet(0)
 * hands off the accumulated value atomically, whereas remove+sum on a
 * LongAdder can strand a racing increment on an orphaned cell. Entries stay
 * in the map after a drain — the key space is small and fixed (dims x areas),
 * so this costs a few hundred boxed zeros at most. The churn counters roughly
 * tripled it, but every one of them is still compile-time bounded: no counter
 * is keyed on a relay url, a host, or any other runtime string.
 *
 * Counters added from inside a pre-flush hook (the CPU sampler, the segment
 * integrators closing an open segment) never re-arm the debounce: they are
 * drained by the very flush that invoked the hook, and letting them schedule
 * would turn every flush into a perpetual 30s wake-and-write loop — the
 * battery ledger becoming its own battery drain.
 *
 * Day attribution: deltas are drained into the bucket of the day they are
 * drained on. Counts within one debounce window of midnight may land on the
 * neighboring day — irrelevant at the ledger's day-level granularity.
 */
class ResourceUsageAccountant(
    private val store: ResourceUsageStore,
    private val scope: CoroutineScope,
    private val epochDay: () -> Long = { System.currentTimeMillis() / DAY_MS },
    private val flushDebounceMs: Long = 30_000L,
) {
    private val live = ConcurrentHashMap<String, AtomicLong>()
    private val flushScheduled = AtomicBoolean(false)

    /** True only on the thread currently running the pre-flush hooks. */
    private val inHookRun = ThreadLocal.withInitial { false }

    /** Hooks run right before a flush drains the counters (e.g. the connection-time integrator closing its open segment). */
    private val preFlushHooks = ConcurrentHashMap.newKeySet<() -> Unit>()

    fun addPreFlushHook(hook: () -> Unit) {
        preFlushHooks.add(hook)
    }

    fun add(
        key: String,
        amount: Long,
    ) {
        if (amount <= 0) return
        // Plain get first: computeIfAbsent locks the bin head when the key is present
        // but not the head node, and the churn counters roughly tripled the key count
        // (so collisions) on a path that runs per relay frame.
        (live[key] ?: live.computeIfAbsent(key) { AtomicLong() }).addAndGet(amount)
        if (inHookRun.get() == true) return
        // Plain read before the CAS: in steady state a flush is always already armed,
        // and the churn counters made this 5-8 calls per relay frame — every one of
        // which would otherwise be a read-modify-write on the same shared cache line
        // from every relay's socket thread, only to fail.
        if (flushScheduled.get()) return
        if (flushScheduled.compareAndSet(false, true)) {
            scope.launch {
                delay(flushDebounceMs)
                flushScheduled.set(false)
                flush()
            }
        }
    }

    private fun runPreFlushHooks() {
        inHookRun.set(true)
        try {
            preFlushHooks.forEach { runCatching { it() } }
        } finally {
            inHookRun.set(false)
        }
    }

    /** Drains the in-memory counters into today's persisted bucket. */
    suspend fun flush() {
        runPreFlushHooks()
        val deltas = drain()
        if (deltas.isNotEmpty()) {
            store.mergeInto(epochDay(), deltas)
        }
    }

    /** Fire-and-forget flush for non-suspending callers (onTrimMemory). */
    fun flushAsync() {
        scope.launch { flush() }
    }

    fun today(): Long = epochDay()

    /**
     * Persisted buckets merged with the not-yet-flushed live counters
     * (attributed to today). This is what the UI, the report assembler,
     * and the alert evaluator read. Reading never schedules a flush.
     */
    suspend fun allDaysIncludingLive(): Map<Long, Map<String, Long>> {
        runPreFlushHooks()
        val persisted = store.allDays()
        val liveSnapshot = live.mapValues { it.value.get() }.filterValues { it > 0 }
        if (liveSnapshot.isEmpty()) return persisted
        val today = epochDay()
        val merged = persisted.toMutableMap()
        val todayBucket = merged[today].orEmpty().toMutableMap()
        liveSnapshot.forEach { (key, value) -> todayBucket[key] = (todayBucket[key] ?: 0L) + value }
        merged[today] = todayBucket
        return merged
    }

    private fun drain(): Map<String, Long> {
        if (live.isEmpty()) return emptyMap()
        val deltas = mutableMapOf<String, Long>()
        for ((key, counter) in live) {
            val value = counter.getAndSet(0L)
            if (value > 0) deltas[key] = value
        }
        return deltas
    }

    companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
