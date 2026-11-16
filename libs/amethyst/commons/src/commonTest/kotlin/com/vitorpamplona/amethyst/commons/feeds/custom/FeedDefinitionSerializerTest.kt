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
package com.vitorpamplona.amethyst.commons.feeds.custom

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedDefinitionSerializerTest {
    @Test
    fun roundTripFilterSource() {
        val feed =
            FeedDefinition(
                id = "test-1",
                name = "Bitcoin",
                emoji = "\u20BF",
                pinned = true,
                pinOrder = 0,
                source =
                    FeedSource.Filter(
                        hashtags = persistentListOf("bitcoin", "btc"),
                        authors = persistentListOf("abc123"),
                        relays = persistentListOf("wss://relay.damus.io"),
                        excludeAuthors = persistentListOf("spammer"),
                        excludeKeywords = persistentListOf("scam"),
                        kinds = persistentListOf(1, 6),
                    ),
                refreshMode = RefreshMode.LIVE_STREAM,
                createdAt = 1000L,
            )

        val json = FeedDefinitionSerializer.serializeList(listOf(feed))
        val deserialized = FeedDefinitionSerializer.deserializeList(json)

        assertEquals(1, deserialized.size)
        assertEquals(feed, deserialized[0])
    }

    @Test
    fun roundTripGlobalSource() {
        val feed =
            FeedDefinition(
                id = "test-global",
                name = "Global",
                emoji = "\uD83C\uDF10",
                pinned = true,
                pinOrder = 1,
                source = FeedSource.Global,
                refreshMode = RefreshMode.LIVE_STREAM,
                createdAt = 2000L,
            )

        val json = FeedDefinitionSerializer.serializeList(listOf(feed))
        val deserialized = FeedDefinitionSerializer.deserializeList(json)

        assertEquals(1, deserialized.size)
        assertEquals(feed, deserialized[0])
    }

    @Test
    fun roundTripFollowingSource() {
        val feed =
            FeedDefinition(
                id = "test-following",
                name = "Following",
                emoji = "\uD83C\uDFE0",
                pinned = false,
                pinOrder = Int.MAX_VALUE,
                source = FeedSource.Following,
                refreshMode = RefreshMode.LIVE_STREAM,
                createdAt = 3000L,
            )

        val json = FeedDefinitionSerializer.serializeList(listOf(feed))
        val deserialized = FeedDefinitionSerializer.deserializeList(json)

        assertEquals(feed, deserialized[0])
    }

    @Test
    fun roundTripDvmSource() {
        val feed =
            FeedDefinition(
                id = "test-dvm",
                name = "Trending",
                emoji = "\uD83D\uDD25",
                pinned = true,
                pinOrder = 2,
                source = FeedSource.DVM(kind = 31990, pubkey = "dvmpub123", dTag = "trending"),
                refreshMode = RefreshMode.POLL_5MIN,
                createdAt = 4000L,
            )

        val json = FeedDefinitionSerializer.serializeList(listOf(feed))
        val deserialized = FeedDefinitionSerializer.deserializeList(json)

        assertEquals(feed, deserialized[0])
    }

    @Test
    fun roundTripPeopleListSource() {
        val feed =
            FeedDefinition(
                id = "test-people",
                name = "Dev Friends",
                emoji = "\uD83D\uDC65",
                pinned = false,
                pinOrder = Int.MAX_VALUE,
                source = FeedSource.PeopleList(kind = 30000, pubkey = "mypub", dTag = "devs"),
                refreshMode = RefreshMode.LIVE_STREAM,
                createdAt = 5000L,
            )

        val json = FeedDefinitionSerializer.serializeList(listOf(feed))
        val deserialized = FeedDefinitionSerializer.deserializeList(json)

        assertEquals(feed, deserialized[0])
    }

    @Test
    fun roundTripInterestSetSource() {
        val feed =
            FeedDefinition(
                id = "test-interest",
                name = "Nostr Dev",
                emoji = "\uD83D\uDEE0",
                pinned = false,
                pinOrder = Int.MAX_VALUE,
                source = FeedSource.InterestSet(kind = 30015, pubkey = "mypub", dTag = "nostrdev"),
                refreshMode = RefreshMode.LIVE_STREAM,
                createdAt = 6000L,
            )

        val json = FeedDefinitionSerializer.serializeList(listOf(feed))
        val deserialized = FeedDefinitionSerializer.deserializeList(json)

        assertEquals(feed, deserialized[0])
    }

    @Test
    fun roundTripSingleRelaySource() {
        val feed =
            FeedDefinition(
                id = "test-relay",
                name = "Damus Relay",
                emoji = "\uD83D\uDCE1",
                pinned = false,
                pinOrder = Int.MAX_VALUE,
                source = FeedSource.SingleRelay(url = "wss://relay.damus.io"),
                refreshMode = RefreshMode.LIVE_STREAM,
                createdAt = 7000L,
            )

        val json = FeedDefinitionSerializer.serializeList(listOf(feed))
        val deserialized = FeedDefinitionSerializer.deserializeList(json)

        assertEquals(feed, deserialized[0])
    }

    @Test
    fun multipleFeeds() {
        val feeds =
            listOf(
                feedDefinition {
                    name = "Bitcoin"
                    emoji = "\u20BF"
                    filter {
                        hashtags += "bitcoin"
                        kinds += 1
                    }
                },
                feedDefinition {
                    name = "Lightning"
                    emoji = "\u26A1"
                    filter {
                        hashtags += "lightning"
                        hashtags += "ln"
                    }
                },
            )

        val json = FeedDefinitionSerializer.serializeList(feeds)
        val deserialized = FeedDefinitionSerializer.deserializeList(json)

        assertEquals(2, deserialized.size)
        assertEquals("Bitcoin", deserialized[0].name)
        assertEquals("Lightning", deserialized[1].name)
    }

    @Test
    fun emptyJsonReturnsEmptyList() {
        assertEquals(emptyList(), FeedDefinitionSerializer.deserializeList(""))
        assertEquals(emptyList(), FeedDefinitionSerializer.deserializeList("  "))
    }

    @Test
    fun serializesToExpectedWireFormat() {
        // Pins the byte-exact JSON output so any future change to the
        // serializer (field order, null handling, number formatting) is
        // caught here. Users have feeds saved on disk in this exact format
        // and may share JSON between app versions; downstream consumers
        // depend on the field order being stable.
        val feed =
            FeedDefinition(
                id = "test-1",
                name = "Bitcoin",
                emoji = "₿",
                pinned = true,
                pinOrder = 0,
                source =
                    FeedSource.Filter(
                        hashtags = persistentListOf("bitcoin", "btc"),
                        authors = persistentListOf("abc123"),
                        relays = persistentListOf("wss://relay.damus.io"),
                        excludeAuthors = persistentListOf("spammer"),
                        excludeKeywords = persistentListOf("scam"),
                        kinds = persistentListOf(1, 6),
                    ),
                refreshMode = RefreshMode.LIVE_STREAM,
                createdAt = 1000L,
            )

        val expected =
            """[{"id":"test-1","name":"Bitcoin","emoji":"₿","pinned":true,""" +
                """"pinOrder":0,"refreshMode":"LIVE_STREAM","createdAt":1000,""" +
                """"source":{"type":"filter","hashtags":["bitcoin","btc"],""" +
                """"authors":["abc123"],"relays":["wss://relay.damus.io"],""" +
                """"excludeAuthors":["spammer"],"excludeKeywords":["scam"],""" +
                """"kinds":[1,6]}}]"""

        assertEquals(expected, FeedDefinitionSerializer.serializeList(listOf(feed)))
    }

    @Test
    fun parsesLegacyJacksonOutput() {
        // Wire format previously emitted by ObjectMapper. Users have feed
        // definitions stored on disk in exactly this shape — the new
        // kotlinx.serialization-backed parser must keep accepting it.
        val legacy =
            """[{"id":"x","name":"Bitcoin","emoji":"₿","pinned":true,"pinOrder":0,""" +
                """"refreshMode":"LIVE_STREAM","createdAt":1000,""" +
                """"source":{"type":"filter","hashtags":["bitcoin","btc"],"authors":["abc"],""" +
                """"relays":[],"excludeAuthors":[],"excludeKeywords":[],"kinds":[1,6]}}]"""

        val feeds = FeedDefinitionSerializer.deserializeList(legacy)
        assertEquals(1, feeds.size)
        val feed = feeds[0]
        assertEquals("x", feed.id)
        assertEquals("Bitcoin", feed.name)
        assertEquals("₿", feed.emoji)
        assertTrue(feed.pinned)
        assertEquals(0, feed.pinOrder)
        assertEquals(RefreshMode.LIVE_STREAM, feed.refreshMode)
        assertEquals(1000L, feed.createdAt)
        val source = feed.source as FeedSource.Filter
        assertEquals(persistentListOf("bitcoin", "btc"), source.hashtags)
        assertEquals(persistentListOf("abc"), source.authors)
        assertEquals(persistentListOf(1, 6), source.kinds)
    }

    @Test
    fun defaultFeedsAreValid() {
        val defaults = defaultFeeds()
        assertEquals(2, defaults.size)
        assertTrue(defaults[0].pinned)
        assertTrue(defaults[1].pinned)
        assertEquals(FeedSource.Following, defaults[0].source)
        assertEquals(FeedSource.Global, defaults[1].source)
    }
}
