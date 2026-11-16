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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.ALICE
import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.id
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaperLinksTest {
    private val naddr = NAddress.create(30311, ALICE, "show", emptyList())

    @Test
    fun theConfiguredFormsBecomeNostrUris() {
        val nevent = toNevent(id(7))
        assertEquals("nostr:$nevent", PaperLinks.toNostrUri("https://jumble.social/notes/$nevent"))
        assertEquals("nostr:$naddr", PaperLinks.toNostrUri("https://jumble.social/notes/$naddr"))
        assertEquals("nostr:${npubOf(ALICE)}", PaperLinks.toNostrUri("https://jumble.social/users/${npubOf(ALICE)}"))
        // Another app's set routes its own forms, and only those.
        val brainstorm = PaperLinkSet("https://brainstorm.world/e/", "https://brainstorm.world/p/")
        assertEquals("nostr:$naddr", PaperLinks.toNostrUri("https://brainstorm.world/e/$naddr", brainstorm))
        assertEquals("nostr:${npubOf(ALICE)}", PaperLinks.toNostrUri("https://brainstorm.world/p/${npubOf(ALICE)}", brainstorm))
        assertNull(PaperLinks.toNostrUri("https://jumble.social/notes/$nevent", brainstorm))
    }

    @Test
    fun everythingElseStaysAWebLink() {
        assertNull(PaperLinks.toNostrUri("https://evil.example/drain"))
        // The writer's hex forms are resolve's to encode; a page never ships with them.
        assertNull(PaperLinks.toNostrUri("https://jumble.social/notes/${id(7)}"))
        // Matched is not decoded: a broken bech32, or the wrong entity on a host, is not routed.
        assertNull(PaperLinks.toNostrUri("https://jumble.social/notes/nevent1qqqqqqq"))
        assertNull(PaperLinks.toNostrUri("https://jumble.social/users/$naddr"))
        assertNull(PaperLinks.toNostrUri("https://jumble.social/notes/${npubOf(ALICE)}"))
        // The per-desk hosts are gone: every kind goes to the app's own viewer.
        assertNull(PaperLinks.toNostrUri("https://zap.stream/$naddr"))
        assertNull(PaperLinks.toNostrUri("https://njump.me/$naddr"))
    }
}
