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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip15Marketplace.formatMarketPrice
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.d
import com.vitorpamplona.amethyst.commons.resources.h
import com.vitorpamplona.amethyst.commons.resources.m
import com.vitorpamplona.amethyst.commons.resources.marketplace_bid_amount
import com.vitorpamplona.amethyst.commons.resources.marketplace_bid_extended
import com.vitorpamplona.amethyst.commons.resources.marketplace_bid_status_accepted
import com.vitorpamplona.amethyst.commons.resources.marketplace_bid_status_pending
import com.vitorpamplona.amethyst.commons.resources.marketplace_bid_status_rejected
import com.vitorpamplona.amethyst.commons.resources.marketplace_bid_status_winner
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip15Marketplace.auction.AuctionEvent
import com.vitorpamplona.quartz.nip15Marketplace.bid.BidEvent
import com.vitorpamplona.quartz.nip15Marketplace.bidConfirmation.BidConfirmationEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A NIP-15 bid (kind 1021): "Bid 5000 sats", with the auction it is on quoted underneath. The
 * amount is in the auction's currency, so the line waits for nothing: until the auction loads it
 * reads as sats, which is what NIP-15 says a bid is.
 */
@Composable
fun RenderBid(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val bid = note.event as? BidEvent ?: return
    val amount = remember(bid) { bid.amount() } ?: return
    val auctionId = remember(bid) { bid.auctionId() }

    // The cache files the bid under its auction (upgraded to the auction's address).
    val auctionNote = note.replyTo?.lastOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (auctionNote != null) {
            val auction by observeNoteEvent<AuctionEvent>(auctionNote, accountViewModel)
            // Only the version the bid names carries its currency; a later edit is another auction.
            val currency = remember(auction) { auction?.takeIf { it.id == auctionId }?.auctionData()?.currencyOrDefault() }
            BidLine(stringRes(Res.string.marketplace_bid_amount, formatMarketPrice(amount, currency ?: "sats")))
        } else {
            BidLine(stringRes(Res.string.marketplace_bid_amount, formatMarketPrice(amount, "sats")))
        }

        if (auctionId != null && quotesLeft > 0) {
            QuotedNote(auctionId, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

@Composable
private fun BidLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            symbol = MaterialSymbols.Gavel,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * A NIP-15 bid confirmation (kind 1022): the merchant's verdict on a bid as a status chip, their
 * message, any time the auction was extended by, and the bid itself quoted underneath.
 */
@Composable
fun RenderBidConfirmation(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val confirmation = note.event as? BidConfirmationEvent ?: return
    val data = remember(confirmation) { confirmation.confirmationData() } ?: return
    val bidId = remember(confirmation) { confirmation.bidId() }

    val statusLabel =
        when (data.status) {
            BidConfirmationEvent.STATUS_ACCEPTED -> stringRes(Res.string.marketplace_bid_status_accepted)
            BidConfirmationEvent.STATUS_REJECTED -> stringRes(Res.string.marketplace_bid_status_rejected)
            BidConfirmationEvent.STATUS_PENDING -> stringRes(Res.string.marketplace_bid_status_pending)
            BidConfirmationEvent.STATUS_WINNER -> stringRes(Res.string.marketplace_bid_status_winner)
            else -> data.status
        }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                symbol = if (data.status == BidConfirmationEvent.STATUS_REJECTED) MaterialSymbols.Cancel else MaterialSymbols.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            statusLabel?.let { MarketBadge(it, warning = data.status == BidConfirmationEvent.STATUS_REJECTED) }
        }

        data.message?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }

        data.durationExtended?.takeIf { it > 0 }?.let {
            MarketFactLine(MaterialSymbols.Timer, stringRes(Res.string.marketplace_bid_extended, shortDuration(it)))
        }

        if (bidId != null && quotesLeft > 0) {
            QuotedNote(bidId, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

/** "5m", "2h", "3d": the largest whole unit, with the app's own unit letters. */
@Composable
private fun shortDuration(seconds: Long): String =
    when {
        seconds >= TimeUtils.ONE_DAY -> "${seconds / TimeUtils.ONE_DAY}${stringRes(Res.string.d)}"
        seconds >= TimeUtils.ONE_HOUR -> "${seconds / TimeUtils.ONE_HOUR}${stringRes(Res.string.h)}"
        else -> "${(seconds / TimeUtils.ONE_MINUTE).coerceAtLeast(1)}${stringRes(Res.string.m)}"
    }

@Composable
private fun QuotedNote(
    id: String,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(baseNoteHex = id) { quoted ->
        if (quoted != null) {
            NoteCompose(
                quoted,
                modifier = Modifier,
                isBoostedNote = true,
                makeItShort = true,
                unPackReply = ReplyRenderType.NONE,
                quotesLeft = quotesLeft - 1,
                parentBackgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}
