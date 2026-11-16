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
package com.vitorpamplona.amethyst.ui.note.share

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.note.share.QrPayloadMode
import com.vitorpamplona.amethyst.commons.ui.note.share.qrPayloadFor
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadTest {
    private val alicePriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val aliceSigner = NostrSignerInternal(KeyPair(alicePriv.hexToByteArray()))

    private suspend fun textNote(): Note {
        val event = TextNoteEvent.build("hello qr") {}.let { aliceSigner.sign(it) }
        val note = Note(event.id)
        note.event = event
        return note
    }

    @Test
    fun webMode_returnsAnHttpsNjumpLink() =
        runTest {
            val payload = qrPayloadFor(textNote(), QrPayloadMode.Web)

            assertTrue(
                "web mode must produce an https URL so a stock phone camera can action it, got: $payload",
                payload.startsWith("https://"),
            )
            assertTrue(payload.contains("nevent1"))
        }

    @Test
    fun nostrMode_returnsANostrUriWithNevent() =
        runTest {
            val payload = qrPayloadFor(textNote(), QrPayloadMode.Nostr)

            assertTrue(payload.startsWith("nostr:nevent1"))
        }

    @Test
    fun bothModes_encodeTheSameNote() =
        runTest {
            val note = textNote()
            val web = qrPayloadFor(note, QrPayloadMode.Web)
            val nostr = qrPayloadFor(note, QrPayloadMode.Nostr)

            // The bech32 body must be identical; only the wrapper differs.
            assertEquals(nostr.removePrefix("nostr:"), web.substringAfterLast('/'))
        }

    @Test
    fun addressableNote_nostrMode_yieldsNaddrNotNevent() =
        runTest {
            // build(description, title, summary, image, publishedAt, dTag, createdAt, init)
            // — `title` is a required positional; `dTag` must be named.
            // LongFormContentEvent.kt:148-157.
            val event =
                LongFormContentEvent
                    .build("body", "My Article", dTag = "my-article") {}
                    .let { aliceSigner.sign(it) }
            val note = AddressableNote(event.address())
            note.event = event

            val payload = qrPayloadFor(note, QrPayloadMode.Nostr)

            assertTrue(
                "AddressableNote must encode as naddr via the toNEvent() override, got: $payload",
                payload.startsWith("nostr:naddr1"),
            )
        }
}
