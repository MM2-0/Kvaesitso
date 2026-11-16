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
package com.vitorpamplona.amethyst.commons.wallet.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.cashu.CashuWalletDiscovery
import com.vitorpamplona.amethyst.commons.cashu.ops.describeMintError
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.nip60Cashu.CashuWalletState
import com.vitorpamplona.amethyst.commons.viewmodels.BaseAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A discovered NIP-60 wallet plus the result of verifying it: its decrypted
 * mint list, whether we could decrypt it at all ([valid]), and a read-only
 * probe of how much ecash is recoverable from its NUT-13 seed per mint.
 */
data class FoundWallet(
    val event: CashuWalletEvent,
    val createdAt: Long,
    val mints: List<String>,
    val valid: Boolean,
    val privkeyHex: String?,
    val recoverableByMint: Map<String, Long>,
    val totalRecoverableSats: Long,
)

/** Top-level wizard flow. */
sealed class WizardState {
    data object Idle : WizardState()

    data class Crawling(
        val relaysCompleted: Int,
        val totalRelays: Int,
    ) : WizardState()

    /** Found candidate wallets; decrypting + probing each mint for balances. */
    data object Analyzing : WizardState()

    /** No (decryptable) wallet anywhere — safe to create a fresh one. */
    data object NoWallet : WizardState()

    /** Exactly one wallet found. */
    data class Single(
        val wallet: FoundWallet,
    ) : WizardState()

    /** Several wallets — [main] (newest) becomes canonical, [others] may hold funds. */
    data class Multiple(
        val main: FoundWallet,
        val others: List<FoundWallet>,
    ) : WizardState()

    data class Error(
        val message: String,
    ) : WizardState()
}

/** State of adopting the chosen wallet as the account's main wallet. */
sealed class AdoptState {
    data object Idle : AdoptState()

    data object Working : AdoptState()

    data class Done(
        val recoveredSats: Long,
    ) : AdoptState()

    data class Error(
        val message: String,
    ) : AdoptState()
}

/** State of recovering funds from a single old/duplicate wallet. */
sealed class RecoveryState {
    data object Working : RecoveryState()

    data class Done(
        val recoveredSats: Long,
    ) : RecoveryState()

    data class Error(
        val message: String,
    ) : RecoveryState()
}

/**
 * Drives the "find or create my Cashu wallet" wizard.
 *
 * Thin presenter: the crawl ([CashuWalletDiscovery]) and every protocol/crypto
 * operation (decrypt, NUT-09 probe/recover, adopt + rebroadcast) live on
 * [CashuWalletState]; this VM only owns the screen's transient flow state.
 */
class CashuWalletWizardViewModel : ViewModel() {
    private var accountViewModel: BaseAccountViewModel? = null
    private var account: Account? = null
    private val state: CashuWalletState get() = account!!.cashuWalletState

    private var discovery: CashuWalletDiscovery? = null
    private var collectJob: Job? = null

    /** Newest discovered kind:10019, rebroadcast alongside the adopted wallet. */
    private var discoveredNutzapInfo: NutzapInfoEvent? = null

    /** The account's live main wallet — non-null once a wallet is adopted/created. */
    val mainWalletEvent get() = state.walletEvent

    private val _wizardState = MutableStateFlow<WizardState>(WizardState.Idle)
    val wizardState = _wizardState.asStateFlow()

    private val _adoptState = MutableStateFlow<AdoptState>(AdoptState.Idle)
    val adoptState = _adoptState.asStateFlow()

    private val _recoveryStates = MutableStateFlow<Map<HexKey, RecoveryState>>(emptyMap())
    val recoveryStates = _recoveryStates.asStateFlow()

    fun init(accountViewModel: BaseAccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        if (discovery == null) {
            discovery =
                CashuWalletDiscovery(
                    pubKey = accountViewModel.account.signer.pubKey,
                    relayDb = { accountViewModel.crawlRelayDb() },
                    clientBuilder = { accountViewModel.buildCrawlClient() },
                    scope = viewModelScope,
                )
        }
    }

    /** Kick off (or restart) the cross-relay search. */
    fun startDiscovery() {
        val d = discovery ?: return
        when (_wizardState.value) {
            is WizardState.Crawling, WizardState.Analyzing -> return
            else -> Unit
        }
        _adoptState.value = AdoptState.Idle
        _recoveryStates.value = emptyMap()
        d.start()
        collectJob?.cancel()
        collectJob =
            viewModelScope.launch {
                d.state.collect { s ->
                    when (s) {
                        is CashuWalletDiscovery.State.Idle -> Unit
                        is CashuWalletDiscovery.State.Crawling ->
                            _wizardState.value = WizardState.Crawling(s.relaysCompleted, s.totalRelays)
                        is CashuWalletDiscovery.State.Done -> analyze(s)
                        is CashuWalletDiscovery.State.Error ->
                            _wizardState.value = WizardState.Error(s.message)
                    }
                }
            }
    }

