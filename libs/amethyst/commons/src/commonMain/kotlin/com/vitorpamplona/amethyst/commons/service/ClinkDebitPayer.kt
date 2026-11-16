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
import com.vitorpamplona.quartz.experimental.clink.client.DebitClient
import com.vitorpamplona.quartz.experimental.clink.debits.DebitEvent
import com.vitorpamplona.quartz.experimental.clink.debits.DebitFrequency
import com.vitorpamplona.quartz.experimental.clink.debits.DebitResponse
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives the CLINK Debits payer round-trips: publishes a kind-21002 request (pay an
 * invoice, or authorize a spending budget) and waits for the encrypted reply. The wallet
 * authorizes against the account's own identity (no shared secret).
 *
 * This is the CLINK-debit spend rail that the zap button / offer card route through
 * when a debit pointer is the selected default payment source. It MUST only be invoked
 * after an explicit user confirmation — a debit moves real sats.
 *
 * Consume-only: Amethyst sends debit requests, it never answers them.
 */
object ClinkDebitPayer {
    const val DEFAULT_TIMEOUT_MS = 30_000L

    /**
     * Asks the wallet to pay [bolt11].
     *
     * @return the decrypted response (`res:"ok"` with optional preimage, or a `GFY`
     *   failure), or null if no reply arrived in time or the pointer carried no relay.
     */
    suspend fun payInvoice(
        account: Account,
        moneyOpRelays: MoneyOpRelayRouting,
        pointer: NDebit,
        bolt11: String,
        amountSats: Long? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): DebitResponse? =
        // Off the Main thread: building the request signs + NIP-44 encrypts, and callers reach
        // this from Compose (Main) scopes (offer card, lightning-address row). See ClinkOfferPayer.
        withContext(Dispatchers.IO) {
            val client = clientFor(pointer, account) ?: return@withContext null
            sendAndAwait(account, moneyOpRelays, client, client.payInvoice(bolt11, amountSats), timeoutMs)
        }

    /**
     * Asks the wallet to authorize a spending budget. Omit [frequency] for a one-time
     * budget; otherwise it recurs every `frequency` (day/week/month).
     */
    suspend fun requestBudget(
        account: Account,
        moneyOpRelays: MoneyOpRelayRouting,
        pointer: NDebit,
        amountSats: Long,
        frequency: DebitFrequency? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): DebitResponse? =
        withContext(Dispatchers.IO) {
            val client = clientFor(pointer, account) ?: return@withContext null
            sendAndAwait(account, moneyOpRelays, client, client.requestBudget(amountSats, frequency), timeoutMs)
        }

    // Debits sign with the persistent account identity (unlike offer requests, which use a
    // throwaway key — see ClinkOfferPayer): the service must see one stable app identity so a
    // budget authorization can cover repeat debits instead of prompting on every payment.
    private fun clientFor(
        pointer: NDebit,
        account: Account,
    ): DebitClient? = if (pointer.relays.isEmpty()) null else DebitClient(pointer, account.signer)

    /** Publishes [request] to the pointer's relays and awaits the matching kind-21002 reply. */
    private suspend fun sendAndAwait(
        account: Account,
        moneyOpRelays: MoneyOpRelayRouting,
        client: DebitClient,
        request: DebitEvent,
        timeoutMs: Long,
    ): DebitResponse? {
        val relays = client.pointer.relays.toSet()

        val reply = CompletableDeferred<DebitEvent>()
        // A random short id: relays cap subscription ids at 64 chars (NIP-01); the reply is matched
        // by request id in the listener, not by subId.
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
                    if (event is DebitEvent && event.requestId() == request.id && !reply.isCompleted) {
                        reply.complete(event)
                    }
                }
            }

        // Saved debit wallets are already fed into the money-op relay set by AccountsTorStateConnector,
        // but register here too so a freshly-added wallet paid before that flow propagates — and any
        // non-saved debit pointer — still routes under the money-operations Tor preference rather than
        // the generic `newRelaysViaTor` policy.
        moneyOpRelays.registerMoneyOpRelays(relays)
        account.client.subscribe(subId, filters, listener)
        return try {
            account.client.publish(request, relays)
            val response = withTimeoutOrNull(timeoutMs) { reply.await() } ?: return null
            // Treat an undecryptable/malformed reply as no usable response rather than
            // throwing — callers only handle null, and an uncaught decode error would
            // leave the calling UI hung (spinner stuck, no toast, sibling zaps cancelled).
            try {
                client.parseResponse(response)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        } finally {
            account.client.unsubscribe(subId)
            moneyOpRelays.unregisterMoneyOpRelays(relays)
        }
    }
}
