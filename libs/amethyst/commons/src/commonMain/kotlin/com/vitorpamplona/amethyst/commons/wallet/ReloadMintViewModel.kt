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
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip60Cashu.CashuWalletState
import com.vitorpamplona.amethyst.commons.viewmodels.BaseAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
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

/** Hand-off payload for the Top-up Mint screen — the pending nutzap to fund. */
data class ReloadMintRequest(
    val baseNote: Note,
    val amountSats: Long,
)

/** Where the top-up funds come from. */
@Immutable
sealed interface ReloadSource {
    /** Move existing cashu from another mint we hold (no new sats). */
    data class Mint(
        val mintUrl: String,
        val balanceSats: Long,
        val canCover: Boolean,
    ) : ReloadSource

    /** Mint fresh ecash by paying an invoice from a configured NWC wallet. */
    data class LightningWallet(
        val walletId: String,
        val name: String,
    ) : ReloadSource

    /** No NWC wallet configured — show an invoice to pay from any external wallet. */
    data object LightningExternal : ReloadSource
}

/** A mint we hold a balance at, for the balances list. */
@Immutable
data class MintBalance(
    val mintUrl: String,
    val balanceSats: Long,
)

/** Progress of the top-up + send. */
@Immutable
sealed interface ReloadStatus {
    data object Configuring : ReloadStatus

    data class Working(
        val step: String,
        val progress: Float,
    ) : ReloadStatus

    /** External Lightning — the user must pay [invoice]; we keep polling. */
    data class AwaitingInvoice(
        val invoice: String,
        val sats: Long,
    ) : ReloadStatus

    data object Done : ReloadStatus

    data class Failed(
        val message: String,
    ) : ReloadStatus
}

@Immutable
data class ReloadUiState(
    val recipient: String = "",
    val recipientName: String = "",
    /** The zap amount — fixed; this is what the recipient receives. */
    val sendSats: Long = 0L,
    /** The amount to add to the target mint — editable; defaults to the shortfall. */
    val topUpSats: Long = 0L,
    val balances: ImmutableList<MintBalance> = persistentListOf(),
    val targetOptions: ImmutableList<String> = persistentListOf(),
    val selectedTarget: String = "",
    /** Minimum top-up the send needs: max(0, sendSats - targetBalance). */
    val shortfallSats: Long = 0L,
    val estFeeSats: Long = 0L,
    val sources: ImmutableList<ReloadSource> = persistentListOf(),
    val selectedSource: ReloadSource? = null,
    val status: ReloadStatus = ReloadStatus.Configuring,
)

class ReloadMintViewModel : ViewModel() {
    private var accountViewModel: BaseAccountViewModel? = null
    private var baseNote: Note? = null
    private val state: CashuWalletState? get() = accountViewModel?.account?.cashuWalletState

    /** The in-flight pipeline, so a second tap can be rejected and the whole flow
     *  cancelled when the screen leaves (it runs on the long-lived BaseAccountViewModel
     *  scope, not this VM's). */
    private var job: Job? = null

    /** Set once the target mint has actually been topped up, so a retry after a
     *  later failure (e.g. the send) re-sends only — it must never move funds
     *  again. Without this, "Try again" re-ran the whole pipeline and double-spent. */
    private var toppedUp = false

    private var recipient: String? = null

    private val _uiState = MutableStateFlow(ReloadUiState())
    val uiState: StateFlow<ReloadUiState> = _uiState.asStateFlow()

