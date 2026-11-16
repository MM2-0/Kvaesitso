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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.amethyst.commons.actions.EmojiPackActions.OnConflict
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmojiPackActionsTest {
    private val signer = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))

    private fun emoji(
        code: String,
        n: Int = 1,
    ) = EmojiUrlTag(code, "https://example.com/$code-$n.png")

    @Test
    fun createSetsMetadataAndDTag() =
        runTest {
            val pack = EmojiPackActions.create(signer, "Cats", "Cat faces", "https://example.com/cover.png", dTag = "cats")
            assertEquals("cats", pack.dTag())
            assertEquals("Cats", pack.title())
            assertEquals("Cat faces", pack.description())
            assertEquals("https://example.com/cover.png", pack.image())
            assertTrue(pack.publicEmojis().isEmpty())
        }

    @Test
    fun addAppendsNewAndSkipsExistingAndRepeats() =
        runTest {
            val pack = EmojiPackActions.create(signer, "Cats", dTag = "cats")
            val first = EmojiPackActions.addEmojis(signer, pack, listOf(emoji("a"), emoji("b")), isPrivate = false)
            val v1 = assertNotNull(first.event)
            assertEquals(listOf("a", "b"), first.added)

            val second = EmojiPackActions.addEmojis(signer, v1, listOf(emoji("a", 2), emoji("c"), emoji("c", 2), emoji("bad code")), isPrivate = false)
            val v2 = assertNotNull(second.event)
            assertEquals(listOf("c"), second.added)
            assertEquals(listOf("a", "c"), second.skipped)
            assertEquals(listOf("bad code"), second.invalid)
            assertEquals(listOf(emoji("a"), emoji("b"), emoji("c")), v2.publicEmojis())
            assertTrue(v2.createdAt > v1.createdAt)
        }

    @Test
    fun replaceSwapsTheUrlInPlace() =
        runTest {
            val pack = EmojiPackActions.addEmojis(signer, EmojiPackActions.create(signer, "Cats"), listOf(emoji("a"), emoji("b")), false).event!!
            val result = EmojiPackActions.addEmojis(signer, pack, listOf(emoji("a", 2), emoji("b")), false, OnConflict.REPLACE)
            assertEquals(listOf("a"), result.replaced)
            assertEquals(listOf("b"), result.skipped)
            assertEquals(listOf(emoji("a", 2), emoji("b")), result.event!!.publicEmojis())
        }

    @Test
    fun nothingNewSignsNothing() =
        runTest {
            val pack = EmojiPackActions.addEmojis(signer, EmojiPackActions.create(signer, "Cats"), listOf(emoji("a")), false).event!!
            assertNull(EmojiPackActions.addEmojis(signer, pack, listOf(emoji("a")), false).event)
            assertNull(EmojiPackActions.removeEmojis(signer, pack, listOf("zzz"), false).event)
        }

    @Test
    fun privateEmojisAreEncryptedAndKeptApartFromPublicOnes() =
        runTest {
            val pack = EmojiPackActions.addEmojis(signer, EmojiPackActions.create(signer, "Cats"), listOf(emoji("a")), false).event!!
            val withPrivate = EmojiPackActions.addEmojis(signer, pack, listOf(emoji("a", 2), emoji("secret")), isPrivate = true).event!!
            assertEquals(listOf(emoji("a")), withPrivate.publicEmojis())
            assertEquals(listOf(emoji("a", 2), emoji("secret")), withPrivate.privateEmojis(signer))
            assertTrue("secret" !in withPrivate.content)

            val removed = EmojiPackActions.removeEmojis(signer, withPrivate, listOf("a"), isPrivate = true)
            assertEquals(listOf("a"), removed.removed)
            assertEquals(listOf(emoji("a")), removed.event!!.publicEmojis())
            assertEquals(listOf(emoji("secret")), removed.event.privateEmojis(signer))
        }

    @Test
    fun removeDropsEveryCopyAndReportsMissing() =
        runTest {
            val pack = EmojiPackActions.addEmojis(signer, EmojiPackActions.create(signer, "Cats"), listOf(emoji("a"), emoji("b"), emoji("c")), false).event!!
            val result = EmojiPackActions.removeEmojis(signer, pack, listOf("a", "c", "zzz"), false)
            assertEquals(listOf("a", "c"), result.removed)
            assertEquals(listOf("zzz"), result.missing)
            assertEquals(listOf(emoji("b")), result.event!!.publicEmojis())
        }

    @Test
    fun updateMetadataReplacesFieldsAndKeepsEmojis() =
        runTest {
            val pack = EmojiPackActions.addEmojis(signer, EmojiPackActions.create(signer, "Cats", "old", "https://example.com/old.png"), listOf(emoji("a")), false).event!!
            val updated = EmojiPackActions.updateMetadata(signer, pack, "Kittens", null, "https://example.com/new.png")
            assertEquals(pack.dTag(), updated.dTag())
            assertEquals("Kittens", updated.title())
            assertNull(updated.description())
            assertEquals("https://example.com/new.png", updated.image())
            assertEquals(listOf(emoji("a")), updated.publicEmojis())
            assertEquals(1, updated.tags.count { it[0] == "title" })
        }
}
