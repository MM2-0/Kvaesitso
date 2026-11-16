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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A screen's seed has to survive the trip through the field: it is serialized into the route,
 * typed into the box as text, and parsed back. If that round trip loses or widens a filter, the
 * search a reader sees is not the screen they tapped from.
 */
class SearchSeedTest {
    private companion object {
        const val PUBKEY = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        const val NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
    }

    /** The seed as the search screen actually receives it: serialized, then read back. */
    private fun throughTheField(seed: SearchQuery): SearchQuery = QueryParser.parse(QuerySerializer.serialize(seed))

    @Test
    fun aKindWindowArrivesAsTheSameKinds() {
        val seed = SearchSeed.ofKinds(LongFormContentEvent.KIND)
        assertEquals("kind:article", QuerySerializer.serialize(seed))
        assertEquals(seed, throughTheField(seed))
    }

    @Test
    fun aKindWithNoAliasStillSurvivesAsItsNumber() {
        val seed = SearchSeed.ofKinds(31337)
        assertEquals("kind:31337", QuerySerializer.serialize(seed))
        assertEquals(seed, throughTheField(seed))
    }

    @Test
    fun aMultiKindAliasIsWrittenOnceNotOncePerKind() {
        // `channel` is 40 and 41. Naming each kind separately wrote "kind:channel kind:channel".
        val seed = SearchSeed.ofKinds(40, 41)
        assertEquals("kind:channel", QuerySerializer.serialize(seed))
        assertEquals(seed, throughTheField(seed))
    }

    @Test
    fun aPartialAliasGroupIsNotWidenedIntoTheWholeAlias() {
        // 30312+30313 is `nest`; 30311 would also be needed for `live`. Writing this as
        // "kind:live" would add a kind the screen never asked for.
        val seed = SearchSeed.ofKinds(30312, 30313)
        assertEquals("kind:nest", QuerySerializer.serialize(seed))
        assertEquals(seed, throughTheField(seed))

        // One kind of a larger group keeps its number rather than borrowing the group's name.
        val single = SearchSeed.ofKinds(30312)
        assertEquals("kind:30312", QuerySerializer.serialize(single))
        assertEquals(single, throughTheField(single))
    }

    @Test
    fun theProfileSeedIsOneAuthor() {
        val seed = SearchSeed.byAuthor(PUBKEY)
        assertTrue(QuerySerializer.serialize(seed).startsWith("from:npub1"))
        assertEquals(listOf(PUBKEY), throughTheField(seed).authors)
    }

    @Test
    fun theHashtagSeedDropsAnyLeadingHash() {
        assertEquals(SearchSeed.byHashtag("#nostr"), SearchSeed.byHashtag("nostr"))
        assertEquals(listOf("nostr"), throughTheField(SearchSeed.byHashtag("#nostr")).hashtags)
    }

    @Test
    fun theGeohashSeedArrivesAsAGeoScope() {
        val seed = SearchSeed.byGeohash("9q8yy")
        assertEquals("geo:9q8yy", QuerySerializer.serialize(seed))
        assertEquals(listOf(ExternalScope("geo", "9q8yy")), throughTheField(seed).scopes)
    }

    @Test
    fun aFeedSeedsItsKindAndItsTopNavFilterTogether() {
        val seed = SearchSeed.merge(SearchSeed.ofKinds(PictureEvent.KIND), TopFilter.Hashtag("nostr").asSearchQuery(null))
        val parsed = throughTheField(seed)
        assertEquals(listOf(PictureEvent.KIND), parsed.kinds)
        assertEquals(listOf("nostr"), parsed.hashtags)
    }

    @Test
    fun aFollowSetSeedsNothingRatherThanSomeOfItsAuthors() {
        // Spelling out a follow list would fill the box with chips, and truncating it would seed
        // a narrower query than the feed the reader was looking at.
        assertTrue(TopFilter.AllFollows.asSearchQuery(PUBKEY).isEmpty)
        assertTrue(TopFilter.DefaultFollows.asSearchQuery(PUBKEY).isEmpty)
        assertTrue(TopFilter.Global.asSearchQuery(PUBKEY).isEmpty)
        assertTrue(TopFilter.PeopleList(Address.parse(Address.assemble(30000, PUBKEY, "friends"))!!).asSearchQuery(PUBKEY).isEmpty)
    }

    @Test
    fun mineSeedsTheReaderAndNobodyWhenThereIsNoReader() {
        assertEquals(listOf(PUBKEY), TopFilter.Mine.asSearchQuery(PUBKEY).authors)
        assertTrue(TopFilter.Mine.asSearchQuery(null).isEmpty)
    }

    @Test
    fun aWindowSpanningSeveralAliasesComesBackInTheSameOrder() {
        // Pictures + every video kind. Naming the longest alias first must not reorder the list
        // the query carries, or the seed and what comes back out are not the same object.
        val seed = SearchSeed.ofKinds(20, 21, 22, 34235, 34236)
        assertEquals("kind:picture kind:video", QuerySerializer.serialize(seed))
        assertEquals(seed, throughTheField(seed))
    }