    fun init(
        accountViewModel: BaseAccountViewModel,
        request: ReloadMintRequest,
    ) {
        if (this.accountViewModel != null) return // already initialized
        this.accountViewModel = accountViewModel
        this.baseNote = request.baseNote
        val st = state ?: return
        val recipient = request.baseNote.author?.pubkeyHex ?: return
        this.recipient = recipient
        _uiState.update {
            it.copy(
                recipient = recipient,
                recipientName =
                    request.baseNote.author
                        ?.toBestDisplayName()
                        .orEmpty(),
                sendSats = request.amountSats,
            )
        }

        // Wallet proofs/mints arrive from relays and can land *after* the screen
        // opens — a one-time snapshot would show only the first-loaded mint. Keep
        // balances/targets in sync as the wallet fills in, but project to just the
        // per-mint balance map and distinctUntilChanged so unrelated wallet churn
        // (an inbound nutzap redeem, a scrub, a token for some other mint) doesn't
        // re-run the whole rebuild on every global tokenEntries emission.
        viewModelScope.launch {
            combine(st.tokenEntries, st.mints) { _, _ -> st.peekMintBalances() }
                .distinctUntilChanged()
                .collect { rebuild(it) }
        }
    }

    /** Rebuild balances/targets/sources from the current wallet state, keeping the
     *  user's chosen target, top-up, source pick, and status intact. */
    private fun rebuild(balances: Map<String, Long>) {
        val recipient = recipient ?: return
        val st = state ?: return
        val targets = st.recipientSharedMints(recipient).sortedByDescending { balances[it] ?: 0L }
        _uiState.update { cur ->
            val target = cur.selectedTarget.takeIf { it in targets } ?: targets.firstOrNull().orEmpty()
            val shortfall = (cur.sendSats - (balances[target] ?: 0L)).coerceAtLeast(0L)
            recompute(
                cur.copy(
                    balances =
                        balances.entries
                            .sortedByDescending { it.value }
                            .map { MintBalance(it.key, it.value) }
                            .toImmutableList(),
                    targetOptions = targets.toImmutableList(),
                    // Default the top-up to the shortfall; bump it up if a wallet
                    // change made the current value too small to fund the send.
                    topUpSats = cur.topUpSats.coerceAtLeast(shortfall),
                ),
                target,
            )
        }
    }

