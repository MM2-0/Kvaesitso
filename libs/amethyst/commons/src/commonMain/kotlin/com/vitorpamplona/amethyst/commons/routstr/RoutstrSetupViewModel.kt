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
package com.vitorpamplona.amethyst.commons.routstr

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.frontpage.DEFAULT_DIGEST_BUDGET
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeyEditKind
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeyEditState
import com.vitorpamplona.amethyst.commons.napplet.gateways.WalletNoAnswerException
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The node list as the picker draws it. */
@Immutable
data class RoutstrNodesUi(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val nodes: List<RoutstrNode> = emptyList(),
    /** Nodes seen this month but not this week, behind "Show N not seen this week". */
    val staleCount: Int = 0,
    val showingStale: Boolean = false,
)

/** Whether the node is who announced it. */
@Immutable
sealed interface RoutstrVerification {
    data object Checking : RoutstrVerification

    /** `/v1/info`'s npub is the announcement's author. */
    data object Verified : RoutstrVerification

    /** The node answers as a Routstr node, under another key than the announcement's. */
    data object Mismatch : RoutstrVerification

    /** Typed by the user: no announcement to compare with. */
    data object Typed : RoutstrVerification

    /** It doesn't answer as a Routstr node, or doesn't answer. */
    data object Unknown : RoutstrVerification
}

@Immutable
sealed interface RoutstrModelsUi {
    data object Loading : RoutstrModelsUi

    /** The text models, enabled ones first. */
    data class Loaded(
        val models: List<RoutstrModel>,
    ) : RoutstrModelsUi

    data class Failed(
        val error: RoutstrError,
    ) : RoutstrModelsUi
}

@Immutable
data class RoutstrNodeDetail(
    val node: RoutstrNode,
    val verification: RoutstrVerification = RoutstrVerification.Checking,
    val models: RoutstrModelsUi = RoutstrModelsUi.Loading,
    /** The onion the node's own https `/v1/info` names: never the announcement's, which could be anyone's. */
    val onionRoot: String? = null,
) {
    /** Where buying here goes: the https root, and only the onion that root names. Null for an onion-only node. */
    val target: RoutstrTarget? get() = node.httpsUrl?.let { RoutstrTarget(it, onionRoot, node.displayName) }

    /** The most a key must hold for the dearest model's reserve: what the buy screen warns under. */
    fun maxCostSats(modelId: String?): Long? {
        val loaded = models as? RoutstrModelsUi.Loaded ?: return null
        val model = modelId?.let { id -> loaded.models.firstOrNull { it.id == id } } ?: return null
        return RoutstrPricing.maxCostSats(model.sats)
    }
}

/** Where buying a key or a top-up stands. */
@Immutable
sealed interface RoutstrBuyUi {
    data object Idle : RoutstrBuyUi

    /** Asking the node for an invoice, or sending a Cashu token. */
    data object Working : RoutstrBuyUi

    /**
     * The invoice to pay: [paying] while the app's wallet pays it; [payError] when it said it
     * didn't; [payNoAnswer] when it didn't answer in time and may still pay. Once the wallet was
     * asked ([payRequested]) Pay is never offered again for this invoice: "Check payment" is.
     */
    data class Invoice(
        val purchase: RoutstrPendingPurchase,
        val paying: Boolean = false,
        val payError: String? = null,
        val paidByWallet: Boolean = purchase.walletPaid,
        val payRequested: Boolean = purchase.walletPayRequested,
        val payNoAnswer: Boolean = false,
        val outcome: RoutstrPurchaseOutcome? = null,
    ) : RoutstrBuyUi {
        /** Whether to offer Pay from the app's wallet: never while a request may be in flight, or after one was sent. */
        val canPay: Boolean get() = !paying && !paidByWallet && !payRequested
    }

    /** How it ended (or where it stopped for the user). */
    data class Finished(
        val outcome: RoutstrPurchaseOutcome,
        val purchaseId: String? = null,
    ) : RoutstrBuyUi

    data class Error(
        val error: RoutstrError?,
        /** The invoice couldn't be kept on this device, so it wasn't shown. */
        val notKept: Boolean = false,
    ) : RoutstrBuyUi
}

/** "I have a key": what checking it said. */
@Immutable
sealed interface RoutstrKeyCheck {
    data object Idle : RoutstrKeyCheck

