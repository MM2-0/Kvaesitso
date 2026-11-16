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
package com.vitorpamplona.amethyst.commons.routstr

import com.vitorpamplona.amethyst.commons.frontpage.WriterFailure
import com.vitorpamplona.amethyst.commons.frontpage.writerExceptionFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Routstr's errors, in both shapes nodes send them, as the screens and the writer read them. */
class RoutstrErrorsTest {
    /** As a live node answered a key from another node, on 2026-11-13. */
    private val keyNotFound =
        """{"detail":{"error":{"message":"API key not found","type":"invalid_request_error","code":"key_not_found"}},"error":{"message":"API key not found","type":"invalid_request_error","code":"key_not_found"}}"""

    private val insufficient =
        """{"error":{"message":"Insufficient balance","type":"insufficient_balance","code":"insufficient_balance","details":{"required_msats":4307200,"available_msats":1000000,"shortfall_msats":3307200}}}"""

    @Test
    fun keyNotFoundInBothShapes() {
        assertEquals(RoutstrFailure.KEY_NOT_FOUND, RoutstrErrors.parse(401, keyNotFound).failure)
        assertEquals(RoutstrFailure.KEY_NOT_FOUND, RoutstrErrors.parse(401, """{"detail":{"error":{"code":"key_not_found"}}}""").failure)
        assertEquals(RoutstrFailure.KEY_NOT_FOUND, RoutstrErrors.parse(401, """{"error":{"code":"key_not_found"}}""").failure)
    }

    @Test
    fun insufficientBalanceWithItsShortfall() {
        val error = RoutstrErrors.parse(402, insufficient)
        assertEquals(RoutstrFailure.INSUFFICIENT_BALANCE, error.failure)
        assertEquals(4_307_200L, error.requiredMsats)
        assertEquals(1_000_000L, error.availableMsats)
        assertEquals(3_308L, error.shortfallSats)
        // Wrapped in `detail`, and with only required and available.
        val wrapped = RoutstrErrors.parse(402, """{"detail":{"error":{"code":"insufficient_balance","details":{"required_msats":2500,"available_msats":1000}}}}""")
        assertEquals(2L, wrapped.shortfallSats)
        // A bare 402 is still a balance problem, not a rejected key.
        assertEquals(RoutstrFailure.INSUFFICIENT_BALANCE, RoutstrErrors.parse(402, "").failure)
    }

    @Test
    fun theRestOfTheTable() {
        assertEquals(RoutstrFailure.UPSTREAM_UNAVAILABLE, RoutstrErrors.parse(424, """{"error":{"code":"UPSTREAM_UNAVAILABLE"}}""").failure)
        assertEquals(RoutstrFailure.UPSTREAM_UNAVAILABLE, RoutstrErrors.parse(424, "").failure)
        assertEquals(RoutstrFailure.RATE_LIMITED, RoutstrErrors.parse(429, """{"detail":{"error":{"code":"UPSTREAM_RATE_LIMIT"}}}""").failure)
        assertEquals(RoutstrFailure.PAYMENT_SETTLEMENT_FAILED, RoutstrErrors.parse(503, """{"error":{"type":"payment_settlement_failed","message":"x"}}""").failure)
        assertEquals(RoutstrFailure.HTTP, RoutstrErrors.parse(503, "Service Unavailable").failure)
        // OpenAI uses the same code: the generic "key rejected".
        assertEquals(RoutstrFailure.INVALID_KEY, RoutstrErrors.parse(401, """{"error":{"code":"invalid_api_key"}}""").failure)
        assertEquals(RoutstrFailure.KEY_EXPIRED, RoutstrErrors.parse(401, """{"error":{"code":"key_expired"}}""").failure)
        assertEquals(RoutstrFailure.NOT_FOUND, RoutstrErrors.parse(404, """{"detail":"Invoice not found"}""").failure)
        assertEquals("Invoice not found", RoutstrErrors.parse(404, """{"detail":"Invoice not found"}""").message)
        assertEquals(RoutstrFailure.TOKEN_REJECTED, RoutstrErrors.parse(400, """{"detail":"Token already spent"}""").failure)
    }

    @Test
    fun theWriterSaysWhatRoutstrMeant() {
        val low = writerExceptionFor(402, insufficient)
        assertEquals(WriterFailure.LOW_BALANCE, low.failure)
        assertEquals(3_308L, low.shortfallSats)
        assertEquals(WriterFailure.KEY_NOT_ON_NODE, writerExceptionFor(401, keyNotFound).failure)
        assertEquals(WriterFailure.UPSTREAM_DOWN, writerExceptionFor(424, """{"error":{"code":"UPSTREAM_UNAVAILABLE"}}""").failure)
        assertEquals(WriterFailure.RATE_LIMITED, writerExceptionFor(429, """{"error":{"code":"UPSTREAM_RATE_LIMIT"}}""").failure)
        assertEquals(WriterFailure.SETTLEMENT_FAILED, writerExceptionFor(503, """{"detail":{"error":{"code":"payment_settlement_failed"}}}""").failure)
        // Other providers read as before.
        assertEquals(WriterFailure.REJECTED, writerExceptionFor(401, """{"error":{"code":"invalid_api_key"}}""").failure)
        assertEquals(WriterFailure.NO_CREDIT, writerExceptionFor(402, """{"error":{"message":"out of credits"}}""").failure)
        assertNull(writerExceptionFor(402, """{"error":{"message":"out of credits"}}""").shortfallSats)
        assertEquals(WriterFailure.HTTP, writerExceptionFor(500, "boom").failure)
    }
}
