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
package com.vitorpamplona.amethyst.commons.model.nip88Polls

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollType
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent

/**
 * The rules a [PollResponsesCache] tally must apply, taken from the kind-1068 poll itself.
 *
 * A tally cannot be computed correctly from the responses alone: whether a response's second
 * `response` tag counts, whether an option code exists at all, and whether a vote arrived while
 * the poll was open are all questions only the poll event can answer. Responses routinely arrive
 * before the poll they reference, so the tally starts permissive (`policy == null`) and is
 * recomputed once [PollResponsesCache.updatePolicy] delivers this.
 */
@Immutable
data class PollTallyPolicy(
    /** Option codes the poll actually declares. Anything else is not a vote. */
    val validCodes: Set<String>,
    val type: PollType,
    /** The poll event's own `created_at`; nothing can be voted before the question exists. */
    val createdAt: Long,
    /** NIP-88 `endsAt`, or null for a poll that never closes. */
    val endsAt: Long?,
    /**
     * A stricter reading the room the poll lives in applies on top of these rules, or null for
     * NIP-88's. See [PollResponseRule].
     */
    val rule: PollResponseRule? = null,
) {
    /**
     * NIP-88: the retained response is "the event with the latest timestamp within poll
     * timeframes" — so a response stamped after the deadline is not a late vote that wins, it is
     * not a vote at all.
     */
    fun isInWindow(createdAt: Long): Boolean = !isBeforeWindow(createdAt) && !isAfterWindow(createdAt)

    /** Stamped before the question existed — a backdated vote, not a late one. */
    fun isBeforeWindow(createdAt: Long): Boolean = createdAt < this.createdAt

    /** Stamped after the deadline. */
    fun isAfterWindow(createdAt: Long): Boolean = endsAt != null && createdAt > endsAt

    /**
     * The option codes a response actually casts.
     *
     * Single choice: NIP-88 says "the first response tag is to be considered the actual response",
     * so only the first tag is read — a response that lists every option casts no valid vote rather
     * than one vote per option.
     *
     * Multiple choice: "the first response tag pointing to each unique ID counts", so codes are
     * de-duplicated, order-independent.
     *
     * Codes the poll never declared are dropped in both cases.
     */
    fun accept(codes: List<String>): Set<String> =
        when (type) {
            PollType.SINGLE_CHOICE -> codes.firstOrNull()?.takeIf { it in validCodes }?.let { setOf(it) } ?: emptySet()
            PollType.MULTI_CHOICE -> codes.filterTo(mutableSetOf()) { it in validCodes }
        }

    companion object {
        fun from(event: PollEvent) =
            PollTallyPolicy(
                validCodes = event.options().mapTo(mutableSetOf()) { it.code },
                type = event.pollType(),
                createdAt = event.createdAt,
                endsAt = event.endsAt(),
            )
    }
}

/**
 * How a room reads its own polls' votes when NIP-88's lenient reading is not what its members see.
 *
 * A Marmot group counts the way MDK does, so Amethyst and White Noise show the same numbers: a
 * response that breaks the poll's rules is not a vote at all, and the voter's earlier valid vote
 * stands instead of being cleared, and of two votes stamped the same second the larger id wins.
 * Implementations hold only values, so two rules for the same poll compare equal.
 */
fun interface PollResponseRule {
    /** The options [response] casts, or null when it is not a valid vote under this rule. */
    fun cast(response: PollResponseEvent): Set<String>?
}
