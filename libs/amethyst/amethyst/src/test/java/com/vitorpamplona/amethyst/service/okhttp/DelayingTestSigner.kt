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
package com.vitorpamplona.amethyst.service.okhttp

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import kotlinx.coroutines.delay

/**
 * Signs with a real key, but only after [delayMs] — standing in for a NIP-55
 * IPC round trip or a NIP-46 relay hop. Counts signatures so a test can assert
 * how many actually happened.
 */
class DelayingTestSigner(
    private val delayMs: Long,
    private val delegate: NostrSignerInternal = NostrSignerInternal(KeyPair()),
) : NostrSigner(delegate.pubKey) {
    @Volatile
    var signatures = 0
        private set

    override fun isWriteable() = true

    override suspend fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T {
        delay(delayMs)
        synchronized(this) { signatures++ }
        return delegate.sign(createdAt, kind, tags, content)
    }

    override suspend fun nip04Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ) = unsupported()

    override suspend fun nip04Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ) = unsupported()

    override suspend fun nip44Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ) = unsupported()

    override suspend fun nip44Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ) = unsupported()

    override suspend fun decryptZapEvent(event: ZapRequestEvent) = unsupported()

    override suspend fun deriveKey(nonce: HexKey) = unsupported()

    override suspend fun signPsbt(psbtHex: String) = unsupported()

    override fun hasForegroundSupport() = false

    private fun unsupported(): Nothing = throw UnsupportedOperationException("test signer")
}
