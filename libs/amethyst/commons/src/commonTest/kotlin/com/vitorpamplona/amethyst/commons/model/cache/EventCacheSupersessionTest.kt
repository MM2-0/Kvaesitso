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
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.publishedAt
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * NIP-01: an address holds the version with the highest `created_at`, ties to the lowest id.
 * Articles, wiki pages, classifieds and the like keep one `published_at` across every edit, and
 * `AddressableNote.createdAt()` shows that date — so supersession must never read it, or every
 * edit looks equally old and whichever arrives last wins.
 */
class EventCacheSupersessionTest {
    private val alice = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))

    private suspend fun article(
        content: String,
        createdAt: Long,
    ) = alice.sign(LongFormContentEvent.build(content, "Article", publishedAt = 500, dTag = "post", createdAt = createdAt))

    private suspend fun listing(
        content: String,
        createdAt: Long,
    ) = alice.sign(
        ClassifiedsEvent.build("Bike", PriceTag("100", "USD", null), content, dTag = "bike", createdAt = createdAt) { publishedAt(500) },
    )

    @Test
    fun olderArticleEditArrivingLateDoesNotReplaceTheNewerOne() =
        runTest {
            val cache = EventCache()
            val v1 = article("first", 1_000)
            val v2 = article("second", 2_000)
            cache.justConsume(v2, null, true)
            cache.justConsume(v1, null, true)

            assertEquals(v2.id, cache.getAddressableNoteIfExists(v2.address())?.event?.id)
        }

    @Test
    fun olderBaseReplaceableEditArrivingLateDoesNotReplaceTheNewerOne() =
        runTest {
            val cache = EventCache()
            val v1 = listing("first", 1_000)
            val v2 = listing("second", 2_000)
            cache.justConsume(v2, null, true)
            cache.justConsume(v1, null, true)

            assertEquals(v2.id, cache.getAddressableNoteIfExists(v2.address())?.event?.id)
        }

    @Test
    fun sameSecondVersionsKeepTheLowestIdWhicheverArrivesFirst() =
        runTest {
            val a = article("first", 1_000)
            val b = article("second", 1_000)
            val lowest = if (a.id < b.id) a else b

            listOf(listOf(a, b), listOf(b, a)).forEach { order ->
                val cache = EventCache()
                order.forEach { cache.justConsume(it, null, true) }
                assertEquals(lowest.id, cache.getAddressableNoteIfExists(a.address())?.event?.id)
            }
        }

    @Test
    fun addressDeletionOlderThanTheCurrentVersionKeepsIt() =
        runTest {
            val cache = EventCache()
            val v1 = article("first", 1_000)
            val v2 = article("second", 2_000)
            val deletion = alice.sign(DeletionRequestEvent.buildAddressOnly(listOf(v1), createdAt = 1_500))
            cache.justConsume(v2, null, true)
            cache.justConsume(deletion, null, true)

            assertEquals(v2.id, cache.getAddressableNoteIfExists(v2.address())?.event?.id)
        }
}
