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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the citation guarantee for private notes: an nevent of a rumor must
 * encode the delivering envelope's id — never the rumor's own id, which is
 * the private event's identity and resolves to nothing on public relays.
 */
class RumorHostCitationTest {
    private val alicePriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val aliceSigner = NostrSignerInternal(KeyPair(alicePriv.hexToByteArray()))

    private val bobPriv = "0000000000000000000000000000000000000000000000000000000000000008"
    private val bobSigner = NostrSignerInternal(KeyPair(bobPriv.hexToByteArray()))

    @Test
    fun rumorNote_toNEvent_citesTheDeliveringWrap() =
        runTest {
            // Alice sends Bob a private note; Bob unwraps his copy.
            val template =
                TextNoteEvent.build("psst") {
                    pTags(listOf(PTag(bobSigner.pubKey, null)))
                }
            val result = NIP17Factory().createNoteNIP17(template, aliceSigner)
            val bobWrap = result.wraps.first { it.recipientPubKey() == bobSigner.pubKey }
            val rumor = bobWrap.unwrapAndUnsealOrNull(bobSigner)
            assertNotNull(rumor)
            assertTrue(rumor.sig.isEmpty())

            // Bob's cache materializes the rumor note and records the wrap.
            val note = Note(rumor.id)
            note.event = rumor
            note.recordRumorHost(bobWrap)

            val nevent = note.toNEvent()
            assertEquals(
                NEvent.create(bobWrap.id, bobWrap.pubKey, bobWrap.kind, null),
                nevent,
                "rumor citations must encode the wrap, not the rumor",
            )
            assertFalse(
                nevent == NEvent.create(rumor.id, rumor.pubKey, rumor.kind, null),
                "the private rumor id must never be encoded",
            )
        }

    @Test
    fun publicNote_toNEvent_citesItsOwnId() =
        runTest {
            val event = aliceSigner.sign(TextNoteEvent.build("hello world"))

            val note = Note(event.id)
            note.event = event

            // toNEvent reads the author from the Note (unset here), so the
            // expected nevent carries a null author too.
            assertEquals(
                NEvent.create(event.id, null, event.kind, null),
                note.toNEvent(),
            )
        }
}
