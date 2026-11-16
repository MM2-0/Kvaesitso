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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class NappletRelayCleartextTest {
    private val allowed: suspend (String) -> Boolean = { true }

    @Test
    fun plaintextPassesThroughWithoutDecrypting() =
        runTest {
            val event = event(content = "hello")
            val result =
                NappletRelayCleartext.forDelivery(event, USER, { _, _ -> error("plaintext must not be decrypted") }, { error("plaintext needs no grant") })

            assertSame(event, result)
        }

    @Test
    fun inboundNip04IsProjectedAsCleartext() =
        runTest {
            val event = event(content = NIP04, tags = arrayOf(arrayOf("p", USER)))
            val result =
                NappletRelayCleartext.forDelivery(event, USER, { ciphertext, peer ->
                    assertEquals(NIP04, ciphertext)
                    assertEquals(AUTHOR, peer)
                    "secret"
                }, allowed)

            assertEquals("secret", result?.content)
            assertEquals(event.id, result?.id)
            assertEquals(event.sig, result?.sig)
        }

    @Test
    fun outboundDmUsesItsRecipientAsPeer() =
        runTest {
            val event = event(pubKey = USER, content = NIP04, tags = arrayOf(arrayOf("p", RECIPIENT)))
            val result =
                NappletRelayCleartext.forDelivery(event, USER, { _, peer ->
                    assertEquals(RECIPIENT, peer)
                    "sent secret"
                }, allowed)

            assertEquals("sent secret", result?.content)
        }

    @Test
    fun withoutADecryptGrantEncryptedContentIsWithheldAndNeverDecrypted() =
        runTest {
            val event = event(content = NIP04, tags = arrayOf(arrayOf("p", USER)))
            var askedFor: String? = null
            val result =
                NappletRelayCleartext.forDelivery(event, USER, { _, _ -> error("must not decrypt without a grant") }) { peer ->
                    askedFor = peer
                    false
                }

            assertNull(result)
            assertEquals(AUTHOR, askedFor)
        }

    // The oracle: an app that may publish a reaction as the user puts someone else's ciphertext in its
    // content and names the peer in its `p` tag, then reads the reaction back. The peer of an event the
    // user wrote that isn't a DM is the user, never the tag the app wrote.
    @Test
    fun userAuthoredNonDmIgnoresItsPTagAndDecryptsOnlyAsSelf() =
        runTest {
            val reaction = event(pubKey = USER, kind = 7, content = NIP04, tags = arrayOf(arrayOf("p", RECIPIENT)))
            var peerUsed: String? = null
            NappletRelayCleartext.forDelivery(reaction, USER, { _, peer ->
                peerUsed = peer
                "self"
            }, allowed)

            assertEquals(USER, peerUsed)
            assertEquals(USER, NappletRelayCleartext.peerOf(reaction, USER))
        }

    @Test
    fun encryptedEventForAnotherUserIsWithheld() =
        runTest {
            val event = event(content = NIP04, tags = arrayOf(arrayOf("p", RECIPIENT)))
            val result =
                NappletRelayCleartext.forDelivery(event, USER, { _, _ -> error("unrelated ciphertext must not be offered to the signer") }, allowed)

            assertNull(result)
        }

    @Test
    fun decryptionFailureWithholdsCiphertext() =
        runTest {
            val event = event(content = NIP04, tags = arrayOf(arrayOf("p", USER)))
            val result = NappletRelayCleartext.forDelivery(event, USER, { _, _ -> error("signer refused") }, allowed)

            assertNull(result)
        }

    private fun event(
        pubKey: String = AUTHOR,
        kind: Int = 4,
        content: String,
        tags: Array<Array<String>> = emptyArray(),
    ) = Event("id", pubKey, 1L, kind, tags, content, "sig")

    companion object {
        private const val USER = "user"
        private const val AUTHOR = "author"
        private const val RECIPIENT = "recipient"
        private const val NIP04 = "ciphertext-that-is-long-enough?iv=123456789012345678901234"
    }
}
