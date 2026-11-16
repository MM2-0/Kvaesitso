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
package com.vitorpamplona.amethyst.commons.service.cashu.melt

import com.vitorpamplona.amethyst.commons.cashu.melt.MeltResult
import com.vitorpamplona.amethyst.commons.notices.CashuRedeemNotice
import com.vitorpamplona.amethyst.commons.service.lnurl.LightningInvoiceResolver
import com.vitorpamplona.amethyst.commons.service.lnurl.LnurlHttpTransport
import com.vitorpamplona.quartz.nip60Cashu.mintApi.CashuMintOperations
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpClient
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpTransport
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintUrlException
import com.vitorpamplona.quartz.nip60Cashu.token.CashuToken
import kotlin.coroutines.cancellation.CancellationException

/**
 * Redeems an externally-received cashu token straight to a Lightning address
 * via NUT-05 melt (`POST /v1/melt/quote/bolt11` + `POST /v1/melt/bolt11`).
 *
 * This is the "Redeem" button on a received cashu token preview — distinct
 * from the NIP-60 wallet's own send-LN flow ([com.vitorpamplona.amethyst.commons.cashu.ops.CashuWalletOps.meltToLightning]),
 * which spends the user's stored proofs and rolls change back into the wallet.
 * Here there is no wallet: the proofs come straight off the pasted token and
 * any leftover stays with the mint.
 *
 * Migrated off the deprecated pre-v1 `/melt` + `/checkfees` endpoints (gone on
 * CDK and other modern mints) onto the same NUT-05 client the wallet uses, so
 * it also picks up NUT-02 per-input fee handling for free.
 */
class MeltProcessor {
    /**
     * @param knownWalletMints the mint URLs of the user's own NIP-60 wallet. A token
     *   pointing at one of those was, by definition, issued by a mint the user
     *   deliberately added, so it is exempt from the private-address block that
     *   [com.vitorpamplona.quartz.nip60Cashu.mintApi.CashuMintUrlValidator] applies
     *   to arbitrary pasted tokens (a self-hosted mint on the LAN is legitimate).
     */
    suspend fun melt(
        token: CashuToken,
        lud16: String,
        mintTransport: MintHttpTransport,
        lnurl: LnurlHttpTransport,
        knownWalletMints: Set<String> = emptySet(),
    ): MeltResult {
        try {
            val isOwnMint = knownWalletMints.any { it.trim().trimEnd('/').equals(token.mint.trim().trimEnd('/'), ignoreCase = true) }
            val ops = CashuMintOperations(MintHttpClient(token.mint, userConfigured = isOwnMint, transport = mintTransport))
            val proofs = token.proofs

            // A Lightning address must commit to an amount before we know the
            // fees, so probe with an invoice for the full token value to learn
            // the LN fee_reserve, then add the NUT-02 input fee the mint
            // charges on these proofs.
            val probeInvoice =
                LightningInvoiceResolver(lnurl).lnAddressInvoice(
                    lnAddress = lud16,
                    milliSats = token.totalAmount * 1000,
                    message = "Calculate Fees for Cashu",
                    onProgress = {},
                )
            val probeQuote = ops.requestMeltQuote(probeInvoice)
            val fees = probeQuote.feeReserve + ops.inputFeeFor(proofs)

            val sendable = token.totalAmount - fees
            if (sendable <= 0) {
                throw LightningInvoiceResolver.LightningAddressError(
                    CashuRedeemNotice.Failed("Token value ${token.totalAmount} does not cover fees $fees"),
                )
            }

            // Real invoice for (total − fees), quote it, then melt without
            // requesting change — there is no wallet to hold leftover proofs,
            // so the unused fee_reserve stays with the mint.
            val invoice =
                LightningInvoiceResolver(lnurl).lnAddressInvoice(
                    lnAddress = lud16,
                    milliSats = sendable * 1000,
                    message = "Redeem Cashu",
                    onProgress = {},
                )
            val quote = ops.requestMeltQuote(invoice)
            ops.meltProofs(quote, proofs, requestChange = false)

            return MeltResult(
                token = token,
                invoice = invoice,
                fees = fees.toInt(),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is LightningInvoiceResolver.LightningAddressError) throw e
            // The mint URL was refused before any request went out: this is OUR
            // message, not the mint's, so don't dress it up as "the mint said".
            if (e is MintUrlException) {
                throw LightningInvoiceResolver.LightningAddressError(CashuRedeemNotice.UnsafeMintUrl(e.message))
            }
            throw LightningInvoiceResolver.LightningAddressError(CashuRedeemNotice.Failed(e.message))
        }
    }
}
