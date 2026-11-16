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
package com.vitorpamplona.amethyst.commons.richtext

import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RichTextParserMultibyteTest {
    @Test
    fun testFullTextWithMultibyte() {
        // Multibyte characters around an email address should not produce URL/Link segments
        val text =
            "マルチバイト文字テストuser@example.com ほげほげ"

        val state =
            RichTextParser()
                .parseText(text, EmptyTagList, null)

        val allSegments =
            state.paragraphs
                .flatMap { it.words }

        // user@example.com should be EmailSegment
        assertTrue(
            allSegments.any { it is EmailSegment && it.segmentText == "user@example.com" },
            "user@example.com should be EmailSegment",
        )

        // user@example.com should NOT be a LinkSegment
        assertTrue(
            allSegments.none { it is LinkSegment && it.segmentText == "user@example.com" },
            "user@example.com should not be a LinkSegment",
        )

        // user@example.com should not be in urlSet
        assertTrue(
            !state.urlSet.withScheme.contains("user@example.com") && !state.urlSet.withoutScheme.contains("user@example.com"),
            "user@example.com should not be in urlSet",
        )
    }

    @Test
    fun testHttpWithoutSpaces() {
        // Multibyte characters around an email address should not produce URL/Link segments
        val text =
            "Vitor, vocêhttp://test.com? \uD83E\uDD7A"

        val state =
            RichTextParser()
                .parseText(text, EmptyTagList, null)

        assertEquals(
            "Vitor, você http://test.com ? \uD83E\uDD7A",
            state.paragraphs.joinToString("\n") { it.words.joinToString(" ") { it.segmentText } },
        )
    }

    @Test
    fun testHttpWithoutSpacesJapan() {
        // Multibyte characters around an email address should not produce URL/Link segments
        val text =
            "Vitor, vocêhttp://test.comほげほげ"

        val state =
            RichTextParser()
                .parseText(text, EmptyTagList, null)

        assertEquals(
            "Vitor, você http://test.com ほげほげ",
            state.paragraphs.joinToString("\n") { it.words.joinToString(" ") { it.segmentText } },
        )
    }

    @Test
    fun testHttpWithoutSpacesJapan2() {
        // Multibyte characters around an email address should not produce URL/Link segments
        val text =
            "Vitor, vocêhttp://test.com。ほげほげ"

        val state =
            RichTextParser()
                .parseText(text, EmptyTagList, null)

        assertEquals(
            "Vitor, você http://test.com 。ほげほげ",
            state.paragraphs.joinToString("\n") { it.words.joinToString(" ") { it.segmentText } },
        )
    }

    @Test
    fun testFullTextWithMultibyteAndLatinAccents() {
        // Multibyte characters around an email address should not produce URL/Link segments
        val text =
            "Vitor, você tem como colocar alguma forma de aviso se o link vai carregar uma imagem ou um vídeo? \uD83E\uDD7A"

        val state =
            RichTextParser()
                .parseText(text, EmptyTagList, null)

        assertEquals(
            "Vitor, você tem como colocar alguma forma de aviso se o link vai carregar uma imagem ou um vídeo? \uD83E\uDD7A",
            state.paragraphs.joinToString("\n") { it.words.joinToString(" ") { it.segmentText } },
        )
    }

    @Test
    fun testFullTextWithMultibyteAndQuotes() {
        // Multibyte characters around an email address should not produce URL/Link segments
        val text =
            "I’ve been thinking lately about how I believe there will more than likely be models unattainable by most. Think Bloomberg Terminal. Where their cost of tokens is too high for the average lay person, but their level of “cognition” is unmatched by anything else. I’m sure there will even be many closed models that are invite only. Crazy times ahead."

        val state =
            RichTextParser()
                .parseText(text, EmptyTagList, null)

        assertEquals(
            "I’ve been thinking lately about how I believe there will more than likely be models unattainable by most. Think Bloomberg Terminal. Where their cost of tokens is too high for the average lay person, but their level of “cognition” is unmatched by anything else. I’m sure there will even be many closed models that are invite only. Crazy times ahead.",
            state.paragraphs.joinToString("\n") { it.words.joinToString(" ") { it.segmentText } },
        )
    }

    @Test
    fun testEmailSegmentStandalone() {
        val text = "user@example.com"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val allSegments = state.paragraphs.flatMap { it.words }
        assertTrue(
            allSegments.any { it is EmailSegment && it.segmentText == "user@example.com" },
            "user@example.com should be EmailSegment",
        )
    }

    @Test
    fun testMultibytePrefix_SchemelessUrl() {
        // ああexample.com → RegularText(ああ) + SchemelessUrl(example.com)
        val text = "ああexample.com"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val allSegments = state.paragraphs.flatMap { it.words }

        val urlSegments = allSegments.filterIsInstance<SchemelessUrlSegment>()
        assertTrue(urlSegments.isNotEmpty(), "Should have SchemelessUrlSegment")
        assertTrue(urlSegments.any { it.segmentText == "example.com" }, "URL should be example.com")

        val textSegments = allSegments.filterIsInstance<RegularTextSegment>()
        assertTrue(textSegments.any { it.segmentText == "ああ" }, "Should have prefix ああ")
    }

    @Test
    fun testMultibyteSuffix_SchemelessUrl() {
        // example.comああ → SchemelessUrl(example.com) + RegularText(ああ)
        val text = "example.comああ"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val allSegments = state.paragraphs.flatMap { it.words }

        val urlSegments = allSegments.filterIsInstance<SchemelessUrlSegment>()
        assertTrue(urlSegments.isNotEmpty(), "Should have SchemelessUrlSegment")
        assertTrue(urlSegments.any { it.segmentText == "example.com" }, "URL should be example.com")

        val textSegments = allSegments.filterIsInstance<RegularTextSegment>()
        assertTrue(textSegments.any { it.segmentText == "ああ" }, "Should have suffix ああ")
    }

    @Test
    fun testMultibytePrefix_Email() {
        // ほむほむuser@example.comほげほげ → RegularText(ほむほむ) + Email(user@example.com) + RegularText(ほげほげ)
        val text = "ほむほむuser@example.comほげほげ"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val allSegments = state.paragraphs.flatMap { it.words }

        val emailSegment = allSegments.filterIsInstance<EmailSegment>()
        assertTrue(emailSegment.isNotEmpty(), "Should have EmailSegment")
        assertTrue(emailSegment.any { it.segmentText == "user@example.com" }, "Email should be user@example.com")

        val textSegments = allSegments.filterIsInstance<RegularTextSegment>()
        assertTrue(textSegments.any { it.segmentText == "ほむほむ" }, "Should have prefix ほむほむ")
        assertTrue(textSegments.any { it.segmentText == "ほげほげ" }, "Should have suffix ほげほげ")
    }

    @Test
    fun testEmailWithSpaceAndMultibyteText() {
        // user@example.com ふがふが → Email(user@example.com) + RegularText(ふがふが)
        val text = "user@example.com ふがふが"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val allSegments = state.paragraphs.flatMap { it.words }

        val emailSegment = allSegments.filterIsInstance<EmailSegment>()
        assertTrue(emailSegment.isNotEmpty(), "Should have EmailSegment")
        assertTrue(emailSegment.any { it.segmentText == "user@example.com" }, "Email should be user@example.com")

        val textSegments = allSegments.filterIsInstance<RegularTextSegment>()
        assertTrue(textSegments.any { it.segmentText == "ふがふが" }, "Should have suffix ふがふが")
    }
}
