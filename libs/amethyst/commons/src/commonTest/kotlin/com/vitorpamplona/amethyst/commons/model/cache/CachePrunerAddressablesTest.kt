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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Pruning an addressable has to drop it from `addressables`, the map every address lookup reads:
 * removing it from `notes` alone leaves the expired or muted event served as if nothing happened.
 *
 * Events go in through [consumePinned]: the cache holds notes weakly (LargeSoftCache), so a GC
 * mid-test would drop a note the pruner kept and fail the "kept" assertion.
 */
class CachePrunerAddressablesTest {
    private val alice = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))
    private val bob = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000009".hexToByteArray()))

    private val pinned = mutableListOf<Note>()

    private fun EventCache.consumePinned(event: LongFormContentEvent) {
        pinned.add(getOrCreateAddressableNote(event.address()))
        pinned.add(getOrCreateNote(event.id))
        justConsume(event, null, true)
    }

    @Test
    fun expiredAddressableIsDroppedFromAddressables() =
        runTest {
            val cache = EventCache()
            val expired = alice.sign(LongFormContentEvent.build("first", "Article", dTag = "exp", createdAt = 1_000) { expiration(1_100) })
            cache.consumePinned(expired)
            assertNotNull(cache.getAddressableNoteIfExists(expired.address())?.event)

            cache.pruner.pruneExpiredEvents()

            assertNull(cache.getAddressableNoteIfExists(expired.address())?.event)
        }

    @Test
    fun hiddenAuthorsAddressableIsDroppedFromAddressables() =
        runTest {
            val cache = EventCache()
            val muted = alice.sign(LongFormContentEvent.build("muted", "Article", dTag = "post", createdAt = 1_000))
            val kept = bob.sign(LongFormContentEvent.build("kept", "Article", dTag = "post", createdAt = 1_000))
            cache.consumePinned(muted)
            cache.consumePinned(kept)

            cache.pruner.pruneHiddenEvents(LiveHiddenUsers(null, emptyList(), emptySet(), emptySet(), hiddenUsers = setOf(alice.pubKey)))

            assertNull(cache.getAddressableNoteIfExists(muted.address())?.event)
            assertNull(cache.getNoteIfExists(muted.id)?.event)
            assertNotNull(cache.getAddressableNoteIfExists(kept.address())?.event)
            assertNotNull(cache.getNoteIfExists(kept.id)?.event)
        }
}
