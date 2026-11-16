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
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueryParserTest {
    private companion object {
        const val VITOR_HEX = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    }

    @Test
    fun emptyInput() {
        val q = QueryParser.parse("")
        assertTrue(q.isEmpty)
        assertEquals(SearchQuery.EMPTY, q)
    }

    @Test
    fun whitespaceOnly() {
        val q = QueryParser.parse("   ")
        assertTrue(q.isEmpty)
    }

    @Test
    fun plainText() {
        val q = QueryParser.parse("bitcoin lightning")
        assertEquals("bitcoin lightning", q.text)
        assertTrue(q.authors.isEmpty())
        assertTrue(q.kinds.isEmpty())
    }

    @Test
    fun kindOperatorAlias() {
        val q = QueryParser.parse("kind:note")
        assertEquals(listOf(1), q.kinds.toList())
        assertTrue(q.text.isBlank())
    }

    @Test
    fun kindOperatorNumeric() {
        val q = QueryParser.parse("kind:30023")
        assertEquals(listOf(30023), q.kinds.toList())
    }

    @Test
    fun kindOperatorArticle() {
        val q = QueryParser.parse("kind:article")
        assertEquals(listOf(30023), q.kinds.toList())
    }

    @Test
    fun kindOperatorInvalid() {
        val q = QueryParser.parse("kind:invalid")
        // Unresolvable kind → treated as text
        assertEquals("kind:invalid", q.text)
        assertTrue(q.kinds.isEmpty())
    }

    @Test
    fun pseudoKindReply() {
        val q = QueryParser.parse("kind:reply")
        assertTrue(q.kinds.isEmpty())
        assertEquals(listOf("reply"), q.pseudoKinds.toList())
    }

    @Test
    fun pseudoKindMedia() {
        val q = QueryParser.parse("kind:media")
        assertTrue(q.kinds.isEmpty())
        assertEquals(listOf("media"), q.pseudoKinds.toList())
    }

    @Test
    fun multipleKinds() {
        val q = QueryParser.parse("kind:note kind:article")
        assertEquals(listOf(1, 30023), q.kinds.toList())
    }

    @Test
    fun sinceDate() {
        val q = QueryParser.parse("since:2025-01-01")
        assertEquals(LocalClock.startOfDay(SearchDate(2025, 1, 1)), q.since)
    }

    @Test
    fun sinceDateYearOnly() {
        val q = QueryParser.parse("since:2025")
        assertEquals(LocalClock.startOfDay(SearchDate(2025, 1, 1)), q.since)
    }

    @Test
    fun sinceDateYearMonth() {
        val q = QueryParser.parse("since:2025-06")
        // 2025-06-01 00:00:00 UTC
        val q2 = QueryParser.parse("since:2025-06-01")
        assertEquals(q2.since, q.since)
    }

    @Test
    fun sinceInvalidDate() {
        val q = QueryParser.parse("since:not-a-date")
        assertNull(q.since)
        assertEquals("since:not-a-date", q.text)
    }

    @Test
    fun untilDate() {
        val q = QueryParser.parse("until:2025-12-31")
        assertNull(q.since)
        assertTrue(q.until != null && q.until > 0)
    }

    @Test
    fun hashtag() {
        val q = QueryParser.parse("#bitcoin")
        assertEquals(listOf("bitcoin"), q.hashtags.toList())
        assertTrue(q.text.isBlank())
    }

    @Test
    fun multipleHashtags() {
        val q = QueryParser.parse("#bitcoin #nostr")
        assertEquals(listOf("bitcoin", "nostr"), q.hashtags.toList())
    }

    @Test
    fun negationTerm() {
        val q = QueryParser.parse("-spam")
        assertEquals(listOf("spam"), q.excludeTerms.toList())
        assertTrue(q.text.isBlank())
    }

    @Test
    fun multipleNegations() {
        val q = QueryParser.parse("-spam -scam")
        assertEquals(listOf("spam", "scam"), q.excludeTerms.toList())
    }

    @Test
    fun quotedPhrase() {
        val q = QueryParser.parse("\"exact phrase\"")
        assertEquals("\"exact phrase\"", q.text)
    }

    @Test
    fun orTerms() {
        val q = QueryParser.parse("bitcoin OR lightning")
        assertEquals(listOf("bitcoin", "lightning"), q.orTerms.toList())
        assertTrue(q.text.isBlank())
    }

    @Test
    fun orTermsWithOperators() {
        val q = QueryParser.parse("from:vitor bitcoin OR lightning kind:note")
        assertEquals(listOf("bitcoin", "lightning"), q.orTerms.toList())
        assertEquals(listOf(1), q.kinds.toList())
        // from:vitor → authorNames since it's not a valid npub
        assertEquals(listOf("vitor"), q.authorNames.toList())
    }

    @Test
    fun orTermsCappedAtThree() {
        val q = QueryParser.parse("a OR b OR c OR d OR e")
        assertEquals(3, q.orTerms.size)
        assertEquals(listOf("a", "b", "c"), q.orTerms.toList())
    }

    @Test
    fun orphanedOr() {
        val q = QueryParser.parse("OR")
        assertEquals("OR", q.text)
        assertTrue(q.orTerms.isEmpty())
    }

    @Test
    fun languageOperator() {
        val q = QueryParser.parse("lang:en bitcoin")
        assertEquals("en", q.language)
        assertEquals("bitcoin", q.text)
    }

    @Test
    fun domainOperator() {
        val q = QueryParser.parse("domain:nostr.com bitcoin")
        assertEquals("nostr.com", q.domain)
        assertEquals("bitcoin", q.text)
    }

    @Test
    fun combinedQuery() {
        val q = QueryParser.parse("kind:note since:2025-01-01 #bitcoin -spam lightning")
        assertEquals(listOf(1), q.kinds.toList())
        assertEquals(LocalClock.startOfDay(SearchDate(2025, 1, 1)), q.since)
        assertEquals(listOf("bitcoin"), q.hashtags.toList())
        assertEquals(listOf("spam"), q.excludeTerms.toList())
        assertEquals("lightning", q.text)
    }

    @Test
    fun caseInsensitiveOperators() {
        val q = QueryParser.parse("FROM:vitor KIND:Note")
        assertEquals(listOf("vitor"), q.authorNames.toList())
        assertEquals(listOf(1), q.kinds.toList())
    }

    @Test
    fun operatorWithNoValue() {
        val q = QueryParser.parse("from:")
        // Malformed → treated as text
        assertEquals("from:", q.text)
        assertTrue(q.authors.isEmpty())
    }

    @Test
    fun fromWithAuthorName() {
        val q = QueryParser.parse("from:vitor")
        assertEquals(listOf("vitor"), q.authorNames.toList())
        assertTrue(q.authors.isEmpty())
    }

    @Test
    fun multipleFromAuthors() {
        val q = QueryParser.parse("from:alice from:bob")
        assertEquals(listOf("alice", "bob"), q.authorNames.toList())
    }

    @Test
    fun duplicateAuthorsDeduped() {
        val q = QueryParser.parse("from:vitor from:vitor")
        assertEquals(1, q.authorNames.size)
    }

    @Test
    fun fromWithAHexKey() {
        val q = QueryParser.parse("from:$VITOR_HEX")
        assertEquals(listOf(VITOR_HEX), q.authors.toList())
        assertTrue(q.authorNames.isEmpty())
        // An uppercase key is the same author, stored lowercase.
        assertEquals(listOf(VITOR_HEX), QueryParser.parse("from:${VITOR_HEX.uppercase()}").authors.toList())
    }

    @Test
    fun fromWithAnNprofile() {
        val q = QueryParser.parse("from:${NProfile.create(VITOR_HEX, null)}")
        assertEquals(listOf(VITOR_HEX), q.authors.toList())
    }

    @Test
    fun fromWithAnEvenLengthNameIsNotAKey() {
        // The lenient hex fallback would read these 14/6-char names as noise nibbles and filter
        // by a garbage author; they are names, and names wait for a resolution.
        val bit = QueryParser.parse("from:mstrofnone.bit")
        assertTrue(bit.authors.isEmpty())
        assertEquals(listOf("mstrofnone.bit"), bit.authorNames.toList())

        val d = QueryParser.parse("from:d/alice")
        assertTrue(d.authors.isEmpty())
        assertEquals(listOf("d/alice"), d.authorNames.toList())

        // to: has no name list — an undecodable value is plain text, not a garbage mention.
        val to = QueryParser.parse("to:mstrofnone.bit")
        assertTrue(to.mentions.isEmpty())
        assertEquals("to:mstrofnone.bit", to.text)
    }

    @Test
    fun parseDateToTimestamp_validDates() {
        // A bound is the reader's own midnight, not UTC's: a search saved in Auckland and
        // reopened in Sao Paulo must still name the same day. So this asserts against the
        // platform clock rather than a hard-coded epoch second.
        assertEquals(LocalClock.startOfDay(SearchDate(1970, 1, 1)), QueryParser.parseDateToTimestamp("1970-01-01"))
        assertEquals(LocalClock.startOfDay(SearchDate(2000, 1, 1)), QueryParser.parseDateToTimestamp("2000-01-01"))
    }

    @Test
    fun parseDateToTimestamp_partialSpellingsNameASpan() {
        // The tokenizer already took the full ISO day; what reaches here names a span, and each
        // end of it takes the end of the span it is bounding.
        assertEquals(LocalClock.startOfDay(SearchDate(2026, 1, 1)), QueryParser.parseDateToTimestamp("2026", DateField.SINCE))
        assertEquals(LocalClock.endOfDay(SearchDate(2026, 12, 31)), QueryParser.parseDateToTimestamp("2026", DateField.UNTIL))
        assertEquals(LocalClock.startOfDay(SearchDate(2026, 2, 1)), QueryParser.parseDateToTimestamp("2026-02", DateField.SINCE))
        assertEquals(LocalClock.endOfDay(SearchDate(2026, 2, 28)), QueryParser.parseDateToTimestamp("2026-02", DateField.UNTIL))
    }

    @Test
    fun parseDateToTimestamp_invalidDates() {
        assertNull(QueryParser.parseDateToTimestamp("not-a-date"))
        assertNull(QueryParser.parseDateToTimestamp("1800-01-01"))
        assertNull(QueryParser.parseDateToTimestamp("2025-13-01"))
        assertNull(QueryParser.parseDateToTimestamp("2025-01-32"))
    }

    @Test
    fun unicodeInFreeText() {
        val q = QueryParser.parse("bitcoin 日本語 🚀")
        assertFalse(q.isEmpty)
        assertTrue(q.text.contains("日本語"))
        assertTrue(q.text.contains("🚀"))
    }

    @Test
    fun veryLongQuery() {
        val longText = "word ".repeat(100).trim()
        val q = QueryParser.parse(longText)
        assertFalse(q.isEmpty)
    }
}
