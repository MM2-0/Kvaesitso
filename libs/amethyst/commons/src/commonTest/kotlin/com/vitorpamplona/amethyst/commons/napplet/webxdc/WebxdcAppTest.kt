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
package com.vitorpamplona.amethyst.commons.napplet.webxdc

import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.LinkSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.WebxdcSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebxdcAppTest {
    private val quakeHash = "c69688c8692b5ef2cf3c0cf92aab45da9957cfc3224a00abbf0bc90c6f98f75f"

    private fun segments(
        content: String,
        tags: List<Array<String>> = emptyList(),
    ) = RichTextParser()
        .parseText(content, ImmutableListOfLists(tags.toTypedArray()), callbackUri = null)
        .paragraphs
        .flatMap { it.words }

    @Test
    fun aNoteWithAWebxdcImetaIsAnAppNotALink() {
        // As Ditto posts one (the Quake III port).
        val url = "https://blossom.ditto.pub/$quakeHash.xdc"
        val words =
            segments(
                "Quake in a note $url",
                listOf(arrayOf("imeta", "url $url", "x $quakeHash", "size 5113443", "m application/x-webxdc", "webxdc eaad54ff-fb6a-402d-a460-ad863308c552", "summary Quake III Arena")),
            )
        val app = words.filterIsInstance<WebxdcSegment>().single().app
        assertEquals(url, app.url)
        assertEquals(quakeHash, app.hash)
        assertEquals("eaad54ff-fb6a-402d-a460-ad863308c552", app.webxdcId)
        assertEquals("Quake III Arena", app.title)
        assertEquals(5113443L, app.sizeBytes)
        assertEquals("eaad54ff-fb6a-402d-a460-ad863308c552", app.instance("note-id"))
    }

    @Test
    fun aBareXdcLinkIsAnAppWithoutHashOrId() {
        val words = segments("Half-Life! https://content.hl2dm.org/xdc/hl.xdc have fun")
        val app = words.filterIsInstance<WebxdcSegment>().single().app
        assertNull(app.hash)
        assertNull(app.webxdcId)
        // Each post is then its own app.
        assertEquals("note-id", app.instance("note-id"))
    }

    @Test
    fun aDeclaredTypeWinsOverTheExtension() {
        // An imeta that types a .xdc link as something else is that something else.
        val url = "https://example.com/archive.xdc"
        assertTrue(segments(url, listOf(arrayOf("imeta", "url $url", "m application/zip"))).none { it is WebxdcSegment })
        // A link without the extension, typed webxdc, is an app.
        val typed = "https://example.com/download?id=7"
        assertTrue(segments(typed, listOf(arrayOf("imeta", "url $typed", "m application/x-webxdc"))).any { it is WebxdcSegment })
        // A word that only looks like a file name stays a word.
        assertTrue(segments("Mero.xdc is a bot").none { it is WebxdcSegment })
        assertTrue(segments("https://example.com/page").all { it !is WebxdcSegment })
        assertTrue(segments("https://example.com/page").any { it is LinkSegment })
    }

    @Test
    fun theHashComesFromXOrABlossomUrlAndNeverFromJunk() {
        assertEquals(quakeHash, WebxdcApp.hashFromUrl("https://blossom.ditto.pub/$quakeHash.bin.xdc"))
        assertNull(WebxdcApp.hashFromUrl("https://content.hl2dm.org/xdc/hl.xdc"))
        val bad = WebxdcApp.from("https://blossom.ditto.pub/$quakeHash.xdc") { if (it == "x") "not-a-hash" else null }
        assertEquals(quakeHash, bad?.hash, "a malformed x falls back to the URL's hash")
        val alt = WebxdcApp.from("https://x.com/a.xdc") { if (it == "alt") "Webxdc app: MachStrike" else null }
        assertEquals("MachStrike", alt?.title)
    }

    @Test
    fun selfAddrIsAPseudonymOfItsOwnInEveryApp() {
        val me = "a1".repeat(32)
        val poster = "b2".repeat(32)
        val addr = WebxdcApp.selfAddr(me, poster, "chess")
        assertEquals(addr, WebxdcApp.selfAddr(me, poster, "chess"))
        assertNotEquals(addr, WebxdcApp.selfAddr(me, poster, "go"))
        assertNotEquals(addr, WebxdcApp.selfAddr(me, "c3".repeat(32), "chess"))
        assertNotEquals(addr, WebxdcApp.selfAddr("d4".repeat(32), poster, "chess"))
        assertFalse(addr.contains(me.take(16)))
    }
}
