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

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedWith
import com.vitorpamplona.amethyst.commons.tor.MoneyOpRelayRouting
import com.vitorpamplona.quartz.experimental.clink.client.OfferClient
import com.vitorpamplona.quartz.experimental.clink.offers.OfferEvent
import com.vitorpamplona.quartz.experimental.clink.offers.OfferResponse
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives the CLINK Offers payer round-trip: publishes a kind-21001 request to the
 * offer's relays and waits for the service's encrypted reply, returning the decrypted
 * [OfferResponse] (an invoice or an error). UI then hands a successful `bolt11` to the
 * existing pay path (e.g. `payViaIntent`).
 *
 * Consume-only: Amethyst never answers offer requests, it only asks.
 */
object ClinkOfferPayer {
    const val DEFAULT_TIMEOUT_MS = 30_000L

    /**
     * @param amountSats overrides the pointer's embedded price (required for spontaneous offers).
     * @return the decrypted response, or null if no reply arrived before [timeoutMs] (or the
     *   pointer carried no relay to reach).
     */
    suspend fun requestInvoice(
        account: Account,
        moneyOpRelays: MoneyOpRelayRouting,
        offer: NOffer,
        amountSats: Long? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): OfferResponse? {
        val relays = offer.relays.toSet()
        if (relays.isEmpty()) return null

        // Keep the round-trip off the Main thread: the ephemeral keygen, JSON serialization,
        // NIP-44 encryption and signing are CPU/crypto-heavy, and callers reach this from a
        // Compose (Main) scope. StrictMode flags any of it running on the UI thread.
        return withContext(Dispatchers.IO) {
            // Sign the request with a fresh throwaway key, like the reference SDK/Zeus/Stacker
            // News do: an offer round-trip is self-contained (the reply is NIP-44'd back to this
            // key and decrypted with it), so there is no reason to expose the user's real identity
            // to every offer service they pay. Payer identity, when needed, travels in the request
            // body (payer_data / a signed zap request), not the transport key.
            val ephemeralSigner = NostrSignerInternal(KeyPair())
            val client = OfferClient(offer, ephemeralSigner)
            val request = client.requestInvoice(amountSats = amountSats)

            val reply = CompletableDeferred<OfferEvent>()
            // A random short id: relays cap subscription ids at 64 chars (NIP-01) and reject an
            // over-long REQ outright. The reply is matched by request id in the listener, not by subId.
            val subId = newSubId()
            val filters: Map<NormalizedRelayUrl, List<Filter>> = relays.associateWith { listOf(client.responseFilter(request.id).taggedWith(SubPurpose.WALLET, "CLINK")) }

            val listener =
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (event is OfferEvent && event.requestId() == request.id && !reply.isCompleted) {
                            reply.complete(event)
                        }
                    }
                }

            // The offer's relays are an ad-hoc payment endpoint, not a saved wallet, so register them
            // as money-operation relays for the duration of the round-trip. Otherwise account.client
            // would treat them as generic "new" relays and route them per `newRelaysViaTor`, silently
            // pushing the payment through Tor (and failing on services that block Tor exits) even when
            // the user disabled Tor for money operations. The subscribe() below triggers a reconnect, and
            // BasicRelayClient rebuilds any socket left on the now-wrong (Tor) transport onto clearnet.
            val torState = moneyOpRelays
            torState.registerMoneyOpRelays(relays)
            account.client.subscribe(subId, filters, listener)
            try {
                account.client.publish(request, relays)
                val response = withTimeoutOrNull(timeoutMs) { reply.await() } ?: return@withContext null
                // A reply that can't be decrypted/parsed (corrupt ciphertext, malformed JSON
                // from a buggy or hostile relay) is treated as no usable response rather than
                // thrown — callers only handle null, and an uncaught decode error would hang
                // the UI (the Pay button stuck on "Requesting…").
                try {
                    client.parseResponse(response)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                }
            } finally {
                account.client.unsubscribe(subId)
                torState.unregisterMoneyOpRelays(relays)
            }
        }
    }
}
