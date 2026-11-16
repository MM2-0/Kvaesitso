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
package com.vitorpamplona.amethyst.commons.model.nip57Zaps

import com.vitorpamplona.amethyst.commons.service.lnurl.LightningAddressResolver
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent

/**
 * Handles NIP-57 zap requests and invoice fetching.
 * Shared between Android and Desktop.
 */
object ZapAction {
    /**
     * Result of a zap operation.
     */
    sealed class ZapResult {
        data class Invoice(
            val bolt11: String,
        ) : ZapResult()

        data class Error(
            val message: String,
        ) : ZapResult()
    }

    /**
     * Creates a zap request and fetches a BOLT11 invoice.
     *
     * @param targetEvent Event to zap
     * @param lnAddress Lightning address of recipient
     * @param amountSats Amount in satoshis
     * @param message Optional zap message
     * @param relays Relay hints (normalized URLs)
     * @param signer Signer for the request
     * @param resolver Lightning address resolver
     * @param zapType Type of zap (default PUBLIC)
     * @param onProgress Progress callback
     */
    suspend fun fetchZapInvoice(
        targetEvent: Event,
        lnAddress: String,
        amountSats: Long,
        message: String = "",
        relays: Set<NormalizedRelayUrl>,
        signer: NostrSigner,
        resolver: LightningAddressResolver,
        zapType: ZapReceiptEvent.ZapType = ZapReceiptEvent.ZapType.PUBLIC,
        onProgress: (Float) -> Unit = {},
    ): ZapResult {
        if (!signer.isWriteable()) {
            return ZapResult.Error("Signer is not writeable")
        }

        // Create zap request using quartz factory
        val zapRequest =
            try {
                ZapRequestEvent.create(
                    zappedEvent = targetEvent,
                    relays = relays,
                    signer = signer,
                    pollOption = null,
                    message = message,
                    zapType = zapType,
                    toUserPubHex = null,
                )
            } catch (e: Exception) {
                return ZapResult.Error("Failed to create zap request: ${e.message}")
            }

        onProgress(0.3f)

        // Fetch invoice
        val result =
            resolver.fetchInvoice(
                lnAddress = lnAddress,
                milliSats = amountSats * 1000,
                message = message,
                zapRequest = zapRequest,
                onProgress = { progress ->
                    onProgress(0.3f + progress * 0.7f)
                },
            )

        return when (result) {
            is LightningAddressResolver.Result.Success -> ZapResult.Invoice(result.invoice)
            is LightningAddressResolver.Result.Error -> ZapResult.Error(result.message)
        }
    }

    /**
     * Creates a zap request for a user profile (no event).
     */
    suspend fun fetchZapInvoiceForUser(
        userPubHex: String,
        lnAddress: String,
        amountSats: Long,
        message: String = "",
        relays: Set<NormalizedRelayUrl>,
        signer: NostrSigner,
        resolver: LightningAddressResolver,
        zapType: ZapReceiptEvent.ZapType = ZapReceiptEvent.ZapType.PUBLIC,
        onProgress: (Float) -> Unit = {},
    ): ZapResult {
        if (!signer.isWriteable()) {
            return ZapResult.Error("Signer is not writeable")
        }

        // Create zap request for user
        val zapRequest =
            try {
                ZapRequestEvent.create(
                    userHex = userPubHex,
                    relays = relays,
                    signer = signer,
                    message = message,
                    zapType = zapType,
                )
            } catch (e: Exception) {
                return ZapResult.Error("Failed to create zap request: ${e.message}")
            }

        onProgress(0.3f)

        // Fetch invoice
        val result =
            resolver.fetchInvoice(
                lnAddress = lnAddress,
                milliSats = amountSats * 1000,
                message = message,
                zapRequest = zapRequest,
                onProgress = { progress ->
                    onProgress(0.3f + progress * 0.7f)
                },
            )

        return when (result) {
            is LightningAddressResolver.Result.Success -> ZapResult.Invoice(result.invoice)
            is LightningAddressResolver.Result.Error -> ZapResult.Error(result.message)
        }
    }
}
