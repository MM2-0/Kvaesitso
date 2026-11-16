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
package com.vitorpamplona.amethyst.commons.cashu.ops

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip60Cashu.bdhke.Bdhke
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.history.SpendingDirection
import com.vitorpamplona.quartz.nip60Cashu.history.TokenReference
import com.vitorpamplona.quartz.nip60Cashu.mintApi.CashuMintOperations
import com.vitorpamplona.quartz.nip60Cashu.mintApi.DleqProofDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MeltQuoteBolt11ResponseDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpClient
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpException
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpTransport
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintProtocolException
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintQuoteBolt11ResponseDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.ProofState
import com.vitorpamplona.quartz.nip60Cashu.mintApi.RandomSecretFactory
import com.vitorpamplona.quartz.nip60Cashu.mintApi.SecretFactory
import com.vitorpamplona.quartz.nip60Cashu.mintApi.SwapFeeExceedsInputsException
import com.vitorpamplona.quartz.nip60Cashu.mintApi.SwapResult
import com.vitorpamplona.quartz.nip60Cashu.mintApi.splitAmountIntoDenominations
import com.vitorpamplona.quartz.nip60Cashu.p2pk.P2PK
import com.vitorpamplona.quartz.nip60Cashu.p2pk.P2PKUnredeemableException
import com.vitorpamplona.quartz.nip60Cashu.p2pk.firstUnsignableP2pkLock
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.token.TokenContent
import com.vitorpamplona.quartz.nip60Cashu.token.V4Encoder
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.tags.NutzapMintTag
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.redemption.notifySender
import com.vitorpamplona.quartz.nip87Ecash.cashu.CashuMintEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Wallet-level operations that combine the [CashuMintOperations] HTTP layer
 * with Nostr event publishing (kind 7375 / 7376 / 7374 / 17375 / 10019 /
 * deletion-5).
 *
 * Stateless w.r.t. wallet contents — each call receives the current state
 * from `CashuWalletState`. Signing and broadcast are abstracted behind the
 * [signer] + [publish] callbacks so the ops layer can be unit-tested without
 * a full [com.vitorpamplona.amethyst.commons.model.Account] graph.
 *
 * Per-mint [CashuMintOperations] instances are cached so repeated calls
 * against the same mint reuse the same `MintHttpClient` and avoid re-
 * instantiating the JSON serializer per request.
 */
