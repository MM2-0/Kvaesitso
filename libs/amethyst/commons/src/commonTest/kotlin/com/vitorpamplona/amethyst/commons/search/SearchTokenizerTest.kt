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

import com.vitorpamplona.amethyst.commons.search.calendar.DateField
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTokenizerTest {
    private companion object {
        const val NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
        const val NOTE = "note1stqea6wmwezg9x6yyr6qkukw95ewtdukyaztycws65l8wppjmtpscawevv"
        const val NADDR = "naddr1qqqqygzxpsj7dqha57pjk5k37gkn6g4nzakewtmqmnwryyhd3jfwlpgxtspsgqqqw4rs3xyxus"
    }

    /** Every segment together must reproduce the input exactly, or a chip covers the wrong text. */
    private fun assertCovers(input: String) {
        assertEquals(input, SearchTokenizer.tokenize(input).joinToString("") { it.rawText }, "segments must cover the input")
    }

    private inline fun <reified T : SearchSegment> only(input: String): T {
        assertCovers(input)
        val hits = SearchTokenizer.tokenize(input).filterIsInstance<T>()
        assertEquals(1, hits.size, "expected exactly one ${T::class.simpleName} in \"$input\", got ${SearchTokenizer.tokenize(input)}")
        return hits.first()
    }

    @Test
    fun plainTextHoldsNoTokens() {
        assertCovers("bitcoin lightning")
        assertTrue(SearchTokenizer.tokenize("bitcoin lightning").all { it is SearchSegment.Text })
    }

    @Test
    fun fromNpubIsAnAuthorToken() {
        val key = only<SearchSegment.Key>("from:$NPUB")
        assertEquals(KeyField.FROM, key.field)
        assertEquals("from:$NPUB", key.raw)
        assertEquals(64, key.pubkey.length)
    }

    @Test
    fun toNpubIsAMentionToken() {
        assertEquals(KeyField.TO, only<SearchSegment.Key>("to:$NPUB").field)
    }

    @Test
    fun bareNpubHasNoFieldAndStaysATerm() {
        assertNull(only<SearchSegment.Key>(NPUB).field)
        assertTrue(QueryParser.parse(NPUB).authors.isEmpty())
        assertEquals(NPUB, QueryParser.parse(NPUB).text)
    }

    @Test
    fun toNoteAsksTheEventTag() {
        val pointer = only<SearchSegment.Pointer>("to:$NOTE")
        assertEquals("e", pointer.tag)
        assertEquals(64, pointer.value.length)
    }

    @Test
    fun toNaddrAsksTheAddressTag() {
        val pointer = only<SearchSegment.Pointer>("to:$NADDR")
        assertEquals("a", pointer.tag)
        assertEquals(3, pointer.value.split(":").size)
    }

    @Test
    fun aCorruptNpubStaysText() {
        val broken = "from:npub1" + "q".repeat(58)
        assertCovers(broken)
        assertTrue(SearchTokenizer.tokenize(broken).all { it is SearchSegment.Text }, "a failed checksum must not become a chip")
    }

    @Test
    fun aTokenInsideAWordIsNotAToken() {
        // The `to:` in a url names nobody; only a word start opens a token.
        assertCovers("https://ex.am/to:$NPUB")
        assertTrue(SearchTokenizer.tokenize("https://ex.am/to:$NPUB").all { it is SearchSegment.Text })
    }

    @Test
    fun sinceIsTheFirstSecondOfItsLocalDay() {
        val date = only<SearchSegment.DateBound>("since:2026-04-01")
        assertEquals(DateField.SINCE, date.field)
        assertEquals(SearchDate(2026, 4, 1), date.date)
        assertEquals(LocalClock.startOfDay(SearchDate(2026, 4, 1)), date.at)
    }

    @Test
    fun untilIsTheLastSecondOfItsLocalDay() {
        val date = only<SearchSegment.DateBound>("until:2026-04-01")
        assertEquals(LocalClock.endOfDay(SearchDate(2026, 4, 1)), date.at)
        // Inclusive, and never midnight + 86,399: a local day can be 23 or 25 hours long.
        assertEquals(LocalClock.startOfDay(SearchDate(2026, 4, 2)) - 1, date.at)
    }

    @Test
    fun aDayThatDoesNotExistStaysText() {
        assertTrue(SearchTokenizer.tokenize("since:2026-02-31").all { it is SearchSegment.Text })
    }

    @Test
    fun aDateFollowedByMoreWordIsNotADate() {
        assertTrue(SearchTokenizer.tokenize("since:2026-04-01-rev2").all { it is SearchSegment.Text })
    }

    @Test
    fun hashtagsAreLoweredButDrawAsTyped() {
        val tag = only<SearchSegment.Hashtag>("hello #Bitcoin world")
        assertEquals("#Bitcoin", tag.raw)
        assertEquals("bitcoin", tag.tag)
    }

    @Test
    fun aHashtagKeepsATrailingHyphenInItsRaw() {
        val tag = only<SearchSegment.Hashtag>("#nostr-")
        assertEquals("#nostr-", tag.raw)
        assertEquals("nostr", tag.tag)
    }

    @Test
    fun aUrlFragmentIsNotAHashtag() {
        assertTrue(SearchTokenizer.tokenize("https://ex.am/page#top").none { it is SearchSegment.Hashtag })
    }

    @Test
    fun scopeValueDropsTrailingSentencePunctuation() {
        val scope = only<SearchSegment.Scope>("site:example.com/a.")
        assertEquals("site", scope.field)
        assertEquals("example.com/a", scope.value)
        assertEquals("site:example.com/a", scope.raw)
    }

    @Test
    fun podcastPublisherWinsOverAShorterPrefix() {
        assertEquals("podcast:publisher", only<SearchSegment.Scope>("podcast:publisher:abc").field)
        assertEquals("podcast:guid", only<SearchSegment.Scope>("podcast:guid:abc").field)
    }

    @Test
    fun aScopeThatAsksNothingIsNotAToken() {
        // `site:#top` has no host once the fragment is dropped, so a pill would claim a filter
        // that the builder would not send.
        assertTrue(SearchTokenizer.tokenize("site:#top").none { it is SearchSegment.Scope })
    }

    @Test
    fun labelAndGroupAreOpaqueAndCaseExact() {
        assertEquals("review/app", only<SearchSegment.Label>("label:review/app").value)
        assertEquals("General", only<SearchSegment.Group>("group:General").id)
    }

    @Test
    fun tokensMixWithTermsAndCoverTheWholeInput() {
        val input = "zaps from:$NPUB #bitcoin since:2026-01-01 group:dev talk"
        assertCovers(input)
        val kinds = SearchTokenizer.tokenize(input).map { it::class.simpleName }
        assertTrue("Key" in kinds && "Hashtag" in kinds && "DateBound" in kinds && "Group" in kinds, kinds.toString())
    }

    @Test
    fun aSettlingTokenUnderTheCaretStaysText() {
        val input = "#bitcoin"
        // Caret at the end of the tag: still being typed, so it must not pill yet.
        assertTrue(SearchTokenizer.drawable(input, input.length).all { it is SearchSegment.Text })
        // Caret elsewhere, or nowhere: it pills.
        assertTrue(SearchTokenizer.drawable("$input ", input.length + 1).any { it is SearchSegment.Hashtag })
        assertTrue(SearchTokenizer.drawable(input, null).any { it is SearchSegment.Hashtag })
    }

    @Test
    fun aKeyNeverSettlesUnderTheCaret() {
        // A key has one spelling and cannot be half-right, so it chips the moment it parses.
        assertTrue(SearchTokenizer.drawable("from:$NPUB", "from:$NPUB".length).any { it is SearchSegment.Key })
    }

    @Test
    fun tidyTermsDropsThePunctuationALiftedTokenStranded() {
        assertEquals("hello world", SearchTokenizer.tidyTerms("hello . world"))
        // NIP-50's own operators survive.
        assertEquals("\"a b\" -c #d", SearchTokenizer.tidyTerms("\"a b\" -c #d"))
    }

    // ---- kind:, lang: and domain: --------------------------------------------------------
    //
    // These used to be read only by QueryParser's second pass over the leftover text, so they
    // filtered but never drew as chips. A screen that seeds `kind:article` needs them to pill
    // like every other filter, or the seeded query reads as if the reader had typed a word.

    @Test
    fun kindAliasIsAToken() {
        val kind = only<SearchSegment.Kind>("kind:article")
        assertEquals("kind:article", kind.raw)
        assertEquals("article", kind.alias)
        assertEquals(listOf(30023), kind.kinds)
        assertNull(kind.pseudoKind)
    }

    @Test
    fun kindNumberIsAToken() {
        assertEquals(listOf(20), only<SearchSegment.Kind>("kind:20").kinds)
    }

    @Test
    fun kindPseudoIsATokenAndCarriesNoKinds() {
        val kind = only<SearchSegment.Kind>("kind:media")
        assertEquals("media", kind.pseudoKind)
        assertTrue(kind.kinds.isEmpty())
    }

    @Test
    fun kindAliasIsCaseInsensitive() {
        assertEquals(listOf(30023), only<SearchSegment.Kind>("kind:Article").kinds)
    }

    @Test
    fun anUnresolvableKindStaysText() {
        // A chip saying `kind:banana` would claim a window the filter builder never sends.
        assertCovers("kind:banana")
        assertTrue(SearchTokenizer.tokenize("kind:banana").all { it is SearchSegment.Text })
        assertTrue(SearchTokenizer.tokenize("kind:99999").all { it is SearchSegment.Text })
        assertTrue(SearchTokenizer.tokenize("kind:").all { it is SearchSegment.Text })
    }

    @Test
    fun langIsAToken() {
        assertEquals("en", only<SearchSegment.Language>("lang:en").code)
        assertEquals("pt-br", only<SearchSegment.Language>("lang:pt-BR").code)
    }

    @Test
    fun aLanguageThatIsNotACodeStaysText() {
        assertCovers("lang:portuguese-brazilian-variant")
        assertTrue(SearchTokenizer.tokenize("lang:portuguese-brazilian-variant").all { it is SearchSegment.Text })
    }

    @Test
    fun domainIsAToken() {
        assertEquals("nostr.com", only<SearchSegment.Domain>("domain:nostr.com").host)
        assertEquals("a.b.example.co.uk", only<SearchSegment.Domain>("domain:A.B.Example.co.uk").host)
    }

    @Test
    fun aDomainWithASchemeOrPathStaysText() {
        // Those belong to `site:`, which asks a different tag entirely.
        assertCovers("domain:https://nostr.com")
        assertTrue(SearchTokenizer.tokenize("domain:https://nostr.com").none { it is SearchSegment.Domain })
        assertTrue(SearchTokenizer.tokenize("domain:localhost").none { it is SearchSegment.Domain })
    }

    @Test
    fun theNewTokensSettleOnlyOnceTheCaretHasLeftThem() {
        val text = "kind:article"
        // Caret still inside the token: it is a word being typed, not a chip.
        assertTrue(SearchTokenizer.drawable(text, text.length).all { it is SearchSegment.Text })
        // Caret gone: it settles.
        assertTrue(SearchTokenizer.drawable("$text ", "$text ".length).any { it is SearchSegment.Kind })
    }

    @Test
    fun aSeededQueryTokenizesEveryPartOfItself() {
        val input = "from:$NPUB kind:article #bitcoin lang:en "
        assertCovers(input)
        val drawn = SearchTokenizer.drawable(input, input.length)
        assertEquals(1, drawn.filterIsInstance<SearchSegment.Key>().size)
        assertEquals(1, drawn.filterIsInstance<SearchSegment.Kind>().size)
        assertEquals(1, drawn.filterIsInstance<SearchSegment.Hashtag>().size)
        assertEquals(1, drawn.filterIsInstance<SearchSegment.Language>().size)
    }
    // ---- -term and "a phrase" --------------------------------------------------------------
    //
    // Drawn only: the parser still owns what they mean, so these check that the chip covers
    // exactly the characters the filter reads, and that the query is left alone.

    @Test
    fun anExclusionIsAToken() {
        val ex = only<SearchSegment.Exclusion>("-scam")
        assertEquals("-scam", ex.raw)
        assertEquals("scam", ex.term)
    }

    @Test
    fun aBareMinusIsNotAnExclusion() {
        assertCovers("a - b")
        assertTrue(SearchTokenizer.tokenize("a - b").none { it is SearchSegment.Exclusion })
        assertTrue(SearchTokenizer.tokenize("-").none { it is SearchSegment.Exclusion })
    }

    @Test
    fun aHyphenInsideAWordIsNotAnExclusion() {
        assertCovers("well-known since:2026-01-01")
        assertTrue(SearchTokenizer.tokenize("well-known").none { it is SearchSegment.Exclusion })
    }

    @Test
    fun aNegatedHashtagIsLeftToTheHashtagSplitter() {
        // Claiming it here would silently turn "the bitcoin hashtag" into "not the word #bitcoin".
        assertTrue(SearchTokenizer.tokenize("-#bitcoin").none { it is SearchSegment.Exclusion })
        assertTrue(SearchTokenizer.tokenize("-#bitcoin").any { it is SearchSegment.Hashtag })
    }

    @Test
    fun aPhraseIsAToken() {
        val phrase = only<SearchSegment.Phrase>("\"hello world\"")
        assertEquals("\"hello world\"", phrase.raw)
        assertEquals("hello world", phrase.text)
    }

    @Test
    fun anUnterminatedPhraseRunsToTheEndLikeTheParserReadsIt() {
        val phrase = only<SearchSegment.Phrase>("\"hello world")
        assertEquals("\"hello world", phrase.raw)
        assertEquals("hello world", phrase.text)
    }

    @Test
    fun aQuoteInsideAWordIsNotAPhrase() {
        assertCovers("say\"what\" next")
        assertTrue(SearchTokenizer.tokenize("say\"what\" next").none { it is SearchSegment.Phrase })
    }

    @Test
    fun drawingThemDoesNotChangeWhatTheQueryMeans() {
        // The whole point of lifting these out is that only the rendering changes.
        val q = QueryParser.parse("bitcoin \"hello world\" -scam -airdrop")
        assertEquals(listOf("scam", "airdrop"), q.excludeTerms)
        assertTrue(q.text.contains("hello world"))
    }
}