    /** Recompute shortfall, the amount we'd mint, fees, sources, and a default source. */
    private fun recompute(
        base: ReloadUiState,
        target: String,
    ): ReloadUiState {
        val balances = base.balances.associate { it.mintUrl to it.balanceSats }
        val targetBalance = balances[target] ?: 0L
        val shortfall = (base.sendSats - targetBalance).coerceAtLeast(0L)
        val moveSats = moveSatsFor(base.topUpSats, shortfall)
        // Rough fee cushion for *enabling* a source — the real Lightning feeReserve
        // is only known once rebalance() fetches the melt quote, so this is a
        // heuristic (1%, min 2 sat). A source that clears the estimate but not the
        // real quote fails recoverably (Failed → pick another), it doesn't move funds.
        val estFee = if (moveSats <= 0L) 0L else (moveSats / 100L).coerceAtLeast(2L)
        val needFromSource = moveSats + estFee

        val mintSources =
            base.balances
                .filter { it.mintUrl != target }
                .map { ReloadSource.Mint(it.mintUrl, it.balanceSats, canCover = moveSats <= 0L || it.balanceSats >= needFromSource) }

        val lightningSources: List<ReloadSource> =
            nwcWallets()
                .map { ReloadSource.LightningWallet(it.id, it.name) }
                .ifEmpty { listOf(ReloadSource.LightningExternal) }
        val sources = mintSources + lightningSources

        // Keep the current pick if still valid across the recompute; else default to
        // the richest mint that can cover (no new sats), else the default LN wallet.
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
            selectedTarget = target,
            shortfallSats = shortfall,
            estFeeSats = estFee,
            sources = sources.toImmutableList(),
            selectedSource = selectedSource,
        )
    }

    fun selectTarget(mintUrl: String) {
        _uiState.update { s ->
            val targetBalance = s.balances.firstOrNull { it.mintUrl == mintUrl }?.balanceSats ?: 0L
            val shortfall = (s.sendSats - targetBalance).coerceAtLeast(0L)
            // A different target changes how much is needed — reset the top-up to it.
            recompute(s.copy(topUpSats = shortfall), mintUrl)
        }
    }

    fun selectSource(source: ReloadSource) {
        _uiState.update { it.copy(selectedSource = source) }
    }

    /** Set a custom top-up amount (defaults to the shortfall; can be increased). */
    fun setTopUp(sats: Long) {
        _uiState.update { recompute(it.copy(topUpSats = sats.coerceAtLeast(0L)), it.selectedTarget) }
    }

    fun confirm() {
        val vm = accountViewModel ?: return
        val note = baseNote ?: return
        val s = _uiState.value
        val source = s.selectedSource ?: return
        if (s.sendSats <= 0L) return

        // In-flight guard: only start from a resting state. Without it a double tap
        // (or the Failed-state Retry) launches a second pipeline — two mint quotes at
        // the target and two melts at the source, double-spending / double-minting.
        // Flip to Working synchronously so the second call bails.
        if (s.status !is ReloadStatus.Configuring && s.status !is ReloadStatus.Failed) return
        setStatus(ReloadStatus.Working("Starting", 0.05f))

        val moveSats = moveSatsFor(s.topUpSats, s.shortfallSats)

        job?.cancel()
        job =
            vm.launchSigner {
                try {
                    when {
                        // Already topped up (or the target already covers the send) —
                        // never move funds again on retry, just (re)send the zap.
                        toppedUp || moveSats <= 0L -> sendNutzapAndFinish(note, s.sendSats, s.selectedTarget)
                        source is ReloadSource.Mint -> rebalanceThenZap(source.mintUrl, s.selectedTarget, moveSats, note, s.sendSats)
                        source is ReloadSource.LightningWallet ->
                            reloadFromLightningThenZap(s.selectedTarget, moveSats, walletUriFor(source.walletId), note, s.sendSats)
                        source is ReloadSource.LightningExternal ->
                            reloadFromLightningThenZap(s.selectedTarget, moveSats, null, note, s.sendSats)
                    }
                } catch (e: CancellationException) {
                    throw e // screen left mid-flow — don't mask as a Failed state
                } catch (e: Exception) {
                    setStatus(ReloadStatus.Failed(describeMintError(e)))
                }
            }
    }

    private suspend fun rebalanceThenZap(
        sourceMint: String,
        targetMint: String,
        moveSats: Long,
        note: Note,
        sendSats: Long,
    ) {
        val st = state ?: return
        setStatus(ReloadStatus.Working("Moving funds", 0.1f))
        st.rebalance(
            sourceMintUrl = sourceMint,
            targetMintUrl = targetMint,
            sats = moveSats,
            onProgress = { p -> setStatus(ReloadStatus.Working("Moving funds", p.coerceIn(0.1f, 0.9f))) },
            // Checkpoint the instant funds leave the source — not after the whole
            // rebalance returns. A failure between the melt and the issue/send steps
            // must never let a retry melt again.
            onFundsMoved = { toppedUp = true },
        )
        awaitTargetFunded(targetMint, sendSats)
        sendNutzapAndFinish(note, sendSats, targetMint)
    }

    private suspend fun reloadFromLightningThenZap(
        targetMint: String,
        moveSats: Long,
        walletUri: Nip47WalletConnect.Nip47URINorm?,
        note: Note,
        sendSats: Long,
    ) {
        val vm = accountViewModel ?: return
        val st = state ?: return
        val ops = st.ops

        setStatus(ReloadStatus.Working("Requesting invoice", 0.1f))
        val flow = ops.startMintFromLightning(targetMint, moveSats)

        if (walletUri != null) {
            setStatus(ReloadStatus.Working("Paying from your wallet", 0.35f))
            // Fire-and-forget: the mint-quote poll below is the source of truth for
            // whether the payment actually landed.
            runCatching {
                vm.account.zaps.sendNwcRequestToWallet(walletUri, PayInvoiceMethod.create(flow.invoice)) { }
            }
        } else {
            // No NWC — surface the invoice for an external wallet and keep polling.
            setStatus(ReloadStatus.AwaitingInvoice(flow.invoice, moveSats))
        }

        // External payment can take a while; the poll runs on a job tied to the
        // screen (cancelled in onCleared), so leaving stops it instead of hammering
        // the mint for 3 minutes with nobody watching.
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
            setStatus(ReloadStatus.Failed("Invoice not paid yet — you can finish it later from the pending quote banner"))
            return
        }
        // The invoice is paid — money has left the wallet. Checkpoint now, before
        // issuing, so a failure in completeMintFromLightning never lets a retry
        // mint+pay a second time (the paid quote is resumable from the banner).
        toppedUp = true
        setStatus(ReloadStatus.Working("Issuing ecash", 0.85f))
        ops.completeMintFromLightning(targetMint, flow.quoteEvent, moveSats)
        awaitTargetFunded(targetMint, sendSats)
        sendNutzapAndFinish(note, sendSats, targetMint)
    }

    /**
     * Freshly moved/minted proofs reach [CashuWalletState] asynchronously (the
     * kind:7375 token event round-trips before [peekMintBalances] reflects it).
     * Give the target a moment to show the balance so the nutzap that follows
     * doesn't fail with "No proofs available" on the first try. Best-effort: if it
     * doesn't land in time the send still proceeds (and fails recoverably without
     * re-moving funds, thanks to the [toppedUp] checkpoint).
     */
    private suspend fun awaitTargetFunded(
        targetMint: String,
        sats: Long,
    ) {
        val st = state ?: return
        repeat(12) {
            if ((st.peekMintBalances()[targetMint] ?: 0L) >= sats) return
            delay(500)
        }
    }

    /**
     * Send the nutzap and only report [ReloadStatus.Done] once it actually succeeds.
     * Suspends on the real send (throwing on failure) instead of the fire-and-forget
     * BaseAccountViewModel.sendNutzap, so a send that fails after a successful top-up
     * surfaces as Failed here rather than silently stranding the just-moved funds
     * while the screen pops on a premature "done".
     */
    private suspend fun sendNutzapAndFinish(
        note: Note,
        sendSats: Long,
        targetMint: String,
    ) {
        val st = state ?: return
        val recipient = note.author?.pubkeyHex ?: throw IllegalStateException("Recipient has no pubkey")
        val zappedEvent = note.toEventHint<Event>() ?: throw IllegalStateException("Nothing to zap")
        setStatus(ReloadStatus.Working("Sending zap", 0.95f))
        st.sendNutzap(
            amountSats = sendSats,
            recipientPubKey = recipient,
            zappedEvent = zappedEvent,
            message = "",
            // Spend from the mint we just topped up, not whichever shared mint
            // happens to hold the most — otherwise the top-up sits idle.
            preferredMintUrl = targetMint.takeIf { it.isNotBlank() },
        )
        setStatus(ReloadStatus.Done)
    }

    private fun setStatus(status: ReloadStatus) {
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

    companion object {
        /**
         * Extra sats minted at the target beyond the bare shortfall, so the follow-up
         * nutzap's own swap fee doesn't leave the mint a hair short. Most mints charge
         * no input fee; this small cushion covers the ones that do.
         */
        private const val RELOAD_FEE_BUFFER_SATS = 2L

        /**
         * How much we actually mint/move: nothing when the target already covers the
         * send; otherwise the larger of the user's top-up and the shortfall plus the
         * swap-fee buffer, so the send can never come up short.
         */
        private fun moveSatsFor(
            topUp: Long,
            shortfall: Long,
        ): Long =
            when {
                topUp > 0L -> maxOf(topUp, shortfall + RELOAD_FEE_BUFFER_SATS)
                shortfall > 0L -> shortfall + RELOAD_FEE_BUFFER_SATS
                else -> 0L
            }
    }
}
