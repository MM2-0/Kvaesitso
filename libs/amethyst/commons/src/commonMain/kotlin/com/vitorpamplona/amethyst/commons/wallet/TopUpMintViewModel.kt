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
package com.vitorpamplona.amethyst.commons.wallet

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.cashu.ops.describeMintError
import com.vitorpamplona.amethyst.commons.model.nip60Cashu.CashuWalletState
import com.vitorpamplona.amethyst.commons.viewmodels.BaseAccountViewModel
import com.vitorpamplona.amethyst.commons.wallet.ReloadMintViewModel
import com.vitorpamplona.amethyst.commons.wallet.ReloadSource
import com.vitorpamplona.amethyst.commons.wallet.ReloadStatus
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceMethod
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presenter for the standalone "Top up a mint" screen.
 *
 * Reuses the same funding primitives as [ReloadMintViewModel] —
 * [CashuWalletState.rebalance] for a mint-to-mint move and
 * [com.vitorpamplona.amethyst.commons.cashu.ops.CashuWalletOps.startMintFromLightning] /
 * `completeMintFromLightning` for an LN top-up — but without any of the
 * zap-specific machinery (recipient, shared-mint intersection, fixed send
 * amount + shortfall, terminal nutzap, fund-then-send atomicity). The user
 * picks an amount and a source; we add ecash to a fixed target mint and pop.
 */
class TopUpMintViewModel : ViewModel() {
    private var accountViewModel: BaseAccountViewModel? = null
    private var targetMint: String = ""
    private val state: CashuWalletState? get() = accountViewModel?.account?.cashuWalletState

    /** The in-flight pipeline, cancelled when the screen leaves (it runs on the
     *  long-lived BaseAccountViewModel scope, not this VM's). */
    private var job: Job? = null

    private val _uiState = MutableStateFlow(TopUpUiState())
    val uiState: StateFlow<TopUpUiState> = _uiState.asStateFlow()

    fun init(
        accountViewModel: BaseAccountViewModel,
        mintUrl: String,
    ) {
        if (this.accountViewModel != null) return // already initialized
        this.accountViewModel = accountViewModel
        this.targetMint = mintUrl
        _uiState.update { it.copy(targetMint = mintUrl) }

        // Wallet proofs/mints arrive from relays and can land after the screen
        // opens, so keep the source list + target balance in sync as the wallet
        // fills in. Project to the per-mint balance map and distinctUntilChanged
        // so unrelated wallet churn doesn't re-run the rebuild on every emission.
        val st = state ?: return
        viewModelScope.launch {
            combine(st.tokenEntries, st.mints) { _, _ -> st.peekMintBalances() }
                .distinctUntilChanged()
                .collect { rebuild(it) }
        }
    }

    /** Rebuild target balance + funding sources from the current wallet state,
     *  keeping the user's amount and source pick intact. */
    private fun rebuild(balances: Map<String, Long>) {
        _uiState.update { cur -> recompute(cur.copy(targetBalanceSats = balances[targetMint] ?: 0L), balances) }
    }

    /** Recompute fee estimate + sources + a default source for the current amount. */
    private fun recompute(
        base: TopUpUiState,
        balances: Map<String, Long>,
    ): TopUpUiState {
        val moveSats = base.amountSats
        // Rough fee cushion for *enabling* a mint source — the real Lightning
        // feeReserve is only known once rebalance() fetches the melt quote, so this
        // is a heuristic (1%, min 2 sat). A source that clears the estimate but not
        // the real quote fails recoverably without moving funds.
        val estFee = if (moveSats <= 0L) 0L else (moveSats / 100L).coerceAtLeast(2L)
        val needFromSource = moveSats + estFee

        val mintSources =
            balances
                .filterKeys { it != targetMint }
                .entries
                .sortedByDescending { it.value }
                .map { ReloadSource.Mint(it.key, it.value, canCover = moveSats <= 0L || it.value >= needFromSource) }

        val lightningSources: List<ReloadSource> =
            nwcWallets()
                .map { ReloadSource.LightningWallet(it.id, it.name) }
                .ifEmpty { listOf(ReloadSource.LightningExternal) }
        val sources = mintSources + lightningSources

        // Keep the current pick if still valid; else default to the richest mint that
        // can cover (no new sats), else the configured/first Lightning wallet.
        val keep =
            when (val prev = base.selectedSource) {
                is ReloadSource.Mint -> mintSources.firstOrNull { it.mintUrl == prev.mintUrl && it.canCover }
                is ReloadSource.LightningWallet -> lightningSources.firstOrNull { it is ReloadSource.LightningWallet && it.walletId == prev.walletId }
                ReloadSource.LightningExternal -> lightningSources.firstOrNull { it is ReloadSource.LightningExternal }
                null -> null
            }
        val selectedSource: ReloadSource? =
            keep
                ?: mintSources.filter { it.canCover }.maxByOrNull { it.balanceSats }
                ?: defaultLightningSource(lightningSources)

        return base.copy(
            estFeeSats = estFee,
            sources = sources.toImmutableList(),
            selectedSource = selectedSource,
        )
    }

    fun selectSource(source: ReloadSource) {
        _uiState.update { it.copy(selectedSource = source) }
    }

    fun setAmount(sats: Long) {
        _uiState.update { recompute(it.copy(amountSats = sats.coerceAtLeast(0L)), currentBalances()) }
    }

