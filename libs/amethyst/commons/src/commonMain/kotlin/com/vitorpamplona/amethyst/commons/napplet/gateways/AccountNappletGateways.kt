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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.NappletOutboxGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletRelayGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletWalletGateway
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * The napplet gateways an [Account] alone can back, shared by every host: publishing and querying
 * through the account's relays, and paying through its NWC wallet. Platform pieces (consent UI,
 * notifications, uploads) stay with each host.
 */
class AccountNappletRelayGateway(
    private val account: Account,
) : NappletRelayGateway {
    override suspend fun publish(event: Event): List<String> {
        val relays = account.computeRelayListToBroadcast(event)
        account.client.publish(event, relays)
        return relays.map { it.url }
    }

    /**
     * Bounded live relay fetch (EOSE/timeout) for all [filters], merged with the local cache, newest-first,
     * as relays signed them: what the app may read of encrypted content is the broker's call
     * ([com.vitorpamplona.amethyst.commons.napplet.NappletBroker.mayDecrypt]).
     *
     * Only events whose signature verifies leave: the cache also holds what never was on a relay, the
     * unsigned rumors unwrapped from the user's NIP-17 gift wraps, and those are private messages.
     */
    override suspend fun query(filters: List<Filter>): List<Event> {
        if (filters.isEmpty()) return emptyList()
        val relays = account.homeRelays.flow.value
        val fromRelays =
            if (relays.isEmpty()) {
                emptyList()
            } else {
                runCatchingCancellable {
                    account.client.taggedAs(SubPurpose.ADD_ONS, "Napplet").fetchAll(filters = relays.associateWith { filters }, idleTimeoutMs = QUERY_TIMEOUT.inWholeMilliseconds)
                }.getOrDefault(emptyList())
            }
        val fromCache = filters.flatMap { filter -> account.cache.filter(filter).mapNotNull { it.event } }
        return merged(fromRelays + fromCache, filters)
    }

    companion object {
        /** Newest-first, one copy of each, only signed events, at most the filters' limit (and never past [MAX_RESULTS]). */
        fun merged(
            events: List<Event>,
            filters: List<Filter>,
        ): List<Event> {
            val limit = (filters.mapNotNull { it.limit }.maxOrNull() ?: MAX_RESULTS).coerceIn(0, MAX_RESULTS)
            return events
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt }
                .asSequence()
                .filter { it.verify() }
                .take(limit)
                .toList()
        }

        const val MAX_RESULTS = NappletOutboxGateway.MAX_RESULTS

        private val QUERY_TIMEOUT = 8.seconds
    }
}

/**
 * Pays an invoice through the account's connected NWC wallet, returning the preimage on success.
 * Throws (→ `Failed`) when no wallet is connected, the wallet reports an error, or it does not
 * respond in time — so the applet never silently believes a payment succeeded.
 */
class AccountNappletWalletGateway(
    private val account: Account,
) : NappletWalletGateway {
    override suspend fun payInvoice(invoice: String): String? {
        if (account.nip47SignerState.defaultWalletUri.value == null) {
            throw IllegalStateException("No Lightning wallet is connected.")
        }
        val result = CompletableDeferred<String?>()
        account.zaps.sendZapPaymentRequestFor(invoice, null) { response ->
            when (response) {
                is PayInvoiceSuccessResponse -> result.complete(response.result?.preimage)
                is PayInvoiceErrorResponse -> result.completeExceptionally(RuntimeException(response.error?.message ?: "Payment failed."))
                is NwcErrorResponse -> result.completeExceptionally(RuntimeException(response.error?.message ?: "Wallet error."))
                else -> result.completeExceptionally(RuntimeException("Unexpected wallet response."))
            }
        }
        // A timeout is not a cancellation of the caller: it is "no answer", which may still pay.
        val answer = withTimeoutOrNull(WALLET_TIMEOUT) { Answered(result.await()) } ?: throw WalletNoAnswerException()
        return answer.preimage
    }

    private class Answered(
        val preimage: String?,
    )

    private companion object {
        val WALLET_TIMEOUT = 60.seconds
    }
}

/**
 * The NWC wallet didn't answer a pay request in time. It may still pay: the caller must not
 * offer to pay the same invoice again, only to check it.
 */
class WalletNoAnswerException : Exception("The wallet didn't answer in time; it may still pay.")
