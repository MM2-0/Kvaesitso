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

/**
 * Timer-free duration integrator: the elapsed time of the current state is
 * exact between transitions, so a segment is accounted only when the state
 * changes — plus on the accountant's pre-flush hook, so multi-hour stable
 * segments (a call, a backgrounded night with relays connected) still land in
 * the right day bucket without any periodic timer.
 *
 * Durations are measured with [elapsedRealtimeMillis] — the monotonic
 * clock — never the wall clock: an NTP/timezone correction mid-segment must
 * not fabricate or delete accounted time. (Wall time is only ever used for
 * choosing the epoch-day bucket, which happens in the accountant.)
 *
 * A `null` state means "nothing to account" (idle); subclasses decide what a
 * non-null state costs per elapsed millisecond.
 */
abstract class TimeSegmentIntegrator<S : Any>(
    protected val accountant: ResourceUsageAccountant,
    private val nowMs: () -> Long = { elapsedRealtimeMillis() },
) {
    private val lock = Any()
    private var current: S? = null
    private var segmentStartMs = 0L

    /** Registers [closeOpenSegment] so flushes and reads see up-to-date totals. */
    fun registerFlushHook() {
        accountant.addPreFlushHook(::closeOpenSegment)
    }

    /** Accounts the running segment up to now without changing state. */
    fun closeOpenSegment() {
        synchronized(lock) {
            val state = current ?: return
            val now = nowMs()
            account(state, now - segmentStartMs)
            segmentStartMs = now
        }
    }

    /**
     * Accounts the previous segment and starts a new one. Returns the previous
     * state so callers can detect activations (null -> non-null).
     */
    fun transitionTo(next: S?): S? =
        synchronized(lock) {
            val now = nowMs()
            val prev = current
            prev?.let { account(it, now - segmentStartMs) }
            current = next
            segmentStartMs = now
            prev
        }

    protected abstract fun account(
        state: S,
        elapsedMs: Long,
    )
}

/**
 * The simplest integrator: total time a boolean condition is active (Tor
 * running, a call in progress, GPS listening), written to [msKey] — plus an
 * optional [startsKey] counting activations, since starts are often the
 * expensive part (a Tor bootstrap, a service cold start).
 */
class SessionTimeIntegrator(
    accountant: ResourceUsageAccountant,
    private val msKey: String,
    private val startsKey: String? = null,
    nowMs: () -> Long = { elapsedRealtimeMillis() },
) : TimeSegmentIntegrator<Unit>(accountant, nowMs) {
    fun setActive(active: Boolean) {
        val prev = transitionTo(if (active) Unit else null)
        if (active && prev == null && startsKey != null) accountant.add(startsKey, 1)
    }

    override fun account(
        state: Unit,
        elapsedMs: Long,
    ) {
        if (elapsedMs > 0) accountant.add(msKey, elapsedMs)
    }
}
