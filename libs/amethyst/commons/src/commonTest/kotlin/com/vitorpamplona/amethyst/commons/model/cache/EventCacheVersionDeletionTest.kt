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

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * NIP-09: an `e` tag deletes that one event. Deleting an addressable's old version by id must leave
 * the newer version in place; only an `a` tag (or the current version's own id) removes the address.
 */
class EventCacheVersionDeletionTest {
    private val alice = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))

    @Test
    fun deletingAnOldVersionByIdKeepsTheCurrentVersion() =
        runTest {
            val cache = EventCache()
            val v1 = alice.sign(LongFormContentEvent.build("first", "Article", dTag = "post", createdAt = 1_000))
            val v2 = alice.sign(LongFormContentEvent.build("second", "Article", dTag = "post", createdAt = 2_000))
            cache.justConsume(v1, null, true)
            cache.justConsume(v2, null, true)

            val deletion = alice.sign(DeletionRequestEvent.buildForVersionOnly(listOf(v1), createdAt = 3_000))
            cache.justConsume(deletion, null, true)

            assertEquals(v2.id, cache.getAddressableNoteIfExists(v2.address())?.event?.id)
        }

    @Test
    fun deletingTheCurrentVersionByIdRemovesIt() =
        runTest {
            val cache = EventCache()
            val v1 = alice.sign(LongFormContentEvent.build("first", "Article", dTag = "post", createdAt = 1_000))
            cache.justConsume(v1, null, true)

            val deletion = alice.sign(DeletionRequestEvent.buildForVersionOnly(listOf(v1), createdAt = 3_000))
            cache.justConsume(deletion, null, true)

            assertNull(cache.getAddressableNoteIfExists(v1.address())?.event)
        }
}
