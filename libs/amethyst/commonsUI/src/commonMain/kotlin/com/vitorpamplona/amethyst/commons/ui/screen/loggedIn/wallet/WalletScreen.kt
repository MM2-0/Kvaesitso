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

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.clink.ui.ClinkBudgetDialog
import com.vitorpamplona.amethyst.commons.icons.brand.PaymentBrandIcons
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcBackupFormat
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcBackupState
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcWalletEntryNorm
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.toConnectionString
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.cashu_wallet_subtitle_mints
import com.vitorpamplona.amethyst.commons.resources.cashu_wallet_title
import com.vitorpamplona.amethyst.commons.resources.clink_budget_approved
import com.vitorpamplona.amethyst.commons.resources.clink_budget_set
import com.vitorpamplona.amethyst.commons.resources.clink_debit_no_response
import com.vitorpamplona.amethyst.commons.resources.clink_debit_pay_only
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.wallet
import com.vitorpamplona.amethyst.commons.resources.wallet_add_connection
import com.vitorpamplona.amethyst.commons.resources.wallet_add_other_types
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_caption
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_delete
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_delete_body
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_delete_confirm
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_delete_title
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_disclaimer
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_dismiss
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_enable_confirm
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_enable_title
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_found_restore
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_none_found
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_published
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_relays_title
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_republish
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_restore
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_restore_confirm
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_restore_found
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_restore_title
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_saved_by
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_searching
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_status_off
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_status_on
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_title
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_view_relays
import com.vitorpamplona.amethyst.commons.resources.wallet_copied
import com.vitorpamplona.amethyst.commons.resources.wallet_copy
import com.vitorpamplona.amethyst.commons.resources.wallet_copy_connection
import com.vitorpamplona.amethyst.commons.resources.wallet_default
import com.vitorpamplona.amethyst.commons.resources.wallet_details_lud16
import com.vitorpamplona.amethyst.commons.resources.wallet_details_relay
import com.vitorpamplona.amethyst.commons.resources.wallet_details_wallet_service
import com.vitorpamplona.amethyst.commons.resources.wallet_hide_balances
import com.vitorpamplona.amethyst.commons.resources.wallet_looking
import com.vitorpamplona.amethyst.commons.resources.wallet_no_wallets
import com.vitorpamplona.amethyst.commons.resources.wallet_no_wallets_description
import com.vitorpamplona.amethyst.commons.resources.wallet_receive
import com.vitorpamplona.amethyst.commons.resources.wallet_remove
import com.vitorpamplona.amethyst.commons.resources.wallet_remove_confirm
import com.vitorpamplona.amethyst.commons.resources.wallet_remove_confirm_description
import com.vitorpamplona.amethyst.commons.resources.wallet_sats
import com.vitorpamplona.amethyst.commons.resources.wallet_send
import com.vitorpamplona.amethyst.commons.resources.wallet_set_default
import com.vitorpamplona.amethyst.commons.resources.wallet_settings
import com.vitorpamplona.amethyst.commons.ui.components.rememberLongNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.wallet.CashuWalletViewModel
import com.vitorpamplona.quartz.experimental.clink.debits.DebitFrequency
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import androidx.compose.material3.Icon as Material3Icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val walletViewModel: WalletViewModel = rememberViewModel { WalletViewModel() }
    walletViewModel.init(accountViewModel)
    RestoreBackupHost(walletViewModel)

    val cashuWalletViewModel: CashuWalletViewModel = rememberViewModel { CashuWalletViewModel() }
    // Synchronous init so state-flow getters don't hit a null `account` on
    // the first composition pass. init() is idempotent — just sets refs.
    cashuWalletViewModel.init(accountViewModel)

    val showOnchainWallet by accountViewModel.settings.uiSettingsFlow.showOnchainWallet
        .collectAsState()
    val hideBalances by accountViewModel.settings.uiSettingsFlow.hideWalletBalances
        .collectAsState()
    val hasNwcWallet by walletViewModel.hasWalletSetup.collectAsState()
    val cashuWalletEvent by cashuWalletViewModel.walletEvent.collectAsState()
    val cashuDiscovering by cashuWalletViewModel.discovering.collectAsState()
    val hasCashuWallet = cashuWalletEvent != null
    val hasWallet = hasNwcWallet || hasCashuWallet
    val cashuBalanceSats by cashuWalletViewModel.balanceSats.collectAsState()
    val cashuMints by cashuWalletViewModel.mints.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // drop(1): re-entering the screen must not re-toast an earlier publish.
    val shortNotice = rememberShortNotice()
    val publishedMsg = stringRes(Res.string.wallet_backup_published)
    LaunchedEffect(Unit) {
        accountViewModel.account.nwcBackup.status
            .drop(1)
            .collect { status ->
                // A publish confirming after a delete turned the toggle off must not toast.
                if (status is NwcBackupState.Status.Published &&
                    accountViewModel.account.settings.backupNwcWallets.value
                ) {
                    shortNotice.show(publishedMsg)
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.wallet)) },
                navigationIcon = {
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            Icon(
                                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                                contentDescription = stringRes(Res.string.back),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        accountViewModel.settings.uiSettingsFlow.hideWalletBalances
                            .tryEmit(!hideBalances)
                    }) {
                        Icon(
                            symbol =
                                if (hideBalances) {
                                    MaterialSymbols.Visibility
                                } else {
                                    MaterialSymbols.VisibilityOff
                                },
                            contentDescription = stringRes(Res.string.wallet_hide_balances),
                        )
                    }
                    IconButton(onClick = { nav.nav(Route.WalletAddNwc()) }) {
                        Icon(
                            symbol = MaterialSymbols.Add,
                            contentDescription = stringRes(Res.string.wallet_add_connection),
                        )
                    }
                },
            )
        },
        bottomBar = {
            AppBottomBar(Route.Wallet, nav, accountViewModel) { route ->
                if (route == Route.Wallet) {
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
    ) { padding ->
        if (showOnchainWallet) {
            // Landing here is an explicit "show me my balance", so it forces a fresh
            // fetch, once per visit, wherever the card below happens to sit. Shared
            // with the zap picker's on-chain gating, which reads the same balance.
            val onchainWallet = accountViewModel.account.onchainWalletState
            LaunchedEffect(onchainWallet) { onchainWallet.refresh(force = true) }
        }
        Column(modifier = Modifier.padding(padding)) {
            // With wallets the card is the list's first item and scrolls with them; the
            // loading and empty states have no list, so it stays pinned above them.
            if (showOnchainWallet && !hasWallet) {
                OnchainSection(
                    accountViewModel = accountViewModel,
                    nav = nav,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            if (!hasWallet && cashuDiscovering) {
                // The Cashu wallet loads async; don't flash the empty state meanwhile.
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringRes(Res.string.wallet_looking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (!hasWallet) {
                NoWalletSetup(
                    modifier = Modifier,
                    nav = nav,
                    walletViewModel = walletViewModel,
                )
            } else {
                MultiWalletHomeContent(
                    walletViewModel = walletViewModel,
                    modifier = Modifier,
                    listState = listState,
                    nav = nav,
                    hasCashuWallet = hasCashuWallet,
                    cashuBalanceSats = cashuBalanceSats,
                    cashuMintCount = cashuMints.size,
                    hideBalance = hideBalances,
                    accountViewModel = accountViewModel,
                    showOnchainWallet = showOnchainWallet,
                )
            }
        }
    }
}

@Composable
private fun NoWalletSetup(
    modifier: Modifier,
    nav: INav,
    walletViewModel: WalletViewModel,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringRes(Res.string.wallet_no_wallets),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringRes(Res.string.wallet_no_wallets_description),
            style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { nav.nav(Route.WalletAddNwc()) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringRes(Res.string.wallet_add_connection))
        }
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { nav.nav(Route.WalletAdd) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringRes(Res.string.wallet_add_other_types))
        }
        Spacer(modifier = Modifier.height(32.dp))
        RestoreBackupLink(walletViewModel = walletViewModel)
    }
}

@Composable
private fun RestoreBackupLink(walletViewModel: WalletViewModel) {
    val backupBusy by walletViewModel.backupBusy.collectAsState()
    val backupAvailable by walletViewModel.backupAvailable.collectAsState()

    LaunchedEffect(walletViewModel) { walletViewModel.checkForBackup() }

    // A known backup earns the prominent button; otherwise a quiet link.
    RestoreBackupButton(
        busy = backupBusy,
        idleLabel = if (backupAvailable) Res.string.wallet_backup_found_restore else Res.string.wallet_backup_restore,
        prominent = backupAvailable,
        onClick = { walletViewModel.lookForBackupToRestore() },
    )
}

/** Restore-from-backup action: a full-width tonal button, or a text link when not [prominent]. */
@Composable
internal fun RestoreBackupButton(
    busy: Boolean,
    idleLabel: StringResource,
    prominent: Boolean,
    onClick: () -> Unit,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = stringRes(if (busy) Res.string.wallet_backup_searching else idleLabel),
            style = if (prominent) LocalTextStyle.current else MaterialTheme.typography.bodySmall,
        )
    }
    if (prominent) {
        FilledTonalButton(
            onClick = onClick,
            enabled = !busy,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            content = content,
        )
    } else {
        TextButton(onClick = onClick, enabled = !busy, content = content)
    }
}

