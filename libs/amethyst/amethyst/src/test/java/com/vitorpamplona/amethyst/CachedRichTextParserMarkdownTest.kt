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
package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedRichTextParserMarkdownTest {
    // ---- Cashu false-positive regression --------------------------------

    private val cashuBToken =
        "cashuBv2FteCJodHRwczovL21pbnQubWluaWJpdHMuY2FzaC9CaXRjb2luYXVjc2F0YWRkVEVTVGF0n79haUgAEHk32wzI" +
            "ZWFwn79hYQJhc3hAZWM1YWI3Yjc1NjViYjBjZTZhNzg2NzBkMDA0OGExMjVlZGQzMjJhYmVjMTEzYWMwZTBmZGVkZmE3NTQ4Mzg3OWFj" +
            "WCED0-ops8Ta6NjKChNJPe_jgIbXLlyxg2KSy2WaSTADo5D_v2FhCGFzeEBkNDZlODU5MDExNjU0NmNjZjAwNTE3ZTQ1NmU0MTY0N2Fm" +
            "ZWUxOWNlMzY2N2IzYTcxODZkMzEwZDY1MjM3OTM4YWNYIQMTDGTY943O4ojhKopoYdemsUE2rSLfzwNBODL8WgOX0v______"

    @Test
    fun cashuBTokenWithTrailingUnderscoresIsNotMarkdown() {
        assertFalse(CachedRichTextParser.isMarkdown(cashuBToken))
    }

    @Test
    fun cashuBTokenInLongerMessageIsNotMarkdown() {
        assertFalse(CachedRichTextParser.isMarkdown("Here, send to a friend: $cashuBToken"))
    }

    @Test
    fun cashuATokenIsNotMarkdown() {
        val fakeCashuA = "cashuAeyJ0b2tlbiI6W3sicHJvb2ZzIjpbXX1dfQ___"
        assertFalse(CachedRichTextParser.isMarkdown(fakeCashuA))
    }

    // ---- ATX headings ---------------------------------------------------

    @Test fun atxH1IsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("# Heading"))

    @Test fun atxH2IsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("## Heading"))

    @Test fun atxH6IsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("###### Heading"))

    @Test fun atxSevenHashesIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("####### Not a heading"))

    @Test fun atxWithoutSpaceIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("#NotAHeading"))

    @Test fun atxWithLeadingSpacesIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("   # Heading"))

    @Test fun atxMidLineIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("issue # 123 in flight"))

    @Test fun atxAfterNewlineIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("intro\n# Heading"))

    // ---- Line-leading markers after a BLANK line ------------------------
    // Regression: the standard `\n\n#` spacing (a blank line before a
    // heading) was undetected because the blank line's second '\n' flipped
    // the line-start tracker off. NIP-23 long-form articles that are pure
    // prose plus `## Section` headings rendered as raw text as a result.

    @Test fun atxAfterBlankLineIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("intro\n\n# Heading"))

    @Test fun atxH3AfterBlankLineIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("intro\n\n### Heading"))

    @Test fun atxAfterMultipleBlankLinesIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("intro\n\n\n\n## Heading"))

    @Test fun atxAfterCrlfBlankLineIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("intro\r\n\r\n## Heading"))

    @Test fun blockquoteAfterBlankLineIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("intro\n\n> quoted"))

    @Test fun bulletAfterBlankLineIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("intro\n\n- item"))

    @Test fun orderedListAfterBlankLineIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("intro\n\n1. first"))

    @Test
    fun proseArticleWithHeadingAfterBlankLineIsMarkdown() =
        assertTrue(
            CachedRichTextParser.isMarkdown(
                "The cryptographic part of commerce has been solved.\n\n" +
                    "What follows is a history.\n\n" +
                    "### The merchant posts of the Hansa\n\n" +
                    "Long before any king claimed a monopoly on letters.",
            ),
        )

    // Blank lines alone must NOT promote ordinary prose to markdown.
    @Test
    fun plainProseWithBlankLinesIsNotMarkdown() =
        assertFalse(
            CachedRichTextParser.isMarkdown(
                "Just a normal sentence.\n\nAnother paragraph with no markdown at all.",
            ),
        )

    // ---- Blockquote -----------------------------------------------------

    @Test fun blockquoteIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("> a quote"))

    @Test fun blockquoteMidLineIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("if x > 0 then bail"))

    @Test fun blockquoteAfterNewlineIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("intro\n> quoted"))

    // ---- Bullet lists ---------------------------------------------------

    @Test fun bulletDashIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("- item"))

    @Test fun bulletStarIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("* item"))

    @Test fun bulletPlusIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("+ item"))

    @Test fun dashWithoutSpaceIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("-not-a-bullet"))

    @Test fun hyphenatedNameIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("Dr. Smith-Jones called"))

    @Test fun mathPlusIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("1+1=2 right?"))

    @Test fun multiplicationStarIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("5 * 3 equals 15"))

    // ---- Ordered lists --------------------------------------------------

    @Test fun orderedListSingleDigitIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("1. first"))

    @Test fun orderedListMultiDigitIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("42. answer"))

    @Test fun digitWithoutDotSpaceIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("1234 sats received"))

    @Test fun digitDotWithoutSpaceIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("v1.0 released"))

    // ---- Emphasis / strong ----------------------------------------------

    @Test fun boldStarsIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("hello **bold** world"))

    @Test fun boldUnderscoresIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("hello __bold__ world"))

    @Test fun italicStarIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("*emphasis*"))

    @Test fun italicUnderscoreIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("_emphasis_"))

    @Test fun loneStarIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("look at that *"))

    @Test fun loneUnderscoreIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("foo_bar style"))

    @Test fun underscoreAcrossNewlineIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("first line _\nsecond line _"))

    // CommonMark §6.2 forbids `_` from opening/closing emphasis intraword.
    // These cases pin down the snake_case carve-out that also protects
    // base64url cashu tokens from false-firing the markdown route.
    @Test fun snakeCaseIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("call snake_case_identifier here"))

    @Test fun pythonDunderSurroundedByWhitespaceIsMarkdown() {
        // CommonMark §6.2: `__text__` flanked by whitespace is strong
        // emphasis. Python identifiers like `__init__` collide with
        // this — there is no language-specific carve-out. Users who
        // want literal dunders should wrap them in backticks.
        assertTrue(CachedRichTextParser.isMarkdown("typedef __init__ method"))
    }

    @Test fun intrawordDoubleUnderscoreIsNotMarkdown() {
        // But `foo__init__bar` (intraword) is not strong emphasis
        // and must stay rich-text — same rule that protects cashuB.
        assertFalse(CachedRichTextParser.isMarkdown("foo__init__bar literal"))
    }

    @Test fun intrawordTripleUnderscoreIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("var foo___bar end"))

    @Test fun underscoreRunAtEndOfWordIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("hash 0v______ end"))

    @Test fun intrawordAsteriskIsMarkdown() {
        // CommonMark allows intraword `*` emphasis — keep behaving
        // the same as a reference renderer so `foo*bar*baz` lights up.
        assertTrue(CachedRichTextParser.isMarkdown("call foo*bar*baz here"))
    }

    @Test fun mathSpacedTripleStarIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("5 * 3 * 7 = 105"))

    @Test fun emailWithUnderscoreIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("ping me at user_name@example.com"))

    @Test fun arbitraryBase64UrlBlobIsNotMarkdown() {
        // Same shape as the cashuB payload but without the `cashuB`
        // prefix, so the upfront shortcut can't help — proves the
        // intraword rule alone keeps base64url blobs out of the
        // markdown route.
        val blob =
            "v2FteCJodHRwczovL21pbnQubWluaWJpdHMuY2FzaC9CaXRjb2luYXVjc2F0YWRkVEVTVGF0n79haUgAEHk32wzI" +
                "ZWFwn79hYQJhc3hAZWM1YWI3Yjc1NjViYjBjZTZhNzg2NzBkMDA0OGExMjVlZGQzMjJhYmVjMTEzYWMwZTBmZGVkZmE3NTQ4Mzg3OWFj" +
                "WCED0-ops8Ta6NjKChNJPe_jgIbXLlyxg2KSy2WaSTADo5D_v2FhCGFzeEBkNDZlODU5MDExNjU0NmNjZjAwNTE3ZTQ1NmU0MTY0N2Fm" +
                "ZWUxOWNlMzY2N2IzYTcxODZkMzEwZDY1MjM3OTM4YWNYIQMTDGTY943O4ojhKopoYdemsUE2rSLfzwNBODL8WgOX0v______"
        assertFalse(CachedRichTextParser.isMarkdown(blob))
    }

    // ---- Code -----------------------------------------------------------

    @Test fun backtickCodeSpanIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("run `ls -la` here"))

    @Test fun loneBacktickIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("the ` char"))

    @Test fun fencedCodeBlockIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("```\ncode\n```"))

    // ---- Links ----------------------------------------------------------

    @Test fun mdLinkIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("see [the docs](https://example.com)"))

    @Test fun bracketWithoutParenIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("array [1, 2, 3]"))

    @Test fun bracketWithNewlineBeforeParenIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("note [draft\n(later)"))

    // ---- Tables ---------------------------------------------------------

    @Test fun pipeAnywhereIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("| col1 | col2 |"))

    @Test fun shellPipeAlsoTriggersMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("cat foo | grep bar"))

    // ---- Strikethrough --------------------------------------------------

    @Test fun strikethroughIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("~~struck~~ through"))

    @Test fun loneTildeIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("approximately ~ 5 sats"))

    // ---- Setext headings ------------------------------------------------

    @Test fun setextH1IsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("Title\n==="))

    @Test fun setextH2IsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("Subtitle\n---"))

    @Test fun setextWithTrailingNewlineIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("Title\n===\nmore"))

    @Test fun horizontalRuleDashesIsMarkdown() = assertTrue(CachedRichTextParser.isMarkdown("---"))

    @Test fun twoEqualsIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("a==b"))

    // ---- Plain text controls --------------------------------------------

    @Test fun plainTextIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("hello world"))

    @Test fun emptyIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown(""))

    @Test fun singleSpaceIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown(" "))

    @Test fun urlWithUnderscorePathIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("https://example.com/path/with_underscore"))

    @Test fun npubAndHashtagIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("hi #amethyst nostr:npub1xyz"))

    @Test fun unicodeIsNotMarkdown() = assertFalse(CachedRichTextParser.isMarkdown("café ☕️ — naïve résumé"))

    // ---- Suite from the proposed algorithm ------------------------------
    // Drives the whole detector against a categorised case list and dumps
    // pass/fail diagnostics to stdout so a failure tells you which case
    // regressed without re-running individual @Test methods.
    @Test
    fun testMarkdown() {
        var passed = true

        fun assertResult(
            input: String,
            expected: Boolean,
            caseName: String,
        ) {
            val actual = CachedRichTextParser.isMarkdown(input)
            if (actual != expected) {
                println("❌ FAIL: $caseName")
                println("   Input: [${input.replace("\n", "\\n")}]")
                println("   Expected: $expected, Actual: $actual\n")
                passed = false
            } else {
                println("✅ PASS: $caseName")
            }
        }

        println("--- RUNNING MARKDOWN DETECTION TESTS ---\n")

        // =================================================================
        // 1. FALSE NEGATIVE TESTS (Valid Markdown that MUST return true)
        // =================================================================
        assertResult("# Heading 1", true, "ATX Header")
        assertResult("###### Heading 6", true, "Max ATX Header")
        assertResult("> This is a blockquote", true, "Blockquote")
        assertResult("- Item 1\n- Item 2", true, "Unordered List (hyphen)")
        assertResult("* Item 1", true, "Unordered List (asterisk)")
        assertResult("1. First item\n2. Second item", true, "Ordered List")
        assertResult("This has **bold** text", true, "Inline Bold")
        assertResult("This has _italic_ text", true, "Inline Italic")
        assertResult("Click [here](https://example.com)", true, "Markdown Link")
        assertResult("An image: ![alt](img.png)", true, "Markdown Image")
        assertResult("Use `val x = 1` here", true, "Inline Code Block")
        assertResult("```\nfun test() {}\n```", true, "Fenced Code Block Block")
        assertResult("~~strikethrough~~", true, "Strikethrough")
        assertResult("| Title | Description |\n|---|---|", true, "Markdown Table")
        assertResult("Heading\n===", true, "Setext Header (Equals)")
        assertResult("Heading\n---", true, "Setext Header / Horizontal Rule")

        // =================================================================
        // 2. FALSE POSITIVE TESTS (Plain text that MUST return false)
        // =================================================================
        assertResult("Hello World!", false, "Plain Text")
        assertResult("", false, "Empty String")
        assertResult("   \n   ", false, "Whitespace Only")
        assertResult("#NotAHeader because no space", false, "Invalid Header (No Space)")
        assertResult("####### Too many hashes", false, "Invalid Header (7 Hashes)")
        assertResult("I want #1 prize", false, "Mid-text hash symbol")
        assertResult("My email is test_underscore@domain.com", false, "Single dangling underscore")
        assertResult("Multiply 5 * 5 = 25", false, "Single dangling asterisk")
        assertResult("This is a [broken link with no parenthesis", false, "Unclosed square bracket")
        assertResult("Shopping list: 1. Milk, 2. Eggs, 3. Bread", false, "Mid-line numbering loop")
        assertResult("Is 5 > 3? Yes.", false, "Math greater-than symbol")
        assertResult("The price went up C++ instead of down", false, "Plus sign mid-text")
        assertResult("Just a normal sentence ending with a hyphen-\nNext line", false, "Single trailing hyphen")
        // The original suite asserted `false` here, but the case name
        // and the symmetric `"Heading\n==="` → true line above both
        // identify this as a setext H1. CommonMark gives no length cap
        // that would distinguish "heading" from "long divider", so the
        // detector and this expectation agree on true.
        assertResult("Standard divider line\n==========", true, "Valid Setext / Text Divider match")

        // =================================================================
        // 3. EDGE CASES / STRESS TESTS
        // =================================================================
        assertResult("a\n***\nb", true, "Horizontal Rule mid-text")
        assertResult("[link](url\nwith-newline)", false, "Link breaking across newline")
        assertResult("Double spaces  **bold** spaces", true, "Spaced out bold tags")
        assertResult("123456789. Numbered list with huge index", true, "Large index ordered list")

        assertTrue("One or more markdown cases failed — see stdout for the list", passed)
    }
}
