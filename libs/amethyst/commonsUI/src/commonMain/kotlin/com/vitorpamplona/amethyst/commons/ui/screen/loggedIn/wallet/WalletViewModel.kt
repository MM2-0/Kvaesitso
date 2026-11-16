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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.clink.ClinkDebitWalletEntryNorm
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcBackupPayload
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcBackupState
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcSignerState
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcWalletEntryNorm
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.send_payment_failed
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_all_already_added
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_cannot_sign
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_deleted
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_deleting
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_failed
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_failed_generic
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_no_relays
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_nothing_yet
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_partially_deleted
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_published
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_rejected
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_republished
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_restored_toast
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_turned_off
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_working
import com.vitorpamplona.amethyst.commons.resources.wallet_balance_request_failed
import com.vitorpamplona.amethyst.commons.resources.wallet_connect_decrypt_failed
import com.vitorpamplona.amethyst.commons.resources.wallet_connect_unreadable_response_error
import com.vitorpamplona.amethyst.commons.resources.wallet_connect_unrecognized_reply
import com.vitorpamplona.amethyst.commons.resources.wallet_invoice_creation_failed
import com.vitorpamplona.amethyst.commons.resources.wallet_no_invoice_returned
import com.vitorpamplona.amethyst.commons.resources.wallet_request_failed
import com.vitorpamplona.amethyst.commons.resources.wallet_request_timed_out
import com.vitorpamplona.amethyst.commons.resources.wallet_request_timed_out_spoofed
import com.vitorpamplona.amethyst.commons.resources.wallet_transactions_load_failed
import com.vitorpamplona.amethyst.commons.resources.wallet_transactions_load_more_failed
import com.vitorpamplona.amethyst.commons.resources.wallet_transactions_not_supported
import com.vitorpamplona.amethyst.commons.service.ClinkDebitPayer
import com.vitorpamplona.amethyst.commons.service.lnurl.LightningInvoiceResolver
import com.vitorpamplona.amethyst.commons.ui.loadPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.sats
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.clink.debits.DebitFrequency
import com.vitorpamplona.quartz.experimental.clink.debits.DebitResponse
import com.vitorpamplona.quartz.experimental.clink.pointers.ClinkPointerParser
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetBalanceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetBalanceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetInfoMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetInfoSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.IErrorResponseLike
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ListTransactionsMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ListTransactionsSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransaction
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed class SendState {
    data object Idle : SendState()

    data object Sending : SendState()

    data class Success(
        val preimage: String?,
    ) : SendState()

    data class Error(
        val message: String,
    ) : SendState()
}

sealed class ReceiveState {
    data object Idle : ReceiveState()

    data object Creating : ReceiveState()

    data class Created(
        val invoice: String,
        val amount: Long,
    ) : ReceiveState()

    data class Error(
        val message: String,
    ) : ReceiveState()
}

enum class TransactionFilter {
    ALL,
    ZAPS,
    NON_ZAPS,
}

data class WalletInfo(
    val walletId: String,
    val name: String,
    val alias: String? = null,
    val balanceSats: Long? = null,
    val isDefault: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    // CLINK debits are spend-only: no balance/transactions to fetch or show.
    val canShowBalance: Boolean = true,
    /** Stays true, even with auto-backup off, until every relay confirms the delete. */
    val isBackedUp: Boolean = false,
)

private const val NWC_TIMEOUT_MS = 30_000L

class WalletViewModel : ViewModel() {
    companion object {
        private const val TAG = "WalletViewModel"

        /** All the bitcoin there will ever be; keeps a typed amount's msat conversion in range. */
        const val MAX_SEND_SATS = 2_100_000_000_000_000L
    }

    // Error text is resolved here rather than in the screens because a single _error
    // flow feeds several of them.
    private suspend fun text(id: StringResource) = loadStringRes(id)

    private suspend fun text(
        id: StringResource,
        vararg args: String?,
    ) = loadStringRes(id, *args)

    private var account: Account? = null
    private var accountViewModel: AccountViewModel? = null

    private val _hasWalletSetup = MutableStateFlow(false)
    val hasWalletSetup = _hasWalletSetup.asStateFlow()

    private val walletInfoMap = MutableStateFlow<Map<String, WalletInfo>>(emptyMap())

    private val _wallets = MutableStateFlow<List<NwcWalletEntryNorm>>(emptyList())
    val wallets = _wallets.asStateFlow()