class CashuWalletOps(
    private val signer: NostrSigner,
    private val publish: suspend (Event) -> Unit,
    /** How mint requests go out (OkHttp on Android and the JVM, picking Tor or a proxy per URL). */
    private val mintTransport: MintHttpTransport,
    /**
     * NUT-13 secret strategy. Defaults to random for backwards
     * compatibility with tests that don't carry a seed. The wallet state
     * supplies a [DeterministicSecretFactory] in production so kind:7375
     * loss is recoverable via NUT-09 /v1/restore.
     */
    private val secretFactory: SecretFactory = RandomSecretFactory,
    /**
     * Suspend callback that ensures the NUT-13 seed is materialised in
     * the caller's cache before any blinding op runs. The factory above
     * reads that cache synchronously — without warming first, a fresh
     * wallet falls back to random secrets for its very first mint.
     * Default is a no-op for tests / random-only callers.
     */
    private val seedWarmer: suspend () -> Unit = {},
    /**
     * Returns the wallet's NUT-13 seed bytes, or null if the wallet
     * isn't ready (kind:17375 not decrypted yet). Used by the
     * "outputs already signed" recovery path in [completeMintFromLightning]
     * to re-derive the blinded outputs the mint has already signed.
     * Default returns null — recovery is then skipped and the original
     * mint error propagates.
     */
    private val seedForRestore: suspend () -> ByteArray? = { null },
    /**
     * Inspect the next NUT-13 counter for a keyset without advancing
     * it. Used to rewind the restore window in [completeMintFromLightning]
     * recovery. Default is 0 (no persistent counter store).
     */
    private val peekCashuCounter: suspend (keysetId: String) -> Long = { 0L },
    /**
     * Atomically reserve [count] consecutive NUT-13 counters and
     * return the first reserved index. Used by the recovery path to
     * advance past slots the mint confirmed in use. Default is a
     * no-op for tests / random-only callers.
     */
    private val reserveCashuCounters: suspend (keysetId: String, count: Int) -> Long = { _, _ -> 0L },
) {
    private val opsCache = ConcurrentMap<String, CashuMintOperations>()

    private fun ops(mintUrl: String): CashuMintOperations =
        opsCache.getOrPut(mintUrl.trimEnd('/')) {
            // userConfigured: these are the mints of the user's own NIP-60 wallet,
            // added deliberately, so a self-hosted mint on the LAN stays usable.
            CashuMintOperations(MintHttpClient(mintUrl, userConfigured = true, transport = mintTransport), secretFactory)
        }

    /**
     * Publish kind:17375 + kind:10019 in one go.
     *
     * The same `mints` list and P2PK key feed both events so that any other
     * client receiving a nutzap to this user will know which mints they
     * accept and which P2PK pubkey to lock against. If [p2pkPrivkeyHex] is
     * null, a fresh key is generated.
     *
     * Returns the generated/used P2PK private key so the caller can persist it
     * across the wallet save flow.
     */
    suspend fun publishWalletEvents(
        mints: List<String>,
        p2pkPrivkeyHex: String?,
        nutzapRelays: List<NormalizedRelayUrl> = emptyList(),
    ): CreatedWallet {
        require(mints.isNotEmpty()) { "Wallet must have at least one mint" }

        val priv = (p2pkPrivkeyHex?.takeIf { it.isNotBlank() } ?: Bdhke.randomScalar().toHexKey())
        val pubKeyHex = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(priv.hexToByteArray())).toHexKey()

        val walletTemplate = CashuWalletEvent.build(mints, priv, signer)
        val walletEvent = signer.sign(walletTemplate)
        publish(walletEvent)

        // Populate `relay` tags so senders know where to publish nutzaps.
        // Per NIP-61 these are the relays where the recipient reads incoming
        // token events, so callers pass our inbox-side relays here (NIP-65
        // outbox model). Without them, senders fall back to NIP-65 and may
        // publish where we don't subscribe for inbound nutzaps.
        val nutzapInfoTemplate =
            NutzapInfoEvent.build(
                mints = mints.map { NutzapMintTag(it, listOf("sat")) },
                relays = nutzapRelays,
                p2pkPubkey = pubKeyHex,
            )
        val nutzapInfoEvent = signer.sign(nutzapInfoTemplate)
        publish(nutzapInfoEvent)

        return CreatedWallet(
            walletEvent = walletEvent,
            nutzapInfo = nutzapInfoEvent,
            p2pkPrivkeyHex = priv,
            p2pkPubkeyHex = pubKeyHex,
        )
    }

    /**
     * Stop advertising that this user accepts NIP-61 nutzaps.
     *
     * Two steps, in order:
     *   1. Replace kind:10019 with an EMPTY version (no mints / relays / P2PK
     *      pubkey). Replaceable-event replacement is honored by every relay,
     *      so even relays that ignore NIP-09 will stop senders — a reader's
     *      nutzap funding resolves against the recipient's kind:10019 and
     *      bails when it has no shared mint or no P2PK pubkey. This is the
     *      durable signal.
     *   2. NIP-09 delete the now-empty kind:10019 by its address coordinate
     *      (DeletionRequestEvent.build adds the `a` tag automatically because the
     *      event is replaceable) so relays that DO honor deletions drop it
     *      entirely. Deletions are optional on Nostr, which is why step 1
     *      runs first as the fallback.
     *
     * Leaves the kind:17375 wallet and the user's proofs untouched — only the
     * inbound-nutzap advertisement goes away; the wallet still sends and holds
     * ecash. Use [deleteWallet] for a full teardown.
     */
    suspend fun stopNutzaps() {
        val emptyTemplate = NutzapInfoEvent.buildEmpty()
        val emptyEvent = signer.sign(emptyTemplate)
        publish(emptyEvent)

        val delTemplate = DeletionRequestEvent.build(listOf(emptyEvent))
        val delEvent = signer.sign(delTemplate)
        publish(delEvent)
    }

    /**
     * Delete the NIP-60 Cashu wallet entirely.
     *
     * First withdraws the nutzap advertisement via [stopNutzaps] (empty
     * kind:10019 + its NIP-09 delete) — a wallet-less user must not keep
     * advertising a receiving address it can no longer redeem against — then
     * NIP-09 deletes the kind:17375 wallet definition itself.
     *
     * Does NOT delete the kind:7375 token events: that ecash still exists at
     * the mint. The caller's UI must warn that any balance left in the wallet,
     * and any inbound nutzaps locked to the discarded P2PK key that haven't
     * been redeemed yet, may become unrecoverable once the key is gone.
     */
    suspend fun deleteWallet(walletEvent: CashuWalletEvent) {
        stopNutzaps()

        val delTemplate = DeletionRequestEvent.build(listOf(walletEvent))
        val delEvent = signer.sign(delTemplate)
        publish(delEvent)
    }

    /**
     * Mint phase 1: ask the mint for a bolt11 invoice and persist the quote
     * id as a kind:7374 event so we can resume on next launch.
     */
    suspend fun startMintFromLightning(
        mintUrl: String,
        amountSats: Long,
    ): MintQuoteStarted {
        // NUT-20: generate a fresh per-quote keypair, bind the mint quote
        // to it, and persist the privkey inside the encrypted kind:7374
        // so the resume-on-next-launch path can sign the matching mint
        // request later. Mints that don't support NUT-20 ignore the
        // pubkey field (per spec), so always-on is safe.
        val signingPriv = Bdhke.randomScalar()
        val signingPub = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(signingPriv)).toHexKey()
        val response = ops(mintUrl).requestMintQuote(amountSats, signingPubkey = signingPub)
        val quoteTemplate =
            CashuMintQuoteEvent.build(
                quoteId = response.quote,
                mintUrl = mintUrl,
                signer = signer,
                signingPrivkey = signingPriv.toHexKey(),
            )
        val quoteEvent = signer.sign(quoteTemplate)
        publish(quoteEvent)
        return MintQuoteStarted(
            quoteEvent = quoteEvent,
            mintQuote = response,
            invoice = response.request,
        )
    }

    /** Poll the mint to check whether a quote has been paid. */
    suspend fun checkMintQuote(
        mintUrl: String,
        quoteId: String,
    ): MintQuoteBolt11ResponseDto = ops(mintUrl).mintQuoteStatus(quoteId)

    /**
     * User-initiated abandonment of a pending mint quote.
     *
     * NIP-09 deletes the kind:7374 so the quote no longer shows in the
     * pending banner. We do NOT call the mint to cancel — bolt11 invoices
     * just expire on their own, and a fresh quote is cheap if the user
     * changes their mind.
     *
     * No-op if the invoice was already paid: by the time the mint issues
     * proofs and completeMintFromLightning runs, the same NIP-09 delete is
     * fired anyway; running it twice on a missing event is harmless.
     */
    suspend fun cancelMintQuote(quoteEvent: CashuMintQuoteEvent) {
        val delTemplate = DeletionRequestEvent.build(listOf(quoteEvent))
        val delEvent = signer.sign(delTemplate)
        publish(delEvent)
    }

    /**
     * Mint phase 2: after the user paid the invoice, exchange the quote for
     * proofs, publish them as kind:7375, log kind:7376, and delete kind:7374.
     *
     * When the mint reports "outputs already signed" / "quote already
     * issued", it means a prior attempt's `/v1/mint/bolt11` succeeded at
     * the mint but we failed to publish the resulting kind:7375 locally
     * (signer dialog dismissed, transient network error, app killed).
     * The mint considers the quote consumed and won't re-issue; new
     * blinded outputs would be rejected too because the quote-budget is
     * spent. NUT-09 restore is the documented recovery path: re-derive
     * the deterministic blinded outputs from the seed, ask the mint
     * which it has signed, unblind those into proofs. The end state
     * matches what we'd have published if the first attempt hadn't
     * crashed.
     */
    suspend fun completeMintFromLightning(
        mintUrl: String,
        quoteEvent: CashuMintQuoteEvent,
        amountSats: Long,
    ): MintCompleted {
        seedWarmer()
        // NUT-20: pull both the quote id and the persisted signing privkey
        // out of the kind:7374 in one decrypt. Legacy quote events stored
        // only the quote id (string), so signingPrivkey is null in that
        // case and we skip the signature — mints that need it will
        // reject, but old quotes opened pre-NUT-20 wouldn't have had a
        // pubkey on them anyway, so the mint should accept.
        val decryptedQuote = quoteEvent.decrypt(signer)
        val tokenContent =
            try {
                ops(mintUrl)
                    .mintProofs(
                        quote = decryptedQuote.quoteId,
                        amountSats = amountSats,
                        signingPrivkey = decryptedQuote.p2pkPriv,
                    ).toTokenContent(mintUrl)
            } catch (e: MintHttpException) {
                if (isOutputsAlreadySignedError(e)) {
                    // Mint already issued for this quote on a prior crashed
                    // attempt. Recover via NUT-09 instead of bailing.
                    recoverPreviouslyIssuedProofs(mintUrl, amountSats)
                        ?: throw MintProtocolException(
                            "Mint already issued for this quote, but seed-based restore found no recoverable proofs",
                        )
                } else {
                    throw e
                }
            }

        val tokenTemplate = CashuTokenEvent.build(tokenContent, signer)
        val tokenEvent = signer.sign(tokenTemplate)
        publish(tokenEvent)

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.IN,
                amount = amountSats,
                tokenReferences =
                    listOf(
                        TokenReference(
                            eventId = tokenEvent.id,
                            relay = null,
                            marker = TokenReference.MARKER_CREATED,
                        ),
                    ),
                signer = signer,
            )
        val historyEvent = signer.sign(historyTemplate)
        publish(historyEvent)

        // NIP-09 delete the now-fulfilled quote event.
        val delTemplate = DeletionRequestEvent.build(listOf(quoteEvent))
        val delEvent = signer.sign(delTemplate)
        publish(delEvent)

        return MintCompleted(
            tokenEvent = tokenEvent,
            historyEvent = historyEvent,
            mintedAmount = amountSats,
        )
    }

    /**
     * NUT-09 fallback for the "outputs already signed" / "quote already
     * issued" mint response — re-derives the deterministic blinded
     * outputs from the seed and asks the mint which it has signed.
     *
     * Scope is intentionally narrow: the prior `/v1/mint/bolt11` call
     * reserved exactly `splitAmounts(amountSats).size` counters and
     * signed one output per slot, so we restrict the restore to
     *   - those amount denominations (skips the ~63-denom fan-out that
     *     would push request size past the mint's 1000-item cap), and
     *   - one batch starting at `peekCashuCounter - DEFAULT_RESTORE_SCAN_BACK`
     *     (the wallet reserved the counters before the failed call, so
     *     the relevant slots sit just below the current high-water mark).
     *
     * Returns null when there's no seed yet (kind:17375 not decrypted)
     * or when the restore turns up no unspent proofs — caller surfaces
     * the failure to the user.
     */
    private suspend fun recoverPreviouslyIssuedProofs(
        mintUrl: String,
        amountSats: Long,
    ): TokenContent? {
        val seed = seedForRestore() ?: return null
        val mintOps = ops(mintUrl)
        val keysetId = mintOps.activeKeyset().id
        val counterBefore = peekCashuCounter(keysetId)
        val startCounter = (counterBefore - DEFAULT_RESTORE_SCAN_BACK).coerceAtLeast(0L)
        // splitAmountIntoDenominations is what the mint flow itself used
        // to decide which amounts to ask /v1/mint/bolt11 for, so the
        // signatures the mint has correspond to exactly these denoms.
        val expectedDenoms = splitAmountIntoDenominations(amountSats).distinct()
        // One batch of DEFAULT_RESTORE_SCAN_BACK counters across just the
        // expected denoms is enough — the proofs we're looking for sit in
        // a contiguous window of `expectedDenoms.size` counters at the top
        // of [startCounter, counterBefore).
        val restoreResult =
            mintOps.restore(
                seed = seed,
                keysetId = keysetId,
                startCounter = startCounter,
                batchSize = DEFAULT_RESTORE_SCAN_BACK.toInt(),
                emptyBatchesToStop = 1,
                amounts = expectedDenoms,
            )
        if (restoreResult.proofs.isEmpty()) return null
        val states = mintOps.checkStates(restoreResult.proofs.map { it.proof })
        val unspent =
            restoreResult.proofs
                .filter { states[it.proof.secret] == ProofState.UNSPENT }
                .map { it.proof }
        if (unspent.isEmpty()) return null
        // Bump the persisted counter past the highest slot we just confirmed
        // in use so the next mint/swap doesn't reuse one of these secrets.
        val current = peekCashuCounter(keysetId)
        val delta = (restoreResult.nextCounterAfterScan - current).coerceAtLeast(0L)
        if (delta > 0) reserveCashuCounters(keysetId, delta.toInt())
        Log.i("CashuWallet") {
            "Recovered ${unspent.size} proof(s) (${unspent.sumOf { it.amount }} sat) via NUT-09 after mint reported quote already issued for $mintUrl"
        }
        return TokenContent(mint = mintUrl, proofs = unspent)
    }

    private fun isOutputsAlreadySignedError(e: MintHttpException): Boolean {
        if (e.code == 10002) return true
        val detail = e.detail?.lowercase().orEmpty()
        return "already signed" in detail ||
            "already issued" in detail ||
            "already minted" in detail
    }

    /**
     * Phase 1 of melt — ask the mint how much an invoice will cost and what
     * its fee_reserve is. Pure read; no state mutation. UI shows the quote to
     * the user; they confirm; the wallet calls [meltToLightning] with the
     * same quote to actually pay.
     */
    suspend fun requestMeltQuote(
        mintUrl: String,
        invoice: String,
    ): MeltQuoteBolt11ResponseDto = ops(mintUrl).requestMeltQuote(invoice)

    /**
     * Phase 2 of melt — pay the invoice using the agreed-upon [quote].
     * Spends [available] token events: picks the smallest subset whose total
     * covers `amount + fee_reserve` (greedy by total amount descending). Any
     * leftover change comes back as proofs in a fresh kind:7375.
     */
    suspend fun meltToLightning(
        mintUrl: String,
        quote: MeltQuoteBolt11ResponseDto,
        available: List<TokenEntry>,
    ): MeltCompleted {
        seedWarmer()
        if (available.isEmpty()) throw IllegalStateException("No proofs available to spend")

        val ops = ops(mintUrl)
        val required = quote.amount + quote.feeReserve
        // The melt mints its inputs on the active keyset (via the swap-down
        // below) and the mint then charges a NUT-02 input fee on them on top
        // of amount + fee_reserve. Reserve it up front so neither proof
        // selection nor the swap-down leaves the melt short — otherwise
        // meltProofs throws "Inputs total X < required Y" the moment the
        // active keyset charges a per-input fee (e.g. mint.coinos.io).
        val target = required + ops.activeKeysetInputFeeFor(required)

        val (selected, _) = selectProofsCovering(available, target)
        val spendingProofs = selected.flatMap { it.content.proofs }
        val total = spendingProofs.sumOf { it.amount }

        // If the selected proofs overshoot, swap them down to (target) first.
        val (inputs, prePaidChangeEvent, prePaidChangeAmount) =
            if (total > target) {
                val swap = ops.swap(spendingProofs, targetSplit = target)
                // The keep-side from the pre-swap is the "extra change" we
                // didn't burn into the melt — publish it now so a crashed
                // melt doesn't lose those proofs.
                val keepEvent =
                    if (swap.keep.isNotEmpty()) {
                        val content = TokenContent(mint = mintUrl, proofs = swap.keep, del = selected.map { it.event.id })
                        val tokenTemplate = CashuTokenEvent.build(content, signer)
                        val signed = signer.sign(tokenTemplate)
                        publish(signed)
                        signed
                    } else {
                        null
                    }
                Triple(swap.send, keepEvent, swap.keep.sumOf { it.amount })
            } else {
                Triple(spendingProofs, null, 0L)
            }

        val meltResult = ops.meltProofs(quote, inputs)

        // Publish the change proofs (mint may have returned fewer than we
        // requested if it consumed some fees).
        val finalChangeEvent =
            if (meltResult.changeProofs.isNotEmpty()) {
                val delIds =
                    if (prePaidChangeEvent != null) {
                        listOf(prePaidChangeEvent.id)
                    } else {
                        selected.map { it.event.id }
                    }
                val content = TokenContent(mint = mintUrl, proofs = meltResult.changeProofs, del = delIds)
                val tokenTemplate = CashuTokenEvent.build(content, signer)
                val signed = signer.sign(tokenTemplate)
                publish(signed)
                signed
            } else {
                null
            }

        // NIP-09 delete the source token events. Per spec, also need k=7375 tag,
        // which DeletionRequestEvent.build adds automatically from the source event kind.
        val deleteEvent =
            run {
                val toDelete = selected.map { it.event } + listOfNotNull(prePaidChangeEvent.takeIf { finalChangeEvent != null })
                val delTemplate = DeletionRequestEvent.build(toDelete)
                signer.sign(delTemplate).also { publish(it) }
            }

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.OUT,
                amount = quote.amount,
                tokenReferences =
                    buildList {
                        selected.forEach {
                            add(TokenReference(it.event.id, null, TokenReference.MARKER_DESTROYED))
                        }
                        finalChangeEvent?.let { add(TokenReference(it.id, null, TokenReference.MARKER_CREATED)) }
                    },
                signer = signer,
            )
        val historyEvent = signer.sign(historyTemplate)
        publish(historyEvent)

        return MeltCompleted(
            preimage = meltResult.preimage,
            paidAmount = quote.amount,
            // Fee = proofs we spent − change we got back (both the pre-paid
            // swap keep and the melt's own change) − the invoice amount. The
            // pre-paid keep was split off before the melt and never burned,
            // so it must not be counted as a fee.
            fees = total - prePaidChangeAmount - meltResult.changeProofs.sumOf { it.amount } - quote.amount,
            historyEvent = historyEvent,
            deleteEvent = deleteEvent,
            newTokenEvent = finalChangeEvent,
        )
    }

    /**
     * Mint a token of [amountSats] using a swap of [available] proofs, encode
     * the resulting "send" proofs as a `cashuB` string, and roll over any
     * change into a new kind:7375.
     */
    suspend fun sendAsToken(
        mintUrl: String,
        amountSats: Long,
        available: List<TokenEntry>,
        memo: String? = null,
    ): SendTokenCompleted {
        if (amountSats <= 0) throw IllegalArgumentException("Amount must be positive")
        val (selected, totalSelected) = selectProofsCovering(available, amountSats)
        if (totalSelected < amountSats) throw IllegalStateException("Insufficient balance for $mintUrl")

        val ops = ops(mintUrl)
        val swap = ops.swap(selected.flatMap { it.content.proofs }, targetSplit = amountSats)

        val tokenString = V4Encoder.encode(mintUrl, swap.send, memo = memo)

        val newKeepEvent =
            if (swap.keep.isNotEmpty()) {
                val content = TokenContent(mint = mintUrl, proofs = swap.keep, del = selected.map { it.event.id })
                val template = CashuTokenEvent.build(content, signer)
                val signed = signer.sign(template)
                publish(signed)
                signed
            } else {
                null
            }

        val deleteEvent =
            run {
                val template = DeletionRequestEvent.build(selected.map { it.event })
                signer.sign(template).also { publish(it) }
            }

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.OUT,
                amount = amountSats,
                tokenReferences =
                    buildList {
                        selected.forEach { add(TokenReference(it.event.id, null, TokenReference.MARKER_DESTROYED)) }
                        newKeepEvent?.let { add(TokenReference(it.id, null, TokenReference.MARKER_CREATED)) }
                    },
                signer = signer,
            )
        val historyEvent = signer.sign(historyTemplate)
        publish(historyEvent)

        return SendTokenCompleted(
            cashuToken = tokenString,
            amount = amountSats,
            deleteEvent = deleteEvent,
            keepEvent = newKeepEvent,
            historyEvent = historyEvent,
        )
    }

    /**
     * Swap proofs from an incoming `cashuB` token into our wallet by minting
     * fresh proofs at the same mint. The original token is consumed: re-using
     * its proofs would double-spend.
     *
     * Throws if the token's mint is not configured in our kind:17375 wallet.
     */
    suspend fun redeemToken(
        cashuToken: String,
        proofs: List<CashuProof>,
        mintUrl: String,
        nutzapEventId: String? = null,
        /**
         * The wallet's NIP-60 P2PK private key (kind:17375 `privkey`, hex).
         * Used to sign the NUT-11 witness when the token is locked to our
         * wallet key. Null when the wallet hasn't decrypted its kind:17375.
         */
        walletP2pkPrivkeyHex: String? = null,
        /**
         * The account's Nostr identity private key (hex), available ONLY for a
         * local nsec signer. Some senders (e.g. Bey Wallet's P2PK send) lock
         * ecash directly to the recipient's npub, so we sign the witness with
         * the identity key when the lock targets it. Null for remote (NIP-46)
         * or external (NIP-55) signers — they can't produce a raw witness
         * signature, so such a token surfaces as [P2PKUnredeemableException].
         */
        identityPrivkeyHex: String? = null,
    ): RedeemCompleted {
        if (proofs.isEmpty()) throw IllegalArgumentException("Token has no proofs")
        // Index our candidate signing keys by their x-only pubkey so a locked
        // proof can be matched to the key that unlocks it. Empty when we hold
        // no keys (e.g. CLI callers) — a locked token then throws a clear
        // P2PKUnredeemableException instead of an unsigned swap.
        val signingKeys = p2pkKeyIndex(walletP2pkPrivkeyHex, identityPrivkeyHex)
        val swap = ops(mintUrl).redeemToken(proofs) { lockXOnly -> signingKeys[lockXOnly] }
        val total = swap.keep.sumOf { it.amount }

        // All output goes to "keep" since targetSplit was null.
        val content = TokenContent(mint = mintUrl, proofs = swap.keep)
        val tokenTemplate = CashuTokenEvent.build(content, signer)
        val tokenEvent = signer.sign(tokenTemplate)
        publish(tokenEvent)

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.IN,
                amount = total,
                tokenReferences =
                    buildList {
                        add(TokenReference(tokenEvent.id, null, TokenReference.MARKER_CREATED))
                        nutzapEventId?.let { add(TokenReference(it, null, TokenReference.MARKER_REDEEMED)) }
                    },
                signer = signer,
            )
        val historyEvent = signer.sign(historyTemplate)
        publish(historyEvent)

        return RedeemCompleted(
            amount = total,
            tokenEvent = tokenEvent,
            historyEvent = historyEvent,
            rawToken = cashuToken,
        )
    }

    /**
     * Send a NIP-61 nutzap.
     *
     * Spends [available] proofs at [mintUrl] to produce P2PK-locked outputs
     * worth [amountSats] for [recipientPubKey]'s [recipientP2pkPubkeyHex].
     * Publishes a kind:9321 with the locked proofs + message (referencing
     * [zappedEvent], or p-tag-only when null for a profile nutzap), rolls
     * leftover change into a new kind:7375, NIP-09-deletes the source token
     * events, and records the spend in kind:7376.
     *
     * Throws if `available` doesn't sum to at least [amountSats].
     */
    suspend fun sendNutzap(
        mintUrl: String,
        amountSats: Long,
        recipientPubKey: HexKey,
        recipientP2pkPubkeyHex: String,
        zappedEvent: EventHintBundle<out Event>?,
        message: String,
        available: List<TokenEntry>,
        onProgress: ((Float) -> Unit)? = null,
    ): NutzapSent {
        if (amountSats <= 0) throw IllegalArgumentException("Amount must be positive")
        seedWarmer()
        val (selected, totalSelected) = selectProofsCovering(available, amountSats)
        if (totalSelected < amountSats) throw IllegalStateException("Insufficient balance for $mintUrl")

        val swap =
            ops(mintUrl).swapToLocked(
                proofs = selected.flatMap { it.content.proofs },
                recipientP2pkPubkeyHex = recipientP2pkPubkeyHex,
                targetSplit = amountSats,
            )
        // Mint round-trip done — biggest chunk of the wall clock. Surface
        // visible motion here so the user knows the click registered even
        // before the recipient sees the kind:9321.
        onProgress?.invoke(0.55f)

        // Build the kind:9321 first so we have its id to reference from history.
        val proofJsons = swap.send.map { nutzapProofJson.encodeToString(NutzapProofJson.serializer(), it.toNutzapJson()) }
        val nutzapTemplate =
            if (zappedEvent != null) {
                NutzapEvent.build(
                    message = message,
                    proofs = proofJsons,
                    mintUrl = mintUrl,
                    unit = "sat",
                    zappedEvent = zappedEvent,
                    recipientPubKey = recipientPubKey,
                )
            } else {
                NutzapEvent.buildToUser(
                    message = message,
                    proofs = proofJsons,
                    mintUrl = mintUrl,
                    unit = "sat",
                    recipientPubKey = recipientPubKey,
                )
            }
        val nutzapEvent = signer.sign(nutzapTemplate)
        publish(nutzapEvent)
        // kind:9321 is out — recipient's auto-redeem can fire from here.
        onProgress?.invoke(0.80f)

        // Roll over change locally if any.
        val keepEvent =
            if (swap.keep.isNotEmpty()) {
                val content = TokenContent(mint = mintUrl, proofs = swap.keep, del = selected.map { it.event.id })
                val template = CashuTokenEvent.build(content, signer)
                val signed = signer.sign(template)
                publish(signed)
                signed
            } else {
                null
            }

        // NIP-09 delete the source token events.
        val deleteEvent =
            run {
                val template = DeletionRequestEvent.build(selected.map { it.event })
                signer.sign(template).also {
                    publish(it)
                }
            }
        onProgress?.invoke(0.95f)

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.OUT,
                amount = amountSats,
                tokenReferences =
                    buildList {
                        selected.forEach { add(TokenReference(it.event.id, null, TokenReference.MARKER_DESTROYED)) }
                        keepEvent?.let { add(TokenReference(it.id, null, TokenReference.MARKER_CREATED)) }
                    },
                signer = signer,
            )
        val historyEvent = signer.sign(historyTemplate)
        publish(historyEvent)
        onProgress?.invoke(1.0f)

        return NutzapSent(
            nutzapEvent = nutzapEvent,
            keepEvent = keepEvent,
            deleteEvent = deleteEvent,
            historyEvent = historyEvent,
            amount = amountSats,
        )
    }

    private fun CashuProof.toNutzapJson() =
        NutzapProofJson(
            id = id,
            amount = amount,
            secret = secret,
            c = c,
            witness = witness,
            // Forward the NUT-12 DLEQ tuple if we have it. Lets the
            // recipient Carol-verify the proofs against the mint's
            // keyset key offline — without it they're stuck with
            // spend-time validation.
            dleq =
                dleq?.let { src ->
                    NutzapDleqJson(e = src.e, s = src.s, r = src.r)
                },
        )

    /**
     * Redeem inbound NIP-61 nutzaps, swapping all of one mint's nutzaps together.
     *
     * Steps:
     *   1. Check each nutzap locally ([prepareNutzap]): a mint tag, proofs, and every proof
     *      P2PK-locked to a key we hold. A nutzap that fails lands in
     *      [NutzapRedemption.unredeemable] without any mint round trip.
     *   2. Group the rest by mint and swap each group's proofs in one `/v1/swap`, signing each
     *      proof's unlock witness with the key its lock names. The NUT-02 input fee is rounded up
     *      once per swap, so batching is what makes small nutzaps redeemable at all: one 1-sat
     *      proof at 100 ppk costs its whole sat, ten together cost 1 sat and keep 9.
     *   3. Per swap, publish one kind:7375 holding the new proofs and one kind:7376 that marks
     *      every covered nutzap `redeemed` (left unencrypted, per the spec) and tags each sender.
     *
     * A swap the mint refuses is narrowed down (spent proofs dropped, then one nutzap at a time),
     * so one spent or bad nutzap can't hold back the rest of its mint. A group whose fee still
     * takes everything goes to [NutzapRedemption.belowFee]: it can only succeed once more nutzaps
     * arrive at that mint.
     *
     * Only nutzaps at one of [walletMints] are redeemed (NIP-61: the mints the recipient lists).
     * A nutzap names its own mint, and anyone can send one, so without this check a stranger
     * could make the wallet contact any URL, a LAN or localhost address included: the client
     * for a wallet mint skips the private-host checks a pasted token gets.
     */
    suspend fun redeemNutzaps(
        nutzaps: List<NutzapEvent>,
        /** The wallet's NIP-60 P2PK private key (kind:17375 `privkey`): the key NIP-61 nutzaps lock to. */
        walletPrivkeyHex: String,
        walletMints: Collection<String>,
        /**
         * The account's Nostr identity private key, available only for a local nsec login. Some
         * senders lock a nutzap to the recipient's npub instead of the kind:10019 `pubkey`; with
         * this key those can be claimed too. Null for a remote (NIP-46) or external (NIP-55)
         * signer, which can't produce the raw witness signature.
         */
        identityPrivkeyHex: String? = null,
    ): NutzapRedemption {
        val accepted = walletMints.mapTo(HashSet()) { normalizeMintUrl(it) }
        val signingKeys = p2pkKeyIndex(walletPrivkeyHex, identityPrivkeyHex)
        val result = NutzapRedemptionBuilder()
        val prepared = ArrayList<PreparedNutzap>(nutzaps.size)
        val proofOwner = HashMap<String, NutzapEvent>()
        for (nutzap in nutzaps) {
            try {
                val p = prepareNutzap(nutzap, signingKeys.keys, accepted)
                // Two nutzaps carrying the same proof would fail any swap they share, and only
                // one of them can ever be redeemed: keep the first.
                val firstOwner = p.proofs.firstNotNullOfOrNull { proofOwner[it.secret] }
                if (firstOwner != null) {
                    result.unredeemable[nutzap] = "Carries the same proofs as nutzap ${firstOwner.id.take(8)}"
                    continue
                }
                p.proofs.forEach { proofOwner[it.secret] = nutzap }
                prepared += p
            } catch (e: IllegalArgumentException) {
                result.unredeemable[nutzap] = e.message ?: "Unredeemable nutzap"
            }
        }
        if (prepared.isEmpty()) return result.build()

        seedWarmer()
        prepared.groupBy { it.mintUrl }.forEach { (mintUrl, group) ->
            chunkByProofCount(group).forEach { batch -> redeemBatch(mintUrl, batch, signingKeys, result) }
        }
        return result.build()
    }

    /**
     * Local checks on one nutzap, before any mint round trip. Throws [IllegalArgumentException]
     * for a nutzap this wallet can't redeem: no mint tag, a mint outside [acceptedMints]
     * (normalized with [normalizeMintUrl]), no proofs, malformed proofs, or a proof not
     * P2PK-locked to one of [signableLocks] (lowercase x-only pubkeys of the keys we hold).
     */
    fun prepareNutzap(
        nutzap: NutzapEvent,
        signableLocks: Set<String>,
        acceptedMints: Set<String>,
    ): PreparedNutzap {
        val rawMintUrl =
            nutzap.mintUrl()
                ?: throw IllegalArgumentException("Nutzap has no mint tag")
        val mintUrl = normalizeMintUrl(rawMintUrl)
        if (mintUrl !in acceptedMints) {
            throw IllegalArgumentException("Nutzap is at ${rawMintUrl.take(80)}, which is not one of this wallet's mints")
        }
        val proofJsons = nutzap.proofs()
        if (proofJsons.isEmpty()) throw IllegalArgumentException("Nutzap has no proofs")

        // SerializationException is an IllegalArgumentException: a malformed proof is as final
        // as a missing one.
        val parsedProofs =
            proofJsons.map { js ->
                val dto = nutzapProofJson.decodeFromString<NutzapProofJson>(js)
                CashuProof(
                    id = dto.id,
                    amount = dto.amount,
                    secret = dto.secret,
                    c = dto.c,
                    dleq =
                        dto.dleq?.let { d ->
                            DleqProofDto(e = d.e, s = d.s, r = d.r)
                        },
                )
            }

        // Verify every lock points at a key we hold BEFORE any mint round-trip:
        // the wallet key, and with a local nsec login the identity key too. The
        // pubkey in the P2PK secret can be 64-char x-only or 66-char compressed;
        // compare x-only, since BIP-340 verification (which the mint uses) is
        // parity-agnostic. Doing this first means a nutzap locked to a stale
        // wallet key (sender saw an old kind:10019 and locked to that pubkey,
        // we've since rotated) is rejected without burning a /v1/keys fetch —
        // and without the auto-redeem loop hammering the mint on every
        // triggerAutoRedeem.
        parsedProofs.forEach { proof ->
            val parsed =
                P2PK.parseSecret(proof.secret)
                    ?: throw IllegalArgumentException("Nutzap proof is not P2PK-locked")
            val lock = parsed.pubKeyHex.lastHex64().lowercase()
            if (lock !in signableLocks) {
                if (lock == signer.pubKey.lowercase()) {
                    throw IllegalArgumentException(
                        "Nutzap is locked to your Nostr identity key, which only a local key (nsec) login can sign for",
                    )
                }
                throw IllegalArgumentException(
                    "Nutzap proof is locked to a different pubkey (${parsed.pubKeyHex.take(16)}…)",
                )
            }
        }
        return PreparedNutzap(nutzap, mintUrl, parsedProofs)
    }

    /**
     * Splits one mint's nutzaps into swaps of at most [MAX_PROOFS_PER_SWAP] inputs, never splitting
     * a nutzap, and evens the chunks out: 101 one-sat proofs go 51 + 50, not 100 + 1, since a lone
     * small proof would be left below the fee.
     */
    private fun chunkByProofCount(group: List<PreparedNutzap>): List<List<PreparedNutzap>> {
        val totalProofs = group.sumOf { it.proofs.size }
        if (totalProofs <= MAX_PROOFS_PER_SWAP) return listOf(group)
        val chunkCount = (totalProofs + MAX_PROOFS_PER_SWAP - 1) / MAX_PROOFS_PER_SWAP
        val target = (totalProofs + chunkCount - 1) / chunkCount

        val chunks = ArrayList<List<PreparedNutzap>>(chunkCount)
        var current = ArrayList<PreparedNutzap>()
        var count = 0
        for (p in group) {
            if (current.isNotEmpty() && count + p.proofs.size > target) {
                chunks += current
                current = ArrayList()
                count = 0
            }
            current += p
            count += p.proofs.size
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    /**
     * Swaps [batch] at [mintUrl] and publishes the result, sorting every nutzap into [result].
     *
     * Only a mint error refusing the inputs ([refusesTheInputs]) says something about the nutzaps
     * themselves, so only that triggers [handleRefusedBatch]. Anything else (network, 5xx, auth,
     * rate limits, a fee disagreement) fails the batch as a whole, to be retried later, rather
     * than costing one more swap per nutzap. Once the swap has gone through the inputs are spent,
     * so nothing after it may retry them.
     */
    private suspend fun redeemBatch(
        mintUrl: String,
        batch: List<PreparedNutzap>,
        signingKeys: Map<String, String>,
        result: NutzapRedemptionBuilder,
        checkSpentOnRefusal: Boolean = true,
    ) {
        val swap =
            try {
                swapNutzaps(mintUrl, batch, signingKeys, result) ?: return
            } catch (e: SwapFeeExceedsInputsException) {
                // The fee takes everything even with the whole batch together. Nothing was spent;
                // these only become redeemable alongside nutzaps that haven't arrived yet.
                batch.forEach { result.belowFee += it.nutzap }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: MintHttpException) {
                if (e.refusesTheInputs()) {
                    handleRefusedBatch(mintUrl, batch, signingKeys, result, e, checkSpentOnRefusal)
                } else {
                    batch.forEach { result.failed[it.nutzap] = e }
                }
                return
            } catch (e: Exception) {
                batch.forEach { result.failed[it.nutzap] = e }
                return
            }

        // The mint has spent the inputs. If saving the new proofs fails, retrying the swap could
        // only get "already spent": report it, and leave the proofs to NUT-13 restore.
        try {
            result.redeemed += publishRedeemed(mintUrl, batch, swap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            result.notSaved += SwappedNotSaved(batch.map { it.nutzap }, mintUrl, swap.keep.sumOf { it.amount }, e)
        }
    }

    /**
     * The mint refused the inputs of [batch] ([refusesTheInputs]). One spent or forged nutzap is enough to refuse the
     * whole swap, so find it without giving up on the rest: ask the mint which proofs are spent
     * (one NUT-07 request), drop those nutzaps as final, and retry the others together, since
     * splitting them up could leave small ones below the fee. If nothing is spent, the culprit is
     * something else (a forged proof without DLEQ, say), so try each nutzap alone.
     */
    private suspend fun handleRefusedBatch(
        mintUrl: String,
        batch: List<PreparedNutzap>,
        signingKeys: Map<String, String>,
        result: NutzapRedemptionBuilder,
        refusal: MintHttpException,
        checkSpent: Boolean,
    ) {
        if (checkSpent) {
            val states =
                try {
                    ops(mintUrl).checkStates(batch.flatMap { it.proofs })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    batch.forEach { result.failed[it.nutzap] = refusal }
                    return
                }
            val (spent, rest) = batch.partition { p -> p.proofs.any { states[it.secret] == ProofState.SPENT } }
            spent.forEach { result.unredeemable[it.nutzap] = "Already spent at the mint" }
            if (spent.isNotEmpty()) {
                if (rest.isNotEmpty()) redeemBatch(mintUrl, rest, signingKeys, result)
                return
            }
        }

        if (batch.size == 1) {
            // Refused alone with its proofs unspent: the mint rejects these proofs themselves (a
            // bad signature, an unknown keyset). Anyone can send such a nutzap, so leaving it to
            // be retried would re-run this whole fallback on every sweep.
            result.unredeemable[batch[0].nutzap] = "Refused by the mint: ${refusal.detail ?: refusal.message}"
            return
        }
        Log.w("CashuWalletOps") { "Mint $mintUrl refused a batch of ${batch.size} nutzaps with none spent, retrying one by one: ${refusal.message}" }
        batch.forEach { redeemBatch(mintUrl, listOf(it), signingKeys, result, checkSpentOnRefusal = false) }
    }

    /**
     * The mint step: NUT-12 checks, then one swap of every proof in [batch]. Returns null when the
     * DLEQ check already sorted the batch into [result] (the forged nutzaps as unredeemable, the
     * rest swapped through a recursive [redeemBatch]).
     */
    private suspend fun swapNutzaps(
        mintUrl: String,
        batch: List<PreparedNutzap>,
        signingKeys: Map<String, String>,
        result: NutzapRedemptionBuilder,
    ): SwapResult? {
        val proofs = batch.flatMap { it.proofs }

        // NUT-12 §3 Carol verification on the incoming proofs. When the
        // sender includes the dleq tuple we can check the proofs against
        // the mint's keyset key BEFORE swapping — defends against a
        // malicious sender handing us proofs the mint won't honour at
        // spend time. Verification is best-effort: legacy nutzaps
        // without dleq skip the check (returns true) and we fall back
        // to spend-time validation.
        val dleqFailures = ops(mintUrl).dleqFailures(proofs)
        if (dleqFailures.isNotEmpty()) {
            // A DLEQ failure is the sender's, and final. Drop the nutzaps carrying the bad
            // proofs and swap the rest together.
            val badSecrets = dleqFailures.mapTo(HashSet()) { it.secret }
            val (bad, good) = batch.partition { p -> p.proofs.any { it.secret in badSecrets } }
            bad.forEach { result.unredeemable[it.nutzap] = "NUT-12 verification failed: proofs don't match the mint's published keys" }
            if (good.isNotEmpty()) redeemBatch(mintUrl, good, signingKeys, result)
            return null
        }

        // Each proof is signed with the key its lock names: the wallet key, or the identity key.
        return ops(mintUrl).redeemToken(proofs) { lockXOnly -> signingKeys[lockXOnly] }
    }

    private suspend fun publishRedeemed(
        mintUrl: String,
        batch: List<PreparedNutzap>,
        swap: SwapResult,
    ): RedeemedNutzaps {
        val total = swap.keep.sumOf { it.amount }

        val tokenContent = TokenContent(mint = mintUrl, proofs = swap.keep)
        val tokenTemplate = CashuTokenEvent.build(tokenContent, signer)
        val tokenEvent = signer.sign(tokenTemplate)
        publish(tokenEvent)

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.IN,
                amount = total,
                tokenReferences =
                    listOf(TokenReference(tokenEvent.id, null, TokenReference.MARKER_CREATED)) +
                        batch.map { TokenReference(it.nutzap.id, null, TokenReference.MARKER_REDEEMED) },
                signer = signer,
            ) {
                // NIP-61: tag each nutzap sender so their client learns it was redeemed.
                batch.distinctBy { it.nutzap.pubKey }.forEach { notifySender(EventHintBundle(it.nutzap)) }
            }
        val historyEvent = signer.sign(historyTemplate)
        publish(historyEvent)

        return RedeemedNutzaps(
            nutzaps = batch.map { it.nutzap },
            completed =
                RedeemCompleted(
                    amount = total,
                    tokenEvent = tokenEvent,
                    historyEvent = historyEvent,
                    rawToken = "nutzap:" + batch.joinToString(",") { it.nutzap.id },
                ),
        )
    }

    /**
     * Greedy proof selection: sort by amount descending, take entries until
     * the running total covers [target]. Returns the picked entries + their
     * total.
     */
    private fun selectProofsCovering(
        available: List<TokenEntry>,
        target: Long,
    ): Pair<List<TokenEntry>, Long> {
        val sorted = available.sortedByDescending { it.content.totalAmount() }
        var running = 0L
        val picked = mutableListOf<TokenEntry>()
        for (entry in sorted) {
            if (running >= target) break
            picked += entry
            running += entry.content.totalAmount()
        }
        return picked to running
    }

    /**
     * Ping `/v1/info` on [mintUrl] and return the mint's display name on
     * success. Used by the Add-Mint UI to give immediate feedback when a URL
     * is typo'd, points at a non-Cashu host, or is otherwise unreachable.
     *
     * Throws on failure so the UI can surface the underlying reason.
     *
     * `userConfigured = true`: the URL was typed by the user into the Add-Mint UI,
     * so a self-hosted mint on the LAN is a legitimate target here.
     */
    suspend fun pingMint(mintUrl: String): String? = MintHttpClient(mintUrl, userConfigured = true, transport = mintTransport).info().name

    /**
     * Fetch the currently-active keyset id for [mintUrl]. Cheap wrapper
     * over [CashuMintOperations.activeKeyset] used by the migration
     * driver to detect proofs minted under rotated-out keysets — see
     * [CashuWalletState.migrateStaleKeysets].
     */
    suspend fun fetchActiveKeysetId(mintUrl: String): String = ops(mintUrl).activeKeyset().id

    /**
     * NUT-07 batched proof-state query. Returns a map keyed by each
     * proof's secret to the mint-side state. Used by the wallet's
     * stale-proof sweep to detect kind:7375 events whose proofs are
     * already spent at the mint (so the parent event can be NIP-09
     * deleted before the next send picks them and hits HTTP 400
     * "proofs already spent").
     */
    suspend fun checkProofStates(
        mintUrl: String,
        proofs: List<CashuProof>,
    ): Map<String, ProofState> = ops(mintUrl).checkStates(proofs)

    /**
     * NUT-07 scrub for a single mint: check every proof in [entries] against
     * the mint's `/checkstate`, then NIP-09 delete every kind:7375 event that
     * holds at least one SPENT proof (a mixed-state entry is unusable — the
     * next swap would pick it and trip HTTP 400). Returns the deleted token
     * events so the caller can drop them from its own indexes.
     *
     * Shared by Android's `CashuWalletState.scrubLocallyStaleProofs` and amy's
     * `cashu maintenance scrub`, so both prune identically.
     */
    suspend fun scrubStaleProofs(
        mintUrl: String,
        entries: List<TokenEntry>,
    ): List<CashuTokenEvent> {
        val allProofs = entries.flatMap { it.content.proofs }
        if (allProofs.isEmpty()) return emptyList()
        val states = ops(mintUrl).checkStates(allProofs)
        val stale =
            entries.filter { entry ->
                entry.content.proofs.any { states[it.secret] == ProofState.SPENT }
            }
        if (stale.isEmpty()) return emptyList()
        val delTemplate = DeletionRequestEvent.build(stale.map { it.event })
        publish(signer.sign(delTemplate))
        return stale.map { it.event }
    }

    /**
     * Swap every proof inside [entries] (which the caller has already
     * filtered to "contains at least one stale-keyset proof") onto the
     * mint's current active keyset, publish the result as a single
     * fresh kind:7375 event, and NIP-09 the originals. Mirrors the
     * change-rollover pattern used by [sendNutzap].
     *
     * No-op when [entries] is empty. The [activeKeysetId] is passed in
     * by the caller so we don't double-fetch /v1/keys between
     * [fetchActiveKeysetId] and this method.
     */
    suspend fun migrateToActiveKeyset(
        mintUrl: String,
        entries: List<TokenEntry>,
        activeKeysetId: String,
    ): MigrationResult {
        if (entries.isEmpty()) return MigrationResult(amountMigrated = 0L, proofsMigrated = 0)
        seedWarmer()
        val allProofs = entries.flatMap { it.content.proofs }
        // Defensive: skip if every input is already on the active
        // keyset. Caller is supposed to pre-filter but a race against
        // mint rotation could land us here with nothing to do.
        if (allProofs.all { it.id == activeKeysetId }) {
            return MigrationResult(amountMigrated = 0L, proofsMigrated = 0)
        }
        val swap = ops(mintUrl).swap(allProofs, targetSplit = null)
        val migratedTotal = swap.keep.sumOf { it.amount }

        // Publish the consolidated proofs as one kind:7375 and delete
        // the originals.
        val tokenContent =
            TokenContent(
                mint = mintUrl,
                proofs = swap.keep,
                del = entries.map { it.event.id },
            )
        val template = CashuTokenEvent.build(tokenContent, signer)
        val signed = signer.sign(template)
        publish(signed)

        val delTemplate = DeletionRequestEvent.build(entries.map { it.event })
        val delEvent = signer.sign(delTemplate)
        publish(delEvent)

        return MigrationResult(
            amountMigrated = migratedTotal,
            proofsMigrated = swap.keep.size,
        )
    }

    /**
     * Publish a NIP-87 kind:38000 recommendation for [mintUrl].
     *
     * The recommendation is keyed by the announcement's d-tag (mint pubkey)
     * when one is known — that makes it discoverable via `#a` queries that
     * scope by mint identity. We always emit a `u` tag with the raw URL too
     * so older clients that index by URL still pick it up. [review] is an
     * optional free-text body (kind:38000 content).
     */
    suspend fun recommendMint(
        mintUrl: String,
        mintAnnouncementDTag: String? = null,
        review: String = "",
    ): MintRecommendationEvent {
        val template =
            MintRecommendationEvent.build(
                mintIdentifier = mintAnnouncementDTag ?: mintUrl,
                mintKind = CashuMintEvent.KIND,
                review = review,
                mintUrls = listOf(mintUrl),
            )
        val event = signer.sign(template)
        publish(event)
        return event
    }

    /**
     * NIP-09 retract a previously-published mint recommendation.
     *
     * kind:38000 is parameterized-replaceable — a delete with an `a` tag
     * pointing at the address coordinate (kind:pubkey:dTag) drops all
     * versions on compliant relays. We also include the original event id
     * via DeletionRequestEvent.build so relays that only track by event id still
     * remove it. MintRecommendationEvent is an AddressableEvent, so
     * DeletionRequestEvent.build writes that `a` tag itself.
     */
    suspend fun deleteRecommendation(event: MintRecommendationEvent) {
        val template = DeletionRequestEvent.build(listOf(event))
        val delEvent = signer.sign(template)
        publish(delEvent)
    }

    /**
     * NUT-09 read-only probe — scan the mint for proofs derivable from
     * [seed] WITHOUT publishing anything. Returns the UNSPENT proofs that
     * aren't already held locally ([existingSecrets]) plus their total, so
     * a caller can show the recoverable amount before committing (the
     * find-my-wallet wizard's "structural + balance" check of each
     * discovered wallet). Pair with [publishRecoveredProofs] to adopt the
     * funds into the wallet.
     *
     * Process:
     *  1. List **every** keyset the mint has published for this unit — see
     *     below on why the active one is not enough.
     *  2. Drive [CashuMintOperations.restore] per keyset — scans counters in
     *     batches until enough empty batches in a row signal "no more proofs".
     *  3. Filter the pooled proofs through /v1/checkstate to keep only
     *     UNSPENT ones — NUT-09 alone returns even spent proofs.
     *  4. Drop secrets already present in [existingSecrets].
     *
     * ### Every keyset, not just the active one
     *
     * NUT-13 derives a separate counter chain per keyset
     * (`m/129372'/0'/<keyset-id-int>'/<counter>'`), so a proof minted under a
     * keyset the mint has since rotated out is not reachable from the active
     * keyset's derivation path at any counter. Scanning only the active keyset
     * therefore reports "nothing to recover" for exactly the balance a restore
     * exists to find: the older it is, the more likely its keyset is retired.
     * That looked like an empty wallet rather than an incomplete scan, because
     * a scan that never asks and a scan that finds nothing return the same
     * thing.
     *
     * A keyset that errors out (mint won't serve its keys, restore rejected)
     * is logged and skipped so one bad keyset can't sink the whole recovery.
     *
     * [RecoverableProofs.keysetId] and [RecoverableProofs.nextCounterAfterScan]
     * continue to describe the **active** keyset specifically, because that is
     * the only chain the wallet will derive new secrets on — an inactive
     * keyset can never receive another mint, so advancing a counter for it
     * would protect nothing.
     *
     * Since it neither signs, swaps, nor publishes, this is safe to run
     * speculatively across many candidate wallets/seeds.
     */
    suspend fun scanRecoverableProofs(
        mintUrl: String,
        seed: ByteArray,
        startCounter: Long = 0L,
        existingSecrets: Set<String> = emptySet(),
    ): RecoverableProofs {
        seedWarmer()
        val mintOps = ops(mintUrl)
        val activeKeysetId = mintOps.activeKeyset().id
        val keysetIds = mintOps.restorableKeysetIds()

        // Scan keysets concurrently, a few at a time. Each keyset's walk costs
        // at least `emptyBatchesToStop` /v1/restore round-trips with batch
        // bodies up to MAX_RESTORE_REQUEST_ITEMS outputs — so a mint that has
        // rotated ten times turns a scan that used to be three requests into
        // thirty, and run end to end that is a minute of staring at a spinner
        // on mobile. The walks are independent (separate derivation chains,
        // read-only at the mint), so the only reason to serialize them is
        // politeness to the mint; [RESTORE_KEYSET_CONCURRENCY] keeps that
        // while cutting the wall clock by roughly the same factor.
        val semaphore = Semaphore(RESTORE_KEYSET_CONCURRENCY)
        val results =
            coroutineScope {
                keysetIds
                    .map { keysetId ->
                        async {
                            semaphore.withPermit {
                                keysetId to
                                    runCatchingCancellable {
                                        mintOps.restore(seed = seed, keysetId = keysetId, startCounter = startCounter)
                                    }.onFailure {
                                        // One retired keyset the mint won't serve keys for must not
                                        // sink the recovery of every other keyset at this mint.
                                        Log.w("CashuWalletOps") {
                                            "NUT-09 restore of keyset $keysetId at $mintUrl failed: ${describeMintError(it)}"
                                        }
                                    }.getOrNull()
                            }
                        }
                    }.awaitAll()
            }

        val recovered = mutableListOf<CashuProof>()
        var activeNextCounter = startCounter
        for ((keysetId, result) in results) {
            if (result == null) continue
            if (keysetId == activeKeysetId) activeNextCounter = result.nextCounterAfterScan
            recovered += result.proofs.map { it.proof }
        }

        if (recovered.isEmpty()) {
            return RecoverableProofs(mintUrl, activeKeysetId, emptyList(), activeNextCounter)
        }

        // /v1/checkstate filters out proofs that were minted but already
        // melted or sent. Without this, recovered "balance" would include
        // already-spent proofs that the mint would reject at next swap.
        val stateMap = mintOps.checkStates(recovered)
        val unspent =
            recovered.filter { proof ->
                stateMap[proof.secret] == ProofState.UNSPENT && proof.secret !in existingSecrets
            }
        return RecoverableProofs(mintUrl, activeKeysetId, unspent, activeNextCounter)
    }

    /**
     * Publish the proofs from a [scanRecoverableProofs] probe as one fresh
     * kind:7375 + a kind:7376 IN history row — the same tail a NUT-09
     * restore or a mint-from-LN uses. No-op (zero outcome) when the probe
     * found nothing.
     *
     * Deliberately does NOT advance any persistent NUT-13 counter: counter
     * bookkeeping belongs to the caller, because recovering from a
     * *foreign* wallet's seed (the wizard moving an old wallet's funds into
     * the main wallet) must not bump THIS wallet's counter for the shared
     * keyset — the recovered proofs are bearer tokens, and the main
     * wallet's own counter only governs the secrets IT derives.
     */
    suspend fun publishRecoveredProofs(recoverable: RecoverableProofs): RestoreOutcome {
        val proofs = recoverable.proofs
        if (proofs.isEmpty()) {
            return RestoreOutcome(
                keysetId = recoverable.keysetId,
                amountRecoveredSats = 0L,
                proofsRecovered = 0,
                nextCounterAfterScan = recoverable.nextCounterAfterScan,
                tokenEvent = null,
            )
        }

        val totalSats = proofs.sumOf { it.amount }
        val tokenContent =
            TokenContent(
                mint = recoverable.mintUrl,
                proofs = proofs,
            )
        val tokenTemplate = CashuTokenEvent.build(tokenContent, signer)
        val tokenEvent = signer.sign(tokenTemplate)
        publish(tokenEvent)

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.IN,
                amount = totalSats,
                tokenReferences =
                    listOf(
                        TokenReference(tokenEvent.id, null, TokenReference.MARKER_CREATED),
                    ),
                signer = signer,
            )
        val historyEvent = signer.sign(historyTemplate)
        publish(historyEvent)

        return RestoreOutcome(
            keysetId = recoverable.keysetId,
            amountRecoveredSats = totalSats,
            proofsRecovered = proofs.size,
            nextCounterAfterScan = recoverable.nextCounterAfterScan,
            tokenEvent = tokenEvent,
        )
    }

    /**
     * NUT-09 wallet restore — recover proofs the wallet previously
     * minted at [mintUrl] but whose kind:7375 token events have been lost
     * (rogue relay, NIP-09 from a compromised key, fresh device with no
     * cache backup). Convenience composition of [scanRecoverableProofs] +
     * [publishRecoveredProofs]; see those for details.
     *
     * [existingSecrets] is the set of NUT-00 `secret` strings the wallet
     * already holds locally (across all kind:7375 events) — recovered
     * proofs whose secret is already in this set are skipped so re-running
     * doesn't republish duplicates and inflate the balance.
     *
     * Idempotent — re-running after a successful restore returns 0.
     */
    suspend fun restoreFromMint(
        mintUrl: String,
        seed: ByteArray,
        startCounter: Long = 0L,
        existingSecrets: Set<String> = emptySet(),
    ): RestoreOutcome = publishRecoveredProofs(scanRecoverableProofs(mintUrl, seed, startCounter, existingSecrets))

    /**
     * [restoreFromMint], then bump the persisted NUT-13 counter past every
     * slot the scan confirmed in use — via the [peekCashuCounter] /
     * [reserveCashuCounters] hooks — so a later mint/swap can't reuse one of
     * the recovered secrets. Same advance rule Android's
     * `CashuWalletState.restoreFromMint` applies after its restore, shared
     * here so every front end bumps identically. `reserveCashuCounters` is
     * atomic, so two restores running concurrently can't collide.
     */
    suspend fun restoreFromMintAdvancingCounter(
        mintUrl: String,
        seed: ByteArray,
        startCounter: Long = 0L,
        existingSecrets: Set<String> = emptySet(),
    ): RestoreOutcome {
        val outcome = restoreFromMint(mintUrl, seed, startCounter, existingSecrets)
        val delta = (outcome.nextCounterAfterScan - peekCashuCounter(outcome.keysetId)).coerceAtLeast(0L)
        if (delta > 0) reserveCashuCounters(outcome.keysetId, delta.toInt())
        return outcome
    }

    companion object {
        /**
         * How far to rewind the persisted counter when looking for proofs
         * the mint already signed but we never published. A previous mint
         * call reserves contiguous slots ending at the current counter,
         * and a typical receive splits into <= 16 power-of-two outputs;
         * 32 is a comfortable upper bound that still keeps the restore
         * cheap (one /v1/restore batch).
         */
        private const val DEFAULT_RESTORE_SCAN_BACK: Long = 32L

        /**
         * How many of a mint's keysets [scanRecoverableProofs] walks at once.
         * Small on purpose: the walks are read-only but each one issues a
         * series of large `/v1/restore` bodies, and a recovery is not worth
         * tripping a mint's rate limiter over.
         */
        private const val RESTORE_KEYSET_CONCURRENCY: Int = 3

        /**
         * Most inputs one nutzap redeem swap carries. Mints cap inputs per request (nutshell's
         * default is 1000); staying well under keeps a large backlog from being refused whole,
         * and costs at most one extra rounded-up fee per chunk.
         */
        const val MAX_PROOFS_PER_SWAP: Int = 100
    }
}

