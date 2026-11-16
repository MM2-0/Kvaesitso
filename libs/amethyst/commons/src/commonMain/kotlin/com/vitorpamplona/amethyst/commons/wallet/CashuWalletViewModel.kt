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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.cashu.ops.CashuWalletOps
import com.vitorpamplona.amethyst.commons.cashu.ops.MintQuoteStarted
import com.vitorpamplona.amethyst.commons.cashu.ops.TokenEntry
import com.vitorpamplona.amethyst.commons.cashu.ops.describeMintError
import com.vitorpamplona.amethyst.commons.cashu.ops.describeRedeemError
import com.vitorpamplona.amethyst.commons.cashu.ops.requireP2pkRedeemable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.nip60Cashu.CashuWalletState
import com.vitorpamplona.amethyst.commons.viewmodels.BaseAccountViewModel
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MeltQuoteBolt11ResponseDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpException
import com.vitorpamplona.quartz.nip60Cashu.p2pk.anyP2pkLocked
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenB64Parser
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.startsWith
import com.vitorpamplona.quartz.utils.toLongValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class CashuWalletCreateState {
    data object Idle : CashuWalletCreateState()

    data object Saving : CashuWalletCreateState()

    data class Error(
        val message: String,
    ) : CashuWalletCreateState()
}

sealed class CashuMintFlowState {
    data object Idle : CashuMintFlowState()

    data object Requesting : CashuMintFlowState()

    data class AwaitingPayment(
        val flow: MintQuoteStarted,
        val mintUrl: String,
        val amountSats: Long,
        /**
         * True while a background poll is asking the mint whether the
         * invoice has been paid. The receive dialog keeps the invoice on
         * screen and shows a small inline indicator instead of swapping to
         * the [Completing] body, so the routine 3s check no longer flickers
         * the whole dialog.
         */
        val checking: Boolean = false,
    ) : CashuMintFlowState()

    data object Completing : CashuMintFlowState()

    data class Completed(
        val amountSats: Long,
    ) : CashuMintFlowState()

    data class Error(
        val message: String,
    ) : CashuMintFlowState()
}

sealed class CashuMeltFlowState {
    data object Idle : CashuMeltFlowState()

    data object Quoting : CashuMeltFlowState()

    /** Mint returned a quote — show amount + fee, await user confirmation. */
    data class Quoted(
        val mintUrl: String,
        val invoice: String,
        val quote: MeltQuoteBolt11ResponseDto,
    ) : CashuMeltFlowState()

    data object Paying : CashuMeltFlowState()

    data class Completed(
        val paidAmount: Long,
        val fees: Long,
        val preimage: String?,
    ) : CashuMeltFlowState()

    data class Error(
        val message: String,
    ) : CashuMeltFlowState()
}

sealed class CashuSendTokenFlowState {
    data object Idle : CashuSendTokenFlowState()

    data object Building : CashuSendTokenFlowState()

    data class Ready(
        val token: String,
        val amount: Long,
    ) : CashuSendTokenFlowState()

    data class Error(
        val message: String,
    ) : CashuSendTokenFlowState()
}

sealed class CashuRebalanceFlowState {
    data object Idle : CashuRebalanceFlowState()

    /** Funds are moving — progress in [0f, 1f] from CashuWalletState.rebalance. */
    data class Working(
        val progress: Float,
    ) : CashuRebalanceFlowState()

    data class Completed(
        val movedSats: Long,
        val targetMintUrl: String,
    ) : CashuRebalanceFlowState()

    data class Error(
        val message: String,
    ) : CashuRebalanceFlowState()
}

sealed class CashuRedeemFlowState {
    data object Idle : CashuRedeemFlowState()

    data object Redeeming : CashuRedeemFlowState()

    data class Completed(
        val amount: Long,
    ) : CashuRedeemFlowState()

    data class Error(
        val message: String,
    ) : CashuRedeemFlowState()
}

/**
 * Presenter ViewModel for the NIP-60 Cashu wallet screens.
 *
 * All persistent state lives on [Account.cashuWalletState]; this VM only
 * holds the transient per-flow UI state (mint quote in progress, melt
 * confirmation pending, redeem result). State flows are forwarded directly
 * from [CashuWalletState] so they survive screen lifecycle.
 */
class CashuWalletViewModel : ViewModel() {
    private var account: Account? = null
    private var accountViewModel: BaseAccountViewModel? = null
    private val state: CashuWalletState get() = account!!.cashuWalletState
    private val ops: CashuWalletOps get() = state.ops

