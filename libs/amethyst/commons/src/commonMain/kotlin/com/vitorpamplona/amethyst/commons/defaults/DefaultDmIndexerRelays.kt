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
package com.vitorpamplona.amethyst.commons.defaults

/**
 * Curated indexer relays for resolving NIP-17 inbox lookups (kind:10050).
 *
 * Used by [com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.DmInboxRelayResolver]
 * via a SEPARATE unauthenticated NostrClient — these queries MUST NOT carry an
 * AUTH event back to the user's identity key (security review F-01: an
 * authenticated indexer fan-out turns "indexer learns we queried for pubkey X"
 * into "indexer learns Amethyst user U queried for pubkey X").
 *
 * Set selected for known kind:10050 indexing coverage; `purplepag.es` is
 * deliberately excluded (metadata indexer, weak kind:10050 coverage).
 */
object DefaultDmIndexerRelays {
    val RELAYS: List<String> =
        listOf(
            "wss://relay.nos.social",
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.nostr.band",
            "wss://purplerelay.com",
        )
}
