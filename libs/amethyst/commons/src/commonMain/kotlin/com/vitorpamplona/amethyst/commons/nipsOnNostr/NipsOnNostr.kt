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
package com.vitorpamplona.amethyst.commons.nipsOnNostr

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Small lookups over the NIPs published on Nostr (kind 30817). Loading them is the NIPs feeds' job
 * ([com.vitorpamplona.amethyst.commons.nipsOnNostr.dal.NipsFeedFilter] and the kind page's feeds,
 * filled by [com.vitorpamplona.amethyst.commons.relayClient.nips.NipsFilterAssembler] while their
 * screens are open); these only read the cache.
 */
object NipsOnNostr {
    private const val PER_AUTHOR = 3

    /**
     * The account's relays a NIP subscription asks: its own, its search and its index relays. The
     * subscription adds [authorOutboxes], which reach NIPs published off the big public relays and
     * new versions of known ones; both are sets the account and the cache decide, so every rebuild
     * of the filters asks the same relays.
     */
    fun accountDiscoveryRelays(account: Account): Set<NormalizedRelayUrl> {
        val relays = HashSet<NormalizedRelayUrl>()
        relays.addAll(account.homeRelays.flow.value)
        relays.addAll(account.searchRelays())
        relays.addAll(account.indexRelays())
        return relays
    }

    /** The outbox relays of every author of a cached NIP, at most [PER_AUTHOR] each. */
    fun authorOutboxes(): Set<NormalizedRelayUrl> {
        val authors = HashSet<User>()
        LocalCache.addressables.filterIntoSet(NipTextEvent.KIND) { _, note ->
            note.author?.let { authors.add(it) }
            false
        }
        val relays = HashSet<NormalizedRelayUrl>()
        for (author in authors) author.outboxRelays()?.let { relays.addAll(it.take(PER_AUTHOR)) }
        return relays
    }

    /** Whether [note] may be listed for [account]: not by a hidden author, and not rejected as spam. */
    fun isListable(
        note: Note,
        account: Account,
    ): Boolean = !note.isHiddenFor(account.hiddenUsers.flow.value) && account.isAcceptable(note)

    /** The name the newest of [definitions] gives [kind] in its `k` tag, if any. */
    fun labelOf(
        kind: Int,
        definitions: List<Note>,
    ): String? {
        val target = kind.toString()
        for (note in definitions) {
            val tags = note.event?.tags ?: continue
            for (tag in tags) {
                if (tag.size > 2 && tag[0] == "k" && tag[1].trim() == target) tag[2].trim().ifBlank { null }?.let { return it }
            }
        }
        return null
    }
}

/** True when a `k` tag of [tags] names [kind]. */
fun definesKind(
    tags: Array<Array<String>>,
    kind: Int,
): Boolean {
    val target = kind.toString()
    for (tag in tags) {
        if (tag.size > 1 && tag[0] == "k" && tag[1].trim() == target) return true
    }
    return false
}
