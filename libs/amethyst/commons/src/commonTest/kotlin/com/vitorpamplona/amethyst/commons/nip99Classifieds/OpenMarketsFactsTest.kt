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
package com.vitorpamplona.amethyst.commons.nip99Classifieds

import com.vitorpamplona.amethyst.commons.nip15Marketplace.formatMarketAmount
import com.vitorpamplona.amethyst.commons.nip15Marketplace.formatMarketPrice
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.collection.ProductCollectionEvent
import com.vitorpamplona.quartz.nip99Classifieds.shipping.ShippingOptionEvent
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.DurationTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenMarketsFactsTest {
    private val seller = "aa".repeat(32)
    private val vendor = "bb".repeat(32)

    private fun listing(
        vararg tags: Array<String>,
        pubKey: String = seller,
    ) = ClassifiedsEvent("00".repeat(32), pubKey, 1_700_000_000L, arrayOf(arrayOf("d", "socks"), arrayOf("title", "Socks"), *tags), "", "22".repeat(64))

    private fun option(vararg tags: Array<String>) = ShippingOptionEvent("00".repeat(32), seller, 1_700_000_000L, arrayOf(arrayOf("d", "ship"), arrayOf("title", "Ship"), *tags), "", "22".repeat(64))

    private fun collection(vararg tags: Array<String>) = ProductCollectionEvent("00".repeat(32), seller, 1_700_000_000L, arrayOf(arrayOf("d", "summit"), arrayOf("title", "Summit"), *tags), "", "22".repeat(64))

    @Test
    fun aSellersStatusWinsThenStockThenVisibility() {
        assertNull(listing().listingStatus())
        assertEquals(ListingStatus.SOLD, listing(arrayOf("status", "Sold"), arrayOf("visibility", "hidden")).listingStatus())
        assertEquals(ListingStatus.ENDED, listing(arrayOf("status", "ended")).listingStatus())
        assertNull(listing(arrayOf("status", "active")).listingStatus())
        assertEquals(ListingStatus.SOLD_OUT, listing(arrayOf("stock", "0"), arrayOf("visibility", "hidden")).listingStatus())
        assertEquals(ListingStatus.UNLISTED, listing(arrayOf("visibility", "hidden")).listingStatus())
        assertEquals(ListingStatus.PRE_ORDER, listing(arrayOf("visibility", "pre-order")).listingStatus())
        assertNull(listing(arrayOf("visibility", "on-sale"), arrayOf("stock", "3")).listingStatus())
    }

    @Test
    fun aVariableParentWithNoStockOfItsOwnIsNotSoldOut() {
        assertNull(listing(arrayOf("type", "variable", "physical"), arrayOf("stock", "0")).listingStatus())
    }

    @Test
    fun unlistedIsStillForSaleButLeavesTheCatalog() {
        val hidden = listing(arrayOf("visibility", "hidden"))
        assertFalse(ListingStatus.UNLISTED.gone)
        assertFalse(hidden.isInCatalog())
        assertTrue(listing(arrayOf("visibility", "pre-order")).isInCatalog())
        assertTrue(listing().isInCatalog())
    }

    @Test
    fun aCollectionShowsAnotherSellersProductOnlyWhenTheProductFilesItselfUnderIt() {
        val summit = collection()
        assertTrue(summit.shows(listing()))
        assertFalse(summit.shows(listing(pubKey = vendor)))
        // The vendor's tag spells the seller's key in upper case; it is still the same collection.
        assertTrue(summit.shows(listing(arrayOf("a", "30405:${seller.uppercase()}:summit"), pubKey = vendor)))
        assertFalse(summit.shows(listing(arrayOf("a", "30405:$seller:other"), pubKey = vendor)))
    }

    @Test
    fun aListingIsInACollectionOnlyWhenTheCollectionAgrees() {
        val summit = collection(arrayOf("a", "30402:${vendor.uppercase()}:socks"))
        assertTrue(summit.acknowledges(listing()))
        assertTrue(summit.acknowledges(listing(pubKey = vendor)))
        // A stranger's listing tagging the collection cannot put itself in it.
        assertFalse(collection().acknowledges(listing(arrayOf("a", "30405:$seller:summit"), pubKey = vendor)))
    }

    @Test
    fun bitcoinKeepsItsSatoshis() {
        assertEquals("1.2345 BTC", formatMarketPrice(1.2345, "btc"))
        assertEquals("0.00021 BTC", formatMarketPrice(0.00021, "BTC"))
        assertEquals("2 BTC", formatMarketPrice(2.0, "BTC"))
        assertEquals("1.23 USD", formatMarketPrice(1.2345, "USD"))
    }

    @Test
    fun amountsAreDecimalsTheSellerWrote() {
        assertEquals(12.5, parseMarketAmount(" 12.50 "))
        assertEquals(0.0, parseMarketAmount("0"))
        assertNull(parseMarketAmount(""))
        assertNull(parseMarketAmount("0x10"))
        assertNull(parseMarketAmount("-3"))
        assertNull(parseMarketAmount("free"))
        assertNull(parseMarketAmount(null))
    }

    @Test
    fun subUnitPricesKeepTheirDigits() {
        assertEquals("0.0021", formatMarketAmount(0.0021))
        assertEquals("0.5", formatMarketAmount(0.5))
        assertEquals("0.00000001", formatMarketAmount(0.00000001))
        assertEquals("12.50", formatMarketAmount(12.5))
        assertEquals("2100", formatMarketAmount(2100.0))
    }

    @Test
    fun theExtraIsAddedInOneMoneyAndShownBesideInTwo() {
        val tenUsd = PriceTag("10", "USD", null)
        assertEquals(ShippingCost.Priced("10 USD"), shippingCost(tenUsd, null, "USD"))
        assertEquals(ShippingCost.Priced("15 USD"), shippingCost(tenUsd, "5", "usd"))
        assertEquals(ShippingCost.Split("10 USD", "5 EUR"), shippingCost(tenUsd, "5", "EUR"))
        assertEquals(ShippingCost.Split("10 USD", "5"), shippingCost(tenUsd, "5", null))
        assertEquals(ShippingCost.Priced("600 sats"), shippingCost(PriceTag("500", "sat", null), "100", "SATS"))
        assertEquals(ShippingCost.Priced("0.3 BTC"), shippingCost(PriceTag("0.1", "BTC", null), "0.2", "BTC"))
        assertEquals(ShippingCost.Free, shippingCost(PriceTag("0", "USD", null), "0", "USD"))
        assertEquals(ShippingCost.Surcharge("5 USD"), shippingCost(null, "5", "USD"))
        assertEquals(ShippingCost.Surcharge("5 USD"), shippingCost(PriceTag("10", null, null), "5", "USD"))
        assertNull(shippingCost(null, null, "USD"))
        assertNull(shippingCost(null, "-5", "USD"))
    }

    @Test
    fun durationsReadLowToHighAndABlankBoundIsUnknown() {
        assertEquals(ShippingDuration(1.0, 3.0, ShippingDurationUnit.DAYS), DurationTag("3", "1", "d").toShippingDuration())
        assertEquals(ShippingDuration(2.0, 2.0, ShippingDurationUnit.WEEKS), DurationTag("", "2", "W").toShippingDuration())
        assertNull(DurationTag("", "", "D").toShippingDuration())
        assertNull(DurationTag("1", "2", "months").toShippingDuration())
        assertEquals("1–3", ShippingDuration(1.0, 3.0, ShippingDurationUnit.DAYS).range())
        assertEquals("24", ShippingDuration(24.0, 24.0, ShippingDurationUnit.HOURS).range())
        assertEquals(1, ShippingDuration(1.0, 1.0, ShippingDurationUnit.DAYS).pluralCount())
    }

    @Test
    fun limitsAndRatesInBothSpecShapes() {
        val facts =
            option(
                arrayOf("price", "10", "USD"),
                arrayOf("price-weight", "0.75", "kg", ""),
                arrayOf("price-distance", "0.1", "EUR", "km"),
                arrayOf("price-volume", "0", "USD", "l"),
                arrayOf("weight-max", "30", "kg"),
                arrayOf("dim-min", " "),
            ).limits()
        assertEquals(
            listOf(
                ShippingLimitFact(ShippingLimit.MAX_WEIGHT, "30 kg"),
                ShippingLimitFact(ShippingLimit.PER_WEIGHT, "0.75 USD / kg"),
                ShippingLimitFact(ShippingLimit.PER_DISTANCE, "0.1 EUR / km"),
            ),
            facts,
        )
    }

    @Test
    fun countriesAreCodesOnceEach() {
        val o = option(arrayOf("country", "us", "CA", " "), arrayOf("country", "US", "mx", "United Kingdom", "united kingdom"))
        assertEquals(listOf("US", "CA", "MX", "United Kingdom"), o.countryCodes())
        assertEquals("US, CA", countriesSummary(listOf("US", "CA")))
        assertEquals("A, B, C, D +2", countriesSummary(listOf("A", "B", "C", "D", "E", "F")))
        assertNull(countriesSummary(emptyList()))
    }

    @Test
    fun aDIsANameOnlyWhenItReadsAsWords() {
        assertEquals("Voltvault worldwide free shipping", shippingNameFromD("voltvault-worldwide-free-shipping"))
        assertNull(shippingNameFromD("3f2504e0-4f89-11d3-9a0c-0305e82c3301"))
        assertNull(shippingNameFromD("ship-1712345678901"))
        assertNull(shippingNameFromD("42"))
    }
}
