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
package com.vitorpamplona.amethyst.commons.relayClient.event.watchers

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.filterAddressesPerRelay
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Newer versions of the addressables on screen. The loader (`filterMissingAddressables`) asks
 * for an address only while the cache has no version of it, then stops, so an article, a list or
 * a list header published again from another device never reached a screen that already showed
 * it. This keeps asking, while the note is watched, where its author writes and where it was seen.
 *
 * The filters are the loader's ([filterAddressesPerRelay]: per kind and author, `#d` in chunks a
 * relay won't cap, plain replaceables by author alone), each with `since` just past the oldest
 * version its addresses hold here. A newer version is newer than the cached one by definition, so
 * that bound never misses one, and a relay with nothing newer answers with nothing. The
 * subscription's EOSE times are no bound: they are kept per note, not per note and relay, so a
 * relay first asked for an address (its author's outbox just arrived) would be asked only for what
 * came after it answered for other notes.
 */
fun filterNewVersionsOfAddresses(keys: List<AddressableNote>): List<RelayBasedFilter>? {
    val perRelay = HashMap<NormalizedRelayUrl, MutableSet<Address>>()
    val held = HashMap<Address, Long>()
    keys.forEach { note ->
        // An address with no version yet is the loader's to find.
        val event = note.event ?: return@forEach
        held[note.address] = event.createdAt + 1
        note.author?.outboxRelays()?.forEach { relay -> perRelay.getOrPut(relay) { HashSet() }.add(note.address) }
        note.relays.forEach { relay -> perRelay.getOrPut(relay) { HashSet() }.add(note.address) }
    }
    if (perRelay.isEmpty()) return null

    return filterAddressesPerRelay(perRelay) { kind, author, dTag -> held[Address(kind, author, dTag)] }
}
