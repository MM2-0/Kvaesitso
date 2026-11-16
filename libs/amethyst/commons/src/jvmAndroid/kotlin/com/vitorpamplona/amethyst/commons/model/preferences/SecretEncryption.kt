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

/**
 * Symmetric encryption for data this app stores at rest — account secrets, the
 * Marmot group state, message bodies.
 *
 * Declared for `jvmAndroid` rather than `commonMain` on purpose: Android backs
 * it with the hardware-held AndroidKeyStore and desktop with a key file the OS
 * user owns, and those are the two targets that store secrets today. An Apple
 * actual belongs with the first iOS build that needs one, written against the
 * Keychain — not stubbed here, where nothing would exercise it.
 *
 * Implementations must be safe to call from several coroutines at once.
 */
expect class SecretEncryption() {
    /** Returns the ciphertext with whatever nonce/IV the implementation needs prefixed. */
    fun encrypt(bytes: ByteArray): ByteArray

    /** Inverse of [encrypt]. Throws if the input is not what [encrypt] produced. */
    fun decrypt(bytes: ByteArray): ByteArray?
}
