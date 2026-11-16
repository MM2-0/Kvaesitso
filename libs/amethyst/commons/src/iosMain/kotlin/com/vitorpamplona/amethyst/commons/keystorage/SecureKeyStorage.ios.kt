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

// Phase 2 compile-only iOS actual. Keychain Services wiring lands in Phase 4
// (write paths: signing, posting, settings) per amethyst/plans/2026-05-24-ios-support.md.
actual class SecureKeyStorage private actual constructor() {
    actual companion object {
        actual fun create(context: Any?): SecureKeyStorage = SecureKeyStorage()
    }

    actual suspend fun savePrivateKey(
        npub: String,
        privKeyHex: String,
    ): Unit = throw SecureStorageException("Keychain Services binding pending (iOS Phase 4)")

    actual suspend fun getPrivateKey(npub: String): String? = throw SecureStorageException("Keychain Services binding pending (iOS Phase 4)")

    actual suspend fun getPrivateKeyOrThrow(npub: String): String? = throw SecureStorageException("Keychain Services binding pending (iOS Phase 4)")

    actual suspend fun deletePrivateKey(npub: String): Boolean = throw SecureStorageException("Keychain Services binding pending (iOS Phase 4)")

    actual suspend fun hasPrivateKey(npub: String): Boolean = throw SecureStorageException("Keychain Services binding pending (iOS Phase 4)")
}
