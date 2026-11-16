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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.onchain_transactions_load_failed
import com.vitorpamplona.amethyst.commons.resources.onchain_transactions_load_more_failed
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.TransactionFilter
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.BitcoinAddressTx
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.OnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One row in the on-chain transactions list. Combines the chain-level view of
 * a transaction ([tx]) with an optional [OnchainZapEvent] from `LocalCache`
 * that references the same txid — the zap event is what lets us identify the
 * Nostr counterparty (sender for incoming, recipient for outgoing) the same
 * way [WalletTransactionsScreen] does for NWC zaps via embedded zap requests.
 */
@Immutable
data class OnchainTxView(
    val tx: BitcoinAddressTx,
    val zap: OnchainZapEvent?,
) {
    val isIncoming: Boolean get() = tx.netValueSats >= 0L

    /**
     * Nostr pubkey of the counterparty if known. For an incoming transaction
     * the zap event is signed by the sender (so [OnchainZapEvent.pubKey] is
     * the sender). For an outgoing transaction the counterparty is the zap's
     * `p`-tagged recipient.
     */
    fun counterpartyPubkeyHex(): String? =
        zap?.let { z ->
            if (isIncoming) z.pubKey else z.recipient()
        }
}

private const val SAMPLE_MILLIS = 500L

class OnchainTransactionsViewModel : ViewModel() {
    private var address: String? = null
    private var backend: OnchainBackend? = null

    /** Most recent confirmed txid we've already fetched; null until we've loaded a page. */
    private var lastSeenTxid: String? = null

    /** Raw chain-side rows, in display order (mempool + confirmed pages appended). */
    private val chainTxs = MutableStateFlow<List<BitcoinAddressTx>>(emptyList())

    /**
     * Local txid → OnchainZapEvent index for this screen, auto-maintained by
     * `LocalCache.observeEvents`. Two observation flows are merged: incoming
     * zaps (signed by someone else, p-tag = me) and outgoing zaps (signed by
     * me). Since `consume(OnchainZapEvent)` rejects self-zaps, the two never
     * collide on the same txid.
     */
    private val zapsByTxid = MutableStateFlow<Map<String, OnchainZapEvent>>(emptyMap())

    private val _transactionFilter = MutableStateFlow(TransactionFilter.ALL)
    val transactionFilter = _transactionFilter.asStateFlow()

    val filteredTransactions =
        combine(chainTxs, zapsByTxid, _transactionFilter) { txs, zaps, filter ->
            val views = txs.map { OnchainTxView(it, zaps[it.txid]) }
            when (filter) {
                TransactionFilter.ALL -> views
                TransactionFilter.ZAPS -> views.filter { it.zap != null }
                TransactionFilter.NON_ZAPS -> views.filter { it.zap == null }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Whether the unfiltered chain-side list has any rows. Drives whether the
     * filter chips stay on screen — once we've loaded at least one transaction
     * the chips should remain visible even if the current filter excludes
     * everything, so the user can switch filters without the row disappearing.
     */
    val hasAnyTransactions: StateFlow<Boolean> =
        chainTxs
            .map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _hasMoreTransactions = MutableStateFlow(true)
    val hasMoreTransactions = _hasMoreTransactions.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** The Taproot address being displayed, exposed for the UI header. */
    private val _displayAddress = MutableStateFlow<String?>(null)
    val displayAddress = _displayAddress.asStateFlow()

    /**
     * Oldest `blockTime` (or null = unconfirmed/unknown) across the currently
     * loaded chain rows. The screen feeds this into the relay subscription so
     * we only ask relays for zap events from that point onwards instead of
     * pulling the user's entire NIP-BC history.
     *
     * Returns null while the page is empty (no constraint — fall back to the
     * relay's full history for the first call) or when the oldest row is in
     * the mempool (we don't have a timestamp to bound on).
     */
    val oldestBlockTime: StateFlow<Long?> =
        chainTxs
            .map { txs -> txs.minOfOrNull { it.blockTime ?: Long.MAX_VALUE }?.takeIf { it != Long.MAX_VALUE } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun init(accountViewModel: AccountViewModel) {
        if (address != null) return
        val pubKey = accountViewModel.account.signer.pubKey
        address = runCatching { TaprootAddress.fromPubKey(pubKey) }.getOrNull()
        backend = LocalCache.onchainBackend
        _displayAddress.value = address

        // Stand up the reactive zap cache. The two filters cover both
        // directions; LocalCache's filter index narrows the fanout, so we
        // only get woken for kind-8333 events that mention us. .sample(500)
        // coalesces bursts so a relay flooding 50 historical zaps at once
        // produces at most one UI update.
        viewModelScope.launch(Dispatchers.IO) {
            val incoming = Filter(kinds = listOf(OnchainZapEvent.KIND), tags = mapOf("p" to listOf(pubKey)))
            val outgoing = Filter(kinds = listOf(OnchainZapEvent.KIND), authors = listOf(pubKey))
            @OptIn(FlowPreview::class)
            combine(
                LocalCache.observeEvents<OnchainZapEvent>(incoming),
                LocalCache.observeEvents<OnchainZapEvent>(outgoing),
            ) { incomingZaps, outgoingZaps ->
                val merged = HashMap<String, OnchainZapEvent>(incomingZaps.size + outgoingZaps.size)
                (incomingZaps.asSequence() + outgoingZaps.asSequence()).forEach { z ->
                    val txid = z.txid() ?: return@forEach
                    merged[txid] = z
                }
                merged
            }.sample(SAMPLE_MILLIS).collect { zapsByTxid.value = it }
        }
    }

    fun setTransactionFilter(filter: TransactionFilter) {
        _transactionFilter.value = filter
    }

    fun fetchTransactions() {
        val addr = address ?: return
        val be = backend ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            lastSeenTxid = null
            _hasMoreTransactions.value = true
            try {
                val rows = withContext(Dispatchers.IO) { be.getTxsForAddress(addr, null) }
                chainTxs.value = rows
                lastSeenTxid = rows.lastOrNull { it.confirmations > 0 }?.txid
                _hasMoreTransactions.value = lastSeenTxid != null
            } catch (t: Throwable) {
                currentCoroutineContext().ensureActive()
                _error.value = t.message ?: loadStringRes(Res.string.onchain_transactions_load_failed)
                _hasMoreTransactions.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMoreTransactions() {
        if (_isLoadingMore.value || !_hasMoreTransactions.value) return
        val addr = address ?: return
        val be = backend ?: return
        val seen = lastSeenTxid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            try {
                val rows = withContext(Dispatchers.IO) { be.getTxsForAddress(addr, seen) }
                if (rows.isEmpty()) {
                    _hasMoreTransactions.value = false
                } else {
                    chainTxs.value = chainTxs.value + rows
                    lastSeenTxid = rows.lastOrNull { it.confirmations > 0 }?.txid ?: seen
                }
            } catch (t: Throwable) {
                currentCoroutineContext().ensureActive()
                _error.value = t.message ?: loadStringRes(Res.string.onchain_transactions_load_more_failed)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
