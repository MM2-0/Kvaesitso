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
package com.vitorpamplona.amethyst.commons.service.upload.blossom

import com.vitorpamplona.amethyst.commons.util.ConcurrentSet

/**
 * BOLT-11 invoices handed to the NIP-47 wallet whose fate we never learned.
 *
 * [BlossomPaymentHandler.pay] waits a bounded time for the wallet to reply, but
 * NIP-47 has no "cancel this pay_invoice": the request is fire-and-forget, so
 * giving up on the wait does **not** stop the payment. An invoice that settles a
 * second after we time out still moved the user's money — and if we then let the
 * user (or an automatic retry) send the very same invoice again, they pay twice.
 *
 * So: claim the invoice before sending it, and release the claim only when the
 * wallet gives a definitive answer. A timed-out invoice stays claimed for the
 * life of the process and can never be paid a second time from this app.
 *
 * This is the weaker half of the fix — a genuine cancel would be better, but the
 * NIP-47 client offers none, so we settle for "never silently pay it twice".
 */
object InFlightInvoices {
    private val claimed = ConcurrentSet<String>()

    /**
     * Claims [invoice] for one payment attempt. Returns false when it was already
     * claimed and never resolved — the caller must not send it again.
     */
    fun tryClaim(invoice: String): Boolean = claimed.add(invoice)

    /** The wallet gave a definitive answer (paid or explicitly failed): the claim can go. */
    fun release(invoice: String) {
        claimed.remove(invoice)
    }

    /** True when [invoice] was sent to the wallet and never resolved. */
    fun isAwaiting(invoice: String): Boolean = invoice in claimed

    /** Test-only reset. */
    internal fun clear() {
        claimed.clear()
    }
}
