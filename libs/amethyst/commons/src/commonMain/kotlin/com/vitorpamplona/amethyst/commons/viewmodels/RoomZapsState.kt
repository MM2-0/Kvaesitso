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
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.utils.toLongValue

/**
 * One in-flight kind-9735 zap to render as a floating overlay on the
 * zapper's avatar. Same ephemeral contract as [RoomReaction]: the
 * aggregator drops entries older than the staleness window so the
 * overlay clears itself without per-component animation bookkeeping.
 *
 * `targetPubkey == null` means the receipt carried no `p` tag — the
 * zap still floats from the zapper's avatar, so the target is only
 * informational.
 */
@Immutable
data class RoomZap(
    /** Zap receipt event id — used for dedup across re-emits. */
    val eventId: String,
    /** Who SENT the zap (the kind-9734 request author, not the LN service). */
    val sourcePubkey: String,
    /** Participant the zap names via its `p` tag, or `null` if none. */
    val targetPubkey: String?,
    /** Amount in sats, or `null` if the invoice couldn't be parsed. */
    val amountSats: Long?,
    val createdAtSec: Long,
) {
    companion object {
        /**
         * Project a kind-9735 [ZapReceiptEvent] into a [RoomZap]. The
         * source pubkey is the embedded kind-9734 request author —
         * the actual zapper — NOT the receipt's own `pubKey`, which
         * is the lnurl service provider's signing key. Falls back to
         * the receipt issuer only when the request can't be parsed.
         */
        fun from(event: ZapReceiptEvent): RoomZap =
            RoomZap(
                eventId = event.id,
                sourcePubkey = event.zapRequest?.pubKey ?: event.pubKey,
                targetPubkey = event.zappedAuthor().firstOrNull(),
                amountSats = event.amount?.toLongValue(),
                createdAtSec = event.createdAt,
            )

        /**
         * Project a kind-9736 [Bolt12ZapEvent] into a [RoomZap]. The zapper is
         * the `P` payer tag (or the event pubkey for an anonymous zap); the amount
         * is the `amount` tag in millisats converted to sats.
         */
        fun from(event: Bolt12ZapEvent): RoomZap =
            RoomZap(
                eventId = event.id,
                sourcePubkey = event.payer() ?: event.pubKey,
                targetPubkey = event.recipient(),
                amountSats = event.amount()?.div(1000),
                createdAtSec = event.createdAt,
            )
    }
}

/**
 * Sliding-window aggregator for room zaps. Mirrors
 * [RoomReactionsAggregator] — same dedup-by-event-id rule and
 * group-by-sender output shape so the UI can use one overlay
 * placement convention for both streams.
 *
 * Not thread-safe — call from the VM's single coroutine.
 */
class RoomZapsAggregator {
    // Insertion-ordered so groupBy preserves arrival order; keyed by
    // event id so the same receipt from two relays only counts once.
    private val byEventId = LinkedHashMap<String, RoomZap>()

    /** Apply one zap and return the post-evict snapshot. */
    fun apply(
        event: ZapReceiptEvent,
        nowSec: Long,
        windowSec: Long,
    ): Map<String, List<RoomZap>> = apply(RoomZap.from(event), nowSec, windowSec)

    fun apply(
        event: Bolt12ZapEvent,
        nowSec: Long,
        windowSec: Long,
    ): Map<String, List<RoomZap>> = apply(RoomZap.from(event), nowSec, windowSec)

    private fun apply(
        incoming: RoomZap,
        nowSec: Long,
        windowSec: Long,
    ): Map<String, List<RoomZap>> {
        // Dedup: a relay re-delivery (or LocalCache.observeNotes's
        // full-list re-emit) of the same receipt must not stack.
        byEventId[incoming.eventId] = incoming
        return evictAndSnapshot(nowSec - windowSec)
    }

    /**
     * Drop zaps older than [olderThanSec]. Caller drives the cadence
     * — typically every second so the floating-up animation frame
     * rate is set by the eviction tick rather than by a
     * per-Composable timer.
     *
     * Zaps are grouped by [RoomZap.sourcePubkey] — the user who SENT
     * the zap — so the chip floats up from the zapper's own avatar,
     * matching how [RoomReactionsAggregator] groups reactions. In a
     * live audio room the audience expects to see who's zapping, not
     * who's being zapped; and the zapper may be an audience member,
     * so the overlay is wired into the audience grid as well as the
     * stage.
     */
    fun evictAndSnapshot(olderThanSec: Long): Map<String, List<RoomZap>> {
        val it = byEventId.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value.createdAtSec < olderThanSec) it.remove()
        }
        return byEventId.values.groupBy { it.sourcePubkey }
    }
}
