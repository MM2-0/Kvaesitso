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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Local search drops events whose content is encrypted, but an emoji pack (and every NIP-51 set) is
 * "encoded" only because its content may hold private items: its title and shortcodes are public,
 * and they are exactly what it is searched by. Those used to be dropped with the ciphertext.
 */
class CacheSearchEncodedContentTest {
    private val alice = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val bob = "c230edd34ca5c8318bf4592ac056cde37519d395c0904c37ea1c650b8ad4a712"
    private val nobody = LiveHiddenUsers(null, emptyList(), emptySet(), emptySet())

    private fun event(
        idSeed: Char,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): Event = EventFactory.create(idSeed.toString().repeat(64), alice, 1_000, kind, tags, content, "0".repeat(128))

    private val publicPack =
        event('1', EmojiPackEvent.KIND, arrayOf(arrayOf("d", "cats"), arrayOf("title", "Cats"), arrayOf("emoji", "blobcat", "https://x/1.png")), "")

    // Same shape, with private emojis in its (encrypted) content.
    private val mixedPack =
        event('2', EmojiPackEvent.KIND, arrayOf(arrayOf("d", "more"), arrayOf("title", "More"), arrayOf("emoji", "catjam", "https://x/2.gif")), "c2VjcmV0?iv=aXY=")

    // A DM whose ciphertext happens to contain the term: never offered.
    private val dm = event('3', EncryptedDmEvent.KIND, arrayOf(arrayOf("p", bob)), "cat?iv=aXY=")

    private fun found(search: String): List<String> {
        val cache = EventCache().apply { listOf(publicPack, mixedPack, dm).forEach { justConsume(it, null, true) } }
        return cache.search
            .findNotesMatching(listOf(Filter(kinds = listOf(EmojiPackEvent.KIND, EncryptedDmEvent.KIND), search = search)), nobody)
            .mapNotNull { it.event?.id }
    }

    @Test
    fun emojiPacksAreFoundByTheirPublicShortcodes() {
        assertEquals(setOf(publicPack.id, mixedPack.id), found("cat").toSet())
    }

    @Test
    fun emojiPacksAreFoundByTitle() {
        assertEquals(listOf(publicPack.id), found("Cats"))
    }

    @Test
    fun encryptedContentIsStillNeverMatched() {
        assertTrue(dm.id !in found("cat"))
        // The pack's ciphertext is not searched either: only its public fields are.
        assertTrue(found("c2VjcmV0").isEmpty())
    }
}
