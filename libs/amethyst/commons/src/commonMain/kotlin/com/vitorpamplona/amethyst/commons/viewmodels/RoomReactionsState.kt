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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent

/**
 * One in-flight reaction to render as a floating overlay on a
 * speaker's avatar. Ephemeral by design — the aggregator drops
 * entries older than the staleness window so the overlay clears
 * itself without per-component animation bookkeeping.
 *
 * `targetPubkey == null` means the reaction targets the room itself
 * (no `p` tag in the event); the UI can render those as a
 * room-wide pulse instead of attaching to a single avatar.
 */
@Immutable
data class RoomReaction(
    /** Source event id — used for dedup across re-emits. */
    val eventId: String,
    /** Who reacted. */
    val sourcePubkey: String,
    /** Speaker the reaction is aimed at, or `null` for room-wide. */
    val targetPubkey: String?,
    /** Emoji / "+" / shortcode body. */
    val content: String,
    val createdAtSec: Long,
) {
    companion object {
        /**
         * Project a kind-7 [ReactionEvent] into a [RoomReaction]. The
         * target pubkey is the FIRST `p` tag — nostrnests reactions
         * carry one when the user picked a speaker; an empty list
         * means a room-wide reaction.
         */
        fun from(event: ReactionEvent): RoomReaction =
            RoomReaction(
                eventId = event.id,
                sourcePubkey = event.pubKey,
                targetPubkey = event.originalAuthor().firstOrNull(),
                content = event.content,
                createdAtSec = event.createdAt,
            )
    }
}

/**
 * Sliding-window aggregator. Holds reactions keyed by event id (so
 * re-emits from `LocalCache.observeEvents` — which republishes the
 * full matching list on every cache mutation — collapse into a
 * single overlay entry instead of stacking duplicates), and surfaces
 * them grouped by target pubkey for the UI (with `null` lumped under
 * the empty-string key so the map's value-type is uniform).
 *
 * Not thread-safe — call from the VM's single coroutine.
 */
class RoomReactionsAggregator {
    // Insertion-ordered so groupBy preserves arrival order; keyed by
    // event id so the same kind-7 from two relays only counts once.
    private val byEventId = LinkedHashMap<String, RoomReaction>()

    /** Apply one reaction and return the post-evict snapshot. */
    fun apply(
        event: ReactionEvent,
        nowSec: Long,
        windowSec: Long,
    ): Map<String, List<RoomReaction>> {
        val incoming = RoomReaction.from(event)
        // Dedup: a relay re-delivery (or LocalCache.observeEvents's
        // full-list re-emit) of the same kind-7 must not stack.
        if (byEventId.put(incoming.eventId, incoming) != null) {
            // Re-emit of an existing reaction: just return the post-
            // evict snapshot without growing the map.
        }
        return evictAndSnapshot(nowSec - windowSec)
    }

    /**
     * Drop reactions older than [olderThanSec]. Caller drives the
     * cadence (typically every second so the floating-up animation
     * frame rate is set by the eviction tick rather than by a
     * per-Composable timer).
     *
     * Reactions are grouped by [RoomReaction.sourcePubkey] — the
     * user who SENT the reaction. The chip floats up from the
     * reactor's own avatar (matching nostrnests' UX) rather than
     * from the target speaker's avatar (which would surface the
     * NIP-25 `p`-tag's "originalAuthor" semantics — useful for
     * comment threads but confusing in a live audio room, where the
     * audience expects to see who's reacting, not who's being
     * reacted to). Room-wide reactions (no `p`-tag at all) still
     * land under their sender's key, so they show up on the
     * reactor's avatar just like targeted ones.
     */
    fun evictAndSnapshot(olderThanSec: Long): Map<String, List<RoomReaction>> {
        val it = byEventId.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value.createdAtSec < olderThanSec) it.remove()
        }
        return byEventId.values.groupBy { it.sourcePubkey }
    }

    /** Whether the aggregator currently holds any unevicted reactions. */
    fun isEmpty(): Boolean = byEventId.isEmpty()
}
