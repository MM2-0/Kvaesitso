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
package com.vitorpamplona.amethyst.commons.service.spellcheck

import kotlin.test.Test
import kotlin.test.assertEquals

class SpellCheckTokenizerTest {
    private fun words(text: String) = SpellCheckTokenizer.words(text).map { text.substring(it.start, it.end) }

    @Test
    fun splitsPlainProse() {
        assertEquals(listOf("Hello", "world", "How", "are", "you"), words("Hello, world! How are you?"))
    }

    @Test
    fun keepsContractionsWhole() {
        assertEquals(listOf("don't", "won’t", "it's"), words("don't won’t it's"))
    }

    @Test
    fun dropsTrailingQuotesAndPossessivePlurals() {
        assertEquals(listOf("say", "users"), words("'say' users'"))
    }

    @Test
    fun skipsLinks() {
        assertEquals(listOf("see", "and"), words("see https://exampel.com/pathh?x=teh and www.wrongg.org"))
    }

    @Test
    fun skipsNostrReferencesAndBareKeys() {
        val npub = "npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"
        assertEquals(listOf("hi", "and"), words("hi nostr:$npub and $npub"))
    }

    @Test
    fun skipsHashtagsMentionsShortcodesAndEmails() {
        assertEquals(listOf("tag", "ping", "smile", "mail"), words("tag #nostrrr ping @vitorr smile :blobcatt: mail me@exampel.com"))
    }

    @Test
    fun skipsEmailsButNotTheSentenceAroundThem() {
        assertEquals(listOf("write", "Thanks"), words("write first.last+tag@mail.exampel.com. Thanks"))
        assertEquals(listOf("meet", "noon", "or", "at"), words("meet @ noon or at@"))
    }

    @Test
    fun skipsRunsLongerThanAnyWord() {
        val blob = "qwertyuiopasdfghjklzxcvbnm".repeat(800)
        assertEquals(listOf("hello", "world"), words("hello $blob world"))
        assertEquals(listOf("Pneumonoultramicroscopicsilicovolcanoconiosis"), words("Pneumonoultramicroscopicsilicovolcanoconiosis"))
    }

    @Test
    fun skipsCode() {
        assertEquals(listOf("run", "then", "done"), words("run `gradlew asembl` then\n```\nfun mian() {}\n```\ndone"))
    }

    @Test
    fun skipsCodeBlockStillBeingTyped() {
        assertEquals(listOf("look"), words("look ```val fooo = barr"))
    }

    @Test
    fun skipsIdentifiersAcronymsAndNumbers() {
        assertEquals(listOf("ok"), words("ok NASA h4x0r 2nd camelCase snake_case file.txt x"))
    }

    @Test
    fun keepsAccentedAndOtherScripts() {
        assertEquals(listOf("café", "naïve", "привет"), words("café naïve привет"))
    }

    @Test
    fun keepsIndicWordsWhole() {
        // Devanagari vowel signs are spacing combining marks, not letters.
        assertEquals(listOf("नमस्ते", "हिन्दी"), words("नमस्ते हिन्दी"))
    }

    @Test
    fun reportsOffsets() {
        val ranges = SpellCheckTokenizer.words("  teh cat")
        assertEquals(listOf(WordRange(2, 5), WordRange(6, 9)), ranges)
    }
}