    /** Decrypt + balance-probe each found wallet, then classify into the outcome. */
    private fun analyze(done: CashuWalletDiscovery.State.Done) {
        val vm = accountViewModel ?: return
        _wizardState.value = WizardState.Analyzing
        discoveredNutzapInfo = done.nutzapInfoEvents.firstOrNull()
        vm.launchSigner {
            // Per-mint probe failures are already swallowed inside
            // probeRecoverableFromSeed; this outer guard catches anything else
            // (a malformed key, an unexpected throw) so the wizard surfaces an
            // error + retry instead of spinning on "Analyzing…" forever.
            try {
                val found = mutableListOf<FoundWallet>()
                for (evt in done.walletEvents) {
                    val config = state.decryptDiscoveredWallet(evt)
                    if (config == null) {
                        found += FoundWallet(evt, evt.createdAt, emptyList(), false, null, emptyMap(), 0L)
                        continue
                    }
                    // A local, so the null check smart-casts: the config type now lives in commons.
                    val privkeyHex = config.privkeyHex
                    val recoverable =
                        if (privkeyHex != null && config.mints.isNotEmpty()) {
                            state.probeRecoverableFromSeed(privkeyHex, config.mints)
                        } else {
                            emptyMap()
                        }
                    found +=
                        FoundWallet(
                            event = evt,
                            createdAt = evt.createdAt,
                            mints = config.mints,
                            valid = true,
                            privkeyHex = config.privkeyHex,
                            recoverableByMint = recoverable,
                            totalRecoverableSats = recoverable.values.sum(),
                        )
                }

                val valid = found.filter { it.valid }.sortedByDescending { it.createdAt }
                _wizardState.value =
                    when {
                        valid.isEmpty() -> WizardState.NoWallet
                        valid.size == 1 -> WizardState.Single(valid.first())
                        else -> WizardState.Multiple(main = valid.first(), others = valid.drop(1))
                    }
            } catch (e: Exception) {
                _wizardState.value = WizardState.Error(describeMintError(e))
            }
        }
    }

    /**
     * Adopt [found] as the account's main wallet (consume + rebroadcast to
     * outbox). When [recoverFunds] is true, also restore its own balance from
     * its seed — bumping the main counter, since this IS the main wallet's key.
     */
    fun adoptAsMain(
        found: FoundWallet,
        recoverFunds: Boolean,
    ) {
        val vm = accountViewModel ?: return
        if (_adoptState.value is AdoptState.Working) return
        _adoptState.value = AdoptState.Working
        vm.launchSigner {
            try {
                state.adoptDiscoveredWallet(found.event, discoveredNutzapInfo)
                val recovered =
                    if (recoverFunds && found.privkeyHex != null && found.mints.isNotEmpty()) {
                        state.recoverFromSeed(found.privkeyHex, found.mints, bumpCounter = true)
                    } else {
                        0L
                    }
                _adoptState.value = AdoptState.Done(recovered)
            } catch (e: Exception) {
                _adoptState.value = AdoptState.Error(describeMintError(e))
            }
        }
    }

    /**
     * Recover the spendable balance of an OLD/duplicate [found] wallet into the
     * main wallet. Foreign seed → never bumps the main counter.
     */
    fun recoverOldWallet(found: FoundWallet) {
        val vm = accountViewModel ?: return
        val privkey = found.privkeyHex ?: return
        if (found.mints.isEmpty()) return
        if (_recoveryStates.value[found.event.id] is RecoveryState.Working) return
        setRecovery(found.event.id, RecoveryState.Working)
        vm.launchSigner {
            try {
                val sats = state.recoverFromSeed(privkey, found.mints, bumpCounter = false)
                setRecovery(found.event.id, RecoveryState.Done(sats))
            } catch (e: Exception) {
                setRecovery(found.event.id, RecoveryState.Error(describeMintError(e)))
            }
        }
    }

    private fun setRecovery(
        id: HexKey,
        value: RecoveryState,
    ) {
        _recoveryStates.update { it + (id to value) }
    }

    fun reset() {
        collectJob?.cancel()
        collectJob = null
        discovery?.cancel()
        _wizardState.value = WizardState.Idle
        _adoptState.value = AdoptState.Idle
        _recoveryStates.value = emptyMap()
    }

    override fun onCleared() {
        discovery?.cancel()
    }
}