    val walletEvent get() = state.walletEvent
    val mints get() = state.mints
    val displayMints get() = state.displayMints
    val unconfiguredMintBalances get() = state.unconfiguredMintBalances
    val balanceSats get() = state.balanceSats
    val mintBalances get() = state.mintBalances
    val tokenEntries: StateFlow<List<TokenEntry>> get() = state.tokenEntries
    val history get() = state.history
    val pendingQuotes get() = state.pendingQuotes
    val discovering get() = state.discovering
    val ownRecommendations get() = state.ownRecommendations

    private val _createState = MutableStateFlow<CashuWalletCreateState>(CashuWalletCreateState.Idle)
    val createState = _createState.asStateFlow()

    private val _mintState = MutableStateFlow<CashuMintFlowState>(CashuMintFlowState.Idle)
    val mintState = _mintState.asStateFlow()

    private val _meltState = MutableStateFlow<CashuMeltFlowState>(CashuMeltFlowState.Idle)
    val meltState = _meltState.asStateFlow()

    private val _sendTokenState = MutableStateFlow<CashuSendTokenFlowState>(CashuSendTokenFlowState.Idle)
    val sendTokenState = _sendTokenState.asStateFlow()

    private val _rebalanceState = MutableStateFlow<CashuRebalanceFlowState>(CashuRebalanceFlowState.Idle)
    val rebalanceState = _rebalanceState.asStateFlow()

    private val _redeemState = MutableStateFlow<CashuRedeemFlowState>(CashuRedeemFlowState.Idle)
    val redeemState = _redeemState.asStateFlow()

    private val _mintPingState = MutableStateFlow<MintPingState>(MintPingState.Idle)
    val mintPingState = _mintPingState.asStateFlow()

    fun init(accountViewModel: BaseAccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        // No subscription / observer / refresh here — CashuWalletState owns
        // that lifecycle and is alive for the whole login session.
    }

    /**
     * Bring the wallet's view of its own money up to date, in the two ways it
     * can be behind.
     *
     * First re-page the proof set off the relays: the live subscription takes
     * whatever one uncapped REQ returns, so proofs older than the relay's cap
     * are simply absent, and the balance quietly reads low (see
     * [CashuWalletState.resyncProofsFromRelays]). `force` because the user
     * opening the wallet is a direct request for a current number, and the
     * startup walk may have run before the relay list was known.
     *
     * Then reconcile every mint we hold tokens at against its NUT-07
     * `/checkstate` — not just the mint a spend targets — so a balance
     * auto-redeemed from a mint we never configured (e.g. a nutzap on a mint
     * not in our kind:10019) still gets its stale proofs swept. Safe to call
     * repeatedly; no-ops when nothing is stale or the wallet hasn't started.
     */
    fun refresh() {
        val vm = accountViewModel ?: return
        vm.launchSigner {
            try {
                state.resyncProofsFromRelays(force = true)
            } catch (e: Exception) {
                Log.w("CashuWallet", "wallet proof re-page failed", e)
            }
            try {
                state.syncAllMints()
            } catch (e: Exception) {
                Log.w("CashuWallet", "wallet refresh sync failed", e)
            }
        }
    }

    /** Verify a mint URL is reachable + speaks Cashu v1. */
    fun pingMint(url: String) {
        val vm = accountViewModel ?: return
        if (url.isBlank()) return
        _mintPingState.value = MintPingState.Pinging
        vm.launchSigner {
            try {
                val name = ops.pingMint(url.trim().trimEnd('/'))
                _mintPingState.value = MintPingState.Ok(name)
            } catch (e: Exception) {
                _mintPingState.value = MintPingState.Failed(describeMintError(e))
            }
        }
    }

    fun resetMintPing() {
        _mintPingState.value = MintPingState.Idle
    }

    /**
     * Per-mint reachability results for mints already in the wallet's list,
     * keyed by normalized URL. Distinct from [mintPingState] (which backs the
     * single "verify the URL I'm typing" button) so a user can re-check any
     * already-added mint without clobbering the input-field result, and each
     * row reflects its own status independently.
     */
    private val _mintVerifications = MutableStateFlow<Map<String, MintPingState>>(emptyMap())
    val mintVerifications: StateFlow<Map<String, MintPingState>> = _mintVerifications.asStateFlow()

