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
package com.vitorpamplona.amethyst.commons.model.privateChats

import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatPreviewTextTest {
    private val labels = ChatPreviewLabels(photo = "Photo", video = "Video", note = "Note")
    private val hex = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val names = mapOf(hex to "Vitor")

    private fun tidy(text: String) = ChatPreviewText.tidy(text, labels) { names[it] }

    @Test
    fun plainTextIsUntouched() {
        assertEquals("hello there", tidy("hello there"))
    }

    @Test
    fun collapsesLineBreaksIntoOneLine() {
        assertEquals("first line second line", tidy("first line\n\n  second line"))
    }

    @Test
    fun eventReferenceBecomesTheNoteLabel() {
        val nevent = NEvent.create(hex, hex, 1, relay = null)
        assertEquals("Note", tidy("nostr:$nevent"))
        assertEquals("look: Note", tidy("look: nostr:${NNote.create(hex)}"))
    }

    @Test
    fun userMentionBecomesTheName() {
        assertEquals("hi @Vitor!", tidy("hi nostr:${NPub.create(hex)}!"))
    }

    @Test
    fun unknownUserMentionIsShortened() {
        val other = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
        val npub = NPub.create(other)
        assertEquals("@${npub.take(10)}…${npub.takeLast(4)}", tidy("nostr:$npub"))
    }

    @Test
    fun imageAndVideoLinksBecomeLabels() {
        assertEquals("Photo", tidy("https://nostr.download/ee078c63eab4f77eb1234.jpg"))
        assertEquals("watch Video.", tidy("watch https://example.com/clip.mp4."))
    }

    @Test
    fun otherLinksBecomeTheirHost() {
        assertEquals("see github.com", tidy("see https://www.github.com/vitorpamplona/amethyst/pull/1"))
        assertEquals("nostr.download", tidy("https://nostr.download/ee078c63eab4f77eb1234"))
    }

    @Test
    fun markdownIsFlattened() {
        assertEquals("[github block/buzz@main] a3870 fix the build", tidy("[github block/buzz@main] `a3870` **fix** the build"))
        assertEquals("the docs here", tidy("the [docs](https://example.com/docs) here"))
        assertEquals("Title quoted", tidy("## Title\n> quoted"))
    }
}
