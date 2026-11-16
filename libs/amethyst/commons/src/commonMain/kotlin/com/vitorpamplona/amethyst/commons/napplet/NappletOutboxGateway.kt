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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/** An event an outbox read returned, with the relays it was seen on (NAP-RELAY's `sidecar.relayHints`). */
data class OutboxEvent(
    val event: Event,
    val relayHints: List<String> = emptyList(),
)

/** What an outbox read returned; [incomplete] when some relay lists or relays did not answer. */
data class OutboxRead(
    val events: List<OutboxEvent>,
    val incomplete: Boolean = false,
)

/** Where an outbox publish went: each relay's answer, and the [toInboxes] authors whose read relays are unknown. */
data class OutboxDelivery(
    val relays: Map<NormalizedRelayUrl, Boolean>,
    val unresolvedInboxes: List<HexKey> = emptyList(),
)

/** The relays the shell would use for some authors (NAP-OUTBOX's `OutboxRelayPlan`). */
data class OutboxRelayPlan(
    val relays: List<String>,
    val source: Source,
    val missingAuthors: List<HexKey> = emptyList(),
) {
    enum class Source(
        val wire: String,
    ) {
        NIP65("nip65"),
        CACHE("cache"),
        POLICY("policy"),
        FALLBACK("fallback"),
    }
}

/**
 * Bridges the broker to NIP-65 relay routing for the [NappletCapability.OUTBOX] capability: the
 * shell, never the napplet, finds each author's relays, reads them, and fans a publish out. Relay
 * hints a napplet passes are checked here (no private networks); an implementation verifies every
 * event before returning it, and hands it over as signed: never decrypted, never a rumor. A `null` gateway makes the broker answer outbox requests with
 * [com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse.Unsupported].
 */
interface NappletOutboxGateway {
    suspend fun getEvent(
        eventId: HexKey,
        author: HexKey?,
        relayHints: List<String>,
        timeoutMs: Long?,
    ): OutboxRead

    suspend fun query(
        filters: List<Filter>,
        authors: List<HexKey>,
        relayHints: List<String>,
        timeoutMs: Long?,
    ): OutboxRead

    /** The relays a live subscription to [filters] should hold open. */
    suspend fun subscriptionRelays(
        filters: List<Filter>,
        authors: List<HexKey>,
        relayHints: List<String>,
    ): Set<NormalizedRelayUrl>

    suspend fun publish(
        event: Event,
        toOutbox: Boolean,
        toInboxes: List<HexKey>,
        relays: List<String>,
    ): OutboxDelivery

    suspend fun plan(
        authors: List<HexKey>,
        write: Boolean,
    ): OutboxRelayPlan

    companion object {
        /** The most events one read returns, whatever limit the napplet asks for. */
        const val MAX_RESULTS = 500

        /** The most filters one read or subscription may carry. */
        const val MAX_FILTERS = 10
    }
}