    /** Ping an already-added mint and record the result under its normalized URL. */
    fun verifyMint(url: String) {
        val vm = accountViewModel ?: return
        val key = url.trim().trimEnd('/')
        if (key.isBlank()) return
        _mintVerifications.update { it + (key to MintPingState.Pinging) }
        vm.launchSigner {
            val result =
                try {
                    MintPingState.Ok(ops.pingMint(key))
                } catch (e: Exception) {
                    MintPingState.Failed(describeMintError(e))
                }
            _mintVerifications.update { it + (key to result) }
        }
    }

    /**
     * Reference to the account-scoped NIP-87 mint directory state. The picker
     * UI calls .open()/.close() on entry/exit and observes .entries directly.
     */
    val directory get() = account!!.cashuMintDirectoryState

    /** Re-subscribes the directory as the account's relay flows settle. */
    private var directoryRelayJob: Job? = null

    /**
     * Open the NIP-87 mint directory subscription.
     *
     * Subscribes **reactively** to the account's relay flows rather than to a
     * one-shot snapshot. A freshly-restored account (notably the find-or-create
     * wizard's "create a new wallet" path) may still be loading its relay lists
     * when the picker opens; a snapshot would capture an empty set, hit
     * [CashuMintDirectoryState]'s empty-relay early-out, and never retry —
     * leaving the mint suggestions blank even though the edit-mints screen shows
     * them later. Collecting the flow re-subscribes the moment relays arrive.
     *
     * Relay set = the union of the user's OUTBOX relays (where they'd publish
     * their own recommendations) and their INDEXER relays, which aggregate the
     * broad NIP-87 mint announcements/recommendations and fall back to a curated
     * default set — so the picker is populated even for a brand-new user who has
     * no wallet and no recommendations of their own yet.
     */
    fun openMintDirectory() {
        val acc = account ?: return
        directoryRelayJob?.cancel()
        directoryRelayJob =
            viewModelScope.launch {
                combine(acc.outboxRelays.flow, acc.indexerRelayList.flow) { outbox, indexer ->
                    outbox + indexer
                }.collect { relays ->
                    // open() is idempotent per opener (a Set add), so repeated
                    // calls just re-point the shared subscription at the new
                    // relay set; a single close() still tears it down.
                    directory.open(this@CashuWalletViewModel, relays)
                }
            }
    }

    fun closeMintDirectory() {
        directoryRelayJob?.cancel()
        directoryRelayJob = null
        directory.close(this)
    }

    /** Publish a NIP-87 kind:38000 recommendation for [mintUrl]. */
    fun recommendMint(
        mintUrl: String,
        review: String = "",
    ) {
        val vm = accountViewModel ?: return
        vm.launchSigner {
            try {
                val dTag = directory.lookup(mintUrl)?.announcement?.dTag()
                ops.recommendMint(mintUrl = mintUrl, mintAnnouncementDTag = dTag, review = review)
            } catch (e: Exception) {
                // Best-effort — the UI doesn't need to block on the result.
                // Pass the throwable through so support reports keep the
                // stack trace; the lambda-only Log.w overload swallows it.
                Log.w("CashuWallet", "recommendMint($mintUrl) failed: ${describeMintError(e)}", e)
            }
        }
    }

    /**
     * NIP-09-retract a previously-published mint recommendation. The local
     * `ownRecommendations` list updates reactively once the delete event
     * round-trips through LocalCache.
     */
    fun deleteRecommendation(event: MintRecommendationEvent) {
        val vm = accountViewModel ?: return
        vm.launchSigner {
            runCatching { ops.deleteRecommendation(event) }
                .onFailure {
                    Log.w("CashuWallet", "deleteRecommendation failed: ${describeMintError(it)}", it)
                }
        }
    }

    /**
     * Stop receiving NIP-61 nutzaps — replaces kind:10019 with an empty event
     * and NIP-09 deletes it. The wallet itself stays put. [onDone] fires after
     * the publish attempt (success or logged failure) so the UI can dismiss.
     */
    fun stopNutzaps(onDone: () -> Unit = {}) {
        val vm = accountViewModel ?: return
        vm.launchSigner {
            runCatching { state.stopNutzaps() }
                .onFailure { Log.w("CashuWallet", "stopNutzaps failed: ${describeMintError(it)}", it) }
            onDone()
        }
    }