/** Result of a keyset migration. See [CashuWalletOps.migrateToActiveKeyset]. */
data class MigrationResult(
    val amountMigrated: Long,
    val proofsMigrated: Int,
)

/**
 * Unpublished result of a NUT-09 scan. See [CashuWalletOps.scanRecoverableProofs].
 * [proofs] are the UNSPENT, not-already-held proofs ready to be adopted into
 * the wallet via [CashuWalletOps.publishRecoveredProofs]; [amountSats] sums them.
 */
data class RecoverableProofs(
    val mintUrl: String,
    val keysetId: String,
    val proofs: List<CashuProof>,
    val nextCounterAfterScan: Long,
) {
    val amountSats: Long get() = proofs.sumOf { it.amount }
    val isEmpty: Boolean get() = proofs.isEmpty()
}

/** Result of a NUT-09 wallet restore. See [CashuWalletOps.restoreFromMint]. */
data class RestoreOutcome(
    val keysetId: String,
    val amountRecoveredSats: Long,
    val proofsRecovered: Int,
    val nextCounterAfterScan: Long,
    val tokenEvent: CashuTokenEvent?,
)

/** Drop the leading parity byte if present so two pubkeys can be compared. */
private fun String.lastHex64(): String = if (length == 66) substring(2) else this

/**
 * Index the given private keys by their 32-byte x-only pubkey hex, skipping
 * blanks. Used by [CashuWalletOps.redeemToken] to resolve which of our keys (if
 * any) unlocks a P2PK proof, comparing against the lock's x-only `data`.
 */
