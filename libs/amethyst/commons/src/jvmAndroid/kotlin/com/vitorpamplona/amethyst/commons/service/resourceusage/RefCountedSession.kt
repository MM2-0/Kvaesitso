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
 * Refcounts a boolean session so overlapping holders don't close each other's
 * segment. [LocationState][com.vitorpamplona.amethyst.service.location.LocationState]
 * exposes two independent location flows that can both be listening at once —
 * the "Around Me" feed plus an open geohash chat — and a bare
 * [SessionTimeIntegrator] would close the segment when either one stops.
 *
 * The count and the transition it drives are taken under one lock. An
 * [java.util.concurrent.atomic.AtomicInteger] beside an unsynchronised call is
 * not enough: two threads can leave the counter at 1 while the last
 * `setActive(false)` lands after the `setActive(true)`, latching the session
 * off with a holder still active.
 *
 * Takes the setter as a lambda rather than a [SessionTimeIntegrator] because
 * that is all it needs, and so tests need no accountant or store file.
 *
 * Reports **transitions only**, not every call — the contract the name implies,
 * and what keeps the class honest for a future caller that reacts to the
 * callback rather than integrating it. (For [SessionTimeIntegrator] specifically
 * a 1 -> 2 re-entry would have been harmless: it splits one segment into two
 * contiguous pieces that `account()` re-adds, and its starts increment is
 * already guarded on `prev == null`.)
 *
 * Releases must be paired with acquires. This class cannot tell an unpaired
 * release from a real one, so callers guarantee the pairing; see `LocationFlow`,
 * which throws rather than reaching `awaitClose` when nothing registered.
 */
class RefCountedSession(
    private val setSessionActive: (Boolean) -> Unit,
) {
    private val lock = Any()
    private var holders = 0

    fun setActive(active: Boolean) {
        synchronized(lock) {
            val wasActive = holders > 0
            holders = if (active) holders + 1 else (holders - 1).coerceAtLeast(0)
            val isActive = holders > 0
            if (isActive != wasActive) setSessionActive(isActive)
        }
    }
}
