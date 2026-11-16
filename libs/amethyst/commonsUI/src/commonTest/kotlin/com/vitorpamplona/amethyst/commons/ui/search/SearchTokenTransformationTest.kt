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
package com.vitorpamplona.amethyst.commons.ui.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.SpanStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What each token draws as, and that the runs a transformation plans still add up to the text it
 * was given. Mapping the caret across a chip is the text field's own job since the move to
 * `OutputTransformation`, so it is not re-tested here.
 */
class SearchTokenTransformationTest {
    private companion object {
        const val NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
        val STYLES =
            SearchTokenStyles(
                person = SpanStyle(),
                pointer = SpanStyle(),
                hashtag = SpanStyle(),
                date = SpanStyle(),
                label = SpanStyle(),
                scope = SpanStyle(),
                group = SpanStyle(),
                kind = SpanStyle(),
                extension = SpanStyle(),
                exclusion = SpanStyle(),
                phrase = SpanStyle(),
            )
    }

    private fun runs(
        text: String,
        caret: Int? = null,
        name: (String) -> String? = { null },
        groupName: (String) -> String? = { null },
        scopeName: (String, String) -> String? = { _, _ -> null },
    ): List<DrawnRun> = SearchTokenTransformation({ caret }, STYLES, name, groupName, scopeName).runs(text)

    private fun transform(
        text: String,
        caret: Int? = null,
        name: (String) -> String? = { null },
        groupName: (String) -> String? = { null },
        scopeName: (String, String) -> String? = { _, _ -> null },
    ): String = runs(text, caret, name, groupName, scopeName).drawnText()

    /**
     * The runs tile [text] end to end, and splicing them into a real text buffer yields the same
     * drawn string they add up to — which is what the field shows.
     */
    private fun assertAppliesCleanly(
        text: String,
        caret: Int? = null,
        name: (String) -> String? = { null },
        groupName: (String) -> String? = { null },
        scopeName: (String, String) -> String? = { _, _ -> null },
    ) {
        val runs = runs(text, caret, name, groupName, scopeName)
        var at = 0
        runs.forEach {
            assertEquals(at, it.start, "runs of \"$text\" leave a gap or overlap at $at")
            at = it.end
        }
        assertEquals(text.length, at, "runs of \"$text\" stop short of its end")

        val buffer = TextFieldState(text)
        buffer.edit { replaceRuns(text, runs) }
        assertEquals(runs.drawnText(), buffer.text.toString(), "splicing \"$text\"")
    }

    @Test
    fun plainTextIsDrawnUnchangedAndMapsOneToOne() {
        val out = transform("bitcoin lightning")
        assertEquals("bitcoin lightning", out)
        assertAppliesCleanly("bitcoin lightning")
    }

    @Test
    fun sameLengthTokensDoNotMoveAnyOffset() {
        // A hashtag, a date, a group and a label all draw as themselves — only tinted.
        val text = "#bitcoin since:2026-04-01 group:dev label:en"
        val out = transform(text)
        assertEquals(text, out)
        // Nothing changes length, so nothing is spliced: the field keeps a one-to-one mapping.
        assertTrue(runs(text).all { it.drawn.length == it.end - it.start })
    }

    @Test
    fun aKnownKeyDrawsAsItsOwnersName() {
        val out = transform("from:$NPUB", name = { "Alice" })
        assertEquals("from:Alice", out)
    }

    @Test
    fun anUnknownKeyDrawsShortenedRatherThanNamed() {
        val out = transform("from:$NPUB")
        assertTrue(out.startsWith("from:npub1"))
        assertTrue(out.contains("…"))
        assertTrue(out.length < "from:$NPUB".length)
    }

    @Test
    fun textAfterAShortenedKeyIsDrawnUnchanged() {
        val text = "from:$NPUB rest"
        val out = transform(text, name = { "Alice" })
        assertEquals("from:Alice rest", out)
        assertAppliesCleanly(text, name = { "Alice" })
    }

    @Test
    fun twoChipsBothShiftWhatFollowsThem() {
        val text = "from:$NPUB to:$NPUB tail"
        assertEquals("from:Al to:Al tail", transform(text, name = { "Al" }))
        assertAppliesCleanly(text, name = { "Al" })
    }