private fun p2pkKeyIndex(vararg privKeysHex: String?): Map<String, String> =
    buildMap {
        privKeysHex.forEach { hex ->
            if (!hex.isNullOrBlank()) {
                // toHexKey() emits lowercase, matching the lowercased x-only the
                // resolver is queried with (see P2PKRedeem.xOnly).
                val xOnly =
                    Secp256k1
                        .pubKeyCompress(Secp256k1.pubkeyCreate(hex.hexToByteArray()))
                        .toHexKey()
                        .lastHex64()
                put(xOnly, hex)
            }
        }
    }

/**
 * Pre-flight a token's proofs against the keys we hold: throws
 * [P2PKUnredeemableException] (naming the offending lock) if any P2PK-locked
 * proof can't be signed. Callers redeem a multi-group token one group at a time,
 * each swapping + publishing, so checking every group up front avoids redeeming
 * some and then failing on an unsignable one — mirroring the unknown-mint
 * pre-check. Plain (unlocked) proofs are ignored.
 */
fun requireP2pkRedeemable(
    proofs: List<CashuProof>,
    walletP2pkPrivkeyHex: String?,
    identityPrivkeyHex: String?,
) {
    val signingKeys = p2pkKeyIndex(walletP2pkPrivkeyHex, identityPrivkeyHex)
    firstUnsignableP2pkLock(proofs) { signingKeys[it] }?.let { throw P2PKUnredeemableException(it) }
}

