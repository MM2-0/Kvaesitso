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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent

/**
 * The NIP-19 citation of this address: its `naddr`, or, when the d-tag is over 255 bytes (no
 * naddr can hold it, so [NAddress.create] throws), the `nevent` of [latest] when it is loaded,
 * else the raw `kind:pubkey:d` value. D-tags come from other people's events, so citing one must
 * never throw.
 */
fun Address.toNIP19OrFallback(
    relays: List<NormalizedRelayUrl>,
    latest: Event?,
): String =
    NAddress.createOrNull(kind, pubKeyHex, dTag, relays)
        ?: latest?.let { NEvent.create(it.id, it.pubKey, it.kind, relays) }
        ?: toValue()

/** [toNIP19OrFallback] with a single, optional relay hint. */
fun Address.toNIP19OrFallback(
    relay: NormalizedRelayUrl?,
    latest: Event?,
): String =
    NAddress.createOrNull(kind, pubKeyHex, dTag, relay)
        ?: latest?.let { NEvent.create(it.id, it.pubKey, it.kind, relay) }
        ?: toValue()

/** `nostr:` + [toNIP19OrFallback], built in one buffer when it is an naddr or nevent. */
fun Address.toNostrUriOrFallback(
    relays: List<NormalizedRelayUrl>,
    latest: Event?,
): String =
    NAddress.createNostrUriOrNull(kind, pubKeyHex, dTag, relays)
        ?: latest?.let { NEvent.createNostrUri(it.id, it.pubKey, it.kind, relays) }
        ?: "nostr:${toValue()}"

/** [toNostrUriOrFallback] with a single, optional relay hint. */
fun Address.toNostrUriOrFallback(
    relay: NormalizedRelayUrl?,
    latest: Event?,
): String =
    NAddress.createNostrUriOrNull(kind, pubKeyHex, dTag, relay)
        ?: latest?.let { NEvent.createNostrUri(it.id, it.pubKey, it.kind, relay) }
        ?: "nostr:${toValue()}"
