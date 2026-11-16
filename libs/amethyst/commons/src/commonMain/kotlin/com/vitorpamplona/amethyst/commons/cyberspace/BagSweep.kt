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
package com.vitorpamplona.amethyst.commons.cyberspace

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.cyberspace.CyberspaceBagContents
import com.vitorpamplona.quartz.cyberspace.CyberspaceBagEvent
import com.vitorpamplona.quartz.cyberspace.CyberspaceHint
import com.vitorpamplona.quartz.cyberspace.RegionSweep
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.TimeSource

/**
 * What a bag's `hint` tag promises, before anything is spent on it.
 *
 * The whole quote is arithmetic on two tags — §7.7's three heights and §8.6's
 * `h` — so a card can carry it without building a single Cantor tree. That is
 * deliberate: a feed may scroll past a bag whose hider chose a gap of 75, and
 * the reader has to be able to say "out of reach" without having taken a
 * stranger's dare first.
 */
@Immutable
sealed class BagSweepQuote {
    /** A search this reader is willing to offer, and how big it is. */
    @Immutable
    data class Searchable(
        val gapBits: Int,
        val candidates: Long,
        /**
         * §7.7: three heights equal to the bag's "name the region itself: the
         * hint is then a destination the seeker can compute or walk to
         * directly, not a search".
         *
         * It is still a tap. A reader who learns that some bags open themselves
         * has learned something a hostile bag can hide inside, so the one
         * candidate is offered exactly like the million.
         */
        val destination: Boolean,
    ) : BagSweepQuote()

    /**
     * A hint whose box is past what this client sweeps, with the gap that
     * decided it — so the card can say how far past, rather than only that it
     * is. §7.7's own table runs from "seconds" to "days to never", and the far
     * end is where a sector-only hint on a shallow bag lands: a gap of 75,
     * which nobody will ever sweep. Such a hint "says where to travel, not
     * where to search", and Amethyst cannot travel.
     */
    @Immutable
    data class OutOfReach(
        val gapBits: Int,
    ) : BagSweepQuote()

    /**
     * Nothing to search from: no `hint` tag, a malformed one (§7.7 says those
     * are the same thing), or no `d` tag to recognise the region by.
     *
     * Not an error in the bag. It is content hidden the hard way, and §7.7 is
     * clear about what that means: "any given bag is equally likely to be at
     * any point in the full 2^256 coordinate space".
     */
    @Immutable
    data object Hidden : BagSweepQuote()
}

/** Where a sweep has got to, for a card that has to show progress and a cancel. */
@Immutable
sealed class BagSweepState {
    /** Pricing the first candidate on this device, before committing to the rest. */
    @Immutable
    data object Measuring : BagSweepState()

    @Immutable
    data class Running(
        val examined: Long,
        val candidates: Long,
    ) : BagSweepState()

    /**
     * The region key came up, and the bag opened.
     *
     * [contents] is null when it did not, which at this point means the
     * ciphertext is damaged rather than that the key is wrong — the `lookup_id`
     * already matched, and §7.2 makes that a hash of this very key.
     */
    @Immutable
    data class Opened(
        val contents: CyberspaceBagContents?,
    ) : BagSweepState()

    /** The whole box was swept and the bag was not in it: the hint was wrong, or it was bait. */
    @Immutable
    data class NotFound(
        val examined: Long,
    ) : BagSweepState()

    /**
     * Measured, not guessed: the first candidate cost [millisPerCandidate] on
     * *this* device, so the box would take [estimateMillis], which is past what
     * this client spends without being asked again.
     */
    @Immutable
    data class OutOfReach(
        val estimateMillis: Long,
        val millisPerCandidate: Long,
    ) : BagSweepState()
}

/**
 * §7.7's hint-and-sweep, priced for a reader with a battery.
 *
 * The protocol half is [RegionSweep]; this is the budget around it, and the
 * budget is the entire product decision. A hint is a stranger's declaration of
 * how hard they want the search to be, so everything here is arranged so the
 * reader finds out the price before paying it:
 *
 * 1. [quote] reads the two tags and costs nothing. A box past [MAX_GAP_BITS] or
 *    a bag deeper than [MAX_BAG_HEIGHT] is out of reach and never becomes a
 *    button.
 * 2. [sweep] times the first candidate on the device it is actually running on
 *    before committing to the rest, and stops if that measurement says the box
 *    is past [BUDGET_MILLIS]. The plan for this feature said to measure the
 *    Android cost before quoting a number in the UI; measuring it at the moment
 *    of the tap is the same answer without a constant that goes stale on the
 *    next handset.
 * 3. The sweep is a cold [Flow] over a cold [Sequence], so a cancelled
 *    collection stops paying immediately.
 *
 * Nothing here talks to a relay, because the bag is already in hand: this is
 * §7.7's search applied to one event someone put in front of you, not a crawl.
 * And nothing starts on its own — [sweep] runs when it is collected.
 */
