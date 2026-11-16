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
package com.vitorpamplona.amethyst.commons.cashu

import com.vitorpamplona.amethyst.commons.cashu.ops.TokenEntry
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.token.TokenContent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.runCatchingCancellable

/**
 * Pure, stateless projection of a stream of NIP-60 / NIP-61 / NIP-87 events
 * into a [WalletSnapshot]. This is the read half of the wallet, extracted out
 * of the Android-only `CashuWalletState` so the headless CLI (`amy`) and the
 * reactive Android holder run the **same** decrypt + del-rollover +
 * pending-quote logic.
 *
 * - Android's `CashuWalletState` keeps its incremental dirty-tracking and
 *   StateFlow side effects but delegates the two tricky computations
 *   ([computeUnspent] and [computePending]) here.
 * - `amy` calls [project] once per command over `store.allOfKinds(...)`.
 *
 * Decryption uses the supplied [signer]; failures are logged and the offending
 * event is skipped, exactly as the Android holder does.
 */
class CashuWalletReader(
    private val signer: NostrSigner,
) {
    data class WalletSnapshot(
        val walletEvent: CashuWalletEvent?,
        val nutzapInfoEvent: NutzapInfoEvent?,
        val mints: List<String>,
        val tokenEntries: List<TokenEntry>,
        val history: List<CashuSpendingHistoryEvent>,
        val pendingQuotes: List<CashuMintQuoteEvent>,
        val nutzapEvents: List<NutzapEvent>,
        val recommendations: List<MintRecommendationEvent>,
    ) {
        /** Total spendable balance in the base unit (sats). */
        val balanceSats: Long get() = tokenEntries.sumOf { it.content.totalAmount() }

        /** Spendable balance grouped by mint URL. */
        val balancesByMint: Map<String, Long>
            get() =
                tokenEntries
                    .groupBy { it.content.mint }
                    .mapValues { (_, byMint) -> byMint.sumOf { it.content.totalAmount() } }
    }

    suspend fun project(events: Iterable<Event>): WalletSnapshot {
        var walletEvent: CashuWalletEvent? = null
        var nutzapInfoEvent: NutzapInfoEvent? = null
        val tokenEvents = LinkedHashMap<HexKey, CashuTokenEvent>()
        val historyEvents = LinkedHashMap<HexKey, CashuSpendingHistoryEvent>()
        val quoteEvents = LinkedHashMap<HexKey, CashuMintQuoteEvent>()
        val nutzapEvents = LinkedHashMap<HexKey, NutzapEvent>()
        val recommendationEvents = LinkedHashMap<String, MintRecommendationEvent>()

        for (event in events) {
            when (event) {
                is CashuWalletEvent ->
                    if (walletEvent == null || event.createdAt > walletEvent.createdAt) walletEvent = event
                is NutzapInfoEvent ->
                    if (nutzapInfoEvent == null || event.createdAt > nutzapInfoEvent.createdAt) nutzapInfoEvent = event
                is CashuTokenEvent -> tokenEvents.getOrPut(event.id) { event }
                is CashuSpendingHistoryEvent -> historyEvents.getOrPut(event.id) { event }
                is CashuMintQuoteEvent -> quoteEvents.getOrPut(event.id) { event }
                is NutzapEvent -> nutzapEvents.getOrPut(event.id) { event }
                is MintRecommendationEvent -> {
                    val key = event.dTag().ifEmpty { event.id }
                    val current = recommendationEvents[key]
                    if (current == null || event.createdAt > current.createdAt) recommendationEvents[key] = event
                }
                else -> Unit
            }
        }

        val mints =
            walletEvent?.let { evt ->
                runCatchingCancellable { evt.mints(signer) }
                    .onFailure { Log.w("CashuWalletReader") { "Failed to decrypt wallet mints: ${it.message}" } }
                    .getOrNull()
            } ?: emptyList()

        val contents = decryptTokens(tokenEvents.values)
        val tokenEntries = computeUnspent(tokenEvents.values, contents)
        val pendingQuotes = computePending(quoteEvents.values, historyEvents.values)

        return WalletSnapshot(
            walletEvent = walletEvent,
            nutzapInfoEvent = nutzapInfoEvent,
            mints = mints,
            tokenEntries = tokenEntries,
            history = historyEvents.values.sortedByDescending { it.createdAt },
            pendingQuotes = pendingQuotes,
            nutzapEvents = nutzapEvents.values.sortedByDescending { it.createdAt },
            recommendations = recommendationEvents.values.sortedByDescending { it.createdAt },
        )
    }

    /** Decrypt every token event's content, skipping (and logging) failures. */
    suspend fun decryptTokens(events: Iterable<CashuTokenEvent>): Map<HexKey, TokenContent> {
        val out = HashMap<HexKey, TokenContent>()
        events.forEach { evt ->
            val content =
                runCatchingCancellable { evt.tokenContent(signer) }
                    .onFailure { Log.w("CashuWalletReader") { "Failed to decrypt token ${evt.id.take(8)}: ${it.message}" } }
                    .getOrNull()
            if (content != null) out[evt.id] = content
        }
        return out
    }

    companion object {
        /**
         * Project decrypted token events into the unspent set: drop anything a
         * later token's `del` rollover marked destroyed or that failed to
         * decrypt, then sort newest-first.
         */
        fun computeUnspent(
            events: Iterable<CashuTokenEvent>,
            contents: Map<HexKey, TokenContent>,
        ): List<TokenEntry> {
            val all = events.toList()
            val deletedIds = mutableSetOf<HexKey>()
            all.forEach { evt -> contents[evt.id]?.del?.let(deletedIds::addAll) }
            return all
                .filter { it.id !in deletedIds && contents.containsKey(it.id) }
                .mapNotNull { evt -> contents[evt.id]?.let { TokenEntry(evt, it) } }
                .sortedByDescending { it.event.createdAt }
        }

        /**
         * A quote is pending when it is neither expired nor referenced as
         * "destroyed" by a kind:7376 history event (completion of a mint flow
         * deletes the kind:7374 and records a destroyed reference to it).
         */
        fun computePending(
            quotes: Iterable<CashuMintQuoteEvent>,
            history: Iterable<CashuSpendingHistoryEvent>,
            now: Long = TimeUtils.now(),
        ): List<CashuMintQuoteEvent> {
            val destroyedQuoteIds =
                history
                    .asSequence()
                    .flatMap { it.destroyedReferences() }
                    .map { it.eventId }
                    .toSet()

            return quotes
                .filter { it.id !in destroyedQuoteIds }
                .filter { evt ->
                    val exp =
                        evt.tags
                            .firstOrNull { it.size >= 2 && it[0] == "expiration" }
                            ?.get(1)
                            ?.toLongOrNull()
                    exp == null || exp > now
                }.sortedByDescending { it.createdAt }
        }
    }
}
