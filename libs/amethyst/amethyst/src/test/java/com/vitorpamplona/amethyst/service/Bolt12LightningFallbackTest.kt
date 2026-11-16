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
package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.commons.service.Bolt12LightningFallback
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Bolt12LightningFallbackTest {
    @Test
    fun offerSideRefusalsRetryOverLightning() {
        listOf(
            NwcErrorCode.EXPIRED,
            NwcErrorCode.NOT_FOUND,
            NwcErrorCode.BAD_REQUEST,
            NwcErrorCode.NOT_IMPLEMENTED,
            NwcErrorCode.UNSUPPORTED_PAYMENT_INSTRUCTION,
            NwcErrorCode.UNSUPPORTED_NETWORK,
        ).forEach { code ->
            assertTrue("$code should fall back to BOLT11", Bolt12LightningFallback.shouldRetry(code))
        }
    }

    @Test
    fun aFailedOrAmbiguousPaymentNeverRetries() {
        // PAYMENT_FAILED "may be due to a timeout" (NIP-47): the HTLC can still settle.
        // The catch-alls and a reply with no code say nothing about whether funds moved.
        listOf(
            NwcErrorCode.PAYMENT_FAILED,
            NwcErrorCode.INTERNAL,
            NwcErrorCode.OTHER,
            null,
        ).forEach { code ->
            assertFalse("$code may already have paid; a retry could double-spend", Bolt12LightningFallback.shouldRetry(code))
        }
    }

    @Test
    fun senderSideRefusalsDoNotRetry() {
        listOf(
            NwcErrorCode.INSUFFICIENT_BALANCE,
            NwcErrorCode.QUOTA_EXCEEDED,
            NwcErrorCode.RATE_LIMITED,
            NwcErrorCode.RESTRICTED,
            NwcErrorCode.UNAUTHORIZED,
            NwcErrorCode.UNSUPPORTED_ENCRYPTION,
        ).forEach { code ->
            assertFalse("$code is about our wallet, BOLT11 would fail the same way", Bolt12LightningFallback.shouldRetry(code))
        }
    }
}
