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
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nipBCOnchainZaps.builder.OnchainZapBuilder
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.OnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.Psbt
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.PsbtFinalizer
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.PsbtSignatureVerifier
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.inputTapKeySig
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.setInputTapKeySig
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.SegwitAddress
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import kotlin.coroutines.cancellation.CancellationException

/** The stage a NIP-BC onchain zap send reached before it finished or failed. */
enum class OnchainZapSendStage {
    /** Querying the chain backend for the sender's spendable UTXOs. */
    LOADING_UTXOS,

    /** Selecting coins and assembling the unsigned PSBT. */
    BUILDING,

    /** Signing the PSBT inputs and finalizing the transaction. */
    SIGNING,

    /** Broadcasting the signed transaction to the network. */
    BROADCASTING,

    /** Publishing the kind:8333 zap receipt to relays. */
    PUBLISHING,
}

/**
 * Machine-readable reason for an [OnchainZapSendResult.Failure], so front ends
 * can map the failure to a localized message. The English
 * [OnchainZapSendResult.Failure.message] stays available for logs, tests, and
 * the CLI.
 */
enum class OnchainZapSendError(
    /**
     * Whether [OnchainZapSendResult.Failure.cause] carries a message crafted
     * for humans (insufficient funds, signer tampering, which share is below
     * dust) as opposed to low-level exception text. UIs may surface the cause
     * message as a diagnostic detail when this is true.
     */
    val causeIsUserFacing: Boolean,
) {
    /** No [OnchainBackend] is configured for the account. */
    BACKEND_NOT_CONFIGURED(causeIsUserFacing = false),

    /** The chain backend could not return the sender's UTXOs. */
    LOAD_UTXOS_FAILED(causeIsUserFacing = false),

    /** Coin selection / PSBT assembly failed (e.g. insufficient funds). */
    BUILD_FAILED(causeIsUserFacing = true),

    /** A recipient's split share is below the dust threshold. */
    RECIPIENT_BELOW_DUST(causeIsUserFacing = true),

    /** Signing, signature verification, or finalization failed. */
    SIGN_FAILED(causeIsUserFacing = true),

    /** The signed transaction could not be broadcast. */
    BROADCAST_FAILED(causeIsUserFacing = false),

    /** The payment broadcast, but a kind:8333 receipt could not be published. */
    RECEIPT_PUBLISH_FAILED(causeIsUserFacing = false),
}

/** Outcome of an [OnchainZapSender.send] attempt. */
sealed interface OnchainZapSendResult {
    /**
     * The transaction was broadcast and the zap receipt was published.
     *
     * @property txid The broadcast Bitcoin transaction id.
     * @property receiptEventId The id of the first published kind:8333 event.
     *   For a split zap, additional receipts (one per recipient) are also
     *   published; see [extraReceiptEventIds]. Null for a plain address send
     *   ([OnchainZapSender.sendToAddress]), which has no receipt to publish.
     * @property feeSats Miner fee paid.
     * @property changeSats Change returned to the sender (0 if none).
     * @property extraReceiptEventIds Receipt ids for the remaining recipients
     *   when this was a split zap. Empty for a single-recipient zap.
     */
    data class Success(
        val txid: String,
        val receiptEventId: HexKey?,
        val feeSats: Long,
        val changeSats: Long,
        val extraReceiptEventIds: List<HexKey> = emptyList(),
    ) : OnchainZapSendResult

    /**
     * The send failed at [stage]. The transaction was NOT broadcast unless
     * [stage] is [OnchainZapSendStage.PUBLISHING], in which case the payment
     * went through but the receipt could not be published.
     */
    data class Failure(
        val stage: OnchainZapSendStage,
        /** Machine-readable reason — map this to a localized message in UIs. */
        val error: OnchainZapSendError,
        /** English description for logs, tests, and the CLI. */
        val message: String,
        val cause: Throwable? = null,
        /** Non-null when the payment was broadcast but a later stage failed. */
        val broadcastTxid: String? = null,
        /**
         * Receipt ids that DID publish before the failure, in the order they
         * were sent. Empty for non-publishing failures and for single-recipient
         * publishes that fail on the first receipt.
         */
        val publishedReceiptEventIds: List<HexKey> = emptyList(),
        /**
         * How many receipts a split zap intended to publish. Null when the
         * failure did not happen while publishing split receipts.
         */
        val totalReceipts: Int? = null,
    ) : OnchainZapSendResult
}

