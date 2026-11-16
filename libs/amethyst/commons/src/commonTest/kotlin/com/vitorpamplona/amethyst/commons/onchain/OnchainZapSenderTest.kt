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
package com.vitorpamplona.amethyst.commons.onchain

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.builder.OnchainZapBuilder
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.BitcoinAddressTx
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.BitcoinTx
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.FeeEstimates
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.OnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.Utxo
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.BitcoinTransaction
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.PsbtSigner
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OnchainZapSenderTest {
    private val senderPriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val recipientPriv = "000000000000000000000000000000000000000000000000000000000000000d"

    private fun xOnly(privHex: String) =
        Secp256k1Instance
            .compressedPubKeyFor(privHex.hexToByteArray())
            .copyOfRange(1, 33)
            .toHexKey()

    private val senderSigner = NostrSignerInternal(KeyPair(senderPriv.hexToByteArray()))
    private val senderPubKey = xOnly(senderPriv)
    private val recipientPubKey = xOnly(recipientPriv)

    /** Records the broadcast tx and hands back its real txid. */
    private class FakeBackend(
        private val utxos: List<Utxo>,
        private val broadcastFails: Boolean = false,
    ) : OnchainBackend {
        var broadcastedHex: String? = null

        override suspend fun getTx(txid: String): BitcoinTx? = null

        override suspend fun getUtxosForAddress(address: String): List<Utxo> = utxos

        override suspend fun getTxsForAddress(
            address: String,
            afterTxid: String?,
        ): List<BitcoinAddressTx> = emptyList()

        override suspend fun broadcast(rawTxHex: String): String {
            if (broadcastFails) throw RuntimeException("relay rejected tx")
            broadcastedHex = rawTxHex
            return BitcoinTransaction.parse(rawTxHex).txid()
        }

        override suspend fun tipHeight(): Long = 800_000L

        override suspend fun feeEstimates(): FeeEstimates = FeeEstimates(20.0, 10.0, 5.0)
    }

    @Test
    fun profileZapSuccess() =
        runTest {
            val backend = FakeBackend(listOf(Utxo("1".repeat(64), 0, 250_000L, 6)))
            var publishedTemplate: com.vitorpamplona.quartz.nip01Core.signers.EventTemplate<OnchainZapEvent>? = null

            val result =
                OnchainZapSender.send(
                    backend = backend,
                    signer = senderSigner,
                    senderPubKey = senderPubKey,
                    recipientPubKey = recipientPubKey,
                    amountSats = 50_000L,
                    feeRateSatPerVByte = 5.0,
                    comment = "thanks!",
                    zappedEvent = null,
                ) { template ->
                    publishedTemplate = template
                    senderSigner.sign(template)
                }

            assertIs<OnchainZapSendResult.Success>(result)
            assertTrue(result.feeSats > 0)

            // The broadcast transaction's txid must match what the receipt references.
            val broadcastTxid = BitcoinTransaction.parse(backend.broadcastedHex!!).txid()
            assertEquals(broadcastTxid, result.txid)

            // The published kind:8333 receipt must reference the same txid, recipient, amount.
            val receipt = senderSigner.sign<OnchainZapEvent>(publishedTemplate!!)
            assertEquals(OnchainZapEvent.KIND, receipt.kind)
            assertEquals(broadcastTxid, receipt.txid())
            assertEquals(recipientPubKey, receipt.recipient())
            assertEquals(50_000L, receipt.claimedAmountInSats())
            assertTrue(receipt.isProfileZap())
        }

    @Test
    fun insufficientFundsFailsAtBuilding() =
        runTest {
            val backend = FakeBackend(listOf(Utxo("2".repeat(64), 0, 10_000L, 6)))
            val result =
                OnchainZapSender.send(
                    backend = backend,
                    signer = senderSigner,
                    senderPubKey = senderPubKey,
                    recipientPubKey = recipientPubKey,
                    amountSats = 1_000_000L,
                    feeRateSatPerVByte = 5.0,
                    comment = "",
                    zappedEvent = null,
                ) { senderSigner.sign(it) }

            assertIs<OnchainZapSendResult.Failure>(result)
            assertEquals(OnchainZapSendStage.BUILDING, result.stage)
            assertEquals(null, result.broadcastTxid)
        }

    @Test
    fun broadcastFailureKeepsNoTxid() =
        runTest {
            val backend =
                FakeBackend(listOf(Utxo("3".repeat(64), 0, 250_000L, 6)), broadcastFails = true)
            val result =
                OnchainZapSender.send(
                    backend = backend,
                    signer = senderSigner,
                    senderPubKey = senderPubKey,
                    recipientPubKey = recipientPubKey,
                    amountSats = 50_000L,
                    feeRateSatPerVByte = 5.0,
                    comment = "",
                    zappedEvent = null,
                ) { senderSigner.sign(it) }

            assertIs<OnchainZapSendResult.Failure>(result)
            assertEquals(OnchainZapSendStage.BROADCASTING, result.stage)
            assertEquals(null, result.broadcastTxid)
        }

    @Test
    fun publishFailureReportsBroadcastTxid() =
        runTest {
            val backend = FakeBackend(listOf(Utxo("4".repeat(64), 0, 250_000L, 6)))
            val result =
                OnchainZapSender.send(
                    backend = backend,
                    signer = senderSigner,
                    senderPubKey = senderPubKey,
                    recipientPubKey = recipientPubKey,
                    amountSats = 50_000L,
                    feeRateSatPerVByte = 5.0,
                    comment = "",
                    zappedEvent = null,
                ) { throw RuntimeException("relay down") }

            assertIs<OnchainZapSendResult.Failure>(result)
            assertEquals(OnchainZapSendStage.PUBLISHING, result.stage)
            // The payment went through — the txid is preserved for retry/diagnostics.
            val broadcastTxid = BitcoinTransaction.parse(backend.broadcastedHex!!).txid()
            assertEquals(broadcastTxid, result.broadcastTxid)
        }

    /**
     * A signer that ignores the PSBT it was handed and instead signs a
     * completely different transaction of its own — the substitution attack.
     */
    private class TamperingSigner(
        private val inner: NostrSignerInternal,
        private val attackerControlledPubKey: HexKey,
    ) : NostrSigner(inner.pubKey) {
        override fun isWriteable() = inner.isWriteable()

        override fun hasForegroundSupport() = inner.hasForegroundSupport()

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

        override suspend fun deriveKey(nonce: HexKey): HexKey = inner.deriveKey(nonce)

        override suspend fun signPsbt(psbtHex: String): String {
            // Discard the requested PSBT entirely; build and sign one that pays
            // the attacker instead, then hand it back as if it were the answer.
            val malicious =
                OnchainZapBuilder.build(
                    senderPubKey = inner.pubKey,
                    recipientPubKey = attackerControlledPubKey,
                    amountSats = 200_000L,
                    feeRateSatPerVByte = 1.0,
                    availableUtxos = listOf(Utxo("9".repeat(64), 0, 250_000L, 6)),
                )
            PsbtSigner.signKeyPathInputs(malicious.psbt, inner.keyPair.privKey!!)
            return malicious.psbt.toHex()
        }
    }

    @Test
    fun rejectsASignerThatReturnsADifferentTransaction() =
        runTest {
            val backend = FakeBackend(listOf(Utxo("5".repeat(64), 0, 250_000L, 6)))
            val tamperingSigner = TamperingSigner(senderSigner, attackerControlledPubKey = recipientPubKey)

            val result =
                OnchainZapSender.send(
                    backend = backend,
                    signer = tamperingSigner,
                    senderPubKey = senderPubKey,
                    recipientPubKey = recipientPubKey,
                    amountSats = 50_000L,
                    feeRateSatPerVByte = 5.0,
                    comment = "",
                    zappedEvent = null,
                ) { senderSigner.sign(it) }

            // The substituted transaction must be rejected at the signing stage,
            // and nothing must have been broadcast.
            assertIs<OnchainZapSendResult.Failure>(result)
            assertEquals(OnchainZapSendStage.SIGNING, result.stage)
            assertEquals(null, backend.broadcastedHex)
        }

    @Test
    fun sendSplitProducesOneReceiptPerRecipient() =
        runTest {
            val r1 = xOnly("000000000000000000000000000000000000000000000000000000000000000d")
            val r2 = xOnly("000000000000000000000000000000000000000000000000000000000000000e")
            val r3 = xOnly("000000000000000000000000000000000000000000000000000000000000000f")
            val backend = FakeBackend(listOf(Utxo("1".repeat(64), 0, 1_000_000L, 6)))
            val publishedReceipts = mutableListOf<OnchainZapEvent>()

            val result =
                OnchainZapSender.sendSplit(
                    backend = backend,
                    signer = senderSigner,
                    senderPubKey = senderPubKey,
                    recipients =
                        listOf(
                            OnchainZapShare(r1, 50_000L, 5.0),
                            OnchainZapShare(r2, 30_000L, 3.0),
                            OnchainZapShare(r3, 20_000L, 2.0),
                        ),
                    feeRateSatPerVByte = 5.0,
                    comment = "thanks all",
                    zappedEvent = null,
                ) { template ->
                    val event = senderSigner.sign<OnchainZapEvent>(template)
                    publishedReceipts += event
                    event
                }

            assertIs<OnchainZapSendResult.Success>(result)
            assertEquals(3, publishedReceipts.size)
            assertEquals(2, result.extraReceiptEventIds.size)

            // All receipts must reference the SAME txid.
            val broadcastTxid = BitcoinTransaction.parse(backend.broadcastedHex!!).txid()
            assertEquals(broadcastTxid, result.txid)
            assertTrue(publishedReceipts.all { it.txid() == broadcastTxid })

            // Per-recipient receipts carry the right pubkey + sat amount.
            val byRecipient = publishedReceipts.associateBy { it.recipient() }
            assertEquals(50_000L, byRecipient[r1]?.claimedAmountInSats())
            assertEquals(30_000L, byRecipient[r2]?.claimedAmountInSats())
            assertEquals(20_000L, byRecipient[r3]?.claimedAmountInSats())
        }

    @Test
    fun sendSplitPartialPublishFailureKeepsBroadcastedReceiptIds() =
        runTest {
            val r1 = xOnly("000000000000000000000000000000000000000000000000000000000000000d")
            val r2 = xOnly("000000000000000000000000000000000000000000000000000000000000000e")
            val r3 = xOnly("000000000000000000000000000000000000000000000000000000000000000f")
            val backend = FakeBackend(listOf(Utxo("1".repeat(64), 0, 1_000_000L, 6)))
            var calls = 0

            val result =
                OnchainZapSender.sendSplit(
                    backend = backend,
                    signer = senderSigner,
                    senderPubKey = senderPubKey,
                    recipients =
                        listOf(
                            OnchainZapShare(r1, 50_000L, 1.0),
                            OnchainZapShare(r2, 30_000L, 1.0),
                            OnchainZapShare(r3, 20_000L, 1.0),
                        ),
                    feeRateSatPerVByte = 5.0,
                    comment = "",
                    zappedEvent = null,
                ) { template ->
                    calls++
                    if (calls == 2) throw RuntimeException("relay rejected receipt")
                    senderSigner.sign(template)
                }

            assertIs<OnchainZapSendResult.Failure>(result)
            assertEquals(OnchainZapSendStage.PUBLISHING, result.stage)
            // Tx is on-chain.
            assertTrue(result.broadcastTxid != null)
            // Exactly one receipt was published before the failure.
            assertEquals(1, result.publishedReceiptEventIds.size)
        }
}
