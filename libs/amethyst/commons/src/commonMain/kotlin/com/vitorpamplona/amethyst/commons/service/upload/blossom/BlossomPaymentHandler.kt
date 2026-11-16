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

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentProof
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentRequired
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.toLongValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Settles a BUD-07 [BlossomPaymentRequired] challenge so a blocked upload/mirror can
 * be retried. Reuses the account's existing NIP-47 (Nostr Wallet Connect) lightning
 * path — this handler never touches keys or moves funds itself, it only asks the
 * user's connected wallet to pay the invoice and returns the resulting preimage as
 * the [BlossomPaymentProof].
 *
 * Cashu-only servers (`X-Cashu`, no `X-Lightning`) are not yet supported here; the
 * caller should surface that a lightning wallet is required.
 */
object BlossomPaymentHandler {
    /**
     * Hard ceiling on a single BUD-07 charge, in sats.
     *
     * BUD-07 charges are per-blob storage fees: real paid Blossom servers ask
     * single-digit to low-hundreds of sats for a media upload. 10,000 sats is
     * roughly USD 10 at a 100k BTC — one to two orders of magnitude above any
     * legitimate per-blob fee, so it never gets in a real user's way, while
     * capping what a hostile or compromised server can drain in one prompt.
     * Anything above this is refused outright rather than shown to the user,
     * because the value is server-chosen and a user tapping through a dialog is
     * not a meaningful defence against a four-digit-sat surprise.
     */
    const val MAX_PAYMENT_SATS = 10_000L

    /** Outcome of [pay]. Everything except [Paid] means no proof and no retry. */
    sealed interface PayResult {
        data class Paid(
            val proof: BlossomPaymentProof,
        ) : PayResult

        /** The amount failed [checkAmount]; [reason] is user-facing. */
        data class Refused(
            val reason: String,
        ) : PayResult

        /** No invoice, no wallet, or the wallet request could not be sent. */
        data object Unavailable : PayResult

        /** The wallet never answered. The invoice stays blocked — see [InFlightInvoices]. */
        data object TimedOut : PayResult
    }

    /** Verdict on the invoice amount, before any money moves. */
    sealed interface AmountCheck {
        data class Ok(
            val sats: Long,
        ) : AmountCheck

        /** BOLT-11 with no amount: the payee picks it. Never payable unattended. */
        data object Amountless : AmountCheck

        data class OverCap(
            val sats: Long,
        ) : AmountCheck

        /** The invoice asks for something other than what the dialog told the user. */
        data class Mismatch(
            val shownSats: Long?,
            val actualSats: Long,
        ) : AmountCheck
    }

    /**
     * Re-derives the amount from the invoice itself and checks it against both the
     * cap and [shownSats] — the number the confirmation dialog put in front of the
     * user. The amount shown must be the amount paid, so a server that swapped the
     * invoice (or leaned on a misleading `X-Reason`) cannot get a different sum
     * approved than the one the user agreed to.
     */
    fun checkAmount(
        payment: BlossomPaymentRequired,
        shownSats: Long?,
    ): AmountCheck {
        val actual = amountSats(payment) ?: return AmountCheck.Amountless
        if (actual > MAX_PAYMENT_SATS) return AmountCheck.OverCap(actual)
        if (shownSats != actual) return AmountCheck.Mismatch(shownSats, actual)
        return AmountCheck.Ok(actual)
    }

    /** Human-readable refusal text for a non-[AmountCheck.Ok] verdict. */
    fun refusalReason(check: AmountCheck): String =
        when (check) {
            is AmountCheck.Ok -> ""
            is AmountCheck.Amountless -> "The server's invoice does not state an amount. Amethyst will not pay it."
            is AmountCheck.OverCap -> "The server asked for ${check.sats} sats, above the $MAX_PAYMENT_SATS sat limit for a media-server payment. Nothing was paid."
            is AmountCheck.Mismatch ->
                "The server's invoice is for ${check.actualSats} sats, not the ${check.shownSats ?: 0} sats shown. Nothing was paid."
        }

    /** True when this account has a wallet we can pay the lightning invoice with. */
    fun canPay(
        account: Account,
        payment: BlossomPaymentRequired,
    ): Boolean = payment.lightning != null && account.nip47SignerState.hasWalletConnectSetup()

    /**
     * The invoice amount in sats for display in a confirmation prompt, or null when it is absent or
     * unreadable. `getAmountInSats` returns ZERO for an amountless BOLT11 rather than null, so a bare
     * read renders "Pay 0 sats" — telling the user a payment is free when the amount is actually
     * unspecified and chosen by the payee.
     */
    fun amountSats(payment: BlossomPaymentRequired): Long? =
        payment.lightning?.let {
            runCatching { LnInvoiceUtil.getAmountInSats(it).toLongValue() }.getOrNull()?.takeIf { sats -> sats > 0 }
        }

    /**
     * Pays the challenge's BOLT-11 invoice via NWC and returns the proof.
     *
     * [shownSats] is what the confirmation dialog displayed; the invoice is
     * re-read here and must match it and sit under [MAX_PAYMENT_SATS], otherwise
     * nothing is sent to the wallet at all.
     */
    suspend fun pay(
        account: Account,
        payment: BlossomPaymentRequired,
        shownSats: Long?,
    ): PayResult {
        val invoice = payment.lightning ?: return PayResult.Unavailable
        if (!account.nip47SignerState.hasWalletConnectSetup()) return PayResult.Unavailable

        val check = checkAmount(payment, shownSats)
        if (check !is AmountCheck.Ok) {
            val reason = refusalReason(check)
            Log.w("BlossomPayment") { "refusing invoice: $reason" }
            return PayResult.Refused(reason)
        }

        // Never send the same invoice twice: an earlier attempt may still settle.
        if (!InFlightInvoices.tryClaim(invoice)) {
            return PayResult.Refused(
                "A payment for this invoice was already sent to your wallet and never confirmed. Amethyst will not pay it again.",
            )
        }

        val preimageResult = CompletableDeferred<String?>()
        try {
            account.zaps.sendZapPaymentRequestFor(invoice, null) { response ->
                // CompletableDeferred.complete is idempotent, so extra callbacks are harmless.
                preimageResult.complete((response as? PayInvoiceSuccessResponse)?.result?.preimage)
            }
        } catch (e: Exception) {
            Log.w("BlossomPayment", "Failed to send NWC payment request", e)
            // The request never left, so the invoice is definitively not in flight.
            InFlightInvoices.release(invoice)
            return PayResult.Unavailable
        }

        // NIP-47 offers no cancel for an outstanding pay_invoice, so a timeout
        // cannot stop the payment — it can only stop us from sending it again.
        // Deliberately do NOT release the claim on the timeout path.
        val answered = withTimeoutOrNull(PAYMENT_TIMEOUT_MS) { preimageResult.await() }
        if (answered == null && !preimageResult.isCompleted) return PayResult.TimedOut

        InFlightInvoices.release(invoice)
        val preimage = answered ?: return PayResult.Unavailable
        return PayResult.Paid(BlossomPaymentProof(lightningPreimage = preimage))
    }

    /**
     * How long we wait for the wallet. Matches the previous behaviour; note the
     * NIP-47 filter itself is dropped after 60s, so a reply past 90s cannot reach
     * us anyway — which is exactly why the invoice stays blocked afterwards.
     */
    private const val PAYMENT_TIMEOUT_MS = 90_000L
}