    private val _debitWallets = MutableStateFlow<List<ClinkDebitWalletEntryNorm>>(emptyList())
    val debitWallets = _debitWallets.asStateFlow()

    private val _defaultWalletId = MutableStateFlow<String?>(null)
    val defaultWalletId = _defaultWalletId.asStateFlow()

    private val _backupEnabled = MutableStateFlow(false)
    val backupEnabled = _backupEnabled.asStateFlow()

    private val _backupCardDismissed = MutableStateFlow(false)
    val backupCardDismissed = _backupCardDismissed.asStateFlow()

    private val _backupRelays = MutableStateFlow<List<String>>(emptyList())
    val backupRelays = _backupRelays.asStateFlow()

    private val backedUpWalletIds = MutableStateFlow<Set<String>>(emptySet())

    private val _backupBusy = MutableStateFlow(false)
    val backupBusy = _backupBusy.asStateFlow()

    private val backupPublishing = MutableStateFlow(false)

    val backupWorking =
        combine(_backupBusy, backupPublishing) { busy, publishing -> busy || publishing }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage = _backupMessage.asStateFlow()

    private var backupObserver: Job? = null
    private var observedAccount: Account? = null

    val walletInfoList =
        combine(_wallets, _debitWallets, _defaultWalletId, walletInfoMap, backedUpWalletIds) { wallets, debits, defaultId, infoMap, backedUpIds ->
            // The unified default falls back to the first source overall (NWC before debits).
            val effectiveDefault = defaultId ?: wallets.firstOrNull()?.id ?: debits.firstOrNull()?.id

            val nwcRows =
                wallets.map { wallet ->
                    val info = infoMap[wallet.id]
                    WalletInfo(
                        walletId = wallet.id,
                        name = wallet.name,
                        alias = info?.alias,
                        balanceSats = info?.balanceSats,
                        isDefault = wallet.id == effectiveDefault,
                        isLoading = info?.isLoading == true,
                        error = info?.error,
                        canShowBalance = true,
                        isBackedUp = wallet.id in backedUpIds,
                    )
                }

            val debitRows =
                debits.map { debit ->
                    WalletInfo(
                        walletId = debit.id,
                        name = debit.name,
                        isDefault = debit.id == effectiveDefault,
                        canShowBalance = false,
                    )
                }

            nwcRows + debitRows
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected wallet for detail view
    private val _selectedWalletId = MutableStateFlow<String?>(null)
    val selectedWalletId = _selectedWalletId.asStateFlow()

    private val _balanceSats = MutableStateFlow<Long?>(null)
    val balanceSats = _balanceSats.asStateFlow()

    private val _walletAlias = MutableStateFlow<String?>(null)
    val walletAlias = _walletAlias.asStateFlow()

    private val allTransactions = MutableStateFlow<List<NwcTransaction>>(emptyList())

    private val _transactionFilter = MutableStateFlow(TransactionFilter.ALL)
    val transactionFilter = _transactionFilter.asStateFlow()

    val filteredTransactions =
        combine(allTransactions, _transactionFilter) { txs, filter ->
            when (filter) {
                TransactionFilter.ALL -> txs
                TransactionFilter.ZAPS -> txs.filter { it.parsedMetadata()?.nostr != null }
                TransactionFilter.NON_ZAPS -> txs.filter { it.parsedMetadata()?.nostr == null }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _hasMoreTransactions = MutableStateFlow(true)
    val hasMoreTransactions = _hasMoreTransactions.asStateFlow()

    private val pageSize = 20

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState = _sendState.asStateFlow()

    private val _receiveState = MutableStateFlow<ReceiveState>(ReceiveState.Idle)
    val receiveState = _receiveState.asStateFlow()

    // A null `Response` means the wallet service replied but the payload could
    // not be decrypted (wrong key, unexpected format). Any other unexpected
    // subtype means the response shape didn't match any known NIP-47 result.
    // Both used to be swallowed silently — now we surface them so the user
    // can distinguish "wallet never answered" from "wallet answered with
    // something we can't read".
    private suspend fun unreadableResponseError(response: Response?): String =
        if (response == null) {
            text(Res.string.wallet_connect_decrypt_failed)
        } else {
            text(Res.string.wallet_connect_unrecognized_reply, response.resultType)
        }

    private fun launchTimeout(
        requestIdProvider: () -> HexKey?,
        onTimeout: () -> Unit,
    ): Job =
        viewModelScope.launch(Dispatchers.IO) {
            delay(NWC_TIMEOUT_MS)
            val requestId = requestIdProvider()
            val spoofs = requestId?.let { account?.zaps?.nwcSpoofAttempts(it) ?: 0 } ?: 0
            _error.value =
                if (spoofs > 0) {
                    loadPluralStringRes(Res.plurals.wallet_request_timed_out_spoofed, spoofs, spoofs)
                } else {
                    text(Res.string.wallet_request_timed_out)
                }
            requestId?.let { account?.zaps?.cleanupNwcRequest(it) }
            onTimeout()
        }

    private val _lnAddress = MutableStateFlow("")
    val lnAddress = _lnAddress.asStateFlow()

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        refreshWalletList()
        observeBackupSettings(accountViewModel.account)
    }

    // init() runs on every recomposition, so only a new account restarts the observers.
    private fun observeBackupSettings(acc: Account) {
        if (observedAccount === acc) return
        observedAccount = acc
        backupObserver?.cancel()
        backupObserver =
            viewModelScope.launch {
                launch { acc.settings.backupNwcWallets.collect { _backupEnabled.value = it } }
                launch { acc.settings.backupNwcCardDismissed.collect { _backupCardDismissed.value = it } }
                launch { acc.settings.nwcBackupRelays.collect { _backupRelays.value = it } }
                launch { acc.settings.backedUpNwcWalletIds.collect { backedUpWalletIds.value = it } }
                launch { acc.nwcBackup.status.collect { backupPublishing.value = it is NwcBackupState.Status.Publishing } }
            }
    }

    fun refreshWalletList() {
        val acc = account ?: return
        _wallets.value = acc.settings.nwcWallets.value
        _debitWallets.value = acc.settings.clinkDebitWallets.value
        _defaultWalletId.value = acc.settings.defaultPaymentSourceId.value
        _hasWalletSetup.value = _wallets.value.isNotEmpty() || _debitWallets.value.isNotEmpty()
    }

    fun setBackupEnabled(
        enabled: Boolean,
        onDone: () -> Unit = {},
    ) {
        val accountVm = accountViewModel ?: return
        _backupBusy.value = true
        accountVm.launchSigner {
            try {
                if (enabled) _backupMessage.value = text(Res.string.wallet_backup_working)
                val acc = account ?: return@launchSigner
                _backupMessage.value =
                    if (enabled) {
                        when {
                            !acc.enableNwcBackup() -> backupFailureText(acc)
                            acc.nwcBackup.status.value is NwcBackupState.Status.NothingToBackUp -> text(Res.string.wallet_backup_nothing_yet)
                            else -> text(Res.string.wallet_backup_published)
                        }
                    } else {
                        acc.disableNwcBackup()
                        text(Res.string.wallet_backup_turned_off)
                    }
            } finally {
                _backupBusy.value = false
                onDone()
            }
        }
    }

    fun republishBackup(onDone: () -> Unit = {}) {
        val accountVm = accountViewModel ?: return
        _backupBusy.value = true
        accountVm.launchSigner {
            try {
                _backupMessage.value = text(Res.string.wallet_backup_working)
                val acc = account ?: return@launchSigner
                _backupMessage.value =
                    if (acc.nwcBackup.republishNow()) {
                        text(Res.string.wallet_backup_republished)
                    } else {
                        backupFailureText(acc)
                    }
            } finally {
                _backupBusy.value = false
                onDone()
            }
        }
    }

    fun deleteBackup(onDone: () -> Unit = {}) {
        val accountVm = accountViewModel ?: return
        _backupBusy.value = true
        accountVm.launchSigner {
            try {
                _backupMessage.value = text(Res.string.wallet_backup_deleting)
                val acc = account ?: return@launchSigner
                _backupMessage.value =
                    when (val outcome = acc.deleteNwcBackup()) {
                        is NwcBackupState.DeleteOutcome.Deleted -> text(Res.string.wallet_backup_deleted)
                        is NwcBackupState.DeleteOutcome.PartiallyDeleted ->
                            loadStringRes(Res.string.wallet_backup_partially_deleted, outcome.remaining.joinToString())
                        is NwcBackupState.DeleteOutcome.Failed -> backupFailureText(outcome.failure)
                    }
            } finally {
                _backupBusy.value = false
                onDone()
            }
        }
    }

    private suspend fun backupFailureText(acc: Account): String = backupFailureText((acc.nwcBackup.status.value as? NwcBackupState.Status.Failed)?.failure)

    private suspend fun backupFailureText(failure: NwcBackupState.Failure?): String =
        when (failure) {
            NwcBackupState.Failure.CannotSign -> text(Res.string.wallet_backup_cannot_sign)
            NwcBackupState.Failure.NoRelays -> text(Res.string.wallet_backup_no_relays)
            is NwcBackupState.Failure.Rejected -> failure.reason?.let { text(Res.string.wallet_backup_rejected, it) } ?: text(Res.string.wallet_backup_failed_generic)
            is NwcBackupState.Failure.Error -> failure.message?.let { text(Res.string.wallet_backup_failed, it) } ?: text(Res.string.wallet_backup_failed_generic)
            null -> text(Res.string.wallet_backup_failed_generic)
        }

    fun fetchBackupForRestore(onResult: (NwcBackupState.FoundNwcBackup?) -> Unit) {
        val accountVm = accountViewModel ?: return
        _backupBusy.value = true
        accountVm.launchSigner {
            val payload =
                try {
                    account?.nwcBackup?.fetchBackup()
                } finally {
                    _backupBusy.value = false
                }
            withContext(Dispatchers.Main) { onResult(payload) }
        }
    }

    // Held here: the empty state a restore starts from can be swapped out before the result lands.
    private val _restoreCandidate = MutableStateFlow<NwcBackupState.FoundNwcBackup?>(null)
    val restoreCandidate = _restoreCandidate.asStateFlow()

    private val _restoreNoneFound = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val restoreNoneFound = _restoreNoneFound.asSharedFlow()

    // Set once per screen visit by an existence-only lookup (no decrypt, no signer prompt).
    private val _backupAvailable = MutableStateFlow(false)
    val backupAvailable = _backupAvailable.asStateFlow()
    private var backupChecked = false

    /** Looks for a live backup on the relays so the empty state can offer a one-tap restore. */
    fun checkForBackup() {
        val acc = account ?: return
        if (backupChecked) return
        backupChecked = true
        viewModelScope.launch {
            val found =
                withContext(Dispatchers.IO) {
                    try {
                        acc.nwcBackup.hasBackup()
                    } catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        Log.w(TAG, "Backup lookup failed", e)
                        null
                    }
                }
            // Relays not ready yet, or the lookup failed: the next visit asks again.
            if (found == null) backupChecked = false else _backupAvailable.value = found
        }
    }

    fun lookForBackupToRestore() {
        fetchBackupForRestore { backup ->
            if (backup == null) {
                _restoreNoneFound.tryEmit(Unit)
            } else {
                _restoreCandidate.value = backup
            }
        }
    }

    fun clearRestoreCandidate() {
        _restoreCandidate.value = null
    }

    /** Returns how many wallets were new. */
    fun applyRestoredBackup(
        backup: NwcBackupState.FoundNwcBackup,
        chosen: List<NwcBackupState.RestorableWallet>,
    ): Int {
        val acc = account ?: return 0
        val wallets = chosen.map { it.wallet }
        val payload =
            NwcBackupPayload(
                wallets = wallets,
                defaultId = backup.defaultId?.takeIf { id -> wallets.any { it.id == id } },
            )
        val added = acc.settings.mergeRestoredNwcWallets(payload)
        refreshWalletList()
        _backupAvailable.value = false
        viewModelScope.launch {
            _backupMessage.value =
                if (added > 0) {
                    loadPluralStringRes(Res.plurals.wallet_backup_restored_toast, added, added)
                } else {
                    text(Res.string.wallet_backup_all_already_added)
                }
        }
        return added
    }

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    fun dismissBackupCard() {
        account?.settings?.dismissBackupNwcCard()
    }

    fun refreshWalletSetup() {
        refreshWalletList()
    }

    fun loadLnAddress() {
        viewModelScope.launch(Dispatchers.IO) {
            val info =
                account
                    ?.userProfile()
                    ?.metadataOrNull()
                    ?.flow
                    ?.value
                    ?.info
            _lnAddress.value = info?.lud16 ?: ""
        }
    }

    fun updateLnAddress(address: String) {
        _lnAddress.value = address
    }

    fun saveLnAddress() {
        val accountVm = accountViewModel ?: return
        accountVm.launchSigner {
            saveLnAddressSuspend()
        }
    }

    suspend fun saveLnAddressSuspend() {
        val acc = account ?: return
        val event = acc.userMetadata.sendNewUserMetadata(lnAddress = _lnAddress.value)
        acc.sendLiterallyEverywhere(event)
    }

    fun setDefaultWallet(walletId: String) {
        val acc = account ?: return
        // Only reflect the change locally if it actually persisted (the id must exist
        // in one of the lists); otherwise the star and the stored default would diverge.
        if (acc.settings.setDefaultPaymentSource(walletId)) {
            _defaultWalletId.value = walletId
        }
    }

    fun removeWallet(walletId: String) {
        val acc = account ?: return
        if (_debitWallets.value.any { it.id == walletId }) {
            acc.settings.removeClinkDebitWallet(walletId)
        } else {
            acc.settings.removeNwcWallet(walletId)
        }
        refreshWalletList()
    }

    /** Adds a CLINK debit pointer (`ndebit1…`) as a spend-only payment source. */
    @OptIn(ExperimentalUuidApi::class)
    fun addClinkDebitWallet(
        name: String,
        ndebit: String,
    ): Boolean {
        val acc = account ?: return false
        val pointer = ClinkPointerParser.parse(ndebit.trim()) as? NDebit ?: return false
        val entry =
            ClinkDebitWalletEntryNorm(
                id =
                    Uuid.random().toString(),
                name = name.ifBlank { "Debit" },
                pointer = pointer,
            )
        acc.settings.addClinkDebitWallet(entry)
        refreshWalletList()
        return true
    }

    /**
     * Asks a CLINK debit wallet to authorize a spending budget (kind-21002). Omit
     * [frequency] for a one-time budget; otherwise it recurs every day/week/month.
     * [onResult] reports the wallet's decision (ok, GFY error text, or null on timeout).
     */
    fun requestDebitBudget(
        walletId: String,
        amountSats: Long,
        frequency: DebitFrequency?,
        onResult: (DebitResponse?) -> Unit,
    ) {
        val acc = account ?: return
        val pointer = _debitWallets.value.firstOrNull { it.id == walletId }?.pointer ?: return
        val avm = accountViewModel ?: return
        val moneyOpRelays = avm.host.moneyOpRelays
        viewModelScope.launch {
            // The request is signed and NIP-44 encrypted. If the signer refuses, nothing reached the
            // wallet, so that is not reported as a wallet timeout: reportSignerErrors shows the
            // read-only / missing-signer notices and keeps a user's rejection silent.
            avm.reportSignerErrors {
                // A malformed budget (e.g. an out-of-spec frequency unit) makes requestBudget throw;
                // treat it as "no response" so the dialog dismisses instead of hanging on a spinner.
                val response =
                    try {
                        ClinkDebitPayer.requestBudget(acc, moneyOpRelays, pointer, amountSats, frequency)
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                onResult(response)
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun addWallet(
        name: String,
        uri: Nip47WalletConnect.Nip47URINorm,
    ): Boolean {
        val acc = account ?: return false
        val entry =
            NwcWalletEntryNorm(
                id =
                    Uuid.random().toString(),
                name = name.ifBlank { "Lightning Wallet" },
                uri = uri,
            )
        if (!acc.settings.addNwcWallet(entry)) return false
        refreshWalletList()
        return true
    }

    // Some wallet services (e.g. Blitz) report "N/A" as their name.
    private fun sanitizeWalletAlias(alias: String?): String? = alias?.trim()?.takeIf { it.isNotEmpty() && !it.equals("n/a", ignoreCase = true) }

    fun selectWallet(walletId: String) {
        _selectedWalletId.value = walletId
        _balanceSats.value = walletInfoMap.value[walletId]?.balanceSats
        _walletAlias.value = walletInfoMap.value[walletId]?.alias
        allTransactions.value = emptyList()
    }

    private fun getWalletUri(walletId: String?): Nip47WalletConnect.Nip47URINorm? = _wallets.value.firstOrNull { it.id == walletId }?.uri

    private suspend fun getSelectedWalletUri(): Nip47WalletConnect.Nip47URINorm? = getWalletUri(_selectedWalletId.value)

    suspend fun fetchAllBalances() {
        _wallets.value.forEach { wallet ->
            fetchBalanceForWallet(wallet.id)
        }
    }

    fun nwcWalletIdsPendingBalance(): List<String> = _wallets.value.filter { walletInfoMap.value[it.id]?.balanceSats == null }.map { it.id }

    suspend fun refreshBalancesFor(walletIds: List<String>) {
        walletIds.forEach { fetchBalanceForWallet(it) }
    }

    private suspend fun fetchBalanceForWallet(walletId: String) {
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            updateWalletInfo(walletId) { it.copy(isLoading = true, error = null) }
            try {
                acc.zaps.sendNwcRequestToWallet(walletUri, GetBalanceMethod.create()) { response ->
                    viewModelScope.launch {
                        when (response) {
                            is GetBalanceSuccessResponse -> {
                                val sats = (response.result?.balance ?: 0L) / 1000L
                                updateWalletInfo(walletId) { it.copy(balanceSats = sats, isLoading = false) }
                            }

                            is NwcErrorResponse -> {
                                updateWalletInfo(walletId) {
                                    it.copy(error = response.error?.message ?: text(Res.string.wallet_balance_request_failed), isLoading = false)
                                }
                            }

                            else -> {
                                updateWalletInfo(walletId) {
                                    it.copy(error = unreadableResponseError(response), isLoading = false)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                updateWalletInfo(walletId) { it.copy(error = text(Res.string.wallet_request_failed, e.message), isLoading = false) }
            }
        }
    }

    suspend fun fetchInfoForWallet(walletId: String) {
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                acc.zaps.sendNwcRequestToWallet(walletUri, GetInfoMethod.create()) { response ->
                    viewModelScope.launch {
                        when (response) {
                            is GetInfoSuccessResponse -> {
                                updateWalletInfo(walletId) { it.copy(alias = sanitizeWalletAlias(response.result?.alias)) }
                            }

                            else -> {}
                        }
                    }
                }
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
            }
        }
    }

    private suspend fun updateWalletInfo(
        walletId: String,
        transform: suspend (WalletInfo) -> WalletInfo,
    ) {
        walletInfoMap.value =
            walletInfoMap.value.toMutableMap().apply {
                val current =
                    get(walletId)
                        ?: WalletInfo(
                            walletId = walletId,
                            name = _wallets.value.firstOrNull { it.id == walletId }?.name ?: "Lightning Wallet",
                        )
                put(walletId, transform(current))
            }
    }

    // --- Methods below operate on the selected wallet ---

    suspend fun fetchBalance() {
        val walletId = _selectedWalletId.value ?: _defaultWalletId.value ?: _wallets.value.firstOrNull()?.id ?: return
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            var requestId: HexKey? = null
            val timeoutJob = launchTimeout({ requestId }) { _isLoading.value = false }
            try {
                requestId =
                    acc.zaps.sendNwcRequestToWallet(walletUri, GetBalanceMethod.create()) { response ->
                        viewModelScope.launch {
                            timeoutJob.cancel()
                            when (response) {
                                is GetBalanceSuccessResponse -> {
                                    _balanceSats.value = (response.result?.balance ?: 0L) / 1000L
                                    updateWalletInfo(walletId) { it.copy(balanceSats = _balanceSats.value) }
                                    _error.value = null
                                }

                                is NwcErrorResponse -> {
                                    _error.value = response.error?.message ?: text(Res.string.wallet_balance_request_failed)
                                }

                                else -> {
                                    _error.value = unreadableResponseError(response)
                                }
                            }
                            _isLoading.value = false
                        }
                    }
            } catch (e: Exception) {
                timeoutJob.cancel()
                currentCoroutineContext().ensureActive()
                _error.value = text(Res.string.wallet_request_failed, e.message)
                _isLoading.value = false
            }
        }
    }

    suspend fun fetchInfo() {
        val walletId = _selectedWalletId.value ?: _defaultWalletId.value ?: _wallets.value.firstOrNull()?.id ?: return
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                acc.zaps.sendNwcRequestToWallet(walletUri, GetInfoMethod.create()) { response ->
                    viewModelScope.launch {
                        when (response) {
                            is GetInfoSuccessResponse -> {
                                _walletAlias.value = sanitizeWalletAlias(response.result?.alias)
                                updateWalletInfo(walletId) { it.copy(alias = sanitizeWalletAlias(response.result?.alias)) }
                            }

                            else -> {}
                        }
                    }
                }
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
            }
        }
    }

    fun fetchTransactions() {
        val walletId = _selectedWalletId.value ?: _defaultWalletId.value ?: _wallets.value.firstOrNull()?.id ?: return
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            _hasMoreTransactions.value = true
            // list_transactions lives in the NWC-05 extension. Only a wallet that publishes an
            // `extensions` tag without 05 (and without the method in its content) is skipped;
            // legacy wallets are still asked. See NwcInfoEvent.mayUseExtensionMethod.
            val info = withTimeoutOrNull(NwcSignerState.NIP44_NEGOTIATION_WAIT_MS) { acc.nwcInfoCache.currentOrFetch(walletUri) }
            if (info?.mayUseExtensionMethod(NwcMethod.LIST_TRANSACTIONS) == false) {
                allTransactions.value = emptyList()
                _hasMoreTransactions.value = false
                _error.value = text(Res.string.wallet_transactions_not_supported)
                _isLoading.value = false
                return@launch
            }
            var requestId: HexKey? = null
            val timeoutJob = launchTimeout({ requestId }) { _isLoading.value = false }
            try {
                requestId =
                    acc.zaps.sendNwcRequestToWallet(
                        walletUri,
                        ListTransactionsMethod.create(
                            limit = pageSize,
                            offset = 0,
                            unpaid = false,
                        ),
                    ) { response ->
                        viewModelScope.launch {
                            timeoutJob.cancel()
                            when (response) {
                                is ListTransactionsSuccessResponse -> {
                                    val txs = response.result?.transactions ?: emptyList()
                                    allTransactions.value = txs
                                    val totalCount = response.result?.total_count
                                    _hasMoreTransactions.value =
                                        if (totalCount != null) {
                                            txs.size < totalCount
                                        } else {
                                            txs.size >= pageSize
                                        }
                                    _error.value = null
                                }

                                is NwcErrorResponse -> {
                                    _error.value = response.error?.message ?: text(Res.string.wallet_transactions_load_failed)
                                }

                                else -> {
                                    _error.value = unreadableResponseError(response)
                                }
                            }
                            _isLoading.value = false
                        }
                    }
            } catch (e: Exception) {
                timeoutJob.cancel()
                currentCoroutineContext().ensureActive()
                _error.value = text(Res.string.wallet_request_failed, e.message)
                _isLoading.value = false
            }
        }
    }

    suspend fun loadMoreTransactions() {
        if (_isLoadingMore.value || !_hasMoreTransactions.value) return
        val walletId = _selectedWalletId.value ?: _defaultWalletId.value ?: _wallets.value.firstOrNull()?.id ?: return
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return
        val currentOffset = allTransactions.value.size
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            _error.value = null
            var requestId: HexKey? = null
            val timeoutJob = launchTimeout({ requestId }) { _isLoadingMore.value = false }
            try {
                requestId =
                    acc.zaps.sendNwcRequestToWallet(
                        walletUri,
                        ListTransactionsMethod.create(
                            limit = pageSize,
                            offset = currentOffset,
                            unpaid = false,
                        ),
                    ) { response ->
                        viewModelScope.launch {
                            timeoutJob.cancel()
                            when (response) {
                                is ListTransactionsSuccessResponse -> {
                                    val newTxs = response.result?.transactions ?: emptyList()
                                    allTransactions.value += newTxs
                                    val totalCount = response.result?.total_count
                                    _hasMoreTransactions.value =
                                        if (totalCount != null) {
                                            allTransactions.value.size < totalCount
                                        } else {
                                            newTxs.size >= pageSize
                                        }
                                    _error.value = null
                                }

                                is NwcErrorResponse -> {
                                    _error.value = response.error?.message ?: text(Res.string.wallet_transactions_load_more_failed)
                                }

                                else -> {
                                    _error.value = unreadableResponseError(response)
                                }
                            }
                            _isLoadingMore.value = false
                        }
                    }
            } catch (e: Exception) {
                timeoutJob.cancel()
                currentCoroutineContext().ensureActive()
                _error.value = text(Res.string.wallet_request_failed, e.message)
                _isLoadingMore.value = false
            }
        }
    }

    /** The wallet a send pays from: the selected one, else the default, else the first. */
    private fun payingWalletUri(): Nip47WalletConnect.Nip47URINorm? = getWalletUri(_selectedWalletId.value ?: _defaultWalletId.value ?: _wallets.value.firstOrNull()?.id)

    fun sendPayment(bolt11: String) {
        val acc = account ?: return
        val walletUri = payingWalletUri() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _sendState.value = SendState.Sending
            payInvoice(acc, walletUri, bolt11)
        }
    }

    private suspend fun payInvoice(
        acc: Account,
        walletUri: Nip47WalletConnect.Nip47URINorm,
        bolt11: String,
    ) {
        try {
            acc.zaps.sendNwcRequestToWallet(walletUri, PayInvoiceMethod.create(bolt11)) { response ->
                viewModelScope.launch {
                    when (response) {
                        is PayInvoiceSuccessResponse -> {
                            _sendState.value = SendState.Success(response.result?.preimage)
                            fetchBalance()
                        }

                        is IErrorResponseLike -> {
                            // Both PayInvoiceErrorResponse (method-specific, kept for
                            // back-compat) and NwcErrorResponse (generic) reduce to the
                            // same user-visible "payment failed" message.
                            _sendState.value =
                                SendState.Error(
                                    response.errorMessage() ?: text(Res.string.send_payment_failed),
                                )
                        }

                        else -> {
                            _sendState.value = SendState.Error(text(Res.string.wallet_connect_unreadable_response_error))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            _sendState.value = SendState.Error(e.message ?: text(Res.string.send_payment_failed))
        }
    }

    /**
     * Pays a Lightning address: asks its LNURL-pay service for an invoice of exactly [sats], then
     * pays that invoice from the wallet [sendPayment] would use, picked before the lookup starts.
     */
    fun sendToLightningAddress(
        lnAddress: String,
        sats: Long,
    ) {
        val avm = accountViewModel ?: return
        val acc = account ?: return
        val walletUri = payingWalletUri() ?: return
        if (sats !in 1..MAX_SEND_SATS) return
        viewModelScope.launch(Dispatchers.IO) {
            _sendState.value = SendState.Sending
            val invoice =
                try {
                    LightningInvoiceResolver(avm.host.lnurlTransport).lnAddressInvoice(
                        lnAddress = lnAddress,
                        milliSats = sats * 1000L,
                        message = "",
                        onProgress = {},
                    )
                } catch (e: LightningInvoiceResolver.LightningAddressError) {
                    _sendState.value = SendState.Error(avm.noticeResolver.resolve(e.notice).message)
                    return@launch
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    _sendState.value = SendState.Error(e.message ?: text(Res.string.send_payment_failed))
                    return@launch
                }
            payInvoice(acc, walletUri, invoice)
        }
    }

    fun createInvoice(
        amountSats: Long,
        description: String? = null,
    ) {
        val walletId = _selectedWalletId.value ?: _defaultWalletId.value ?: _wallets.value.firstOrNull()?.id ?: return
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _receiveState.value = ReceiveState.Creating
            try {
                acc.zaps.sendNwcRequestToWallet(
                    walletUri,
                    MakeInvoiceMethod.create(
                        amount = amountSats * 1000L,
                        description = description,
                    ),
                ) { response ->
                    viewModelScope.launch {
                        when (response) {
                            is MakeInvoiceSuccessResponse -> {
                                val invoice = response.result?.invoice
                                if (invoice != null) {
                                    _receiveState.value = ReceiveState.Created(invoice, amountSats)
                                } else {
                                    _receiveState.value = ReceiveState.Error(text(Res.string.wallet_no_invoice_returned))
                                }
                            }

                            is NwcErrorResponse -> {
                                _receiveState.value =
                                    ReceiveState.Error(
                                        response.error?.message ?: text(Res.string.wallet_invoice_creation_failed),
                                    )
                            }

                            else -> {
                                _receiveState.value = ReceiveState.Error(text(Res.string.wallet_connect_unreadable_response_error))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                _receiveState.value = ReceiveState.Error(e.message ?: text(Res.string.wallet_invoice_creation_failed))
            }
        }
    }

    fun resetSendState() {
        _sendState.value = SendState.Idle
    }

    fun resetReceiveState() {
        _receiveState.value = ReceiveState.Idle
    }

    fun clearError() {
        _error.value = null
    }

    fun setTransactionFilter(filter: TransactionFilter) {
        _transactionFilter.value = filter
    }
}