/** Catches mint HTTP / protocol errors and surfaces their detail message. */
fun describeMintError(e: Throwable): String =
    when (e) {
        is P2PKUnredeemableException -> "This ecash is locked to a public key this wallet can't sign for."
        is MintHttpException -> "Mint error (HTTP ${e.httpStatus}): ${e.detail ?: e.message}"
        is MintProtocolException -> "Mint refused: ${e.message}"
        else -> e.message ?: e::class.simpleName ?: "Unknown error"
    }

/**
 * Error text for the redeem-token flow. Adds context [describeMintError] can't:
 * when a token is P2PK-locked to the user's own [identityPubKeyHex] but we
 * couldn't sign for it, it means the current signer is a bunker/external one
 * that can't produce a raw witness — so point the user at claiming it elsewhere.
 * Falls back to [describeMintError] for everything else.
 */
fun describeRedeemError(
    e: Throwable,
    identityPubKeyHex: String?,
): String =
    if (e is P2PKUnredeemableException) {
        val lock = e.lockPubKeyHex.lastHex64()
        if (identityPubKeyHex != null && lock.equals(identityPubKeyHex.lastHex64(), ignoreCase = true)) {
            "This ecash is locked to your Nostr identity key, which the current login can't sign for " +
                "(only a local key / nsec login can). Import your nsec into a Cashu wallet to claim it."
        } else {
            "This ecash is locked to a public key you don't control (${lock.take(12)}…) and can't be claimed here."
        }
    } else {
        describeMintError(e)
    }

