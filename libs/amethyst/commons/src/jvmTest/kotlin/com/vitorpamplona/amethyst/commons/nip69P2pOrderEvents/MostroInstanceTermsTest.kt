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
package com.vitorpamplona.amethyst.commons.nip69P2pOrderEvents

import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.MostroInfoEvent
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MostroInstanceTermsTest {
    private val mostro = "11".repeat(32)
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun pinLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    private fun info(vararg tags: Array<String>) = MostroInfoEvent("00".repeat(32), mostro, 1_700_000_000L, arrayOf(arrayOf("d", mostro), *tags), "", "22".repeat(64))

    @Test
    fun feeFractionReadsAsAPercentWithoutFloatingPointNoise() {
        assertEquals(0.6, feeToPercent(0.006))
        assertEquals(0.0, feeToPercent(0.0))
        assertEquals(1.25, feeToPercent(0.0125))
        assertEquals("0.6%", formatPercent(feeToPercent(0.006)!!))
        assertEquals("1.25%", formatPercent(feeToPercent(0.0125)!!))
        assertEquals("0%", formatPercent(feeToPercent(0.0)!!))
    }

    @Test
    fun nonsenseFeesAreLeftOut() {
        assertNull(feeToPercent(-0.01))
        assertNull(feeToPercent(1.0))
        assertNull(feeToPercent(Double.NaN))
    }

    @Test
    fun escrowDefaultsToLightningWhenTheTagIsMissing() {
        assertEquals(MostroEscrow.LIGHTNING, MostroEscrow.of(null))
        assertEquals(MostroEscrow.LIGHTNING, MostroEscrow.of("lightning"))
        assertEquals(MostroEscrow.CASHU, MostroEscrow.of("cashu"))
        assertEquals(MostroEscrow.OTHER, MostroEscrow.of("fedimint"))
    }

    @Test
    fun readsTheTermsOfALiveShapedInstance() {
        val terms =
            MostroInstanceTerms.from(
                info(
                    arrayOf("mostro_version", "0.12.8"),
                    arrayOf("max_order_amount", "1000000"),
                    arrayOf("min_order_amount", "100"),
                    arrayOf("expiration_hours", "24"),
                    arrayOf("fiat_currencies_accepted", "usd,EUR,ARS,EUR"),
                    arrayOf("fee", "0.006"),
                    arrayOf("escrow_mode", "cashu"),
                    arrayOf("maintenance_mode", "true"),
                    arrayOf("y", "mostro", "Mostro Cuba"),
                    arrayOf("z", "info"),
                ),
            )

        assertEquals("Mostro Cuba", terms.name)
        assertEquals("0.12.8", terms.version)
        assertEquals(0.6, terms.feePercent)
        assertEquals(100L, terms.minOrderSats)
        assertEquals(1_000_000L, terms.maxOrderSats)
        assertEquals(listOf("USD", "EUR", "ARS"), terms.currencies)
        assertFalse(terms.acceptsEveryCurrency)
        assertEquals(24L, terms.pendingExpirationHours)
        assertEquals(MostroEscrow.CASHU, terms.escrow)
        assertTrue(terms.inMaintenance)
    }

    @Test
    fun anEmptyCurrencyListMeansEveryCurrencyAndAMissingOneMeansUnknown() {
        val every = MostroInstanceTerms.from(info(arrayOf("fiat_currencies_accepted", "")))
        assertTrue(every.acceptsEveryCurrency)

        val unknown = MostroInstanceTerms.from(info(arrayOf("mostro_version", "0.12.8")))
        assertNull(unknown.currencies)
        assertFalse(unknown.acceptsEveryCurrency)
        assertEquals(MostroEscrow.LIGHTNING, unknown.escrow)
        assertFalse(unknown.inMaintenance)
        assertNull(unknown.feePercent)
    }

    @Test
    fun longListsAreCutWithACountOfTheRest() {
        assertEquals(listOf("A", "B") to 0, listOf("A", "B").takeWithOverflow(3))
        assertEquals(listOf("A", "B") to 2, listOf("A", "B", "C", "D").takeWithOverflow(2))
    }
}