    /**
     * Delete the whole Cashu wallet (kind:17375) and stop nutzaps in one go.
     * Destructive — the caller must confirm with the user first. [onDone] fires
     * after the publish attempt so the screen can navigate away.
     */
    fun deleteWallet(onDone: () -> Unit = {}) {
        val vm = accountViewModel ?: return
        vm.launchSigner {
            runCatching { state.deleteWallet() }
                .onFailure { Log.w("CashuWallet", "deleteWallet failed: ${describeMintError(it)}", it) }
            onDone()
        }
    }

    /**
     * Rotate the wallet's NIP-61 P2PK key (Danger Zone). [manualPrivkey] null
     * generates a fresh key; a non-blank hex string imports it. [onResult]
     * reports null on success or an error message on failure so the dialog can
     * surface a bad key paste instead of silently dismissing.
     */
    fun recreateNutzapKey(
        manualPrivkey: String? = null,
        onResult: (String?) -> Unit = {},
    ) {
        val vm = accountViewModel ?: return
        vm.launchSigner {
            val error =
                try {
                    state.recreateNutzapKey(manualPrivkey)
                    null
                } catch (e: Exception) {
                    Log.w("CashuWallet", "recreateNutzapKey failed: ${describeMintError(e)}", e)
                    describeMintError(e)
                }
            onResult(error)
        }
    }

    // -------- NUT-09 restore --------

    sealed class RestoreFlowState {
        data object Idle : RestoreFlowState()

        data object Running : RestoreFlowState()

        data class Completed(
            val totalSatsRecovered: Long,
            val proofsRecovered: Int,
            val mintsScanned: Int,
        ) : RestoreFlowState()

        data class Error(
            val message: String,
        ) : RestoreFlowState()
    }

    private val _restoreState = MutableStateFlow<RestoreFlowState>(RestoreFlowState.Idle)
    val restoreState = _restoreState.asStateFlow()

    /**
     * NUT-09 wallet restore — scans every mint we know of for proofs the
     * user previously minted but whose kind:7375 events have been lost.
     * Recovered unspent proofs are republished as fresh kind:7375 + kind:7376
     * IN history rows.
     *
     * Scans [CashuWalletState.displayMints] (configured kind:17375 mints plus
     * any mint we currently hold tokens at), not just the configured list —
     * so a mint dropped from the wallet config while it still holds tokens,
     * or one a nutzap was auto-redeemed on, is still recovered rather than
     * silently skipped.
     *
     * Best-effort across mints: a failure on one mint logs and moves to
     * the next. The total reported in [RestoreFlowState.Completed]
     * aggregates across all mints scanned.
     */
    fun restoreFromAllMints() {
        val vm = accountViewModel ?: return
        if (_restoreState.value is RestoreFlowState.Running) return
        _restoreState.value = RestoreFlowState.Running
        vm.launchSigner {
            try {
                val mintsToScan = state.displayMints.value
                var totalSats = 0L
                var totalProofs = 0
                for (mint in mintsToScan) {
                    runCatching { state.restoreFromMint(mint) }
                        .onSuccess { outcome ->
                            if (outcome != null) {
                                totalSats += outcome.amountRecoveredSats
                                totalProofs += outcome.proofsRecovered
                            }
                        }.onFailure {
                            Log.w("CashuWallet", "restoreFromMint($mint) failed: ${describeMintError(it)}", it)
                        }
                }
                _restoreState.value =
                    RestoreFlowState.Completed(
                        totalSatsRecovered = totalSats,
                        proofsRecovered = totalProofs,
                        mintsScanned = mintsToScan.size,
                    )
            } catch (e: Exception) {
                _restoreState.value = RestoreFlowState.Error(describeMintError(e))
            }
        }
    }

    fun resetRestoreState() {
        _restoreState.value = RestoreFlowState.Idle
    }

    /**
     * Serialises [publishMints] so two near-simultaneous mint adds can't both
     * reach [CashuWalletOps.publishWalletEvents] before the first has cached
     * its key. Without it, the second add — firing before the freshly created
     * kind:17375 round-trips back into `_walletEvent` — would read a null key
     * and generate a *second* one, rotating the P2PK key and orphaning any
     * inbound nutzaps locked to the first.
     */
    private val publishMutex = Mutex()

    /**
     * The P2PK key in use for the wallet currently being edited on screen.
     * Set on the first successful publish (inside [publishMutex]); reused by
     * every later add/remove so the nutzap key stays put across changes.
     */
    private var sessionPrivkeyHex: String? = null

