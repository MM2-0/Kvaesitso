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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.cordn.appEncryptedMedia.CordnEncryptedMedia
import com.vitorpamplona.cordn.appEncryptedMedia.CordnMediaEncryption
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.marmot.mip04EncryptedMedia.Mip04MediaEncryption
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * cordn and Marmot both encrypt media under an MLS exporter, with the same context word and
 * different derivations. They live in separate libraries that never import each other, so the
 * check that one's blob cannot pass for the other's lives here, where both are on the classpath.
 */
class CordnMarmotMediaSeparationTest {
    private val key = ByteArray(32) { it.toByte() }
    private val file = "a small photo".encodeToByteArray()

    private fun roundTrip(
        mime: String = "image/jpeg",
        name: String = "photo.jpg",
    ): CordnEncryptedMedia = CordnMediaEncryption.encrypt(file, key, mime, name)

    @Test
    fun `a cordn blob is not a Marmot blob`() {
        // §4.5: same exporter context word, different key derivation and
        // different AAD. Confusing the two must fail loudly rather than
        // produce plausible garbage, and this is the test that says so.
        val sealed = roundTrip()

        assertFailsWith<Exception> {
            Mip04MediaEncryption.decrypt(
                sealed.ciphertext,
                key,
                sealed.nonce,
                sealed.plaintextHash,
                sealed.mimeType,
                sealed.filename,
            )
        }
    }

    @Test
    fun `the cordn file key is not the Marmot file key for the same inputs`() {
        val marmot = Mip04MediaEncryption.deriveFileKey(key, sha256(file), "image/jpeg", "photo.jpg")

        // cordn uses the exporter output as-is; Marmot expands it. If these
        // ever matched, one of the two implementations would be wrong.
        assertNotEquals(key.toList(), marmot.toList())
    }
}
