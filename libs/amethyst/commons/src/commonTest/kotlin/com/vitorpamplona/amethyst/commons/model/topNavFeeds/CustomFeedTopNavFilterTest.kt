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

import com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource
import com.vitorpamplona.amethyst.commons.model.StubCache
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.custom.CustomFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.custom.CustomFeedTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.custom.CustomFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.home.nip01Core.filterHomePostsByCustomFeed
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomFeedTopNavFilterTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val relay = "wss://relay.example.com".normalizeRelayUrl()

    private fun note(
        author: String,
        content: String = "gm",
        hashtags: List<String> = emptyList(),
        kind: Int = 1,
    ) = Event("0".repeat(64), author, 1000, kind, hashtags.map { arrayOf("t", it) }.toTypedArray(), content, "0".repeat(128))

    private fun filter(source: FeedSource.Filter?) = CustomFeedTopNavFilter(source, MutableStateFlow(setOf(relay)), MutableStateFlow(emptySet()), emptySet())

    @Test
    fun everyConditionThatIsSetMustHold() {
        val feed = filter(FeedSource.Filter(authors = persistentListOf(alice), hashtags = persistentListOf("Nostr")))

        assertTrue(feed.match(note(alice, hashtags = listOf("nostr"))))
        assertFalse(feed.match(note(alice)), "missing the hashtag")
        assertFalse(feed.match(note(bob, hashtags = listOf("nostr"))), "another author")
    }

    @Test
    fun kindsAndExclusionsNarrowIt() {
        val feed =
            filter(
                FeedSource.Filter(
                    hashtags = persistentListOf("nostr"),
                    kinds = persistentListOf(1),
                    excludeAuthors = persistentListOf(bob),
                    excludeKeywords = persistentListOf("airdrop"),
                ),
            )

        assertTrue(feed.match(note(alice, hashtags = listOf("nostr"))))
        assertFalse(feed.match(note(alice, hashtags = listOf("nostr"), kind = 30023)), "a kind it does not show")
        assertFalse(feed.match(note(bob, hashtags = listOf("nostr"))), "an excluded author")
        assertFalse(feed.match(note(alice, content = "Free AIRDROP", hashtags = listOf("nostr"))), "an excluded keyword")
    }

    @Test
    fun aDeletedFeedShowsNothing() {
        val feed = filter(null)

        assertFalse(feed.match(note(alice)))
        assertTrue(feed.startValue(StubCache()).set.isEmpty())
    }

    @Test
    fun ownRelaysWinAndTheRequestCombinesEverything() {
        val own = "wss://own.example.com"
        val feed = filter(FeedSource.Filter(authors = persistentListOf(alice), hashtags = persistentListOf("nostr"), relays = persistentListOf(own)))
        val set = feed.startValue(StubCache())

        assertEquals(setOf(own.normalizeRelayUrl()), set.relays())

        val req = filterHomePostsByCustomFeed(set, null, 500).single()
        assertEquals(listOf(alice), req.filter.authors)
        assertTrue("nostr" in req.filter.tags!!.getValue("t"))
        assertEquals(500, req.filter.since)
    }

    @Test
    fun withoutKindsTheRequestUsesThePostKindsAndNoAuthors() {
        val set = CustomFeedTopNavPerRelayFilterSet(mapOf(relay to CustomFeedTopNavPerRelayFilter(emptySet(), setOf("nostr"), emptySet())))
        val req = filterHomePostsByCustomFeed(set, null, null).single()

        assertNull(req.filter.authors)
        assertTrue(1 in req.filter.kinds!!)
    }
}