    /**
     * Publish (or re-publish) the wallet's kind:17375 + kind:10019 with the
     * given mint list. The Add/Edit screen has no Save button — the wallet is
     * created the instant the first mint is added and kept in sync on every
     * subsequent add/remove, so this runs on each list change.
     *
     * Key handling, in priority order:
     *   1. [sessionPrivkeyHex] — the key from a publish we already did this
     *      session (covers rapid successive adds before the event round-trips).
     *   2. [CashuWalletState.exportP2pkPrivkeyHex] — the existing wallet's key
     *      when editing a wallet that was already on cache when the screen
     *      opened.
     *   3. null → [CashuWalletOps.publishWalletEvents] generates a fresh key
     *      (a genuinely new wallet).
     *
     * An empty list is a no-op: a NIP-60 wallet must advertise at least one
     * mint, so removing the last mint leaves the previously published config
     * untouched rather than publishing an empty, unusable wallet.
     */
    fun publishMints(mints: List<String>) {
        val vm = accountViewModel ?: return
        val acc = account ?: return
        if (mints.isEmpty()) return

        _createState.value = CashuWalletCreateState.Saving
        vm.launchSigner {
            publishMutex.withLock {
                try {
                    val privkey = sessionPrivkeyHex ?: state.exportP2pkPrivkeyHex()
                    val created =
                        ops.publishWalletEvents(
                            mints = mints,
                            p2pkPrivkeyHex = privkey,
                            // Advertise our NIP-65 inbox relays as the nutzap relays,
                            // so senders publish kind:9321 where we read incoming
                            // events (NIP-65 outbox model). The kind:10019 default
                            // copies the inbox relay list, not outbox; the wallet
                            // still subscribes to a wider inbox + DM + tags set.
                            nutzapRelays =
                                acc.notificationRelays.flow.value
                                    .toList(),
                        )
                    sessionPrivkeyHex = created.p2pkPrivkeyHex
                    _createState.value = CashuWalletCreateState.Idle
                } catch (e: Exception) {
                    _createState.value = CashuWalletCreateState.Error(describeMintError(e))
                }
            }
        }
    }

    fun resetCreateState() {
        _createState.value = CashuWalletCreateState.Idle
    }

    // -------- Mint from LN --------

    fun startMintFromLightning(
        mintUrl: String,
        amountSats: Long,
    ) {
        val vm = accountViewModel ?: return
        if (amountSats <= 0) {
            _mintState.value = CashuMintFlowState.Error("Amount must be positive")
            return
        }
        if (mintUrl.isBlank()) {
            _mintState.value = CashuMintFlowState.Error("Pick a mint")
            return
        }
        _mintState.value = CashuMintFlowState.Requesting
        vm.launchSigner {
            try {
                val flow = ops.startMintFromLightning(mintUrl, amountSats)
                _mintState.value = CashuMintFlowState.AwaitingPayment(flow, mintUrl, amountSats)
            } catch (e: Exception) {
                _mintState.value = CashuMintFlowState.Error(describeMintError(e))
            }
        }
    }

    /**
     * Polls the mint to see if the user's bolt11 has been paid yet. If so,
     * completes the mint flow (proof issuance + kind:7375/7376 publish).
     *
     * If the mint replies "quote not found" the local kind:7374 is NIP-09
     * deleted — the quote is either expired or already issued in a prior
     * session whose cleanup didn't land, so keeping it would just leave
     * the pending banner stuck pointing at dead state.
     */
    fun checkAndCompleteMint() {
        val vm = accountViewModel ?: return
        val current = _mintState.value as? CashuMintFlowState.AwaitingPayment ?: return
        // Already polling — don't stack a second request.
        if (current.checking) return
        // Atomic flip to checking=true so a concurrent poll (the receive
        // dialog fires this every 3s) can't both reach
        // completeMintFromLightning. Without the gate, poll 1 consumed
        // the mint quote and poll 2 hit "outputs already signed". We stay
        // in AwaitingPayment so the invoice keeps showing — the dialog
        // renders an inline "checking the mint" indicator off `checking`
        // instead of swapping its whole body, which used to flicker.
        if (!_mintState.compareAndSet(current, current.copy(checking = true))) return
        vm.launchSigner {
            try {
                val status = ops.checkMintQuote(current.mintUrl, current.flow.mintQuote.quote)
                val paid = status.isSettled()
                if (!paid) {
                    // Clear the checking flag so the polling LaunchedEffect
                    // picks up again on the next tick, invoice still on screen.
                    _mintState.value = current
                    return@launchSigner
                }
                // Payment confirmed — now it's worth showing the full
                // "issuing proofs" body while we finalize the mint.
                _mintState.value = CashuMintFlowState.Completing
                ops.completeMintFromLightning(current.mintUrl, current.flow.quoteEvent, current.amountSats)
                _mintState.value = CashuMintFlowState.Completed(current.amountSats)
            } catch (e: Exception) {
                if (isQuoteGoneError(e)) {
                    runCatching { ops.cancelMintQuote(current.flow.quoteEvent) }
                    _mintState.value = CashuMintFlowState.Idle
                } else {
                    _mintState.value = CashuMintFlowState.Error(describeMintError(e))
                }
            }
        }
    }