/** A decrypted, unspent token event ready to be spent. */
data class TokenEntry(
    val event: CashuTokenEvent,
    val content: TokenContent,
)

@Serializable
private data class NutzapProofJson(
    val id: String,
    val amount: Long,
    val secret: String,
    @SerialName("C") val c: String,
    val witness: String? = null,
    /**
     * NUT-12 DLEQ tuple — `(e, s, r)` where `r` is the original
     * blinding factor the sender's wallet used at mint time. Carried
     * along the nutzap so the recipient can verify the proof against
     * the mint's keyset key WITHOUT a mint round-trip (Carol
     * verification). Optional — older sender wallets omit it; we then
     * fall back to spend-time validation.
     */
    val dleq: NutzapDleqJson? = null,
)

@Serializable
private data class NutzapDleqJson(
    val e: String,
    val s: String,
    val r: String? = null,
)

private val nutzapProofJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

data class CreatedWallet(
    val walletEvent: CashuWalletEvent,
    val nutzapInfo: NutzapInfoEvent,
    val p2pkPrivkeyHex: String,
    val p2pkPubkeyHex: String,
)

data class MintQuoteStarted(
    val quoteEvent: CashuMintQuoteEvent,
    val mintQuote: MintQuoteBolt11ResponseDto,
    val invoice: String,
)

