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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.actions.ConcordChannelPins
import com.vitorpamplona.amethyst.commons.actions.ConcordPinning
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs the delayed Pin List duties a PIN_MESSAGES holder owes keyless readers (CORD-04 §7) — the
 * deletion omission and the Edit refresh — from the account, not from an open channel screen, so a
 * debt is settled whether or not anyone is looking at the channel.
 *
 * Rate-limited the way the spec asks: each duty waits a short random delay
 * ([ConcordPinning.dutyDelayMs], 3–15 s) and the caller's settle re-reads before publishing, so
 * simultaneous curators collapse to one publisher and a burst of deletes or edits costs one write;
 * at most one duty per channel is in flight; and each distinct debt is attempted **once**, so a
 * write that keeps failing never spins. A new debt (another delete, a newer Edit) is a new attempt.
 */
class ConcordPinDutyScheduler(
    private val delayMs: () -> Long = { ConcordPinning.dutyDelayMs() },
) {
    private val lock = KmpLock()

    // Channel key -> the debt last attempted there.
    private val attempted = HashMap<String, String>()

    // Channel key -> its duty in flight.
    private val inFlight = HashMap<String, Job>()

    /**
     * Schedules [settle] in [scope] for the channel [key] when [debt] is non-null, was not attempted
     * yet, and no duty for [key] is in flight. Returns whether it scheduled one.
     */
    fun schedule(
        scope: CoroutineScope,
        key: String,
        debt: String?,
        settle: suspend () -> Unit,
    ): Boolean {
        if (debt == null) return false
        return lock.withLock {
            if (attempted[key] == debt || inFlight[key]?.isActive == true) return@withLock false
            attempted[key] = debt
            inFlight[key] =
                scope.launch {
                    delay(delayMs())
                    try {
                        settle()
                    } finally {
                        lock.withLock { inFlight.remove(key) }
                    }
                }
            true
        }
    }

    companion object {
        /** The identity of what [pins] owes (its erased entries and the Edits to attach), or null when nothing. */
        fun debtOf(pins: ConcordChannelPins?): String? {
            if (pins == null || !pins.owesRepublish) return null
            return (pins.killed.map { "d" + it.rumorId } + pins.pins.mapNotNull { p -> p.newerEdit?.let { "e" + it.rumorId } })
                .sorted()
                .joinToString("|")
        }

        /** The scheduler key of one channel. */
        fun keyOf(
            communityId: String,
            channelIdHex: String,
        ): String = "$communityId|$channelIdHex"
    }
}