    /**
     * Resume an unfulfilled kind:7374 quote from a previous session.
     *
     * If the mint reports the quote PAID/ISSUED, complete the mint inline
     * (proof issuance + kind:7375 + history + kind:5 of the quote) — the
     * user already paid outside Amethyst and would otherwise see the
     * banner forever. If the mint says "quote not found" (expired, or
     * issued earlier whose kind:5 didn't land), NIP-09 delete the local
     * kind:7374 so the banner clears.
     */
    fun resumeMintQuote(quoteEvent: com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent) {
        val vm = accountViewModel ?: return
        val mintUrl =
            quoteEvent.mint() ?: run {
                _mintState.value = CashuMintFlowState.Error("Quote has no mint tag")
                return
            }
        // Double-tap on the pending banner would otherwise race two
        // completeMintFromLightning calls against the mint — same hazard
        // as the receive-dialog poller. Only proceed when there's no
        // mint flow already in progress.
        if (!_mintState.compareAndSet(CashuMintFlowState.Idle, CashuMintFlowState.Completing)) return
        vm.launchSigner {
            try {
                val quoteId = quoteEvent.quoteId(account!!.signer)
                val status = ops.checkMintQuote(mintUrl, quoteId)
                // Recover amount from the bolt11 invoice — kind:7374 doesn't
                // carry the sat amount, and the mint quote status DTO doesn't
                // echo it either. The bolt11 itself does, so parsing it back
                // is the only reliable way to drive completeMintFromLightning
                // on resume.
                val amountSats =
                    runCatching { LnInvoiceUtil.getAmountInSats(status.request).toLongValue() }
                        .getOrElse { 0L }
                val paid = status.isSettled()
                if (paid && amountSats > 0) {
                    ops.completeMintFromLightning(mintUrl, quoteEvent, amountSats)
                    _mintState.value = CashuMintFlowState.Completed(amountSats)
                } else {
                    _mintState.value =
                        CashuMintFlowState.AwaitingPayment(
                            flow = MintQuoteStarted(quoteEvent = quoteEvent, mintQuote = status, invoice = status.request),
                            mintUrl = mintUrl,
                            amountSats = amountSats,
                        )
                }
            } catch (e: Exception) {
                if (isQuoteGoneError(e)) {
                    runCatching { ops.cancelMintQuote(quoteEvent) }
                    _mintState.value = CashuMintFlowState.Idle
                } else {
                    _mintState.value = CashuMintFlowState.Error(describeMintError(e))
                }
            }
        }
    }

    /**
     * True when the mint reports the quote no longer exists — either
     * because the bolt11 expired before payment or because the wallet
     * issued the proofs in an earlier session and the local kind:5
     * cleanup didn't land. Either way the resume path should drop the
     * local kind:7374 instead of leaving it in the pending banner.
     */
    private fun isQuoteGoneError(e: Throwable): Boolean {
        if (e !is MintHttpException) return false
        if (e.httpStatus == 404) return true
        // Most cashu mints return 400 with `detail: "quote not found"` —
        // match by substring to cover phrasing variants ("quote not
        // found", "Quote does not exist", etc.).
        val detail = e.detail?.lowercase() ?: return false
        return "quote" in detail && ("not found" in detail || "does not exist" in detail || "unknown" in detail)
    }

    fun resetMintState() {
        _mintState.value = CashuMintFlowState.Idle
    }

