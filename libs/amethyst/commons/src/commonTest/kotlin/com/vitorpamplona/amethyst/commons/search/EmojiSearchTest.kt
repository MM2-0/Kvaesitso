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

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmojiSearchTest {
    private val author = "c230edd34ca5c8318bf4592ac056cde37519d395c0904c37ea1c650b8ad4a712"
    private val context = UserContext { addr -> AddressableNote(addr) }

    private fun pack(
        id: Char,
        title: String,
        vararg emojis: Pair<String, String>,
    ): Note {
        val event =
            EmojiPackEvent(
                id = id.toString().repeat(64),
                pubKey = author,
                createdAt = 1_800_000_000L,
                tags = arrayOf(arrayOf("d", title), arrayOf("title", title)) + emojis.map { arrayOf("emoji", it.first, it.second) },
                content = "",
                sig = "",
            )
        return Note(event.id).also { it.loadEvent(event, User(author, context), emptyList()) }
    }

    @Test
    fun termsDropTheColonsAReaderTypesAroundAShortcode() {
        assertEquals(listOf("blobcat"), EmojiSearch.terms(":blobcat:").all)
        assertEquals(listOf("blob", "cat"), EmojiSearch.terms("Blob CAT").all)
        assertTrue(EmojiSearch.terms("  ").isEmpty)
    }

    @Test
    fun anExclusionDropsTheEmojisThatHoldIt() {
        val cats = pack('a', "Cats", "catdance" to "https://x/1.gif", "catjam" to "https://x/2.gif")
        // As the app's parser hands it over: `-dance` is not in the text.
        val hits = EmojiSearch.find(listOf(cats), EmojiSearch.terms("cat", excludeTerms = listOf("dance")))
        assertEquals(listOf("catjam"), hits.map { it.emoji.code })
        // And as a NIP-50 exclusion left in the text.
        assertEquals(listOf("catjam"), EmojiSearch.find(listOf(cats), EmojiSearch.terms("cat -dance")).map { it.emoji.code })
    }

    @Test
    fun aQuotedPhraseMatchesItsWordsAcrossSeparators() {
        val cats = pack('a', "Cats", "blob_cat" to "https://x/1.png", "blobcat" to "https://x/2.png", "cat" to "https://x/3.png")
        val hits = EmojiSearch.find(listOf(cats), EmojiSearch.terms("\"blob cat\""))
        assertEquals(setOf("blob_cat", "blobcat"), hits.map { it.emoji.code }.toSet())
    }

    @Test
    fun anOrChainNeedsOneOfItsTerms() {
        val pets = pack('a', "Pets", "cat" to "https://x/1.png", "dog" to "https://x/2.png", "fish" to "https://x/3.png")
        val hits = EmojiSearch.find(listOf(pets), EmojiSearch.terms("", orTerms = listOf("cat", "dog")))
        assertEquals(setOf("cat", "dog"), hits.map { it.emoji.code }.toSet())
    }

    @Test
    fun keepsOnlyTheEmojisWhoseShortcodeHoldsEveryTerm() {
        val cats = pack('a', "Cats", "blobcat" to "https://x/1.png", "blobdog" to "https://x/2.png", "catjam" to "https://x/3.gif")
        val hits = EmojiSearch.find(listOf(cats), EmojiSearch.terms("blob cat"))
        assertEquals(listOf("blobcat"), hits.map { it.emoji.code })
        assertEquals(cats, hits.single().pack)
    }

    @Test
    fun exactBeforePrefixBeforeTheRest() {
        val one = pack('a', "One", "blobcat" to "https://x/1.png", "cat" to "https://x/2.png")
        val two = pack('b', "Two", "catjam" to "https://x/3.png")
        val hits = EmojiSearch.find(listOf(one, two), EmojiSearch.terms("cat"))
        assertEquals(listOf("cat", "catjam", "blobcat"), hits.map { it.emoji.code })
    }

    @Test
    fun theSameImageInTwoPacksIsOneHit() {
        val one = pack('a', "One", "cat" to "https://x/same.png")
        val two = pack('b', "Two", "kitty_cat" to "https://x/same.png")
        assertEquals(1, EmojiSearch.find(listOf(one, two), EmojiSearch.terms("cat")).size)
    }

    @Test
    fun aPackFoundByItsTitleGivesNoEmojis() {
        val apc = pack('a', "APC", "20261114_134139" to "https://x/1.jpg")
        assertTrue(EmojiSearch.find(listOf(apc), EmojiSearch.terms("apc")).isEmpty())
    }

    @Test
    fun aSearchWithNoTextAsksForPacksNotEmojis() {
        val cats = pack('a', "Cats", "blobcat" to "https://x/1.png")
        assertTrue(EmojiSearch.find(listOf(cats), EmojiSearch.terms("")).isEmpty())
    }
}