// Above the swapping loading/empty/list content, so the result can't vanish mid-restore.
@Composable
private fun RestoreBackupHost(walletViewModel: WalletViewModel) {
    val candidate by walletViewModel.restoreCandidate.collectAsState()
    val shortNotice = rememberShortNotice()
    val noneFoundMsg = stringRes(Res.string.wallet_backup_none_found)

    LaunchedEffect(walletViewModel) {
        walletViewModel.restoreNoneFound.collect { shortNotice.show(noneFoundMsg) }
    }

    candidate?.let { backup ->
        RestoreFoundDialog(
            backup = backup,
            onConfirm = {
                walletViewModel.applyRestoredBackup(backup, it)
                walletViewModel.clearRestoreCandidate()
            },
            onDismiss = { walletViewModel.clearRestoreCandidate() },
        )
    }
}

@Composable
private fun MultiWalletHomeContent(
    walletViewModel: WalletViewModel,
    modifier: Modifier,
    listState: LazyListState,
    nav: INav,
    hasCashuWallet: Boolean,
    cashuBalanceSats: Long,
    cashuMintCount: Int,
    hideBalance: Boolean,
    accountViewModel: AccountViewModel,
    showOnchainWallet: Boolean,
) {
    val walletInfoList by walletViewModel.walletInfoList.collectAsState()
    val nwcWallets by walletViewModel.wallets.collectAsState()
    val longNotice = rememberLongNotice()
    val budgetApprovedMsg = stringRes(Res.string.clink_budget_approved)
    val debitNoResponseMsg = stringRes(Res.string.clink_debit_no_response)

    val backupEnabled by walletViewModel.backupEnabled.collectAsState()
    val backupCardDismissed by walletViewModel.backupCardDismissed.collectAsState()
    val backupWorking by walletViewModel.backupWorking.collectAsState()

    val walletIdsKey = nwcWallets.joinToString(",") { it.id }
    LaunchedEffect(walletIdsKey) {
        walletViewModel.fetchAllBalances()
        nwcWallets.forEach { wallet -> walletViewModel.fetchInfoForWallet(wallet.id) }
        // A fetch at cold start often races the relay connection.
        repeat(2) {
            delay(4_000)
            walletViewModel.refreshBalancesFor(walletViewModel.nwcWalletIdsPendingBalance())
        }
    }

    var settingsFor by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showOnchainWallet) {
            item(key = "onchain") {
                OnchainSection(
                    accountViewModel = accountViewModel,
                    nav = nav,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        items(walletInfoList, key = { info -> info.walletId }) { walletInfo ->
            WalletCard(
                walletInfo = walletInfo,
                nwcWallet = nwcWallets.firstOrNull { it.id == walletInfo.walletId },
                hideBalance = hideBalance,
                backupWorking = backupWorking,
                onSelect = {
                    // The detail screen is NWC-only (balance/transactions); debits have neither.
                    if (walletInfo.canShowBalance) {
                        walletViewModel.selectWallet(walletInfo.walletId)
                        nav.nav(Route.WalletDetail(walletInfo.walletId))
                    }
                },
                onReceive = { nav.nav(Route.WalletReceive(walletInfo.walletId)) },
                onSend = { nav.nav(Route.WalletSend(walletInfo.walletId)) },
                onOpenSettings = { settingsFor = walletInfo.walletId },
                onSetDefault = {
                    walletViewModel.setDefaultWallet(walletInfo.walletId)
                },
                onRemove = {
                    walletViewModel.removeWallet(walletInfo.walletId)
                },
                // Spending-budget authorization is a CLINK-debit-only capability.
                onSetBudget =
                    if (!walletInfo.canShowBalance) {
                        { amount, frequency ->
                            walletViewModel.requestDebitBudget(walletInfo.walletId, amount, frequency) { response ->
                                val error = response?.failureDetail()
                                val msg =
                                    when {
                                        response?.isOk() == true -> budgetApprovedMsg
                                        !error.isNullOrBlank() -> error
                                        else -> debitNoResponseMsg
                                    }
                                longNotice.show(msg)
                            }
                        }
                    } else {
                        null
                    },
            )
        }

        if (hasCashuWallet) {
            item {
                CashuWalletRow(
                    balanceSats = cashuBalanceSats,
                    mintCount = cashuMintCount,
                    hideBalance = hideBalance,
                    onClick = { nav.nav(Route.CashuWallet) },
                )
            }
        }

        if (!backupCardDismissed && nwcWallets.isNotEmpty() && !backupEnabled) {
            item {
                NwcBackupSection(
                    walletViewModel = walletViewModel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { nav.nav(Route.WalletAdd) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringRes(Res.string.wallet_add_other_types))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    val sheetInfo = settingsFor?.let { id -> walletInfoList.firstOrNull { it.walletId == id } }
    val sheetWallet = settingsFor?.let { id -> nwcWallets.firstOrNull { it.id == id } }
    if (sheetInfo != null && sheetWallet != null) {
        WalletSettingsSheet(
            walletInfo = sheetInfo,
            nwcWallet = sheetWallet,
            walletViewModel = walletViewModel,
            onDismiss = { settingsFor = null },
            onSetDefault = { walletViewModel.setDefaultWallet(sheetInfo.walletId) },
            onRemove = {
                walletViewModel.removeWallet(sheetInfo.walletId)
                settingsFor = null
            },
        )
    }
}

@Composable
private fun NwcBackupSection(
    walletViewModel: WalletViewModel,
    modifier: Modifier = Modifier,
) {
    val backupEnabled by walletViewModel.backupEnabled.collectAsState()
    val backupBusy by walletViewModel.backupBusy.collectAsState()
    val backupMessage by walletViewModel.backupMessage.collectAsState()
    val shortNotice = rememberShortNotice()
    val noneFoundMsg = stringRes(Res.string.wallet_backup_none_found)

    val backupCardDismissed by walletViewModel.backupCardDismissed.collectAsState()
    if (backupCardDismissed) return

    var showEnableDialog by remember { mutableStateOf(false) }
    var restoreCandidate by remember { mutableStateOf<NwcBackupState.FoundNwcBackup?>(null) }

    LaunchedEffect(backupMessage) {
        if (backupMessage != null) {
            delay(6000)
            walletViewModel.clearBackupMessage()
        }
    }

    if (showEnableDialog) {
        BackupEnableDialog(
            onConfirm = {
                showEnableDialog = false
                walletViewModel.setBackupEnabled(true)
            },
            onDismiss = { showEnableDialog = false },
        )
    }

    restoreCandidate?.let { payload ->
        RestoreFoundDialog(
            backup = payload,
            onConfirm = {
                walletViewModel.applyRestoredBackup(payload, it)
                restoreCandidate = null
            },
            onDismiss = { restoreCandidate = null },
        )
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringRes(Res.string.wallet_backup_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (backupBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Switch(
                        checked = backupEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                showEnableDialog = true
                            } else {
                                walletViewModel.setBackupEnabled(false)
                            }
                        },
                    )
                }
            }
            Text(
                text = stringRes(Res.string.wallet_backup_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            backupMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    walletViewModel.fetchBackupForRestore { backup ->
                        if (backup == null) {
                            shortNotice.show(noneFoundMsg)
                        } else {
                            restoreCandidate = backup
                        }
                    }
                },
                enabled = !backupBusy,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringRes(Res.string.wallet_backup_restore), style = MaterialTheme.typography.bodySmall)
            }

            TextButton(
                onClick = { walletViewModel.dismissBackupCard() },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = stringRes(Res.string.wallet_backup_dismiss),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DefaultWalletIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = MaterialSymbols.Star,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringRes(Res.string.wallet_default),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun WalletCard(
    walletInfo: WalletInfo,
    nwcWallet: NwcWalletEntryNorm?,
    hideBalance: Boolean,
    backupWorking: Boolean,
    onSelect: () -> Unit,
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onOpenSettings: () -> Unit,
    onSetDefault: () -> Unit,
    onRemove: () -> Unit,
    onSetBudget: ((Long, DebitFrequency?) -> Unit)? = null,
) {
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }

    val subtitle =
        when {
            walletInfo.alias != null && walletInfo.alias != walletInfo.name -> walletInfo.name
            nwcWallet != null -> relayHostOf(nwcWallet)
            else -> null
        }

    if (showBudgetDialog && onSetBudget != null) {
        ClinkBudgetDialog(
            onConfirm = { amount, frequency ->
                showBudgetDialog = false
                onSetBudget(amount, frequency)
            },
            onDismiss = { showBudgetDialog = false },
        )
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringRes(Res.string.wallet_remove_confirm)) },
            text = { Text(stringRes(Res.string.wallet_remove_confirm_description)) },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    onRemove()
                }) {
                    Text(stringRes(Res.string.wallet_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(stringRes(Res.string.cancel))
                }
            },
        )
    }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = walletInfo.canShowBalance, onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        border =
            if (walletInfo.isDefault) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (walletInfo.isDefault) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (nwcWallet != null) {
                    BrandMark(PaymentBrandIcons.Nwc)
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = walletInfo.alias ?: walletInfo.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    when {
                        !walletInfo.canShowBalance -> {
                            Text(
                                text = stringRes(Res.string.clink_debit_pay_only),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        hideBalance -> BalanceDots(pulsing = false)

                        // Never a false 0: until the wallet answers, pulse.
                        walletInfo.balanceSats == null -> BalanceDots(pulsing = true)

                        else -> {
                            val formattedBalance =
                                remember(walletInfo.balanceSats) {
                                    formatGrouped(walletInfo.balanceSats)
                                }
                            Text(
                                text = formattedBalance,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (walletInfo.canShowBalance) {
                        Text(
                            text = stringRes(Res.string.wallet_sats),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (walletInfo.error != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = walletInfo.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (nwcWallet == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!walletInfo.isDefault) {
                        OutlinedButton(
                            onClick = onSetDefault,
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(MaterialSymbols.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringRes(Res.string.wallet_set_default), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (onSetBudget != null) {
                        OutlinedButton(
                            onClick = { showBudgetDialog = true },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(stringRes(Res.string.clink_budget_set), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = { showRemoveDialog = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            MaterialSymbols.Delete,
                            contentDescription = stringRes(Res.string.wallet_remove),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            symbol = MaterialSymbols.Settings,
                            contentDescription = stringRes(Res.string.wallet_settings),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (backupWorking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                        )
                    } else if (walletInfo.isBackedUp) {
                        Box(
                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34D399)),
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    OutlinedButton(
                        onClick = onReceive,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(MaterialSymbols.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringRes(Res.string.wallet_receive), style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = onSend,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(MaterialSymbols.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringRes(Res.string.wallet_send), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletSettingsSheet(
    walletInfo: WalletInfo,
    nwcWallet: NwcWalletEntryNorm,
    walletViewModel: WalletViewModel,
    onDismiss: () -> Unit,
    onSetDefault: () -> Unit,
    onRemove: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val shortNotice = rememberShortNotice()
    val copiedMsg = stringRes(Res.string.wallet_copied)
    val noneFoundMsg = stringRes(Res.string.wallet_backup_none_found)
    val copyToClipboard: (String) -> Unit = { value ->
        scope.launch { clipboard.setText(value) }
        shortNotice.show(copiedMsg)
    }
    val backupEnabled by walletViewModel.backupEnabled.collectAsState()
    val backupBusy by walletViewModel.backupBusy.collectAsState()
    val backupMessage by walletViewModel.backupMessage.collectAsState()

    var showEnableDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var restoreCandidate by remember { mutableStateOf<NwcBackupState.FoundNwcBackup?>(null) }
    var showBackupRelays by remember { mutableStateOf(false) }
    var showDeleteBackup by remember { mutableStateOf(false) }

    val confirmedBackupRelays by walletViewModel.backupRelays.collectAsState()
    val connectionString = remember(nwcWallet) { nwcWallet.uri.toConnectionString() }
    val serviceNpub =
        remember(nwcWallet) {
            nwcWallet.uri.pubKeyHex
                .hexToByteArray()
                .toNpub()
        }

    LaunchedEffect(backupMessage) {
        if (backupMessage != null) {
            delay(6000)
            walletViewModel.clearBackupMessage()
        }
    }

    if (showEnableDialog) {
        BackupEnableDialog(
            onConfirm = {
                showEnableDialog = false
                walletViewModel.setBackupEnabled(true)
            },
            onDismiss = { showEnableDialog = false },
        )
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringRes(Res.string.wallet_remove_confirm)) },
            text = { Text(stringRes(Res.string.wallet_remove_confirm_description)) },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    onRemove()
                }) {
                    Text(stringRes(Res.string.wallet_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(stringRes(Res.string.cancel))
                }
            },
        )
    }

    restoreCandidate?.let { payload ->
        RestoreFoundDialog(
            backup = payload,
            onConfirm = {
                walletViewModel.applyRestoredBackup(payload, it)
                restoreCandidate = null
            },
            onDismiss = { restoreCandidate = null },
        )
    }

    if (showDeleteBackup) {
        AlertDialog(
            onDismissRequest = { showDeleteBackup = false },
            title = { Text(stringRes(Res.string.wallet_backup_delete_title)) },
            text = { Text(stringRes(Res.string.wallet_backup_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteBackup = false
                    walletViewModel.deleteBackup()
                }) {
                    Text(
                        text = stringRes(Res.string.wallet_backup_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBackup = false }) {
                    Text(stringRes(Res.string.cancel))
                }
            },
        )
    }

    if (showBackupRelays) {
        AlertDialog(
            onDismissRequest = { showBackupRelays = false },
            title = { Text(stringRes(Res.string.wallet_backup_relays_title)) },
            text = {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(max = 320.dp),
                ) {
                    confirmedBackupRelays.forEach { relay ->
                        Text(
                            text = relay,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupRelays = false }) {
                    Text(stringRes(Res.string.close))
                }
            },
        )
    }

    // The half-open default would hide the destructive actions at the bottom.
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = walletInfo.alias ?: walletInfo.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = relayHostOf(nwcWallet),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            WalletDetailRow(
                label = stringRes(Res.string.wallet_details_wallet_service),
                value = shortenMiddle(serviceNpub),
                copyValue = serviceNpub,
                onCopy = copyToClipboard,
            )
            WalletDetailRow(
                label = stringRes(Res.string.wallet_details_relay),
                value = nwcWallet.uri.relayUri.url,
                copyValue = nwcWallet.uri.relayUri.url,
                onCopy = copyToClipboard,
            )
            nwcWallet.uri.lud16?.let { lud16 ->
                WalletDetailRow(
                    label = stringRes(Res.string.wallet_details_lud16),
                    value = lud16,
                    copyValue = lud16,
                    onCopy = copyToClipboard,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { copyToClipboard(connectionString) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(MaterialSymbols.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringRes(Res.string.wallet_copy_connection), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringRes(Res.string.wallet_backup_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (walletInfo.isBackedUp) {
                                            Color(0xFF34D399)
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                    ),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text =
                                stringRes(
                                    if (walletInfo.isBackedUp) {
                                        Res.string.wallet_backup_status_on
                                    } else {
                                        Res.string.wallet_backup_status_off
                                    },
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (backupBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Switch(
                        checked = backupEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                showEnableDialog = true
                            } else {
                                walletViewModel.setBackupEnabled(false)
                            }
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringRes(Res.string.wallet_backup_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            backupMessage?.let { message ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    walletViewModel.fetchBackupForRestore { payload ->
                        if (payload == null) {
                            shortNotice.show(noneFoundMsg)
                        } else {
                            restoreCandidate = payload
                        }
                    }
                },
                enabled = !backupBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(stringRes(Res.string.wallet_backup_restore), style = MaterialTheme.typography.bodySmall)
            }

            if (backupEnabled) {
                OutlinedButton(
                    onClick = { walletViewModel.republishBackup() },
                    enabled = !backupBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringRes(Res.string.wallet_backup_republish), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (confirmedBackupRelays.isNotEmpty()) {
                TextButton(
                    onClick = { showBackupRelays = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringRes(Res.string.wallet_backup_view_relays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (walletInfo.isBackedUp || confirmedBackupRelays.isNotEmpty()) {
                TextButton(
                    onClick = { showDeleteBackup = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringRes(Res.string.wallet_backup_delete),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            if (walletInfo.isDefault) {
                DefaultWalletIndicator(modifier = Modifier.fillMaxWidth().height(40.dp))
            } else {
                OutlinedButton(
                    onClick = onSetDefault,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(MaterialSymbols.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringRes(Res.string.wallet_set_default), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { showRemoveDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    MaterialSymbols.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringRes(Res.string.wallet_remove),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun BackupEnableDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.wallet_backup_enable_title)) },
        text = { Text(stringRes(Res.string.wallet_backup_disclaimer)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(Res.string.wallet_backup_enable_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

@Composable
internal fun RestoreFoundDialog(
    backup: NwcBackupState.FoundNwcBackup,
    onConfirm: (List<NwcBackupState.RestorableWallet>) -> Unit,
    onDismiss: () -> Unit,
) {
    // An older backup may hold a revoked connection, so only the newest one's wallets start checked.
    val newest = backup.wallets.first()
    var selected by remember(backup) {
        mutableStateOf(backup.wallets.filter { it.dTag == newest.dTag && it.savedAt == newest.savedAt }.toSet())
    }
    val choosable = backup.wallets.size > 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.wallet_backup_restore_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(pluralStringRes(Res.plurals.wallet_backup_restore_found, backup.wallets.size, backup.wallets.size))
                Spacer(modifier = Modifier.height(8.dp))
                backup.wallets.forEach { candidate ->
                    val checked = candidate in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            if (choosable) {
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = if (checked) selected - candidate else selected + candidate }
                            } else {
                                Modifier.fillMaxWidth()
                            },
                    ) {
                        if (choosable) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { selected = if (it) selected + candidate else selected - candidate },
                            )
                        }
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = candidate.wallet.uri.lud16 ?: candidate.wallet.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text =
                                    stringRes(
                                        Res.string.wallet_backup_saved_by,
                                        NwcBackupFormat.appName(candidate.dTag),
                                        formatDateTime(candidate.savedAt * 1000L, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT),
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(backup.wallets.filter { it in selected }) },
                enabled = selected.isNotEmpty(),
            ) {
                Text(stringRes(Res.string.wallet_backup_restore_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

@Composable
internal fun BalanceDots(pulsing: Boolean) {
    val alpha =
        if (pulsing) {
            val transition = rememberInfiniteTransition(label = "balanceDots")
            transition
                .animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 500),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "balanceDotsAlpha",
                ).value
        } else {
            1f
        }
    Text(
        text = BALANCE_DOTS,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
    )
}

/** A labelled value with an optional copy button; shared with the transaction details. */
@Composable
internal fun WalletDetailRow(
    label: String,
    value: String,
    copyValue: String? = null,
    onCopy: (String) -> Unit = {},
    monospace: Boolean = true,
    maxLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
        if (copyValue != null) {
            IconButton(
                onClick = { onCopy(copyValue) },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.ContentCopy,
                    contentDescription = stringRes(Res.string.wallet_copy),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// A rail's official full-color mark, sized like the Bitcoin chip so the three
// rails line up.
@Composable
private fun BrandMark(icon: ImageVector) {
    Material3Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier.size(28.dp),
    )
}

internal const val BALANCE_DOTS = "\u2022\u2022\u2022"

private fun relayHostOf(wallet: NwcWalletEntryNorm): String =
    wallet.uri.relayUri.url
        .removePrefix("wss://")
        .removePrefix("ws://")
        .trimEnd('/')

internal fun shortenMiddle(
    value: String,
    head: Int = 12,
    tail: Int = 6,
): String = if (value.length <= head + tail + 2) value else value.take(head) + "\u2026" + value.takeLast(tail)

@Composable
private fun CashuWalletRow(
    balanceSats: Long,
    mintCount: Int,
    hideBalance: Boolean,
    onClick: () -> Unit,
) {
    val formattedBalance =
        remember(balanceSats) { formatGrouped(balanceSats) }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        // Same rail-card treatment as the on-chain card: a 2dp brand stroke over
        // a faint tint of the brand color.
        border = BorderStroke(2.dp, PaymentBrandIcons.CashuAccent),
        colors =
            CardDefaults.cardColors(
                containerColor = PaymentBrandIcons.CashuAccent.copy(alpha = 0.12f),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(PaymentBrandIcons.Cashu)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringRes(Res.string.cashu_wallet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text =
                        pluralStringRes(
                            Res.plurals.cashu_wallet_subtitle_mints,
                            mintCount,
                            mintCount,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (hideBalance) {
                    Text(
                        text = BALANCE_DOTS,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        text = formattedBalance,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringRes(Res.string.wallet_sats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
