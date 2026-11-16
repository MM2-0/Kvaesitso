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
package com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

// This allows multiple screen to be listening to tags, even the same tag.
// The subscription filter carries `#e: [request id]` plus `#p: [client pubkey]`.
// The `#p` field is the routing key for purpose-built NWC relays (e.g.
// relay.getalby.com/v1) — dropping it caused those relays to silently never
// deliver the response. `authors` is intentionally left out because some
// wallets sign responses with a key different from the URI-advertised one.
@Stable
class NWCPaymentQueryState(
    val toUserHex: HexKey,
    val replyingToHex: HexKey,
    val relay: NormalizedRelayUrl,
)

@Stable
class NWCPaymentFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<NWCPaymentQueryState>() {
    val group =
        listOf(
            NWCPaymentWatcherSubAssembler(client, ::allKeys),
        )

    // Trailing-edge debounce: after a response arrives we wait this long
    // before actually removing the request id from the subscription filter.
    // If more responses (or a fresh subscribe of any query) arrive within
    // the window they coalesce into ONE relay-side filter update. This
    // matters because some NWC relays (e.g. relay-nwc.rizful.com) replay
    // cached kind-23195 events every time the REQ filter changes — so each
    // unbatched filter mutation triggers a fresh replay storm.
    private val unsubDelayMs = 1500L
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingLock = KmpLock()
    private val pendingUnsubs = mutableSetOf<NWCPaymentQueryState>()

    @Volatile private var pendingJob: Job? = null

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun invalidateKeys() = invalidateFilters()

    /**
     * Synchronously sends the REQ frame to the relay, bypassing the 500ms
     * BundledUpdate debounce. Used for NIP-47 RPC where the response is an
     * ephemeral event (kind 23195) and the subscription must be active on the
     * relay before we publish the request event — otherwise the relay drops
     * the response with no replay.
     *
     * Also drains any pending unsubscribes synchronously so the relay sees
     * a direct {old} → {new} transition instead of {old} → {old, new} → {new}.
     * The intermediate `{old, new}` state is the trigger that makes Rizful's
     * relay replay cached responses for `old`.
     */
    fun subscribeAndFlush(query: NWCPaymentQueryState) {
        drainPendingUnsubs()
        subscribe(query)
        group.forEach { it.forceInvalidate() }
    }

    /**
     * Removes [query] from the active subscription after a short debounce.
     * Repeated calls within the window coalesce into a single batch
     * unsubscribe. Idempotent: calling with a query that's already been
     * unsubscribed is a no-op.
     */
    fun unsubscribeSoon(query: NWCPaymentQueryState) {
        val oldJob: Job? =
            pendingLock.withLock {
                pendingUnsubs.add(query)
                val previous = pendingJob
                pendingJob =
                    scope.launch {
                        try {
                            delay(unsubDelayMs)
                            val batch =
                                pendingLock.withLock {
                                    val copy = pendingUnsubs.toList()
                                    pendingUnsubs.clear()
                                    pendingJob = null
                                    copy
                                }
                            if (batch.isNotEmpty()) {
                                unsubscribe(batch)
                            }
                        } catch (_: CancellationException) {
                            // Superseded by a newer schedule; the replacement
                            // job owns the pending set from here.
                        }
                    }
                previous
            }
        oldJob?.cancel()
    }

    private fun drainPendingUnsubs() {
        val (batch, cancelled) =
            pendingLock.withLock {
                val copy = pendingUnsubs.toList()
                pendingUnsubs.clear()
                val old = pendingJob
                pendingJob = null
                copy to old
            }
        cancelled?.cancel()
        if (batch.isNotEmpty()) {
            unsubscribe(batch)
        }
    }

    override fun destroy() {
        scope.cancel()
        group.forEach { it.destroy() }
    }
}
