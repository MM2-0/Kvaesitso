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
package com.vitorpamplona.amethyst.commons.viewmodels.nip88Polls

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent

/**
 * What the relays said about a poll's responses, against what the subscription actually delivered.
 *
 * [reported] is deliberately **not** a sum across relays. The same vote is usually stored on
 * several relays, so adding their counts would multiply the poll. NIP-45's optional HyperLogLog
 * registers are the only way to combine counts without double-counting; when relays supply them
 * the registers are merged and re-estimated, and when they don't the best honest answer is the
 * largest single relay's count — a lower bound, never an inflated one. Relays that don't implement
 * COUNT at all simply don't answer, and are excluded from both tallies here.
 */
@Immutable
class PollLoadReport(
    /**
     * Distinct kind-1018 events the relays believe exist, already floored by what is in the local
     * cache. Null when no relay could answer at all.
     */
    val reported: Int?,
    /**
     * True when [reported] cannot be trusted as exact — either it is a HyperLogLog estimate, or
     * some relays never answered so the figure is only a lower bound.
     */
    val approximate: Boolean,
    val relaysAsked: Int,
    val relaysAnswered: Int,
)

/**
 * Reports how many responses the relays believe a poll has.
 *
 * Loading the votes belongs to the screen's subscription, not here; this exists only so the screen
 * can tell the difference between "these are all the votes" and "these are the votes we got". A
 * COUNT is a one-shot question with no streaming form, which is why it is not a subscription.
 */
fun interface PollResponseLoader {
    suspend fun load(poll: PollEvent): PollLoadReport
}
