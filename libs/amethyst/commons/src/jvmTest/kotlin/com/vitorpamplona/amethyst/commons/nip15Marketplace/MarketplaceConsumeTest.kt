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

import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip15Marketplace.auction.AuctionEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The NIP-15 kinds reach the cache (stalls, products, markets and auctions at their addresses,
 * bids and confirmations under the auction they name), other apps' records on the same
 * kind numbers do not, and the auction card's bid summary reads what the cache filed.
 */
class MarketplaceConsumeTest {
    private val merchant = "aa".repeat(32)
    private val bidder1 = "bb".repeat(32)
    private val bidder2 = "cc".repeat(32)
    private val sig = "22".repeat(64)

    private fun event(
        idByte: String,
        kind: Int,
        createdAt: Long,
        content: String,
        vararg tags: Array<String>,
        author: String = merchant,
    ): Event = EventFactory.create(idByte.repeat(32), author, createdAt, kind, arrayOf(*tags), content, sig)

    private fun EventCache.stored(event: Event) = getAddressableNoteIfExists((event as AddressableEvent).address())?.event

    @Test
    fun storesTheMarketplaceKindsAndSkipsOtherAppsShapes() {
        val cache = EventCache()

        val stall = event("01", 30017, 1_700_000_000L, """{"id":"s1","name":"Shop","currency":"USD","shipping":[]}""", arrayOf("d", "s1"))
        val product = event("02", 30018, 1_700_000_000L, """{"id":"p1","stall_id":"s1","name":"Mug","price":"12.5","currency":"USD","quantity":null}""", arrayOf("d", "p1"))
        val market = event("03", 30019, 1_700_000_000L, """{"name":"Market","merchants":["$merchant"]}""", arrayOf("d", "m1"))
        val auction = event("04", 30020, 1_700_000_000L, "Digital Art", arrayOf("d", "a1"), arrayOf("title", "Roatan"), arrayOf("start_price", "1000"))

        listOf(stall, product, market, auction).forEach {
            assertTrue(cache.justConsume(it, null, true), it.toJson())
            assertSame(it, cache.stored(it))
        }

        val typingGame = event("05", 30017, 1_700_000_000L, """{"app":"typer","wpm":80}""", arrayOf("d", "score"))
        val order = event("06", 30019, 1_700_000_000L, """{"type":"offer","title":"Bike"}""", arrayOf("d", "o"))
        val blob = event("07", 30020, 1_700_000_000L, "Ao8V99Qf/HvXebiOBPXsSn", arrayOf("d", "x"))
        listOf(typingGame, order, blob).forEach {
            assertFalse(cache.justConsume(it, null, true), it.toJson())
            assertNull(cache.stored(it))
        }
    }

    @Test
    fun bidsAreFiledUnderTheAuctionVersionAndSummarized() {
        val cache = EventCache()
        val auction = event("04", 30020, 1_700_000_000L, """{"id":"a1","name":"Painting","starting_bid":1000,"start_date":1700000000,"duration":3600}""", arrayOf("d", "a1")) as AuctionEvent
        assertTrue(cache.justConsume(auction, null, true))

        val low = event("10", 1021, 1_700_000_100L, "1500", arrayOf("e", auction.id), author = bidder1)
        val high = event("11", 1021, 1_700_000_200L, "3000", arrayOf("e", auction.id), author = bidder2)
        val elsewhere = event("12", 1021, 1_700_000_300L, "9999", arrayOf("e", "ff".repeat(32)), author = bidder2)
        val junk = event("13", 1021, 1_700_000_300L, "lots", arrayOf("e", auction.id), author = bidder2)
        listOf(low, high, elsewhere).forEach { assertTrue(cache.justConsume(it, null, true)) }
        assertFalse(cache.justConsume(junk, null, true))

        // An `e` to an addressable version is upgraded to the auction's address: the bids land in
        // the addressable note's replies, which the card already observes. The bid on another
        // auction does not.
        val version = cache.getAddressableNoteIfExists(auction.address())!!
        assertEquals(setOf(low.id, high.id), version.replies.mapTo(HashSet()) { it.idHex })

        val open = AuctionBidSummary.of(auction, version.replies)
        assertEquals(2, open.bidCount)
        assertEquals(3000.0, open.highestBid)
        assertEquals(bidder2, open.highestBidder)
        assertFalse(open.highestConfirmed)

        // The merchant rejects the high bid and extends the auction; a stranger's "winner" is ignored.
        val reject = event("20", 1022, 1_700_000_400L, """{"status":"rejected","duration_extended":300}""", arrayOf("e", high.id), arrayOf("e", auction.id))
        val accept = event("21", 1022, 1_700_000_400L, """{"status":"accepted"}""", arrayOf("e", low.id), arrayOf("e", auction.id))
        val forged = event("22", 1022, 1_700_000_500L, """{"status":"winner"}""", arrayOf("e", high.id), arrayOf("e", auction.id), author = bidder2)
        listOf(reject, accept, forged).forEach { assertTrue(cache.justConsume(it, null, true)) }

        // Confirmations are filed under the bid as well as the auction.
        assertTrue(cache.getNoteIfExists(high.id)!!.replies.any { it.idHex == reject.id })

        val confirmed = AuctionBidSummary.of(auction, version.replies)
        assertEquals(1500.0, confirmed.highestBid)
        assertEquals(bidder1, confirmed.highestBidder)
        assertTrue(confirmed.highestConfirmed)
        assertEquals(300L, confirmed.extensionSeconds)
        assertNull(confirmed.winner)
    }

    @Test
    fun noBidsIsEmpty() {
        val auction = event("04", 30020, 1_700_000_000L, """{"name":"Painting"}""", arrayOf("d", "a1")) as AuctionEvent
        assertEquals(AuctionBidSummary.EMPTY, AuctionBidSummary.of(auction, emptyList()))
    }

    @Test
    fun pricesReadExactly() {
        assertEquals("2100", formatMarketAmount(2100.0))
        assertEquals("12.50", formatMarketAmount(12.5))
        assertEquals("0.07", formatMarketAmount(0.07))
        assertEquals("-3", formatMarketAmount(-3.0))
        assertEquals("2100 sats", formatMarketPrice(2100.0, "sat"))
        assertEquals("9 USD", formatMarketPrice(9.0, "usd"))
        assertEquals("9", formatMarketPrice(9.0, " "))
        assertNull(marketCurrencyLabel(null))
        assertEquals("sats", marketCurrencyLabel("Sats"))
    }
}
