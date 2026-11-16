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
package com.vitorpamplona.amethyst.model.topNavFeeds.favoriteAlgoFeeds

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.favoriteAlgoFeeds.FavoriteAlgoFeedTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteAlgoFeedTopNavFilterTest {
    private fun textNote(id: String) = TextNoteEvent(id = id, pubKey = "a".repeat(64), createdAt = 1, tags = emptyArray(), content = "", sig = "x".repeat(128))

    private fun longFormNote(
        pubkey: String,
        dTag: String,
    ) = LongFormContentEvent(
        id = "0".repeat(64),
        pubKey = pubkey,
        createdAt = 1,
        tags = arrayOf(arrayOf("d", dTag)),
        content = "",
        sig = "x".repeat(128),
    )

    private val dvmAddress = Address(31990, "d".repeat(64), "content")

    @Test
    fun matchesNoteWhoseIdIsInAcceptedSet() {
        val filter =
            FavoriteAlgoFeedTopNavFilter(
                feedAddress = dvmAddress,
                acceptedIds = setOf("1".repeat(64)),
                acceptedAddresses = emptySet(),
                contentRelays = emptySet(),
                listenRelays = emptySet(),
                requestId = null,
            )

        assertTrue(filter.match(textNote("1".repeat(64))))
    }

    @Test
    fun rejectsNoteNotInAcceptedSet() {
        val filter =
            FavoriteAlgoFeedTopNavFilter(
                feedAddress = dvmAddress,
                acceptedIds = setOf("1".repeat(64)),
                acceptedAddresses = emptySet(),
                contentRelays = emptySet(),
                listenRelays = emptySet(),
                requestId = null,
            )

        assertFalse(filter.match(textNote("2".repeat(64))))
    }

    @Test
    fun matchesAddressableEventByAddressTag() {
        val articleAuthor = "c".repeat(64)
        val articleDTag = "my-post"
        val articleAddress = "30023:$articleAuthor:$articleDTag"

        val filter =
            FavoriteAlgoFeedTopNavFilter(
                feedAddress = dvmAddress,
                acceptedIds = emptySet(),
                acceptedAddresses = setOf(articleAddress),
                contentRelays = emptySet(),
                listenRelays = emptySet(),
                requestId = null,
            )

        assertTrue(filter.match(longFormNote(articleAuthor, articleDTag)))
    }

    @Test
    fun nullRequestIdCollapsesToEmptyRequestIdsInFilterSet() {
        val filter =
            FavoriteAlgoFeedTopNavFilter(
                feedAddress = dvmAddress,
                acceptedIds = emptySet(),
                acceptedAddresses = emptySet(),
                contentRelays = emptySet(),
                listenRelays = emptySet(),
                requestId = null,
            )

        // passing a LocalCache is only needed because the method demands it;
        // FavoriteAlgoFeedTopNavFilter.startValue doesn't actually consult it.
        val set = filter.startValue(com.vitorpamplona.amethyst.commons.model.cache.LocalCache)
        assertTrue(set.requestIds.isEmpty())
    }

    @Test
    fun nonNullRequestIdProducesSingletonInFilterSet() {
        val filter =
            FavoriteAlgoFeedTopNavFilter(
                feedAddress = dvmAddress,
                acceptedIds = emptySet(),
                acceptedAddresses = emptySet(),
                contentRelays = emptySet(),
                listenRelays = emptySet(),
                requestId = "9".repeat(64),
            )

        val set = filter.startValue(com.vitorpamplona.amethyst.commons.model.cache.LocalCache)
        assertTrue(set.requestIds == setOf("9".repeat(64)))
    }
}
