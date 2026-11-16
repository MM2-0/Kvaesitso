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

import com.vitorpamplona.amethyst.commons.model.preferences.SecretEncryption
import com.vitorpamplona.cordn.client.CordnBlobCipher

/**
 * The desktop [CordnBlobCipher]: the AES-256-GCM key that already protects the desktop's Marmot
 * group state and account secrets ([SecretEncryption]), so Cordn's MLS state is no weaker and no
 * stronger than theirs. That key is a file only this OS user can read, which covers a stolen disk
 * or a careless backup, not another process running as the same user.
 */
class SecretEncryptionCordnBlobCipher : CordnBlobCipher {
    // Built on first use, as on Android: CordnRuntime is built with the Account, and a key file that
    // cannot be read must fail Cordn's save, not the whole account.
    private val encryption by lazy { SecretEncryption() }

    override fun encrypt(bytes: ByteArray): ByteArray = encryption.encrypt(bytes)

    override fun decrypt(bytes: ByteArray): ByteArray = encryption.decrypt(bytes) ?: throw IllegalStateException("not a cordn blob: ${bytes.size} bytes")
}