    /**
     * User-initiated discard of the pending mint quote currently showing in
     * the Receive dialog. NIP-09 deletes the kind:7374 so the pending-quote
     * banner doesn't keep re-surfacing it, then drops back to Idle.
     *
     * Works from both the AwaitingPayment state (just-requested invoice)
     * and the Error state (failed completion). No-op otherwise.
     */
    fun discardMintQuote() {
        val vm = accountViewModel ?: return
        val quoteEvent =
            when (val s = _mintState.value) {
                is CashuMintFlowState.AwaitingPayment -> s.flow.quoteEvent
                else -> null
            } ?: run {
                _mintState.value = CashuMintFlowState.Idle
                return
            }
        vm.launchSigner {
            runCatching { ops.cancelMintQuote(quoteEvent) }
                .onFailure {
                    Log.w("CashuWallet", "cancelMintQuote failed: ${describeMintError(it)}", it)
                }
            _mintState.value = CashuMintFlowState.Idle
        }
    }

    // -------- Melt to LN --------

    /**
     * Phase 1 of melt — request a quote so the user can see amount + fees
     * before committing. Use [confirmMelt] to actually pay.
     */
    fun startMelt(
        mintUrl: String,
        invoice: String,
    ) {
        val vm = accountViewModel ?: return
        if (invoice.isBlank()) {
            _meltState.value = CashuMeltFlowState.Error("Paste a bolt11 invoice")
            return
        }
        if (mintUrl.isBlank()) {
            _meltState.value = CashuMeltFlowState.Error("Pick a mint")
            return
        }
        val available = tokenEntries.value.filter { it.content.mint == mintUrl }
        if (available.isEmpty()) {
            _meltState.value = CashuMeltFlowState.Error("No proofs available at $mintUrl")
            return
        }

        _meltState.value = CashuMeltFlowState.Quoting
        vm.launchSigner {
            try {
                val quote = ops.requestMeltQuote(mintUrl, invoice.trim())
                val balance = available.sumOf { it.content.totalAmount() }
                if (balance < quote.amount + quote.feeReserve) {
                    _meltState.value =
                        CashuMeltFlowState.Error(
                            "Need ${quote.amount + quote.feeReserve} sat (incl. fees) but only have $balance",
                        )
                    return@launchSigner
                }
                _meltState.value = CashuMeltFlowState.Quoted(mintUrl, invoice.trim(), quote)
            } catch (e: Exception) {
                _meltState.value = CashuMeltFlowState.Error(describeMintError(e))
            }
        }
    }

    /** Phase 2 of melt — user confirmed the fee, pay the invoice now. */
    fun confirmMelt() {
        val vm = accountViewModel ?: return
        val quoted = _meltState.value as? CashuMeltFlowState.Quoted ?: return

        _meltState.value = CashuMeltFlowState.Paying
        vm.launchSigner {
            try {
                val result = state.meltToLightning(quoted.mintUrl, quoted.quote)
                _meltState.value = CashuMeltFlowState.Completed(result.paidAmount, result.fees, result.preimage)
            } catch (e: Exception) {
                _meltState.value = CashuMeltFlowState.Error(describeMintError(e))
            }
        }
    }

    fun resetMeltState() {
        _meltState.value = CashuMeltFlowState.Idle
    }

    // -------- Send-as-token --------

    fun sendAsToken(
        mintUrl: String,
        amountSats: Long,
        memo: String? = null,
    ) {
        val vm = accountViewModel ?: return
        // sendAsToken on the state runs a NUT-07 scrub + balance check
        // before invoking the mint swap, so the viewmodel only needs to
        // surface failures.
        _sendTokenState.value = CashuSendTokenFlowState.Building
        vm.launchSigner {
            try {
                val result = state.sendAsToken(mintUrl, amountSats, memo)
                _sendTokenState.value = CashuSendTokenFlowState.Ready(result.cashuToken, result.amount)
            } catch (e: Exception) {
                _sendTokenState.value = CashuSendTokenFlowState.Error(describeMintError(e))
            }
        }
    }

    fun resetSendTokenState() {
        _sendTokenState.value = CashuSendTokenFlowState.Idle
    }

    // -------- Move coins between mints (rebalance) --------

