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

import com.vitorpamplona.amethyst.commons.cartridges.CartLinks
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** A multi-cart note: the carts other than the one in the text are in its `imeta` tags only. */
class RichTextParserCartLinksTest {
    private val front = "https://example.com/front.png"
    private val main = "https://example.com/main.png"
    private val blossom = "blossom:${"b".repeat(64)}.png?xs=cdn.example.com"
    private val x = "a".repeat(64)

    private val frontTag = arrayOf("imeta", "url $front", "m image/png", "cart #game")
    private val mainTag = arrayOf("imeta", "url $main", "x $x", "m image/png", "dim 160x205", "cart #game_main-7")

    private fun parse(
        content: String,
        vararg imetas: Array<String>,
    ) = RichTextParser().parseText(content, ImmutableListOfLists(arrayOf(*imetas)), null)

    private fun RichTextViewerState.image(url: String) = assertIs<MediaUrlImage>(mediaForPager[url])

    private fun RichTextViewerState.linksOf(url: String) = assertNotNull(CartLinks.forEntry(url, assertNotNull(image(url).cartImetas)))

    @Test
    fun anExtraCartIsNotShownInTheNote() {
        val state = parse("Into Ruins $front", frontTag, mainTag)

        assertEquals(setOf(front), state.mediaForPager.keys)
        assertEquals(listOf(front), state.mediaList.map { it.url })
    }

    @Test
    fun theEntryReachesItsCarts() {
        val links = parse("Into Ruins $front", frontTag, mainTag).linksOf(front)

        assertEquals(listOf("#game"), links.ownNames)
        assertEquals(listOf("#game_main-7"), links.extras.single().names)
    }

    @Test
    fun anOrdinaryImageKeepsNoTags() {
        val state = parse("A photo $front", arrayOf("imeta", "url $front", "m image/png"))

        assertNull(state.image(front).cartImetas)
    }

    // Links built per image cost a stranger's note its tags times its images: 398 MB for a 290 KB note.
    @Test
    fun theImagesOfANoteShareOneReference() {
        val state = parse("$front https://example.com/photo.png", frontTag, mainTag)

        assertSame(state.image(front).cartImetas, state.image("https://example.com/photo.png").cartImetas)
    }

    @Test
    fun twoPlainCartsInANoteReachEachOther() {
        val plain = arrayOf("m image/png", "dim 160x205")
        val state = parse("Both carts $front $main", arrayOf("imeta", "url $front", "x ${"b".repeat(64)}", *plain), arrayOf("imeta", "url $main", "x $x", *plain))

        assertEquals(x, state.linksOf(front).partner?.sha256)
        assertEquals("b".repeat(64), state.linksOf(main).partner?.sha256)
    }

    @Test
    fun aBlossomEntryReachesItsCartsToo() {
        val state = parse("Into Ruins $blossom", arrayOf("imeta", "url $blossom", "m image/png", "cart #game"), mainTag)

        val links = state.linksOf(blossom)
        assertEquals(listOf("#game"), links.ownNames)
        assertEquals(1, links.extras.size)
        assertFalse(main in state.mediaForPager)
    }

    @Test
    fun aBlossomImageWithNoImetaIsTheSameAsBefore() {
        val image = parse("A photo $blossom").image(blossom)

        assertNull(image.cartImetas)
        assertNull(image.hash)
        assertNull(image.dim)
    }

    @Test
    fun aBlossomImageSeesItsImeta() {
        val image = parse("A photo $blossom", arrayOf("imeta", "url $blossom", "dim 100x200", "alt a cat", "x $x")).image(blossom)

        assertEquals(100, image.dim?.width)
        assertEquals(x, image.hash)
    }
}
