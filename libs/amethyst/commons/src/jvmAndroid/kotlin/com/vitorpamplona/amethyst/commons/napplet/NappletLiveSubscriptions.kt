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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The registry of live relay subscriptions applets have open, keyed by the requesting surface's owner
 * key [K] (Android's reply `Messenger`, a desktop window) plus the applet's own `subId`. One broker serves every surface, and each page numbers its
 * subs from scratch (`s0`, `s1`, …), so the `subId` alone would let one tab's REQ replace — or its
 * `relay.close` kill — another tab's feed. Each entry
 * holds the exact [INostrClient] that opened it, so teardown unsubscribes from the right account
 * even after an account switch, plus an EOSE latch so a multi-relay subscription emits a single
 * `relay.eose`. Encodes the `relay.event`/`relay.eose`/`relay.closed` pushes and hands them to the
 * caller-supplied sink — it never touches the transport itself.
 *
 * The account is supplied per [open] by the caller, which resolves it from the requesting surface's
 * LAUNCH account — not from whoever is signed in at the time. A full-screen surface survives an
 * account switch, and reading live would have pointed its REQs at the new account's relays while its
 * signatures still came from the old one. [open] is reached only after the broker authorized the
 * subscription (RELAY consent).
 */
class NappletLiveSubscriptions<K : Any>(
    private val scope: CoroutineScope,
    private val attendance: NappletAttendance<K>,
) {
    private data class Key<K>(
        val owner: K,
        val subId: String,
    )

    private val liveSubs = ConcurrentHashMap<Key<K>, LiveSub>()
    private val liveSeq = AtomicInteger(0)

    // Requests are handled concurrently, and a subscribe waits on consent while a close is answered at
    // once: a page's `subscribe s1` then `close s1` could open s1 after its close, streaming to a page
    // that thinks it closed. Each request is stamped as it arrives ([arrival]); a close remembers its
    // stamp, and a subscribe that arrived before it never opens. Guarded by [lock], with [gone]: owners
    // whose surface went away, so nothing opens for them after [closeAllFor] (held weakly).
    private val lock = Any()
    private val arrivals = AtomicLong(0)
    private val closedAt = LinkedHashMap<Key<K>, Long>()
    private val gone: MutableSet<K> = Collections.newSetFromMap(WeakHashMap())

    /** Stamps a request as it arrives, before anything about it suspends; pass it to [open] and [close]. */
    fun arrival(): Long = arrivals.incrementAndGet()

    private class LiveSub(
        val clientSubId: String,
        val client: INostrClient,
    ) {
        val eoseSent = AtomicBoolean(false)
        val deliveries = Channel<Delivery>(Channel.UNLIMITED)
        var deliveryJob: Job? = null

        // Encrypted events that arrived while nobody was looking at the page, still encrypted: they are
        // decrypted and delivered when it is attended again. Touched only by the delivery coroutine.
        val heldEncrypted = ArrayDeque<Delivery.RelayEvent>()
    }

    private sealed interface Delivery {
        data class RelayEvent(
            val event: Event,
            val relay: NormalizedRelayUrl,
        ) : Delivery

        data object Eose : Delivery

        // The page is being looked at again: deliver what was held.
        data object Attended : Delivery

        data class Closed(
            val reason: String,
        ) : Delivery
    }

    /**
     * Opens [owner]'s live relay subscription [nappletSubId], streaming `relay.event`/`relay.eose`/
     * `relay.closed` envelopes to [push] as events arrive. Replaces any existing subscription [owner] has
     * under the same id. With no account/relays/filters it pushes a single empty EOSE to close it.
     *
     * An [outbox] subscription (NAP-OUTBOX) reads [relays], the ones the broker routed it to, and
     * streams `outbox.event` (with the relay each event came from) and `outbox.closed` instead; it has
     * no end-of-stored-events message.
     */
    fun open(
        owner: K,
        nappletSubId: String,
        filters: List<Filter>,
        account: Account?,
        arrivedAt: Long,
        relays: Set<NormalizedRelayUrl>? = null,
        outbox: Boolean = false,
        mayDecrypt: suspend (peer: HexKey) -> Boolean = { false },
        push: (String) -> Unit,
    ): Unit =
        synchronized(lock) {
            // Closed after this request arrived, or its surface is gone: the page doesn't want it.
            if (owner in gone || (closedAt[Key(owner, nappletSubId)] ?: Long.MIN_VALUE) > arrivedAt) return
            openLocked(owner, nappletSubId, filters, account, relays, outbox, mayDecrypt, push)
        }

    private fun openLocked(
        owner: K,
        nappletSubId: String,
        filters: List<Filter>,
        account: Account?,
        routedRelays: Set<NormalizedRelayUrl>?,
        outbox: Boolean,
        mayDecrypt: suspend (peer: HexKey) -> Boolean,
        push: (String) -> Unit,
    ) {
        val relays = routedRelays ?: account?.homeRelays?.flow?.value ?: emptySet()
        if (account == null || filters.isEmpty() || relays.isEmpty()) {
            push(if (outbox) NappletProtocolJson.encodeOutboxClosed(nappletSubId, "no relays") else NappletProtocolJson.encodeRelayEose(nappletSubId))
            return
        }

        fun eventPush(
            event: Event,
            relay: NormalizedRelayUrl,
        ) = if (outbox) NappletProtocolJson.encodeOutboxEvent(nappletSubId, event, listOf(relay.url)) else NappletProtocolJson.encodeRelayEvent(nappletSubId, event)

        val key = Key(owner, nappletSubId)
        liveSubs.remove(key)?.let(::stop)
        // Each outbox subscription can reach dozens of relays the napplet picked: a page can't keep
        // opening them.
        if (outbox && liveSubs.keys.count { it.owner == owner } >= MAX_OUTBOX_SUBS) {
            push(NappletProtocolJson.encodeOutboxClosed(nappletSubId, "too many subscriptions"))
            return
        }
        // Every relay subscription is a REQ on the user's own relay connections: a page that opened
        // thousands would push those relays past their limits and starve the app's own feeds.
        if (!outbox && liveSubs.keys.count { it.owner == owner } >= MAX_RELAY_SUBS) {
            push(NappletProtocolJson.encodeRelayClosed(nappletSubId, "too many subscriptions"))
            return
        }
        // liveSeq guarantees a unique client subId, so a rapid re-open of the same applet subId
        // can't collide with the subscription it's replacing.
        val sub =
            LiveSub(
                "napplet-$nappletSubId-${liveSeq.incrementAndGet()}",
                // The napplet writes its own filters, so they arrive untagged.
                account.client.taggedAs(SubPurpose.ADD_ONS, "Napplet"),
            )
        liveSubs[key] = sub
        sub.deliveryJob =
            scope.launch {
                // Outbox events seen already: each relay sends its own copy, and only the first is news.
                val seen = if (outbox) LinkedHashSet<String>() else null
                for (delivery in sub.deliveries) {
                    if (liveSubs[key] !== sub) break
                    when (delivery) {
                        is Delivery.RelayEvent -> {
                            val event = delivery.event
                            if (seen != null) {
                                // NAP-OUTBOX hands over events as signed, never decrypted (a decrypted one
                                // no longer matches its signature), and only once they verify: the
                                // relays may be ones the napplet named.
                                if (event.id in seen || !event.verify()) continue
                                seen += event.id
                                if (seen.size > MAX_SEEN) seen.remove(seen.first())
                                push(eventPush(event, delivery.relay))
                            } else if (NappletRelayCleartext.isEncrypted(event) && !attendance.isAttended(owner)) {
                                // Don't decrypt for a page nobody is watching: keep it (bounded) for later.
                                if (sub.heldEncrypted.size >= MAX_HELD_ENCRYPTED) sub.heldEncrypted.removeFirst()
                                sub.heldEncrypted.addLast(delivery)
                            } else {
                                NappletRelayCleartext.forDelivery(event, account.signer, mayDecrypt)?.let {
                                    push(eventPush(it, delivery.relay))
                                }
                            }
                        }
                        Delivery.Attended ->
                            while (sub.heldEncrypted.isNotEmpty() && attendance.isAttended(owner)) {
                                val held = sub.heldEncrypted.removeFirst()
                                NappletRelayCleartext.forDelivery(held.event, account.signer, mayDecrypt)?.let {
                                    push(eventPush(it, held.relay))
                                }
                            }
                        Delivery.Eose -> if (!outbox) push(NappletProtocolJson.encodeRelayEose(nappletSubId))
                        is Delivery.Closed ->
                            if (outbox) {
                                // Every relay refused it: the napplet hears it ended, and it ends here too.
                                push(NappletProtocolJson.encodeOutboxClosed(nappletSubId, delivery.reason))
                                synchronized(lock) { liveSubs.remove(key, sub) }
                                sub.deliveries.close()
                                runCatching { sub.client.unsubscribe(sub.clientSubId) }
                                break
                            } else {
                                push(NappletProtocolJson.encodeRelayClosed(nappletSubId, delivery.reason))
                            }
                    }
                }
            }

        // The relays that closed this subscription: an outbox one ends only when all of them have.
        val closedBy = ConcurrentHashMap.newKeySet<NormalizedRelayUrl>()

        val listener =
            object : SubscriptionListener {
                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    sub.deliveries.trySend(Delivery.RelayEvent(event, relay))
                }

                // A subscription fans out to several relays; collapse their EOSEs into the single
                // relay.eose the SDK expects (fired when the first relay finishes its stored events).
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (sub.eoseSent.compareAndSet(false, true)) sub.deliveries.trySend(Delivery.Eose)
                }

                override fun onClosed(
                    message: String,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (!outbox || (closedBy.add(relay) && closedBy.size >= relays.size)) sub.deliveries.trySend(Delivery.Closed(message))
                }
            }

        runCatching { sub.client.subscribe(sub.clientSubId, relays.associateWith { filters }, listener) }
    }

    /** [owner] is being looked at again: its subscriptions deliver the encrypted events they held. */
    fun onAttended(owner: K) {
        liveSubs.forEach { (key, sub) -> if (key.owner == owner) sub.deliveries.trySend(Delivery.Attended) }
    }

    /**
     * Stops [owner]'s live subscription [nappletSubId], unsubscribing from the client that opened it,
     * and keeps one that was requested before this close ([arrivedAt]) from opening later.
     */
    fun close(
        owner: K,
        nappletSubId: String,
        arrivedAt: Long,
    ): Unit =
        synchronized(lock) {
            val key = Key(owner, nappletSubId)
            closedAt[key] = maxOf(closedAt.remove(key) ?: Long.MIN_VALUE, arrivedAt)
            // Remembered only to beat a subscribe still waiting on consent: past the cap the oldest go,
            // so a page closing ids it never opened can't grow this without bound.
            if (closedAt.size > MAX_CLOSED_REMEMBERED) {
                closedAt.keys.iterator().let {
                    it.next()
                    it.remove()
                }
            }
            liveSubs.remove(key)?.let(::stop)
        }

    /**
     * Stops every subscription [owner] still has open (its surface went away without closing them),
     * and any it requested that hasn't opened yet.
     */
    fun closeAllFor(owner: K): Unit =
        synchronized(lock) {
            gone += owner
            closedAt.keys.removeAll { it.owner == owner }
            liveSubs.keys
                .filter { it.owner == owner }
                .forEach { key -> liveSubs.remove(key)?.let(::stop) }
        }

    /** Tears down every open subscription (service teardown). */
    fun closeAll() {
        liveSubs.values.forEach(::stop)
        liveSubs.clear()
    }

    private fun stop(sub: LiveSub) {
        sub.deliveries.close()
        sub.deliveryJob?.cancel()
        runCatching { sub.client.unsubscribe(sub.clientSubId) }
    }

    private companion object {
        // Per subscription: past this, the oldest held encrypted event is dropped.
        const val MAX_HELD_ENCRYPTED = 500

        // Outbox subscriptions one surface may hold open at once.
        const val MAX_OUTBOX_SUBS = 16

        // Relay subscriptions one surface may hold open at once.
        const val MAX_RELAY_SUBS = 64

        // Closed subscription ids remembered (see [closedAt]).
        const val MAX_CLOSED_REMEMBERED = 1_024

        // Event ids an outbox subscription remembers to drop duplicates.
        const val MAX_SEEN = 5_000
    }
}
