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

/**
 * The narrow slice of a key store this needs.
 *
 * An interface rather than [SecureKeyStorage] directly so the decision logic
 * that decides which store wins, and what happens when one fails, is testable without
 * a real keystore, which no unit test can reach.
 */
interface PrivateKeyVault {
    /** The stored key, or null only when genuinely absent. Throws when the store cannot be read. */
    suspend fun get(npub: String): String?

    suspend fun save(
        npub: String,
        privKeyHex: String,
    )

    suspend fun delete(npub: String)
}

/** [PrivateKeyVault] over the real [SecureKeyStorage]. */
class SecureKeyStorageVault(
    private val storage: SecureKeyStorage,
) : PrivateKeyVault {
    override suspend fun get(npub: String): String? = storage.getPrivateKeyOrThrow(npub)

    override suspend fun save(
        npub: String,
        privKeyHex: String,
    ) = storage.savePrivateKey(npub, privKeyHex)

    override suspend fun delete(npub: String) {
        storage.deletePrivateKey(npub)
    }
}
