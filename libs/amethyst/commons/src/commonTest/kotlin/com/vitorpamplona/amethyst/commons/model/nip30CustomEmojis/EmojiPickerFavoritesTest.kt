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
package com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis

import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.Favorite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class EmojiPickerFavoritesTest {
    private val pepe = EmojiMedia("pepe", "https://example.com/pepe.png")
    private val catJam = EmojiMedia("catjam", "https://example.com/catjam.gif")

    private fun describe(favorites: List<Favorite>) =
        favorites.map {
            when (it) {
                is Favorite.Unicode -> it.emoji
                is Favorite.Custom -> ":${it.media.code}:"
            }
        }

    @Test
    fun keepsUnicodeChoicesInOrderAndDropsLikeAndDislike() {
        val favorites = EmojiPackState.resolveFavorites(listOf("+", "🚀", "-", "😂", "🚀", " "), emptyList())
        assertEquals(listOf("🚀", "😂"), describe(favorites))
    }

    @Test
    fun keepsCustomEmojiOnlyWhileASelectedPackCarriesIt() {
        val choices = listOf(":pepe:https://example.com/pepe.png", ":gone:https://example.com/gone.png", "🎉")
        val favorites = EmojiPackState.resolveFavorites(choices, listOf(pepe, catJam))

        assertEquals(listOf(":pepe:", "🎉"), describe(favorites))
        assertSame(pepe, assertIs<Favorite.Custom>(favorites[0]).media)
    }

    @Test
    fun matchesByImageSoARenamedShortcodeStillInsertsThePacksCode() {
        val favorites = EmojiPackState.resolveFavorites(listOf(":oldname:https://example.com/catjam.gif"), listOf(pepe, catJam))
        assertEquals(listOf(":catjam:"), describe(favorites))
    }

    @Test
    fun resolvesABareShortcodeByCode() {
        assertEquals(listOf(":pepe:"), describe(EmojiPackState.resolveFavorites(listOf(":pepe:", ":nope:"), listOf(pepe))))
    }

    @Test
    fun theFirstPackUsingAShortcodeWinsIt() {
        val first = EmojiPackState.EmojiPackGroup("a", "A", null, listOf(EmojiMedia("heart", "https://a/heart.png"), pepe))
        val second = EmojiPackState.EmojiPackGroup("b", "B", null, listOf(EmojiMedia("heart", "https://b/heart.png"), EmojiMedia("love", pepe.link)))

        val codes = EmojiPackState.firstByCode(listOf(first, second))

        assertEquals(listOf("heart" to "https://a/heart.png", "pepe" to pepe.link, "love" to pepe.link), codes.map { it.code to it.link })
    }
}