data class MintCompleted(
    val tokenEvent: CashuTokenEvent,
    val historyEvent: CashuSpendingHistoryEvent,
    val mintedAmount: Long,
)

data class MeltCompleted(
    val preimage: String?,
    val paidAmount: Long,
    val fees: Long,
    val historyEvent: CashuSpendingHistoryEvent,
    val deleteEvent: DeletionRequestEvent,
    val newTokenEvent: CashuTokenEvent?,
)

data class SendTokenCompleted(
    val cashuToken: String,
    val amount: Long,
    val deleteEvent: DeletionRequestEvent,
    val keepEvent: CashuTokenEvent?,
    val historyEvent: CashuSpendingHistoryEvent,
)

/**
 * A mint URL in the one spelling nutzap grouping and the wallet's mint list compare by: trimmed,
 * no trailing slash, scheme and host lowercased. The path keeps its case, since some mints serve
 * several units under case-sensitive paths (`https://mint.minibits.cash/Bitcoin`).
 */
fun normalizeMintUrl(url: String): String {
    val trimmed = url.trim().trimEnd('/')
    val schemeEnd = trimmed.indexOf("://")
    if (schemeEnd <= 0) return trimmed
    val hostEnd = trimmed.indexOf('/', schemeEnd + 3).let { if (it < 0) trimmed.length else it }
    return trimmed.substring(0, hostEnd).lowercase() + trimmed.substring(hostEnd)
}

