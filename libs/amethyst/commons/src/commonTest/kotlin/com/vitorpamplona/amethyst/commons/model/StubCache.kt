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

import com.vitorpamplona.amethyst.commons.model.cache.ICacheEventStream
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer

/**
 * Minimal [ICacheProvider] for the pure reply-resolution tests in this package: a fixed set of
 * notes and users, and an optional set of note ids that should report as living in a channel.
 * Everything the resolvers never touch throws rather than returning a plausible-looking default.
 */
internal class StubCache(
    private val notesById: Map<HexKey, Note> = emptyMap(),
    private val users: Map<HexKey, User> = emptyMap(),
    private val channelFor: Set<HexKey> = emptySet(),
) : ICacheProvider {
    override val relayHints = HintIndexer()

    override fun getAnyChannel(note: Note): Channel? =
        if (note.idHex in channelFor) {
            object : Channel() {
                override fun toBestDisplayName() = "a channel"
            }
        } else {
            null
        }

    override fun getUserIfExists(pubkey: HexKey): User? = users[pubkey]

    override fun countUsers(predicate: (String, User) -> Boolean): Int = 0

    override fun getNoteIfExists(hexKey: HexKey): Note? = notesById[hexKey]

    override fun checkGetOrCreateNote(hexKey: HexKey): Note? = notesById[hexKey]

    override fun getOrCreateAddressableNote(address: Address): AddressableNote = error("unused by the reply resolvers")

    override fun getEventStream(): ICacheEventStream = error("unused by the reply resolvers")

    override fun hasBeenDeleted(event: Any): Boolean = false

    override fun getOrCreateUser(pubkey: HexKey): User? = users[pubkey]

    override fun consumeEmbedded(event: Event) = Unit

    override fun justConsumeMyOwnEvent(event: Event): Boolean = false
}
