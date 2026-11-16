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
package com.vitorpamplona.amethyst.commons.relayClient.podcasts

import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MergeFilterTagsTest {
    @Test
    fun `null base passes the extra through`() {
        assertEquals(mapOf("d" to listOf("podcast-metadata")), mergeFilterTags(null, PODCAST_METADATA_D_FILTER))
    }

    @Test
    fun `null extra passes the base through`() {
        val base = mapOf("t" to listOf("tech"))
        assertEquals(base, mergeFilterTags(base, null))
    }

    @Test
    fun `both null stays null`() {
        assertNull(mergeFilterTags(null, null))
    }

    @Test
    fun `disjoint keys are unioned - layering d onto an existing t constraint`() {
        val merged = mergeFilterTags(mapOf("t" to listOf("tech")), PODCAST_METADATA_D_FILTER)
        assertEquals(
            mapOf("t" to listOf("tech"), "d" to listOf("podcast-metadata")),
            merged,
        )
    }

    @Test
    fun `same key merges and de-duplicates values`() {
        val merged = mergeFilterTags(mapOf("d" to listOf("podcast-metadata")), mapOf("d" to listOf("podcast-metadata", "other")))
        assertEquals(mapOf("d" to listOf("podcast-metadata", "other")), merged)
    }

    @Test
    fun `the d filter targets the podstr metadata kind and d-tag`() {
        assertEquals(listOf(AppSpecificDataEvent.KIND), PODCASTING20_METADATA_KINDS)
        assertEquals(mapOf("d" to listOf("podcast-metadata")), PODCAST_METADATA_D_FILTER)
    }
}