/**
 * Whether this mint error refuses the swap's inputs, so narrowing the batch down can help.
 *
 * NUT-00 has mints report every protocol error as HTTP 400 with a numeric `code` (nutshell also
 * answers request validation with 422). Any other status (401/403 auth, 404, 408, 429, 5xx) is
 * about the request or the mint, not one nutzap. So are two 400 codes: 3xxxx, NUT-21/22
 * authentication, and 11002, "transaction not balanced", our fee disagreeing with the mint's,
 * which no split of the batch fixes. Answering those with more requests only adds load.
 */
private fun MintHttpException.refusesTheInputs(): Boolean {
    if (httpStatus != 400 && httpStatus != 422) return false
    val c = code ?: return true
    return c !in 30000..39999 && c != 11002
}

/** A nutzap that passed [CashuWalletOps.prepareNutzap]: its mint and its parsed, P2PK-locked proofs. */
class PreparedNutzap(
    val nutzap: NutzapEvent,
    /** Normalized with [normalizeMintUrl]. */
    val mintUrl: String,
    val proofs: List<CashuProof>,
)

/** One swap of [CashuWalletOps.redeemNutzaps]: the nutzaps it covered and the events it published. */
class RedeemedNutzaps(
    val nutzaps: List<NutzapEvent>,
    val completed: RedeemCompleted,
)

/**
 * Nutzaps whose swap went through, so their proofs are spent at the mint, but whose new proofs
 * could not be published (a signer or relay failure). Retrying would only get "already spent";
 * the proofs come back with a NUT-13 restore from the wallet seed.
 */
class SwappedNotSaved(
    val nutzaps: List<NutzapEvent>,
    val mintUrl: String,
    val amount: Long,
    val error: Exception,
)

/** What [CashuWalletOps.redeemNutzaps] did with each nutzap it was given. */
class NutzapRedemption(
    /** One entry per successful swap. */
    val redeemed: List<RedeemedNutzaps>,
    /**
     * Final, never retryable as they are: wrong lock, no mint tag, malformed proofs, a failed
     * NUT-12 check, or already spent at the mint. Value is the reason.
     */
    val unredeemable: Map<NutzapEvent, String>,
    /** Worth no more than the mint's input fee, even swapped together with the rest of their mint. Nothing was spent. */
    val belowFee: List<NutzapEvent>,
    /** Spent at the mint but not saved; see [SwappedNotSaved]. Must not be retried. */
    val notSaved: List<SwappedNotSaved>,
    /** The mint or the network failed; may succeed later. */
    val failed: Map<NutzapEvent, Exception>,
)

private class NutzapRedemptionBuilder {
    val redeemed = ArrayList<RedeemedNutzaps>()
    val unredeemable = LinkedHashMap<NutzapEvent, String>()
    val belowFee = ArrayList<NutzapEvent>()
    val notSaved = ArrayList<SwappedNotSaved>()
    val failed = LinkedHashMap<NutzapEvent, Exception>()

    fun build() = NutzapRedemption(redeemed, unredeemable, belowFee, notSaved, failed)
}

data class RedeemCompleted(
    val amount: Long,
    val tokenEvent: CashuTokenEvent,
    val historyEvent: CashuSpendingHistoryEvent,
    val rawToken: String,
)

data class NutzapSent(
    val nutzapEvent: NutzapEvent,
    val keepEvent: CashuTokenEvent?,
    val deleteEvent: DeletionRequestEvent,
    val historyEvent: CashuSpendingHistoryEvent,
    val amount: Long,
)