    @Test
    fun everyFeedKindWindowNamesItselfAndComesBackUnchanged() {
        // One case per screen that seeds a kind window. A window that serialized to a name the
        // parser read as a *different* set would hand the reader a search their screen never
        // asked for, and nothing else in the round trip would notice.
        mapOf(
            "kind:community" to listOf(34550),
            "kind:workout" to listOf(1301),
            "kind:playlist" to listOf(34139),
            "kind:music" to listOf(36787),
            "kind:podcast" to listOf(10154),
            "kind:episode" to listOf(30054),
            "kind:software" to listOf(32267),
            "kind:short" to listOf(34236),
            "kind:classified" to listOf(30402),
            "kind:followpack" to listOf(39089),
            "kind:live" to listOf(30311, 30312, 30313),
            "kind:stories" to listOf(34235, 34236),
            "kind:longvideo" to listOf(34235),
            "kind:calendar" to listOf(31923, 31922),
            "kind:calendarset" to listOf(31924),
            "kind:poll kind:zappoll" to listOf(1068, 6969),
        ).forEach { (token, kinds) ->
            val seed = SearchSeed.ofKinds(*kinds.toIntArray())
            assertEquals(token, QuerySerializer.serialize(seed), "kinds $kinds")
            assertEquals(seed, throughTheField(seed), "kinds $kinds")
        }
    }

    @Test
    fun theVideoFeedsDoNotBorrowEachOthersNames() {
        // `short`, `longvideo`, `stories` and `video` are nested windows over the same kinds.
        // Each must name exactly its own set: widening any of them silently changes the feed the
        // search claims to have come from.
        assertEquals(listOf(34236), throughTheField(SearchSeed.ofKinds(34236)).kinds)
        assertEquals(listOf(34235), throughTheField(SearchSeed.ofKinds(34235)).kinds)
        assertEquals(listOf(34235, 34236), throughTheField(SearchSeed.ofKinds(34235, 34236)).kinds)
        assertEquals(listOf(21, 22, 34235, 34236), throughTheField(SearchSeed.ofKinds(21, 22, 34235, 34236)).kinds)
    }

    @Test
    fun everyTokenPinsTheScopeToNotes() {
        // The people search is one NIP-50 string against kind 0, so every one of these would be
        // dropped without a word if All or People stayed selectable. One case per field: a field
        // added to SearchQuery and forgotten here is a token that silently does nothing again.
        assertTrue(QueryParser.parse("kind:article bitcoin").pinsToNotes)
        assertTrue(QueryParser.parse("kind:20").pinsToNotes)
        assertTrue(QueryParser.parse("kind:reply").pinsToNotes, "a pseudo-kind is still a kind")
        assertTrue(QueryParser.parse("kind:media").pinsToNotes)
        assertTrue(QueryParser.parse("from:$NPUB").pinsToNotes, "authors")
        assertTrue(QueryParser.parse("from:vitor").pinsToNotes, "an unresolved name")
        assertTrue(QueryParser.parse("since:2025-01-01").pinsToNotes)
        assertTrue(QueryParser.parse("until:2025-01-01").pinsToNotes)
        assertTrue(QueryParser.parse("#nostr").pinsToNotes)
        assertTrue(QueryParser.parse("lang:en bitcoin").pinsToNotes)
        assertTrue(QueryParser.parse("domain:nostr.com").pinsToNotes)
        assertTrue(QueryParser.parse("to:$NPUB").pinsToNotes, "mentions")
        assertTrue(SearchQuery(cites = persistentListOf("abc")).pinsToNotes)
        assertTrue(SearchQuery(addrs = persistentListOf("30023:$PUBKEY:d")).pinsToNotes)
        assertTrue(SearchQuery(labels = persistentListOf("nsfw")).pinsToNotes)
        assertTrue(SearchQuery(scopes = persistentListOf(ExternalScope("isbn", "123"))).pinsToNotes)
        assertTrue(SearchQuery(groups = persistentListOf("abc")).pinsToNotes)
        // Every seeded feed window pins it too.
        assertTrue(SearchSeed.ofKinds(LongFormContentEvent.KIND).pinsToNotes)
    }

    @Test
    fun freeTextAndItsOperatorsLeaveTheScopeAlone() {
        // Words, and the three ways of writing words: nothing here is a filter a person cannot be
        // matched against, so the reader keeps All and People.
        assertFalse(QueryParser.parse("bitcoin").pinsToNotes)
        assertFalse(QueryParser.parse("\"hal finney\"").pinsToNotes)
        assertFalse(QueryParser.parse("bitcoin OR lightning").pinsToNotes)
        assertFalse(QueryParser.parse("bitcoin -spam").pinsToNotes)
        assertFalse(SearchQuery.EMPTY.pinsToNotes)
        // A `kind:` the registry cannot resolve never became a filter, so it pins nothing either.
        assertFalse(QueryParser.parse("kind:banana").pinsToNotes)
    }
}