object BagSweep {
    /**
     * The largest box offered as a button, as §7.7's gap exponent.
     *
     * 2^20 is about a million candidate regions. §7.7's own table calls 12
     * "seconds" and 24 "hours"; this sits between them, at the scale a phone
     * can finish while someone watches. Past it the card says so instead, which
     * is the honest answer to a hint that was chosen to be expensive.
     */
    const val MAX_GAP_BITS = 20

    /**
     * The deepest bag this offers to search.
     *
     * Not about the box but about a single key: a region key is three folds of
     * integers that double in width at every level, so one candidate at height
     * 16 is seconds on its own and one at height 20 is minutes. ONOSENDAI draws
     * the same line, capping its own discovery scan at height 12 and selling
     * the deeper ones as a service.
     */
    const val MAX_BAG_HEIGHT = 12

    /**
     * How long a sweep may be expected to take before it is refused outright.
     *
     * Two minutes is where §7.7's "seconds" has clearly ended, and it is
     * checked against a measurement from this device rather than a table, so a
     * slower phone refuses boxes a faster one accepts. That asymmetry is
     * correct: the cost is the reader's, so the reader's own hardware decides.
     */
    const val BUDGET_MILLIS = 120_000L

    /** How many candidates pass between progress emissions. */
    private const val PROGRESS_EVERY = 64L

    /**
     * Where [sweep] should be collected. A sweep is minutes of CPU on one thread,
     * so it must not sit on Default, where it would take one of the few core-sized
     * workers for that long; but a feed can show several bags, and plain IO would
     * let every started sweep spin a thread of its own. Two at a time keeps the
     * rest of the device responsive; any further sweep waits its turn.
     */
    val SweepDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_SWEEPS)

    private const val MAX_CONCURRENT_SWEEPS = 2

    /**
     * What this bag's hint promises, from its tags alone.
     *
     * Costs two subtractions and an addition — safe to call while composing a
     * feed row, and deliberately so, because the card has to be able to show
     * the price of a search it will not run.
     */
    fun quote(bag: CyberspaceBagEvent): BagSweepQuote {
        if (!bag.isKnownVersion()) return BagSweepQuote.Hidden
        if (bag.lookupId() == null) return BagSweepQuote.Hidden
        if (bag.payload() == null) return BagSweepQuote.Hidden

        val height = bag.height() ?: return BagSweepQuote.Hidden
        val hint = bag.hint() ?: return BagSweepQuote.Hidden

        val gap = hint.gapBits(height)
        if (height > MAX_BAG_HEIGHT || gap > MAX_GAP_BITS) return BagSweepQuote.OutOfReach(gap)

        val candidates = hint.candidates(height) ?: return BagSweepQuote.OutOfReach(gap)
        return BagSweepQuote.Searchable(gap, candidates, hint.isDestination(height))
    }

    /**
     * Sweep this bag's box, emitting progress, and open it if the key turns up.
     *
     * Cold: nothing runs until collected, and cancelling the collection stops
     * the work at the next candidate. Collect it off the main thread — the
     * arithmetic is arbitrary-precision and the whole point is that it is slow.
     */
    fun sweep(bag: CyberspaceBagEvent): Flow<BagSweepState> =
        flow {
            val quote = quote(bag)
            if (quote !is BagSweepQuote.Searchable) {
                // Priced and declined before a tree was built. There is nothing
                // to emit that the card did not already know from [quote].
                return@flow
            }

            val height = bag.height() ?: return@flow
            val hint = bag.hint() ?: return@flow
            val target = bag.lookupId() ?: return@flow

            emit(BagSweepState.Measuring)

            // The box's own base region, priced on the way past. A gap-0 hint
            // names exactly this one, so the measurement is never wasted work:
            // it is the first candidate either way.
            val mark = TimeSource.Monotonic.markNow()
            val base = RegionSweep.of(CyberspaceHint(hint.base, height, height, height), height).first()
            val perCandidate = mark.elapsedNow().inWholeMilliseconds

            if (base.lookupId == target) {
                emit(BagSweepState.Opened(bag.open(base.decryptionKey)))
                return@flow
            }

            // One candidate here is three axis roots and a combine; every
            // candidate after it is one combine, because §4.7's axes are reused
            // across the box. So this over-quotes — by about four times at the
            // shallow heights and two at the deep ones — and over-quoting is
            // the safe direction for a number whose only job is to decide
            // whether to spend somebody's battery.
            val estimate = perCandidate * quote.candidates
            if (estimate > BUDGET_MILLIS) {
                emit(BagSweepState.OutOfReach(estimate, perCandidate))
                return@flow
            }

            var examined = 0L
            emit(BagSweepState.Running(examined, quote.candidates))

            for (material in RegionSweep.of(hint, height)) {
                currentCoroutineContext().ensureActive()
                examined++
                if (material.lookupId == target) {
                    emit(BagSweepState.Opened(bag.open(material.decryptionKey)))
                    return@flow
                }
                if (examined % PROGRESS_EVERY == 0L) emit(BagSweepState.Running(examined, quote.candidates))
            }

            emit(BagSweepState.NotFound(examined))
        }
}
