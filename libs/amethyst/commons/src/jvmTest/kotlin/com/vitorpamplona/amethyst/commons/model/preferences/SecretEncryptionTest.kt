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
package com.vitorpamplona.amethyst.commons.model.preferences

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class SecretEncryptionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun subject(name: String = "secret.key") = SecretEncryption(File(folder.root, name))

    @Test
    fun roundTripsBytes() {
        val encryption = subject()
        val plaintext = "an nsec, a wallet string, a group state".encodeToByteArray()

        assertArrayEquals(plaintext, encryption.decrypt(encryption.encrypt(plaintext)))
    }

    @Test
    fun roundTripsEmptyInput() {
        val encryption = subject()

        assertArrayEquals(ByteArray(0), encryption.decrypt(encryption.encrypt(ByteArray(0))))
    }

    @Test
    fun ciphertextDiffersFromPlaintext() {
        val plaintext = "correct-horse-battery-staple".encodeToByteArray()

        val ciphertext = subject().encrypt(plaintext)

        assertFalse(ciphertext.decodeToString().contains("correct-horse"))
    }

    /** GCM must never reuse an IV under the same key, so the same input encrypts differently each time. */
    @Test
    fun encryptingTwiceProducesDifferentCiphertext() {
        val encryption = subject()
        val plaintext = "same input".encodeToByteArray()

        assertNotEquals(
            encryption.encrypt(plaintext).toList(),
            encryption.encrypt(plaintext).toList(),
        )
    }

    /** A second instance over the same key file must read the first one's output. */
    @Test
    fun keyPersistsAcrossInstances() {
        val plaintext = "survives a restart".encodeToByteArray()
        val ciphertext = subject().encrypt(plaintext)

        assertArrayEquals(plaintext, subject().decrypt(ciphertext))
    }

    /** A different key file must not decrypt — otherwise the key is not doing anything. */
    @Test
    fun aDifferentKeyCannotDecrypt() {
        val ciphertext = subject("first.key").encrypt("secret".encodeToByteArray())

        assertThrows(Exception::class.java) { subject("second.key").decrypt(ciphertext) }
    }

    private fun isPosix() = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    @Test
    fun keyFileIsOwnerOnly() {
        // Windows has no POSIX permissions, and `restrictToOwner` is documented
        // as a silent no-op there — the user profile's NTFS ACLs apply instead.
        // Asking for them anyway raises UnsupportedOperationException, which is
        // how this failed on the Windows CI runner while passing everywhere else.
        if (!isPosix()) return

        subject().encrypt("x".encodeToByteArray())

        val perms = Files.getPosixFilePermissions(File(folder.root, "secret.key").toPath())
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms)
    }

    /**
     * A wrong-sized key file means data already on disk was written under a key
     * we no longer have. Overwriting it would strand that data silently.
     */
    @Test
    fun refusesToOverwriteAMalformedKeyFile() {
        val keyFile = File(folder.root, "truncated.key")
        keyFile.writeBytes(ByteArray(7))

        val error =
            assertThrows(IllegalStateException::class.java) {
                SecretEncryption(keyFile).encrypt("x".encodeToByteArray())
            }
        assertTrue(error.message!!.contains("Refusing to overwrite"))
    }
}