    private fun currentBalances(): Map<String, Long> = state?.peekMintBalances().orEmpty()

    fun confirm() {
        val vm = accountViewModel ?: return
        val s = _uiState.value
        val source = s.selectedSource ?: return
        val moveSats = s.amountSats
        if (moveSats <= 0L) return

        // In-flight guard: only start from a resting state so a double tap (or the
        // Failed-state retry) can't launch a second pipeline.
        if (s.status !is TopUpStatus.Configuring && s.status !is TopUpStatus.Failed) return
        setStatus(TopUpStatus.Working("Starting", 0.05f))

        job?.cancel()
        job =
            vm.launchSigner {
                try {
                    when (source) {
                        is ReloadSource.Mint -> topUpFromMint(source.mintUrl, moveSats)
                        is ReloadSource.LightningWallet -> topUpFromLightning(moveSats, walletUriFor(source.walletId))
                        ReloadSource.LightningExternal -> topUpFromLightning(moveSats, null)
                    }
                    setStatus(TopUpStatus.Done)
                } catch (e: CancellationException) {
                    throw e // screen left mid-flow — don't mask as a Failed state
                } catch (e: Exception) {
                    setStatus(TopUpStatus.Failed(describeMintError(e)))
                }
            }
    }

    private suspend fun topUpFromMint(
        sourceMint: String,
        moveSats: Long,
    ) {
        val st = state ?: return
        setStatus(TopUpStatus.Working("Moving funds", 0.1f))
        st.rebalance(
            sourceMintUrl = sourceMint,
            targetMintUrl = targetMint,
            sats = moveSats,
            onProgress = { p -> setStatus(TopUpStatus.Working("Moving funds", p.coerceIn(0.1f, 0.95f))) },
        )
    }

    private suspend fun topUpFromLightning(
        moveSats: Long,
        walletUri: Nip47WalletConnect.Nip47URINorm?,
    ) {
        val vm = accountViewModel ?: return
        val st = state ?: return
        val ops = st.ops

        setStatus(TopUpStatus.Working("Requesting invoice", 0.1f))
        val flow = ops.startMintFromLightning(targetMint, moveSats)

        if (walletUri != null) {
            setStatus(TopUpStatus.Working("Paying from your wallet", 0.35f))
            // Fire-and-forget: the mint-quote poll below is the source of truth for
            // whether the payment actually landed.
            runCatching {
                vm.account.zaps.sendNwcRequestToWallet(walletUri, PayInvoiceMethod.create(flow.invoice)) { }
            }
        } else {
            // No NWC — surface the invoice for an external wallet and keep polling.
            setStatus(TopUpStatus.AwaitingInvoice(flow.invoice, moveSats))
        }

        // External payment can take a while; the poll runs on a job tied to the
        // screen (cancelled in onCleared), so leaving stops it.
        val attempts = 90
        val delayMs = 2_000L
        var paid = false
        var attempt = 0
        while (!paid && attempt < attempts) {
            paid = ops.checkMintQuote(targetMint, flow.mintQuote.quote).isSettled()
            if (!paid) {
                delay(delayMs)
                attempt++
            }
        }
        if (!paid) {
            setStatus(TopUpStatus.Failed("Invoice not paid yet — you can finish it later from the pending quote banner"))
            return
        }
        setStatus(TopUpStatus.Working("Issuing ecash", 0.85f))
        ops.completeMintFromLightning(targetMint, flow.quoteEvent, moveSats)
    }

    private fun setStatus(status: TopUpStatus) {
        _uiState.update { it.copy(status = status) }
    }

    private fun nwcWallets() =
        accountViewModel
            ?.account
            ?.settings
            ?.nwcWallets
            ?.value
            .orEmpty()

    private fun walletUriFor(walletId: String) = nwcWallets().firstOrNull { it.id == walletId }?.uri

    /** The configured default NWC wallet's source, else the first Lightning source. */
    private fun defaultLightningSource(lightning: List<ReloadSource>): ReloadSource? {
        val defaultId =
            accountViewModel
                ?.account
                ?.settings
                ?.defaultNwcWallet()
                ?.id
        return lightning.firstOrNull { it is ReloadSource.LightningWallet && it.walletId == defaultId }
            ?: lightning.firstOrNull()
    }

    override fun onCleared() {
        // The pipeline runs on the BaseAccountViewModel scope, not this VM's, so it would
        // outlive the screen — cancel it when the screen goes away.
        job?.cancel()
    }
}

/** Progress of a standalone mint top-up. Mirrors [ReloadStatus] minus the zap step. */
@Immutable
sealed interface TopUpStatus {
    data object Configuring : TopUpStatus

    data class Working(
        val step: String,
        val progress: Float,
    ) : TopUpStatus

    /** External Lightning — the user must pay [invoice]; we keep polling. */
    data class AwaitingInvoice(
        val invoice: String,
        val sats: Long,
    ) : TopUpStatus

    data object Done : TopUpStatus

    data class Failed(
        val message: String,
    ) : TopUpStatus
}

@Immutable
data class TopUpUiState(
    val targetMint: String = "",
    val targetBalanceSats: Long = 0L,
    /** The amount to add to the target mint. */
    val amountSats: Long = 0L,
    val estFeeSats: Long = 0L,
    val sources: ImmutableList<ReloadSource> = persistentListOf(),
    val selectedSource: ReloadSource? = null,
    val status: TopUpStatus = TopUpStatus.Configuring,
)
