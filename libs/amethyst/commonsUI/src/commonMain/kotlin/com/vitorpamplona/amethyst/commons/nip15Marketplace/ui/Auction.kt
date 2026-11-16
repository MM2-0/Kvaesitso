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
package com.vitorpamplona.amethyst.commons.nip15Marketplace.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip15Marketplace.AuctionBidSummary
import com.vitorpamplona.amethyst.commons.nip15Marketplace.formatMarketPrice
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReplies
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.marketplace_auction
import com.vitorpamplona.amethyst.commons.resources.marketplace_auction_ended
import com.vitorpamplona.amethyst.commons.resources.marketplace_auction_ended_chip
import com.vitorpamplona.amethyst.commons.resources.marketplace_auction_ends
import com.vitorpamplona.amethyst.commons.resources.marketplace_auction_live_chip
import com.vitorpamplona.amethyst.commons.resources.marketplace_auction_scheduled_chip
import com.vitorpamplona.amethyst.commons.resources.marketplace_auction_started
import com.vitorpamplona.amethyst.commons.resources.marketplace_auction_starts
import com.vitorpamplona.amethyst.commons.resources.marketplace_bids
import com.vitorpamplona.amethyst.commons.resources.marketplace_highest_bid
import com.vitorpamplona.amethyst.commons.resources.marketplace_highest_bid_unconfirmed
import com.vitorpamplona.amethyst.commons.resources.marketplace_starting_bid
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateSkeletonFormatter
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.StatTile
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip15Marketplace.auction.AuctionData
import com.vitorpamplona.quartz.nip15Marketplace.auction.AuctionEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

private val auctionDateFormatter = DateSkeletonFormatter("yMMMdjm")

/** NIP-15 bids are "an amount of sats"; an auction that names no currency of its own is in sats. */
private const val DEFAULT_AUCTION_CURRENCY = "sats"

/** The currency an auction's amounts are in. */
fun AuctionData.currencyOrDefault() = currency ?: DEFAULT_AUCTION_CURRENCY

@Immutable
private class AuctionCardState(
    val data: AuctionData,
    val name: String,
    val images: ImmutableList<String>,
    val startingBid: String?,
    val specs: ImmutableList<MarketSpec>,
    val topics: ImmutableList<String>,
    val stallAddress: Address?,
)

private enum class AuctionPhase { UPCOMING, LIVE, ENDED, UNSCHEDULED }

/** Times, phase and the bid line, worked out once per (event, bids) pair. */
@Immutable
private class AuctionTimes(
    val phase: AuctionPhase,
    val start: String?,
    val end: String?,
    val highestBid: String?,
)

/**
 * A NIP-15 auction (kind 30020): like a product, plus the starting bid, when it starts and ends
 * ("Ended" once past), and the highest bid seen so far.
 *
 * Bids name the auction version by event id, so the card watches that version for them (the same
 * per-note engagement subscription every card's reply counter uses, closed when the card leaves the
 * screen); the cache files them under the auction's address, whose replies the card observes.
 */
@Composable
fun RenderAuction(
    note: Note,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<AuctionEvent>(note, accountViewModel)
    val auction = event ?: return
    val state = remember(auction) { cardState(auction) } ?: return

    val versionNote = remember(auction) { LocalCache.getOrCreateNote(auction.id) }
    EventFinderFilterAssemblerSubscription(versionNote, accountViewModel)

    val addressNote = remember(auction) { LocalCache.getOrCreateAddressableNote(auction.address()) }
    val replies by observeNoteReplies(addressNote, accountViewModel)
    val bids = remember(auction, replies) { AuctionBidSummary.of(auction, addressNote.replies) }
    val times = remember(state, bids) { auctionTimes(state.data, bids, TimeUtils.now()) }

    val images = rememberMarketImages(state.images, note)

    MarketCardFrame {
        MarketImageGallery(images, accountViewModel)

        MarketCardBody {
            MarketKindLabel(MaterialSymbols.Gavel, stringRes(Res.string.marketplace_auction)) {
                when (times.phase) {
                    AuctionPhase.LIVE -> MarketBadge(stringRes(Res.string.marketplace_auction_live_chip))
                    AuctionPhase.UPCOMING -> MarketBadge(stringRes(Res.string.marketplace_auction_scheduled_chip))
                    AuctionPhase.ENDED -> MarketBadge(stringRes(Res.string.marketplace_auction_ended_chip), warning = true)
                    AuctionPhase.UNSCHEDULED -> {}
                }
            }

            MarketTitle(state.name)

            BidTiles(state, bids, times)

            times.start?.let {
                val label = if (times.phase == AuctionPhase.UPCOMING) Res.string.marketplace_auction_starts else Res.string.marketplace_auction_started
                MarketFactLine(MaterialSymbols.Schedule, stringRes(label, it))
            }
            times.end?.let {
                val label = if (times.phase == AuctionPhase.ENDED) Res.string.marketplace_auction_ended else Res.string.marketplace_auction_ends
                MarketFactLine(MaterialSymbols.Timer, stringRes(label, it))
            }

            MarketStallLine(state.stallAddress, accountViewModel, nav)

            state.data.description?.let { MarketDescription(it, makeItShort) }

            MarketSpecs(state.specs)

            MarketTopics(state.topics, nav)

            MessageSellerButton(note, accountViewModel, nav)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BidTiles(
    state: AuctionCardState,
    bids: AuctionBidSummary,
    times: AuctionTimes,
) {
    if (state.startingBid == null && times.highestBid == null) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.startingBid?.let { StatTile(value = it, label = stringRes(Res.string.marketplace_starting_bid)) }
        times.highestBid?.let {
            val label = if (bids.highestConfirmed) Res.string.marketplace_highest_bid else Res.string.marketplace_highest_bid_unconfirmed
            StatTile(value = it, label = stringRes(label), emphasized = true)
        }
        if (bids.bidCount > 0) {
            StatTile(value = bids.bidCount.toString(), label = pluralStringRes(Res.plurals.marketplace_bids, bids.bidCount, bids.bidCount))
        }
    }
}

private fun cardState(event: AuctionEvent): AuctionCardState? {
    val data = event.auctionData() ?: return null
    val name = data.name ?: return null
    return AuctionCardState(
        data = data,
        name = name,
        images = data.images.toImmutableList(),
        startingBid = data.startingBid?.let { formatMarketPrice(it, data.currencyOrDefault()) },
        specs = data.specs.toMarketSpecs(),
        topics = event.categories().distinct().toImmutableList(),
        stallAddress = event.stallAddress(),
    )
}

private fun auctionTimes(
    data: AuctionData,
    bids: AuctionBidSummary,
    now: Long,
): AuctionTimes {
    val start = data.startDate
    val end = data.endsAt(bids.extensionSeconds)
    val phase =
        when {
            start == null -> AuctionPhase.UNSCHEDULED
            start > now -> AuctionPhase.UPCOMING
            end != null && end <= now -> AuctionPhase.ENDED
            else -> AuctionPhase.LIVE
        }
    return AuctionTimes(
        phase = phase,
        start = start?.let { auctionDateFormatter.format(it * 1000) },
        end = end?.let { auctionDateFormatter.format(it * 1000) },
        highestBid = bids.highestBid?.let { formatMarketPrice(it, data.currencyOrDefault()) },
    )
}