/**
 * Orchestrates a NIP-BC onchain zap end to end:
 * load UTXOs → build PSBT → sign → finalize → broadcast → publish kind:8333.
 *
 * Stateless and platform-agnostic — it takes the chain backend, the signer,
 * and a publish callback, so the same pipeline works from the Android app, the
 * CLI, or tests. Per-stage failures are reported as
 * [OnchainZapSendResult.Failure] rather than thrown, except [CancellationException]
 * which always propagates.
 */
object OnchainZapSender {
    /**
     * @param backend Chain data source (UTXOs + broadcast).
     * @param signer The sender's signer; must support `signPsbt`.
     * @param senderPubKey The sender's x-only Nostr pubkey (hex).
     * @param recipientPubKey The recipient's x-only Nostr pubkey (hex).
     * @param amountSats Amount to pay the recipient.
     * @param feeRateSatPerVByte Target fee rate.
     * @param comment Optional human-readable comment for the receipt's content.
     * @param zappedEvent The event being zapped, or null for a profile zap.
     * @param publish Publishes the signed kind:8333 receipt and returns the event.
     */
    suspend fun send(
        backend: OnchainBackend,
        signer: NostrSigner,
        senderPubKey: HexKey,
        recipientPubKey: HexKey,
        amountSats: Long,
        feeRateSatPerVByte: Double,
        comment: String,
        zappedEvent: EventHintBundle<out Event>?,
        publish: suspend (EventTemplate<OnchainZapEvent>) -> Event,
    ): OnchainZapSendResult {
        // 1. Load the sender's UTXOs.
        val utxos =
            try {
                val address = TaprootAddress.fromPubKey(senderPubKey)
                backend.getUtxosForAddress(address)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.LOADING_UTXOS, OnchainZapSendError.LOAD_UTXOS_FAILED, "Could not load your Bitcoin balance", e)
            }

        // 2. Coin-select and assemble the unsigned PSBT.
        val built =
            try {
                OnchainZapBuilder.build(
                    senderPubKey = senderPubKey,
                    recipientPubKey = recipientPubKey,
                    amountSats = amountSats,
                    feeRateSatPerVByte = feeRateSatPerVByte,
                    availableUtxos = utxos,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.BUILDING, OnchainZapSendError.BUILD_FAILED, e.message ?: "Could not build the transaction", e)
            }

        // 3. Sign, verify the signer didn't tamper, and finalize.
        val rawTxHex =
            try {
                signVerifyFinalize(built, signer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.SIGNING, OnchainZapSendError.SIGN_FAILED, e.message ?: "Could not sign the transaction", e)
            }

