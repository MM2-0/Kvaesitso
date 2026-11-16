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
package com.vitorpamplona.amethyst.commons.keystorage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SecureKeyStorageFallbackCipherTest {
    @Test
    fun encryptedFileFallbackRoundTrips() {
        // AES/GCM initialised with IvParameterSpec threw on every JDK, which made
        // the whole no-keyring fallback (headless Linux, containers) dead code.
        val storage = SecureKeyStorage.create()
        val ciphertext = storage.encryptData("nsec1secret", "correct horse")
        assertNotEquals("nsec1secret", ciphertext)
        assertEquals("nsec1secret", storage.decryptData(ciphertext, "correct horse"))
    }

    @Test(expected = Exception::class)
    fun wrongPasswordIsRejected() {
        val storage = SecureKeyStorage.create()
        storage.decryptData(storage.encryptData("nsec1secret", "right"), "wrong")
    }
}