    data object Checking : RoutstrKeyCheck

    /** A `cashu…` token, which is money: never saved as a key. */
    data object IsCashuToken : RoutstrKeyCheck

    data class Works(
        val balance: RoutstrBalance,
    ) : RoutstrKeyCheck

    data class Failed(
        val error: RoutstrError,
    ) : RoutstrKeyCheck
}

/**
 * The Routstr setup screen's state: the node picker (kind 38421, no node contacted), the node
 * picked (its `/v1/info` and `/v1/models`, asked only now), and buying or adding a key. The
 * purchase itself runs on the account's scope ([RoutstrPurchases.follow]), so leaving the
 * screen never drops a paid invoice.
 */
class RoutstrSetupViewModel(
    val routstr: RoutstrAccount,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val showStale = MutableStateFlow(false)

    fun setQuery(text: String) {
        query.value = text
    }

    fun setShowStale(show: Boolean) {
        showStale.value = show
    }

    val nodes: StateFlow<RoutstrNodesUi> =
        combine(routstr.nodes, query, showStale) { list, q, stale ->
            when (list) {
                RoutstrNodeList.Loading -> RoutstrNodesUi(loading = true)
                RoutstrNodeList.Failed -> RoutstrNodesUi(loading = false, failed = true)
                is RoutstrNodeList.Loaded -> {
                    val picks = RoutstrNodes.list(list.events, routstr.torEnabled())
                    RoutstrNodesUi(
                        loading = false,
                        nodes = picks.all(stale).filter { RoutstrNodes.matches(it, q) },
                        staleCount = picks.stale.count { RoutstrNodes.matches(it, q) },
                        showingStale = stale,
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RoutstrNodesUi())

    /** What is on its way, but an unpaid invoice the user cancelled: that one is kept out of sight until it can't settle. */
    val pending: StateFlow<List<RoutstrPendingPurchase>> =
        routstr.purchases.pending
            .map { list -> list.filterNot { it.hiddenByUser && it.stage == RoutstrStage.AWAITING_PAYMENT } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        routstr.refreshNodes()
        viewModelScope.launch { routstr.purchases.load() }
    }

    fun refresh() = routstr.refreshNodes(maxAgeSeconds = 0)

    // --- The node picked -----------------------------------------------------------------

    private val _detail = MutableStateFlow<RoutstrNodeDetail?>(null)
    val detail: StateFlow<RoutstrNodeDetail?> = _detail.asStateFlow()
    private var detailJob: Job? = null

    /** Opens [node]: its `/v1/info` (to verify it) and `/v1/models` (the prices), asked only now. */
    fun open(
        node: RoutstrNode,
        typed: Boolean = false,
    ) {
        _detail.value = RoutstrNodeDetail(node, if (typed) RoutstrVerification.Typed else RoutstrVerification.Checking)
        detailJob?.cancel()
        detailJob =
            viewModelScope.launch {
                // Over https only: an onion's answer, or an announcement's onion, could be anyone's.
                if (!typed) {
                    val verification = routstr.routing.verify(node)
                    _detail.update { it?.takeIf { d -> d.node == node }?.copy(verification = verification) ?: it }
                }
                val https = node.httpsUrl
                val onion = https?.let { routstr.routing.target(it, null).onionRoot }
                _detail.update { it?.takeIf { d -> d.node == node }?.copy(onionRoot = onion) ?: it }
                // A node with https: there, or the onion it names itself. An onion-only node: its onion, with Tor.
                val base = if (https != null) routstr.routing.baseFor(https) else node.callBase(routstr.torEnabled())
                val models =
                    if (base == null) {
                        RoutstrModelsUi.Failed(RoutstrError(null, RoutstrFailure.UNREACHABLE))
                    } else {
                        try {
                            RoutstrModelsUi.Loaded(textModels(routstr.api.models(base)))
                        } catch (e: RoutstrException) {
                            RoutstrModelsUi.Failed(e.error)
                        }
                    }
                _detail.update { it?.takeIf { d -> d.node == node }?.copy(models = models) ?: it }
            }
    }

    /** Opens the node announced at [address], once the announcements are read. */
    fun openAddress(address: String) {
        viewModelScope.launch {
            routstr.node(address)?.let { return@launch open(it) }
            routstr.nodes.first { it !is RoutstrNodeList.Loading }
            routstr.node(address)?.let { open(it) }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _detail.value = null
    }

    private val _urlCheck = MutableStateFlow<RoutstrError?>(null)

    /** Why a typed URL wasn't opened. */
    val urlCheck: StateFlow<RoutstrError?> = _urlCheck.asStateFlow()

    private val _checkingUrl = MutableStateFlow(false)
    val checkingUrl: StateFlow<Boolean> = _checkingUrl.asStateFlow()

    /** "Enter a node URL": confirmed with `/v1/info` before it opens. https only, no IP literal unless typed. */
    fun openUrl(typed: String) {
        val root = RoutstrNodes.root(typed)
        if (!root.startsWith("https://", ignoreCase = true) || root.length <= "https://".length) {
            _urlCheck.value = RoutstrError(null, RoutstrFailure.UNEXPECTED)
            return
        }
        _urlCheck.value = null
        _checkingUrl.value = true
        viewModelScope.launch {
            try {
                val info = routstr.routing.ask(root)
                if (!info.isRoutstr) {
                    _urlCheck.value = RoutstrError(null, RoutstrFailure.UNEXPECTED)
                } else {
                    open(
                        RoutstrNode(
                            pubKey = info.pubKeyHex ?: "",
                            dTag = "",
                            name = info.name,
                            about = info.description,
                            lastSeen = TimeUtils.now(),
                            version = info.version,
                            mints = info.mints.orEmpty(),
                            models = emptyList(),
                            httpsUrl = root,
                            onionUrl = RoutstrNodes.onionRoot(info.onionUrl),
                        ),
                        typed = true,
                    )
                }
            } catch (e: RoutstrException) {
                _urlCheck.value = e.error
            } finally {
                _checkingUrl.value = false
            }
        }
    }

    // --- Buying ----------------------------------------------------------------------------

    private val _buy = MutableStateFlow<RoutstrBuyUi>(RoutstrBuyUi.Idle)
    val buy: StateFlow<RoutstrBuyUi> = _buy.asStateFlow()

    init {
        // Here, after _buy exists. The invoice on screen is checked every few seconds; the others,
        // every minute.
        viewModelScope.launch {
            var showing: String? = null
            try {
                _buy.map { (it as? RoutstrBuyUi.Invoice)?.purchase?.id }.distinctUntilChanged().collect { id ->
                    showing?.let { routstr.purchases.setShown(it, false) }
                    id?.let { routstr.purchases.setShown(it, true) }
                    showing = id
                }
            } finally {
                showing?.let { routstr.purchases.setShown(it, false) }
            }
        }
    }

    /** Whether the account has a Lightning wallet to pay with (NWC, the zaps' wallet). */
    val hasLightningWallet: StateFlow<Boolean> = routstr.hasLightningWallet

    /** The sats the Cashu wallet holds at each of [mints] the node takes. */
    fun cashuBalances(mints: List<String>): Map<String, Long> = routstr.cashuWallet()?.balances(mints).orEmpty()

    fun resetBuy() {
        _buy.value = RoutstrBuyUi.Idle
    }

    /** A Lightning invoice for [amountSats], kept on this device before it is shown, then followed until it settles. */
    fun makeInvoice(
        target: RoutstrTarget,
        amountSats: Long,
        topupKey: AgentKeyTag? = null,
    ) {
        if (_buy.value is RoutstrBuyUi.Working) return
        _buy.value = RoutstrBuyUi.Working
        viewModelScope.launch {
            try {
                val purpose = if (topupKey != null) RoutstrPurpose.TOPUP else RoutstrPurpose.CREATE
                val purchase = routstr.purchases.buyWithLightning(target, amountSats, purpose, topupKey)
                _buy.value = RoutstrBuyUi.Invoice(purchase)
                follow(purchase.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RoutstrException) {
                _buy.value = RoutstrBuyUi.Error(e.error)
            } catch (e: RoutstrNotPersistedException) {
                _buy.value = RoutstrBuyUi.Error(null, notKept = true)
            }
        }
    }

    /** Shows invoice [id] again (a pending one tapped in the list), and follows it. */
    fun showInvoice(id: String) {
        val purchase = routstr.purchases.get(id) ?: return
        if (purchase.bolt11 == null) return
        // Paid by the wallet, or asked to be: Pay isn't offered again.
        _buy.value = RoutstrBuyUi.Invoice(purchase)
        follow(id)
    }

    private fun follow(id: String) {
        // On the account's scope: leaving the screen keeps checking a paid invoice.
        routstr.purchases.follow(routstr.account.scope, id)
        viewModelScope.launch {
            routstr.purchases.outcomes
                .map { it[id] }
                .first { it != null && it.settles() }
                ?.let { outcome -> _buy.update { if ((it as? RoutstrBuyUi.Invoice)?.purchase?.id == id) RoutstrBuyUi.Finished(outcome, id) else it } }
        }
        viewModelScope.launch {
            routstr.purchases.outcomes.collect { all ->
                val outcome = all[id] ?: return@collect
                _buy.update { if (it is RoutstrBuyUi.Invoice && it.purchase.id == id) it.copy(outcome = outcome) else it }
            }
        }
    }

    // Not Expired: an expired invoice may still settle for a day, and stays on screen, checked.
    private fun RoutstrPurchaseOutcome.settles(): Boolean =
        this is RoutstrPurchaseOutcome.KeySaved ||
            this == RoutstrPurchaseOutcome.KeyNotSaved ||
            this == RoutstrPurchaseOutcome.ToppedUp

    /**
     * Pays the invoice on screen from the account's NWC wallet, once: the request is kept on this
     * device before it goes, and from then on only "Check payment" is offered, whatever the
     * wallet answers (or doesn't). Runs on the account's scope, so leaving the screen doesn't
     * lose its answer.
     */
    fun payWithWallet() {
        val shown = _buy.value as? RoutstrBuyUi.Invoice ?: return
        val bolt11 = shown.purchase.bolt11 ?: return
        if (!shown.canPay) return
        val id = shown.purchase.id
        _buy.value = shown.copy(paying = true, payError = null, payNoAnswer = false)

        fun update(change: (RoutstrBuyUi.Invoice) -> RoutstrBuyUi.Invoice) = _buy.update { if (it is RoutstrBuyUi.Invoice && it.purchase.id == id) change(it) else it }
        routstr.account.scope.launch {
            try {
                routstr.purchases.markPayRequested(id)
                routstr.payWithLightningWallet(bolt11)
                routstr.purchases.markWalletPaid(id)
                update { it.copy(paying = false, payRequested = true, paidByWallet = true) }
                // Ask now rather than at the next poll.
                routstr.purchases.check(id)
            } catch (e: WalletNoAnswerException) {
                // It may still pay: never offered again, checked instead.
                update { it.copy(paying = false, payRequested = true, payNoAnswer = true) }
                routstr.purchases.check(id)
            } catch (e: CancellationException) {
                update { it.copy(paying = false, payRequested = true) }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "The wallet didn't pay the Routstr invoice", e)
                update { it.copy(paying = false, payRequested = true, payError = e.message ?: "") }
            }
        }
    }

    /** "Check payment": asks the node about the invoice on screen now. */
    fun checkPayment() {
        val shown = _buy.value as? RoutstrBuyUi.Invoice ?: return
        retry(shown.purchase.id)
    }

    /**
     * Cancels the invoice on screen: hidden, but kept and checked slowly until it can't settle,
     * since it may have been paid from another wallet. One the app's wallet was asked to pay can't
     * be cancelled.
     */
    fun abandonInvoice() {
        val shown = _buy.value as? RoutstrBuyUi.Invoice ?: return
        routstr.account.scope.launch {
            if (routstr.purchases.abandon(shown.purchase.id)) _buy.update { if (it == shown || (it as? RoutstrBuyUi.Invoice)?.purchase?.id == shown.purchase.id) RoutstrBuyUi.Idle else it }
        }
    }

    /**
     * A pasted Cashu token, for a new key or a top-up of [topupKey]. The spend runs on the
     * account's scope and can't be cancelled halfway: a token sent is followed to its answer.
     */
    fun payWithToken(
        target: RoutstrTarget,
        token: String,
        topupKey: AgentKeyTag? = null,
    ) {
        if (_buy.value is RoutstrBuyUi.Working) return
        _buy.value = RoutstrBuyUi.Working
        routstr.account.scope.launch {
            val purpose = if (topupKey != null) RoutstrPurpose.TOPUP else RoutstrPurpose.CREATE
            val outcome = withContext(NonCancellable) { routstr.purchases.buyWithCashu(target, token, purpose, topupKey) }
            _buy.value = RoutstrBuyUi.Finished(outcome)
        }
    }

    /** [amountSats] from the Cashu wallet's proofs at [mint], a mint the node takes; on the account's scope, never cancelled halfway. */
    fun payFromCashuWallet(
        target: RoutstrTarget,
        mint: String,
        amountSats: Long,
        topupKey: AgentKeyTag? = null,
    ) {
        if (_buy.value is RoutstrBuyUi.Working) return
        _buy.value = RoutstrBuyUi.Working
        routstr.account.scope.launch {
            val purpose = if (topupKey != null) RoutstrPurpose.TOPUP else RoutstrPurpose.CREATE
            _buy.value =
                withContext(NonCancellable) {
                    try {
                        RoutstrBuyUi.Finished(routstr.purchases.buyFromWallet(target, mint, amountSats, purpose, topupKey))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "The Cashu wallet couldn't pay", e)
                        RoutstrBuyUi.Error(RoutstrError(null, RoutstrFailure.TOKEN_REJECTED, message = e.message))
                    }
                }
        }
    }

    /** Tries pending purchase [id] again now: a key not saved yet, change the wallet didn't take, an invoice. */
    fun retry(id: String) {
        routstr.account.scope.launch { routstr.purchases.check(id) }
    }

    fun forgetChange(id: String) {
        viewModelScope.launch { routstr.purchases.forgetToken(id) }
    }

    // --- I have a key --------------------------------------------------------------------

    private val _keyCheck = MutableStateFlow<RoutstrKeyCheck>(RoutstrKeyCheck.Idle)
    val keyCheck: StateFlow<RoutstrKeyCheck> = _keyCheck.asStateFlow()
    private var keyJob: Job? = null

    /** Checks a pasted key with `/v1/balance/info`; a `cashu…` string is refused before anything is sent. */
    fun checkKey(
        target: RoutstrTarget,
        key: String,
    ) {
        keyJob?.cancel()
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            _keyCheck.value = RoutstrKeyCheck.Idle
            return
        }
        if (RoutstrPurchases.isCashuToken(trimmed)) {
            _keyCheck.value = RoutstrKeyCheck.IsCashuToken
            return
        }
        _keyCheck.value = RoutstrKeyCheck.Checking
        keyJob =
            viewModelScope.launch {
                val base = target.callBase(routstr.torEnabled())
                _keyCheck.value =
                    try {
                        RoutstrKeyCheck.Works(routstr.api.balance(base, trimmed))
                    } catch (e: RoutstrException) {
                        RoutstrKeyCheck.Failed(e.error)
                    }
            }
    }

    fun clearKeyCheck() {
        keyJob?.cancel()
        _keyCheck.value = RoutstrKeyCheck.Idle
    }

    /** Saves a checked key under [target]'s `/v1`, through the AI-key editor. */
    fun saveKey(
        target: RoutstrTarget,
        key: String,
    ) = routstr.account.agentKeyEditor.submit(AgentKeyEditKind.SAVE, AgentKeyTag(key.trim(), RoutstrNodes.keyUrl(target.root)))

    val editState: StateFlow<AgentKeyEditState> = routstr.account.agentKeyEditor.state

    companion object {
        private const val TAG = "RoutstrSetup"

        /** Text-writing models, enabled first, cheapest edition first where priced. */
        fun textModels(models: List<RoutstrModel>): List<RoutstrModel> =
            models
                .filter { it.textOutput }
                .sortedWith(
                    compareByDescending<RoutstrModel> { it.enabled }
                        .thenBy { RoutstrPricing.editionSats(it.sats, DEFAULT_DIGEST_BUDGET) ?: Long.MAX_VALUE }
                        .thenBy { it.id },
                )

        /** One edition with [model], in sats, with the same figures as the print's estimate. */
        fun editionSats(model: RoutstrModel): Long? = RoutstrPricing.editionSats(model.sats, DEFAULT_DIGEST_BUDGET)
    }
}