    @Test
    fun everyFixtureAppliesCleanlyAtEveryCaret() {
        listOf(
            "",
            "bitcoin",
            "from:$NPUB",
            "from:$NPUB #bitcoin since:2026-04-01",
            "to:$NPUB group:dev label:review/app site:example.com tail",
        ).forEach { text ->
            assertAppliesCleanly(text)
            assertAppliesCleanly(text, name = { "A very long display name indeed" })
            (0..text.length).forEach { caret -> assertAppliesCleanly(text, caret) }
        }
    }

    @Test
    fun aKnownGroupDrawsAsItsNameAndAnUnknownOneKeepsItsId() {
        // An id typed from memory is unverifiable; the name is the only part a reader can check
        // against the room they meant.
        assertEquals("group:Dev Chat", transform("group:abc123", groupName = { "Dev Chat" }))
        assertEquals("group:abc123", transform("group:abc123"))
    }

    @Test
    fun aGeohashDrawsAsItsCityOnceTheCacheHasOne() {
        assertEquals("geo:San Francisco", transform("geo:9q8yy", scopeName = { f, _ -> if (f == "geo") "San Francisco" else null }))
        // Null means not resolved yet, which must leave the geohash showing rather than a guess.
        assertEquals("geo:9q8yy", transform("geo:9q8yy"))
    }

    @Test
    fun namedTokensStillApplyCleanly() {
        listOf("group:abc123 tail", "geo:9q8yy tail", "group:abc123 geo:9q8yy #tag").forEach { text ->
            assertAppliesCleanly(text, groupName = { "A Much Longer Group Name Than The Id" }, scopeName = { _, _ -> "Reykjavik" })
            assertAppliesCleanly(text, groupName = { "x" }, scopeName = { _, _ -> "y" })
        }
    }

    @Test
    fun aSettlingTokenUnderTheCaretIsDrawnAsPlainText() {
        val text = "#bitcoin"
        assertEquals(text, transform(text, caret = text.length))
    }

    @Test
    fun aNumericKindDrawsUnderItsName() {
        assertEquals("kind:picture", transform("kind:20"))
        assertAppliesCleanly("kind:20")
        assertAppliesCleanly("from:$NPUB kind:20 #bitcoin lang:en ")
    }

    @Test
    fun aKindThatAlreadyNamesItselfIsDrawnAsTyped() {
        assertEquals("kind:article", transform("kind:article"))
        assertEquals("kind:media", transform("kind:media"))
        // 30312 alone is not the whole `live` group, so it keeps its number.
        assertEquals("kind:30312", transform("kind:30312"))
    }

    @Test
    fun languageAndDomainDrawAsTyped() {
        assertEquals("lang:en domain:nostr.com", transform("lang:en domain:nostr.com"))
        assertAppliesCleanly("lang:en domain:nostr.com")
    }

    @Test
    fun aPhraseDrawsWithoutItsQuotes() {
        assertEquals("hello world", transform("\"hello world\""))
        assertAppliesCleanly("\"hello world\"")
        assertAppliesCleanly("bitcoin \"hello world\" -scam")
    }

    @Test
    fun anExclusionKeepsItsMinusSoItStillReadsAsOne() {
        assertEquals("-scam", transform("-scam"))
        assertAppliesCleanly("bitcoin -scam -airdrop")
    }

    @Test
    fun anUnterminatedQuoteDrawsTheRestOfTheLine() {
        // The parser reads it to the end of the input, so the chip has to cover the same span.
        assertEquals("hello world", transform("\"hello world"))
        assertAppliesCleanly("\"hello world")
    }

    @Test
    fun aBlankNameIsNotAName() {
        // A profile whose name is blank must not erase its chip: it keeps the short npub instead.
        val out = transform(NPUB, name = { "  " })
        assertTrue(out.startsWith("npub1") && out.contains("…"), out)
        assertEquals("group:abc123", transform("group:abc123", groupName = { "" }))
        assertEquals("geo:9q8yy", transform("geo:9q8yy", scopeName = { _, _ -> " " }))
    }
}
