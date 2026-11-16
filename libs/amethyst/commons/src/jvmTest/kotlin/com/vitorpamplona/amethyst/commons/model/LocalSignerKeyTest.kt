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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.NostrSignerWithClientTag
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * An account's signer is always decorated (client tag, and metering on Android), so the local
 * key must be found through the wrappers. A bare `signer is NostrSignerInternal` on the account
 * signer was always false, which hid an nsec login's key from the Cashu wallet.
 */
class LocalSignerKeyTest {
    private val keyPair = KeyPair()
    private val local = NostrSignerInternal(keyPair)

    /** Stand-in for Android's metering decorator, which lives in the app module. */
    private class Forwarding(
        override val inner: NostrSigner,
    ) : NostrSigner(inner.pubKey),
        ForwardingNostrSigner {
        override fun isWriteable() = inner.isWriteable()

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T = inner.sign(createdAt, kind, tags, content)

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip04Encrypt(plaintext, toPublicKey)

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = inner.nip04Decrypt(ciphertext, fromPublicKey)

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip44Encrypt(plaintext, toPublicKey)

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = inner.nip44Decrypt(ciphertext, fromPublicKey)

        override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = inner.decryptZapEvent(event)

        override suspend fun deriveKey(nonce: HexKey) = inner.deriveKey(nonce)

        override suspend fun signPsbt(psbtHex: String) = inner.signPsbt(psbtHex)

        override fun hasForegroundSupport() = inner.hasForegroundSupport()
    }

    @Test
    fun `the desktop account signer (client tag around the local key) yields the key`() {
        val accountSigner = NostrSignerWithClientTag(inner = local, clientName = "Amethyst")

        assertSame(local, accountSigner.innermostSigner())
        assertArrayEquals(keyPair.privKey, accountSigner.localPrivKeyOrNull())
    }

    @Test
    fun `the Android account signer (client tag around metering around the local key) yields the key`() {
        val accountSigner = NostrSignerWithClientTag(inner = Forwarding(local), clientName = "Amethyst")

        assertArrayEquals(keyPair.privKey, accountSigner.localPrivKeyOrNull())
    }

    @Test
    fun `a remote or external signer has no local key`() {
        val remote = mockk<NostrSigner>(relaxed = true)

        assertNull(NostrSignerWithClientTag(inner = Forwarding(remote), clientName = "Amethyst").localPrivKeyOrNull())
    }

    @Test
    fun `a read-only login has no local key`() {
        val readOnly = NostrSignerInternal(KeyPair(pubKey = keyPair.pubKey))

        assertNull(NostrSignerWithClientTag(inner = readOnly, clientName = "Amethyst").localPrivKeyOrNull())
    }
}
