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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Community top-nav filters place a NIP-22 comment by its root `A`: a nested reply keeps the
 * community as its root but has no lowercase `a` (its parent is another comment).
 */
class CommunityTopNavFilterTest {
    private val owner = "9ca0bd7450742d6a20319c0e3d4c679c9e046a9dc70e8ef55c2905e24052340b"
    private val movies = "34550:$owner:movies"
    private val books = "34550:$owner:books"
    private val stranger = "44".repeat(32)
    private val noRelays = MutableStateFlow(emptySet<NormalizedRelayUrl>())

    private fun nestedComment() =
        CommentEvent(
            id = "00".repeat(32),
            pubKey = stranger,
            createdAt = 1_700_000_000L,
            tags =
                arrayOf(
                    arrayOf("A", movies),
                    arrayOf("K", "34550"),
                    arrayOf("e", "33".repeat(32)),
                    arrayOf("k", "1111"),
                ),
            content = "nested reply",
            sig = "22".repeat(64),
        )

    private fun approvedPost(community: String) =
        TextNoteEvent(
            id = "00".repeat(32),
            pubKey = stranger,
            createdAt = 1_700_000_000L,
            tags = arrayOf(arrayOf("a", community)),
            content = "post",
            sig = "22".repeat(64),
        )

    @Test
    fun singleCommunityMatchesNestedComment() {
        val inMovies = SingleCommunityTopNavFilter(movies, null, emptySet(), noRelays)
        val inBooks = SingleCommunityTopNavFilter(books, null, emptySet(), noRelays)

        assertTrue(inMovies.match(nestedComment()))
        assertFalse(inBooks.match(nestedComment()))
        assertTrue(inMovies.match(approvedPost(movies)))
        assertFalse(inBooks.match(approvedPost(movies)))
    }

    @Test
    fun allCommunitiesMatchesNestedComment() {
        assertTrue(AllCommunitiesTopNavFilter(setOf(books, movies), noRelays).match(nestedComment()))
        assertFalse(AllCommunitiesTopNavFilter(setOf(books), noRelays).match(nestedComment()))
        assertTrue(AllCommunitiesTopNavFilter(setOf(books, movies), noRelays).match(approvedPost(movies)))
    }

    @Test
    fun allFollowsMatchesNestedCommentInAFollowedCommunity() {
        val proxy = AllFollowsByProxyTopNavFilter(communities = setOf(books, movies), proxyRelays = emptySet())
        val outbox = AllFollowsByOutboxTopNavFilter(communities = setOf(books, movies), defaultRelays = noRelays, blockedRelays = noRelays)

        assertTrue(proxy.match(nestedComment()))
        assertTrue(outbox.match(nestedComment()))
        assertFalse(AllFollowsByProxyTopNavFilter(communities = setOf(books), proxyRelays = emptySet()).match(nestedComment()))
        assertTrue(proxy.match(approvedPost(movies)))
    }
}
