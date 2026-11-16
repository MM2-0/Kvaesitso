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
package com.vitorpamplona.amethyst.commons.nip15Marketplace

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip15Marketplace.auction.AuctionEvent
import com.vitorpamplona.quartz.nip15Marketplace.bid.BidEvent
import com.vitorpamplona.quartz.nip15Marketplace.bidConfirmation.BidConfirmationEvent

/**
 * What the bids seen so far say about one auction version.
 *
 * [highestBid] is the largest bid the merchant has not rejected (NIP-15 asks clients to wait for
 * the merchant's confirmation; few merchants publish one, so an unconfirmed bid still counts and
 * [highestConfirmed] says whether this one was accepted). [extensionSeconds] adds up the
 * `duration_extended` of the merchant's confirmations, which push the end of the auction back.
 */
@Immutable
data class AuctionBidSummary(
    val bidCount: Int,
    val highestBid: Double?,
    val highestBidder: HexKey?,
    val highestConfirmed: Boolean,
    val extensionSeconds: Long,
    val winner: HexKey?,
) {
    companion object {
        val EMPTY = AuctionBidSummary(0, null, null, false, 0, null)

        /**
         * Reads the bids (1021) on [auction] and the confirmations (1022) its author signed out of
         * [notes] (the auction's replies, where the cache files both). Bids on an earlier version
         * of the auction (NIP-15 bids name the event id, so an edit drops them), notes about other
         * auctions, and confirmations by anyone but the merchant are ignored.
         */
        fun of(
            auction: AuctionEvent,
            notes: Iterable<Note>,
        ): AuctionBidSummary {
            val bids = LinkedHashMap<HexKey, BidEvent>()
            val statusByBid = HashMap<HexKey, BidConfirmationEvent>()
            val confirmations = HashSet<HexKey>()
            var extension = 0L

            for (note in notes) {
                when (val event = note.event) {
                    is BidEvent -> {
                        if (event.auctionId() == auction.id && event.amount() != null) bids[event.id] = event
                    }

                    is BidConfirmationEvent -> {
                        if (event.pubKey != auction.pubKey || event.auctionId() != auction.id) continue
                        if (!confirmations.add(event.id)) continue
                        val bidId = event.bidId() ?: continue
                        val previous = statusByBid[bidId]
                        if (previous == null || event.createdAt > previous.createdAt) statusByBid[bidId] = event
                        event.confirmationData()?.durationExtended?.let { if (it > 0) extension += it }
                    }

                    else -> {}
                }
            }

            if (bids.isEmpty()) return EMPTY.copy(extensionSeconds = extension)

            var best: BidEvent? = null
            var bestAmount = 0.0
            var winner: HexKey? = null
            for (bid in bids.values) {
                val status = statusByBid[bid.id]?.status()
                if (status == BidConfirmationEvent.STATUS_WINNER) winner = bid.pubKey
                if (status == BidConfirmationEvent.STATUS_REJECTED) continue
                val amount = bid.amount() ?: continue
                val current = best
                // Ties go to the earlier bid.
                if (current == null || amount > bestAmount || (amount == bestAmount && bid.createdAt < current.createdAt)) {
                    best = bid
                    bestAmount = amount
                }
            }

            val bestStatus = best?.let { statusByBid[it.id]?.status() }
            return AuctionBidSummary(
                bidCount = bids.size,
                highestBid = best?.let { bestAmount },
                highestBidder = best?.pubKey,
                highestConfirmed = bestStatus == BidConfirmationEvent.STATUS_ACCEPTED || bestStatus == BidConfirmationEvent.STATUS_WINNER,
                extensionSeconds = extension,
                winner = winner,
            )
        }
    }
}
