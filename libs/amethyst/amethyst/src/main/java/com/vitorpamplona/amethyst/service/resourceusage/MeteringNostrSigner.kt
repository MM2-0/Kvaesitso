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
package com.vitorpamplona.amethyst.service.resourceusage

import com.vitorpamplona.amethyst.commons.model.ForwardingNostrSigner
import com.vitorpamplona.amethyst.commons.service.resourceusage.ResourceUsageAccountant
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.NostrSignerWithClientTag

/**
 * A [NostrSigner] decorator (same pattern as [NostrSignerWithClientTag]) that
 * accounts signing and encryption work into the resource-usage ledger:
 *
 *  - signature counts by signer kind — a local-key signature is ~free, a
 *    NIP-55 one wakes the Amber process over IPC, and a NIP-46 one is a full
 *    relay round-trip, so the *kind* is the battery-relevant dimension;
 *  - NIP-04/44 decrypt/encrypt counts, with CPU time metered only when the
 *    wrapped signer is a local key ([NostrSignerInternal]) — for external and
 *    remote signers the elapsed time would be IPC/network wait, not crypto
 *    cost, and would poison the CPU numbers.
 *
 * Overhead per operation: one counter increment (plus two [System.nanoTime]
 * calls for local-key crypto, nanoseconds against a millisecond-scale ECDH) —
 * nothing here can slow the operations being measured.
 *
 * Wrapped INSIDE [NostrSignerWithClientTag] (metering the raw signer), so the
 * existing `signer is NostrSignerWithClientTag` call sites keep working;
 * anything that needs the raw signer should unwrap via [com.vitorpamplona.amethyst.commons.model.innermostSigner].
 */
class MeteringNostrSigner(
    override val inner: NostrSigner,
    private val accountant: ResourceUsageAccountant,
) : NostrSigner(inner.pubKey),
    ForwardingNostrSigner {
    private val signKey = UsageKeys.signs(signerKind(inner))
    private val meterCryptoTime = inner is NostrSignerInternal

    override fun isWriteable(): Boolean = inner.isWriteable()

    override suspend fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T {
        accountant.add(signKey, 1)
        return inner.sign(createdAt, kind, tags, content)
    }

    override suspend fun nip04Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String = metered(UsageKeys.ENCRYPT_COUNT, UsageKeys.ENCRYPT_US) { inner.nip04Encrypt(plaintext, toPublicKey) }

    override suspend fun nip04Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String = metered(UsageKeys.DECRYPT_COUNT, UsageKeys.DECRYPT_US) { inner.nip04Decrypt(ciphertext, fromPublicKey) }

    override suspend fun nip44Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String = metered(UsageKeys.ENCRYPT_COUNT, UsageKeys.ENCRYPT_US) { inner.nip44Encrypt(plaintext, toPublicKey) }

    override suspend fun nip44Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String = metered(UsageKeys.DECRYPT_COUNT, UsageKeys.DECRYPT_US) { inner.nip44Decrypt(ciphertext, fromPublicKey) }

    override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = metered(UsageKeys.DECRYPT_COUNT, UsageKeys.DECRYPT_US) { inner.decryptZapEvent(event) }

    override suspend fun deriveKey(nonce: HexKey): HexKey = inner.deriveKey(nonce)

    override suspend fun signPsbt(psbtHex: String): String {
        accountant.add(signKey, 1)
        return inner.signPsbt(psbtHex)
    }

    override fun hasForegroundSupport(): Boolean = inner.hasForegroundSupport()

    private inline fun <T> metered(
        countKey: String,
        usKey: String,
        op: () -> T,
    ): T {
        accountant.add(countKey, 1)
        if (!meterCryptoTime) return op()
        val start = System.nanoTime()
        try {
            return op()
        } finally {
            accountant.add(usKey, (System.nanoTime() - start) / 1_000)
        }
    }

    companion object {
        fun signerKind(signer: NostrSigner): String =
            when (signer) {
                is NostrSignerExternal -> UsageKeys.SIGNER_NIP55
                is NostrSignerRemote -> UsageKeys.SIGNER_NIP46
                else -> UsageKeys.SIGNER_LOCAL
            }
    }
}