        // 4. Broadcast.
        val txid =
            try {
                backend.broadcast(rawTxHex)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.BROADCASTING, OnchainZapSendError.BROADCAST_FAILED, "Could not broadcast the transaction", e)
            }

        // 5. Publish the kind:8333 receipt. The payment is already on-chain at
        // this point, so a failure here keeps the txid for retry/diagnostics.
        val receiptId =
            try {
                val template =
                    if (zappedEvent != null) {
                        OnchainZapEvent.build(txid, recipientPubKey, amountSats, zappedEvent, comment)
                    } else {
                        OnchainZapEvent.buildProfileZap(txid, recipientPubKey, amountSats, comment)
                    }
                publish(template).id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return OnchainZapSendResult.Failure(
                    stage = OnchainZapSendStage.PUBLISHING,
                    error = OnchainZapSendError.RECEIPT_PUBLISH_FAILED,
                    message = "Payment sent, but the zap receipt could not be published",
                    cause = e,
                    broadcastTxid = txid,
                )
            }

        return OnchainZapSendResult.Success(
            txid = txid,
            receiptEventId = receiptId,
            feeSats = built.feeSats,
            changeSats = built.changeSats,
        )
    }

    /**
     * Send an onchain split zap: pays N recipients with one Bitcoin transaction
     * (one output per recipient) and publishes one kind:8333 receipt per
     * recipient, all sharing the same `i <txid>` tag.
     *
     * Per-recipient shares MUST be precomputed (e.g. via
     * [OnchainZapSplitter.distribute]) so the on-chain output amounts and the
     * `amount` tags on the receipts agree exactly.
     *
     * Failure semantics: if any receipt fails to publish, the transaction is
     * already on-chain; the result reports the broadcast txid, the ids of any
     * receipts that *did* publish, and the publishing-stage failure.
     */
    suspend fun sendSplit(
        backend: OnchainBackend,
        signer: NostrSigner,
        senderPubKey: HexKey,
        recipients: List<OnchainZapShare>,
        feeRateSatPerVByte: Double,
        comment: String,
        zappedEvent: EventHintBundle<out Event>?,
        publish: suspend (EventTemplate<OnchainZapEvent>) -> Event,
    ): OnchainZapSendResult {
        require(recipients.isNotEmpty()) { "recipients must be non-empty" }

        // 1. Load the sender's UTXOs.
        val utxos =
            try {
                val address = TaprootAddress.fromPubKey(senderPubKey)
                backend.getUtxosForAddress(address)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.LOADING_UTXOS, OnchainZapSendError.LOAD_UTXOS_FAILED, "Could not load your Bitcoin balance", e)
            }

        // 2. Coin-select and assemble the multi-output PSBT.
        val built =
            try {
                OnchainZapBuilder.buildSplit(
                    senderPubKey = senderPubKey,
                    recipients = recipients.map { it.recipientPubKey to it.sats },
                    feeRateSatPerVByte = feeRateSatPerVByte,
                    availableUtxos = utxos,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.BUILDING, OnchainZapSendError.BUILD_FAILED, e.message ?: "Could not build the transaction", e)
            }

        // 3. Sign, verify, and finalize. Same fund-safety contract as [send].
        val rawTxHex =
            try {
                signVerifyFinalize(built, signer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.SIGNING, OnchainZapSendError.SIGN_FAILED, e.message ?: "Could not sign the transaction", e)
            }

        // 4. Broadcast.
        val txid =
            try {
                backend.broadcast(rawTxHex)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.BROADCASTING, OnchainZapSendError.BROADCAST_FAILED, "Could not broadcast the transaction", e)
            }

        // 5. Publish one receipt per recipient. If any one fails, surface the
        // failure but keep the txid + receipts that did publish so the user
        // (and any retry tooling) has the full picture.
        val publishedIds = ArrayList<HexKey>(recipients.size)
        for (share in recipients) {
            try {
                val template =
                    if (zappedEvent != null) {
                        OnchainZapEvent.build(txid, share.recipientPubKey, share.sats, zappedEvent, comment)
                    } else {
                        OnchainZapEvent.buildProfileZap(txid, share.recipientPubKey, share.sats, comment)
                    }
                publishedIds.add(publish(template).id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return OnchainZapSendResult.Failure(
                    stage = OnchainZapSendStage.PUBLISHING,
                    error = OnchainZapSendError.RECEIPT_PUBLISH_FAILED,
                    message =
                        "Payment sent (${publishedIds.size} of ${recipients.size} receipts published), " +
                            "but the next receipt could not be published",
                    cause = e,
                    broadcastTxid = txid,
                    publishedReceiptEventIds = publishedIds.toList(),
                    totalReceipts = recipients.size,
                )
            }
        }

        return OnchainZapSendResult.Success(
            txid = txid,
            receiptEventId = publishedIds.first(),
            feeSats = built.feeSats,
            changeSats = built.changeSats,
            extraReceiptEventIds = publishedIds.drop(1),
        )
    }

    /**
     * Pay an explicit Bitcoin address (e.g. a profile's NIP-A3 `bitcoin`
     * payment target) from the sender's NIP-BC Taproot wallet.
     *
     * This is a plain wallet send, not a zap: the destination is not derived
     * from a Nostr pubkey, so no kind:8333 receipt is possible and none is
     * published — [OnchainZapSendResult.Success.receiptEventId] is null.
     * Only native segwit (`bc1…`) mainnet addresses are supported.
     */
    suspend fun sendToAddress(
        backend: OnchainBackend,
        signer: NostrSigner,
        senderPubKey: HexKey,
        recipientAddress: String,
        amountSats: Long,
        feeRateSatPerVByte: Double,
    ): OnchainZapSendResult {
        // 1. Load the sender's UTXOs.
        val utxos =
            try {
                val address = TaprootAddress.fromPubKey(senderPubKey)
                backend.getUtxosForAddress(address)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.LOADING_UTXOS, OnchainZapSendError.LOAD_UTXOS_FAILED, "Could not load your Bitcoin balance", e)
            }

        // 2. Decode the destination address and assemble the unsigned PSBT.
        val built =
            try {
                OnchainZapBuilder.buildToScripts(
                    senderPubKey = senderPubKey,
                    recipients = listOf(SegwitAddress.scriptPubKeyFor(recipientAddress) to amountSats),
                    feeRateSatPerVByte = feeRateSatPerVByte,
                    availableUtxos = utxos,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.BUILDING, OnchainZapSendError.BUILD_FAILED, e.message ?: "Could not build the transaction", e)
            }

        // 3. Sign, verify, and finalize. Same fund-safety contract as [send].
        val rawTxHex =
            try {
                signVerifyFinalize(built, signer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.SIGNING, OnchainZapSendError.SIGN_FAILED, e.message ?: "Could not sign the transaction", e)
            }

        // 4. Broadcast. No receipt stage: an address send has nothing to publish.
        val txid =
            try {
                backend.broadcast(rawTxHex)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.BROADCASTING, OnchainZapSendError.BROADCAST_FAILED, "Could not broadcast the transaction", e)
            }

        return OnchainZapSendResult.Success(
            txid = txid,
            receiptEventId = null,
            feeSats = built.feeSats,
            changeSats = built.changeSats,
        )
    }

    /**
     * Sign with the fund-safety contract shared by every send path: the signer
     * may ONLY contribute signatures. The unsigned transaction must come back
     * byte-identical (rejects substitution attacks), only the tap key sigs are
     * copied onto the PSBT WE built (witness UTXOs and tap internal keys stay
     * the values we chose), and every input must verify before money moves.
     *
     * @return the finalized raw transaction hex, ready to broadcast.
     * @throws IllegalStateException with a user-facing message on any violation.
     */
    private suspend fun signVerifyFinalize(
        built: OnchainZapBuilder.Result,
        signer: NostrSigner,
    ): String {
        val signedHex = signer.signPsbt(built.psbt.toHex())
        val signedPsbt = Psbt.parse(signedHex)

        val expectedTx = built.psbt.global.get(Psbt.PSBT_GLOBAL_UNSIGNED_TX)
        val returnedTx = signedPsbt.global.get(Psbt.PSBT_GLOBAL_UNSIGNED_TX)
        check(expectedTx != null && returnedTx != null && expectedTx.contentEquals(returnedTx)) {
            "The signer returned a different transaction than the one it was asked to sign"
        }

        built.psbt.unsignedTx.inputs.indices.forEach { i ->
            val sig =
                checkNotNull(signedPsbt.inputTapKeySig(i)) {
                    "The signer did not sign every input"
                }
            built.psbt.setInputTapKeySig(i, sig)
        }

        check(PsbtSignatureVerifier.verifyAllKeyPathInputs(built.psbt)) {
            "The signed transaction has invalid signatures"
        }
        return PsbtFinalizer.finalizeToHex(built.psbt)
    }

    private fun fail(
        stage: OnchainZapSendStage,
        error: OnchainZapSendError,
        message: String,
        cause: Throwable? = null,
    ) = OnchainZapSendResult.Failure(stage, error, message, cause)
}
