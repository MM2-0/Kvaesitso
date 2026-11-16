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
package com.vitorpamplona.amethyst.commons.service

import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorCode

/**
 * Decides whether a BOLT12 zap the wallet refused should be re-sent as a BOLT11 zap.
 *
 * Only ever consulted for a NIP-47 *error* reply. An allowlist, because not every
 * error means no money moved: NIP-47 defines `PAYMENT_FAILED` as "may be due to a
 * timeout, exhausting all routes, insufficient capacity or similar", and a wallet
 * that gave up on a payment whose HTLC is still in flight can see it settle later.
 * Retrying on that, or on the catch-all `INTERNAL` / `OTHER` / no-code replies,
 * could pay the recipient twice. Only refusals the wallet raises *before* it
 * attempts a payment qualify — the offer could not be resolved or has expired, the
 * request was rejected as malformed, or our wallet does not handle `lno` at all.
 * Those are the "recipient's configuration is stale" cases the fallback exists for.
 * Refusals about our own wallet (balance, quota, permissions) are out too: BOLT11
 * through the same wallet would fail identically and only add a second error.
 */
object Bolt12LightningFallback {
    /** Refusals raised before any payment attempt, about the offer or the instruction. */
    private val offerSideCodes =
        setOf(
            NwcErrorCode.EXPIRED,
            NwcErrorCode.NOT_FOUND,
            NwcErrorCode.BAD_REQUEST,
            NwcErrorCode.NOT_IMPLEMENTED,
            NwcErrorCode.UNSUPPORTED_PAYMENT_INSTRUCTION,
            NwcErrorCode.UNSUPPORTED_NETWORK,
        )

    /** True when a refusal with [code] (null when the wallet sent none) should be retried over BOLT11. */
    fun shouldRetry(code: NwcErrorCode?): Boolean = code in offerSideCodes
}
