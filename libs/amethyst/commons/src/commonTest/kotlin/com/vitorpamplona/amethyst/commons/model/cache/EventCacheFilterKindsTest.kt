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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [EventCache.filter] walks the regular notes only for a filter that can match one: a filter of
 * addressable or replaceable kinds alone (a repository announcement, as every git card asks for)
 * skips that walk, and must still answer exactly what it answered before.
 */
class EventCacheFilterKindsTest {
    private val alice = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    private fun event(
        idSeed: Char,
        kind: Int,
        createdAt: Long,
        tags: Array<Array<String>> = emptyArray(),
    ): Event = EventFactory.create(idSeed.toString().repeat(64), alice, createdAt, kind, tags, "", "0".repeat(128))

    private val note1 = event('1', 1, 1_000)
    private val note2 = event('2', 1, 2_000)

    // Two versions of one addressable event: both are in the cache by id, only the newest at its address.
    private val repoV1 = event('3', 30_617, 1_500, arrayOf(arrayOf("d", "amethyst")))
    private val repoV2 = event('4', 30_617, 2_500, arrayOf(arrayOf("d", "amethyst")))
    private val otherRepo = event('5', 30_617, 3_000, arrayOf(arrayOf("d", "quartz")))

    private fun cache() =
        EventCache().apply {
            listOf(note1, note2, repoV1, repoV2, otherRepo).forEach { justConsume(it, null, true) }
        }

    private fun EventCache.ids(filter: Filter) = filter(filter).mapNotNull { it.event?.id }

    @Test
    fun addressableOnlyFilterFindsTheCurrentVersionsAndNoRegularNote() {
        val cache = cache()
        assertEquals(listOf(otherRepo.id, repoV2.id), cache.ids(Filter(kinds = listOf(30_617))))
        assertEquals(listOf(repoV2.id), cache.ids(Filter(kinds = listOf(30_617), tags = mapOf("d" to listOf("amethyst")))))
    }

    @Test
    fun mixedKindsStillFindBothHalves() {
        val cache = cache()
        assertEquals(
            listOf(otherRepo.id, repoV2.id, note2.id, note1.id),
            cache.ids(Filter(kinds = listOf(1, 30_617))),
        )
    }

    @Test
    fun regularOnlyAndKindlessFiltersStillFindTheNotes() {
        val cache = cache()
        assertEquals(listOf(note2.id, note1.id), cache.ids(Filter(kinds = listOf(1))))
        assertEquals(listOf(otherRepo.id, repoV2.id, note2.id, note1.id), cache.ids(Filter(authors = listOf(alice))))
    }
}