    /**
     * Move [sats] from [sourceMintUrl] to [targetMintUrl] with no new
     * Lightning sats entering the wallet — the evacuation path for coins
     * sitting at a mint the user doesn't trust. Backed by the tested
     * [CashuWalletState.rebalance], which fetches its own melt quote and
     * refuses to spend if the source can't cover amount + fees.
     */
    fun rebalanceOut(
        sourceMintUrl: String,
        targetMintUrl: String,
        sats: Long,
    ) {
        val vm = accountViewModel ?: return
        if (sats <= 0) {
            _rebalanceState.value = CashuRebalanceFlowState.Error("Amount must be positive")
            return
        }
        if (sourceMintUrl == targetMintUrl) {
            _rebalanceState.value = CashuRebalanceFlowState.Error("Pick a different destination mint")
            return
        }
        _rebalanceState.value = CashuRebalanceFlowState.Working(0f)
        vm.launchSigner {
            try {
                val result =
                    state.rebalance(
                        sourceMintUrl = sourceMintUrl,
                        targetMintUrl = targetMintUrl,
                        sats = sats,
                        onProgress = { p -> _rebalanceState.value = CashuRebalanceFlowState.Working(p) },
                    )
                _rebalanceState.value =
                    CashuRebalanceFlowState.Completed(result.movedSats, targetMintUrl)
            } catch (e: Exception) {
                _rebalanceState.value = CashuRebalanceFlowState.Error(describeMintError(e))
            }
        }
    }

    fun resetRebalanceState() {
        _rebalanceState.value = CashuRebalanceFlowState.Idle
    }

    // -------- Redeem inbound cashuB / cashuA --------

    fun redeemToken(rawToken: String) {
        val vm = accountViewModel ?: return
        val trimmed = rawToken.trim()
        if (trimmed.isEmpty()) {
            _redeemState.value = CashuRedeemFlowState.Error("Paste a Cashu token")
            return
        }

        if (!trimmed.startsWith(CashuTokenB64Parser.CashuAPrefix) && !trimmed.startsWith(CashuTokenB64Parser.CashuBPrefix)) {
            _redeemState.value =
                CashuRedeemFlowState.Error("Not a Cashu token (must start with cashuA or cashuB)")
            return
        }

        // A single token string can carry proofs from more than one mint;
        // redeem every group rather than just the first.
        val parsedTokens =
            CashuTokenB64Parser.parse(trimmed)?.takeIf { it.isNotEmpty() }
                ?: run {
                    _redeemState.value = CashuRedeemFlowState.Error("Could not parse token")
                    return
                }

        val unknownMint = parsedTokens.firstOrNull { it.mint !in mints.value }?.mint
        if (unknownMint != null) {
            _redeemState.value =
                CashuRedeemFlowState.Error("Token mint ($unknownMint) is not in your wallet. Add it first.")
            return
        }

        _redeemState.value = CashuRedeemFlowState.Redeeming
        vm.launchSigner {
            try {
                // Keys that can unlock a P2PK-locked token: our wallet key, and
                // — for a local nsec login only — the identity key (some senders,
                // e.g. Bey Wallet, P2PK-lock ecash straight to the recipient npub).
                // Only gathered when a proof is actually locked: redeemSigningKeys()
                // decrypts the kind:17375 privkey, which is a signer round-trip on
                // a bunker/external signer we shouldn't pay for a plain token.
                val (walletKey, identityKey) =
                    if (parsedTokens.any { it.proofs.anyP2pkLocked() }) {
                        state.redeemSigningKeys()
                    } else {
                        null to null
                    }
                // All-or-nothing: reject an unsignable P2PK lock before redeeming
                // any group, so a multi-mint token never ends up half-redeemed.
                requireP2pkRedeemable(parsedTokens.flatMap { it.proofs }, walletKey, identityKey)
                val total =
                    parsedTokens.sumOf {
                        ops
                            .redeemToken(
                                cashuToken = trimmed,
                                proofs = it.proofs,
                                mintUrl = it.mint,
                                walletP2pkPrivkeyHex = walletKey,
                                identityPrivkeyHex = identityKey,
                            ).amount
                    }
                _redeemState.value = CashuRedeemFlowState.Completed(total)
            } catch (e: Exception) {
                _redeemState.value =
                    CashuRedeemFlowState.Error(describeRedeemError(e, account!!.signer.pubKey))
            }
        }
    }

    fun resetRedeemState() {
        _redeemState.value = CashuRedeemFlowState.Idle
    }
}

sealed class MintPingState {
    data object Idle : MintPingState()

    data object Pinging : MintPingState()

    data class Ok(
        val name: String?,
    ) : MintPingState()

    data class Failed(
        val message: String,
    ) : MintPingState()
}
