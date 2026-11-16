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

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end check of the private note path: a kind-1 template is wrapped
 * to its p-tagged users plus the sender's self-copy, and a recipient who
 * unwraps it lands on a rumor with the same id and an EMPTY signature —
 * the discriminator Note.isPrivateRumor() relies on.
 */
class PrivateNoteFactoryTest {
    private val alicePriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val aliceSigner = NostrSignerInternal(KeyPair(alicePriv.hexToByteArray()))

    private val bobPriv = "0000000000000000000000000000000000000000000000000000000000000008"
    private val bobSigner = NostrSignerInternal(KeyPair(bobPriv.hexToByteArray()))

    @Test
    fun privateNote_wrapsToTaggedUsersAndSelf_andUnwrapsToEmptySigRumor() =
        runTest {
            val template =
                TextNoteEvent.build("for your eyes only") {
                    pTags(listOf(PTag(bobSigner.pubKey, null)))
                }

            val result = NIP17Factory().createNoteNIP17(template, aliceSigner)

            assertEquals(TextNoteEvent.KIND, result.msg.kind)
            assertEquals(
                setOf(aliceSigner.pubKey, bobSigner.pubKey),
                result.wraps.mapNotNull { it.recipientPubKey() }.toSet(),
                "wraps must cover every p-tagged user plus the sender's self-copy",
            )

            // Bob unwraps his copy: same note id, but materialized as an
            // unsigned rumor.
            val bobWrap = result.wraps.first { it.recipientPubKey() == bobSigner.pubKey }
            val rumor = bobWrap.unwrapAndUnsealOrNull(bobSigner)

            assertNotNull(rumor, "recipient must be able to unwrap and unseal")
            assertEquals(result.msg.id, rumor.id, "rumor id must match the signed inner event's id")
            assertEquals(aliceSigner.pubKey, rumor.pubKey)
            assertEquals("for your eyes only", rumor.content)
            assertTrue(rumor.sig.isEmpty(), "unsealed rumors must carry an empty signature")
        }

    @Test
    fun privateDeletion_wrapsToExplicitRecipients_andRetractsTheRumorId() =
        runTest {
            // Alice retracts a private reaction she previously wrapped to Bob.
            val reaction = aliceSigner.sign(TextNoteEvent.build("the rumor being retracted"))

            val result =
                NIP17Factory().createDeletionNIP17(
                    template = DeletionRequestEvent.build(listOf(reaction)),
                    to = listOf(bobSigner.pubKey),
                    signer = aliceSigner,
                )

            assertEquals(DeletionRequestEvent.KIND, result.msg.kind)
            assertEquals(
                setOf(aliceSigner.pubKey, bobSigner.pubKey),
                result.wraps.mapNotNull { it.recipientPubKey() }.toSet(),
                "deletion wraps must cover the explicit recipients plus the sender's self-copy",
            )

            val bobWrap = result.wraps.first { it.recipientPubKey() == bobSigner.pubKey }
            val rumor = bobWrap.unwrapAndUnsealOrNull(bobSigner)

            assertNotNull(rumor, "recipient must be able to unwrap and unseal the deletion")
            assertEquals(DeletionRequestEvent.KIND, rumor.kind)
            assertEquals(aliceSigner.pubKey, rumor.pubKey, "deletions only apply when the author matches")
            assertTrue(rumor.sig.isEmpty(), "the deletion travels as an unsigned rumor")
            assertTrue(
                rumor.tags.any { it.size >= 2 && it[0] == "e" && it[1] == reaction.id },
                "the deletion must e-tag the retracted rumor id (inside the wrap only)",
            )
        }
}
