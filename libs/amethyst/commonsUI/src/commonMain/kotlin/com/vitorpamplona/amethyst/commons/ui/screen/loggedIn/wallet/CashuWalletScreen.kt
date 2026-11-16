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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip60Cashu.CashuWalletHistoryEoseManager
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.cashu_action_receive
import com.vitorpamplona.amethyst.commons.resources.cashu_action_redeem
import com.vitorpamplona.amethyst.commons.resources.cashu_action_send_ln
import com.vitorpamplona.amethyst.commons.resources.cashu_action_send_token
import com.vitorpamplona.amethyst.commons.resources.cashu_amount_sats
import com.vitorpamplona.amethyst.commons.resources.cashu_balance
import com.vitorpamplona.amethyst.commons.resources.cashu_building_token
import com.vitorpamplona.amethyst.commons.resources.cashu_checking_mint
import com.vitorpamplona.amethyst.commons.resources.cashu_completing_mint
import com.vitorpamplona.amethyst.commons.resources.cashu_copy_invoice
import com.vitorpamplona.amethyst.commons.resources.cashu_copy_token
import com.vitorpamplona.amethyst.commons.resources.cashu_create_token
import com.vitorpamplona.amethyst.commons.resources.cashu_discard_invoice
import com.vitorpamplona.amethyst.commons.resources.cashu_discovering
import com.vitorpamplona.amethyst.commons.resources.cashu_discovering_description
import com.vitorpamplona.amethyst.commons.resources.cashu_done
import com.vitorpamplona.amethyst.commons.resources.cashu_get_quote
import com.vitorpamplona.amethyst.commons.resources.cashu_getting_quote
import com.vitorpamplona.amethyst.commons.resources.cashu_history
import com.vitorpamplona.amethyst.commons.resources.cashu_history_all_loaded
import com.vitorpamplona.amethyst.commons.resources.cashu_history_incoming
import com.vitorpamplona.amethyst.commons.resources.cashu_history_loading_older
import com.vitorpamplona.amethyst.commons.resources.cashu_history_outgoing
import com.vitorpamplona.amethyst.commons.resources.cashu_history_some_relays_unreachable
import com.vitorpamplona.amethyst.commons.resources.cashu_invoice_bolt11
import com.vitorpamplona.amethyst.commons.resources.cashu_memo_optional
import com.vitorpamplona.amethyst.commons.resources.cashu_mints
import com.vitorpamplona.amethyst.commons.resources.cashu_move_coins_body
import com.vitorpamplona.amethyst.commons.resources.cashu_move_coins_done
import com.vitorpamplona.amethyst.commons.resources.cashu_move_coins_export_token
import com.vitorpamplona.amethyst.commons.resources.cashu_move_coins_fee_hint
import com.vitorpamplona.amethyst.commons.resources.cashu_move_coins_move
import com.vitorpamplona.amethyst.commons.resources.cashu_move_coins_no_trusted
import com.vitorpamplona.amethyst.commons.resources.cashu_move_coins_title
import com.vitorpamplona.amethyst.commons.resources.cashu_move_coins_to_mint
import com.vitorpamplona.amethyst.commons.resources.cashu_move_coins_withdraw_ln
import com.vitorpamplona.amethyst.commons.resources.cashu_move_coins_working
import com.vitorpamplona.amethyst.commons.resources.cashu_no_wallet
import com.vitorpamplona.amethyst.commons.resources.cashu_no_wallet_description
import com.vitorpamplona.amethyst.commons.resources.cashu_paid_amount
import com.vitorpamplona.amethyst.commons.resources.cashu_pay_invoice
import com.vitorpamplona.amethyst.commons.resources.cashu_paying_invoice
import com.vitorpamplona.amethyst.commons.resources.cashu_pending_quotes_resume
import com.vitorpamplona.amethyst.commons.resources.cashu_pending_quotes_subtitle
import com.vitorpamplona.amethyst.commons.resources.cashu_pending_quotes_title
import com.vitorpamplona.amethyst.commons.resources.cashu_pick_mint
import com.vitorpamplona.amethyst.commons.resources.cashu_quote_confirm
import com.vitorpamplona.amethyst.commons.resources.cashu_receive_invoice_explainer
import com.vitorpamplona.amethyst.commons.resources.cashu_received_amount
import com.vitorpamplona.amethyst.commons.resources.cashu_redeem_button
import com.vitorpamplona.amethyst.commons.resources.cashu_redeemed_amount
import com.vitorpamplona.amethyst.commons.resources.cashu_redeeming
import com.vitorpamplona.amethyst.commons.resources.cashu_request_invoice
import com.vitorpamplona.amethyst.commons.resources.cashu_requesting_invoice
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_title
import com.vitorpamplona.amethyst.commons.resources.cashu_token_label
import com.vitorpamplona.amethyst.commons.resources.cashu_token_ready
import com.vitorpamplona.amethyst.commons.resources.cashu_untrusted_mint_badge
import com.vitorpamplona.amethyst.commons.resources.cashu_untrusted_mint_move
import com.vitorpamplona.amethyst.commons.resources.cashu_untrusted_mint_subtitle
import com.vitorpamplona.amethyst.commons.resources.cashu_untrusted_mint_title
import com.vitorpamplona.amethyst.commons.resources.cashu_waiting_for_payment
import com.vitorpamplona.amethyst.commons.resources.cashu_wallet_title
import com.vitorpamplona.amethyst.commons.resources.paste_from_clipboard
import com.vitorpamplona.amethyst.commons.resources.topup_mint_action
import com.vitorpamplona.amethyst.commons.resources.wallet_add_cashu_title
import com.vitorpamplona.amethyst.commons.resources.wallet_sats
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.util.getText
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.wallet.CashuMeltFlowState
import com.vitorpamplona.amethyst.commons.wallet.CashuMintFlowState
import com.vitorpamplona.amethyst.commons.wallet.CashuRebalanceFlowState
import com.vitorpamplona.amethyst.commons.wallet.CashuRedeemFlowState
import com.vitorpamplona.amethyst.commons.wallet.CashuSendTokenFlowState
import com.vitorpamplona.amethyst.commons.wallet.CashuWalletViewModel
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.history.SpendingDirection
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon as Material3Icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashuWalletScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: CashuWalletViewModel = rememberViewModel { CashuWalletViewModel() }
    // Synchronous init so state-flow getters don't hit a null `account` on
    // the first composition pass. init() is idempotent — just sets refs.
    viewModel.init(accountViewModel)

    val walletEvent by viewModel.walletEvent.collectAsState()
    val discovering by viewModel.discovering.collectAsState()
    // `mints` is the configured (kind:17375) list — used by the send/receive
    // dialogs. `displayMints` adds any mint we merely hold tokens at so the
    // per-mint rows below sum to the full balance.
    val mints by viewModel.mints.collectAsState()
    val displayMints by viewModel.displayMints.collectAsState()
    // Mints we hold a balance at but never configured (typically coins from a
    // nutzap auto-redeemed on a mint outside our kind:10019). Highlighted so
    // the user can move them somewhere they trust.
    val unconfiguredMintBalances by viewModel.unconfiguredMintBalances.collectAsState()
    val balanceSats by viewModel.balanceSats.collectAsState()
    val mintBalances by viewModel.mintBalances.collectAsState()
    val history by viewModel.history.collectAsState()
    val pendingQuotes by viewModel.pendingQuotes.collectAsState()

    // Reconcile every mint we hold tokens at whenever the wallet opens —
    // sweeps stale proofs across all mints, not just the one a spend
    // targets, so a balance auto-redeemed from a mint not in our configured
    // list (e.g. a nutzap on a mint outside our kind:10019) still gets
    // checked. No-ops when the wallet is empty or nothing is stale.
    LaunchedEffect(walletEvent != null) {
        if (walletEvent != null) viewModel.refresh()
    }

    // Once discovery resolves to "no wallet here", drive the user into the
    // find-or-create wizard (which crawls every relay for a portable wallet
    // before offering to create one). rememberSaveable (not remember) so the
    // guard survives navigating into the wizard: this screen leaves composition
    // on forward-nav, and a plain remember would reset to false on back, then
    // re-fire the effect and trap the user in an inescapable wizard loop.
    var autoLaunchedWizard by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(walletEvent == null && !discovering) {
        if (walletEvent == null && !discovering && !autoLaunchedWizard) {
            autoLaunchedWizard = true
            nav.nav(Route.CashuWalletWizard)
        }
    }

    var receiveOpen by remember { mutableStateOf(false) }
    var sendLnOpen by remember { mutableStateOf(false) }
    var sendTokenOpen by remember { mutableStateOf(false) }
    var redeemOpen by remember { mutableStateOf(false) }
    // The unconfigured mint the user chose to move coins off of, plus the
    // source mint to pre-select when the Send dialogs are opened from that
    // flow (null = the dialog picks its own default).
    var evacuateMint by remember { mutableStateOf<String?>(null) }
    var sendInitialMint by remember { mutableStateOf<String?>(null) }

    // pendingQuotes drives a non-modal banner in the wallet body (see
    // PendingQuoteBanner below). Tapping the banner is what opens the
    // Receive dialog with the stored quote pre-loaded — we do NOT auto-pop
    // the dialog on every entry to the screen, which would re-surface
    // dismissed quotes every time the user navigates back.

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.cashu_wallet_title)) },
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
                    IconButton(onClick = { nav.nav(Route.CashuWalletSettings) }) {
                        Icon(
                            symbol = MaterialSymbols.Settings,
                            contentDescription = stringRes(Res.string.cashu_settings_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            walletEvent != null ->
                CashuWalletContent(
                    modifier = Modifier.padding(padding),
                    balanceSats = balanceSats,
                    mints = displayMints,
                    mintBalances = mintBalances,
                    unconfiguredMints = unconfiguredMintBalances.keys,
                    history = history,
                    pendingQuoteCount = pendingQuotes.size,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onReceive = { receiveOpen = true },
                    onSendLn = { sendLnOpen = true },
                    onSendToken = { sendTokenOpen = true },
                    onRedeem = { redeemOpen = true },
                    onTopUpMint = { nav.nav(Route.TopUpMint(it)) },
                    onMoveCoins = { evacuateMint = it },
                    onResumePendingQuote = {
                        pendingQuotes.firstOrNull()?.let {
                            viewModel.resumeMintQuote(it)
                            receiveOpen = true
                        }
                    },
                )

            // NIP-60 wallets are portable across clients — show a "looking
            // for your wallet" state until the relay subscription returns
            // anything (or times out). Without this, a user who created
            // their wallet in another app would see the Create CTA on
            // first launch and would clobber the remote kind:17375.
            discovering ->
                DiscoveringCashuWallet(modifier = Modifier.padding(padding))

            else ->
                EmptyCashuWallet(
                    modifier = Modifier.padding(padding),
                    onCreate = { nav.nav(Route.CashuWalletWizard) },
                )
        }
    }

    if (receiveOpen) {
        ReceiveDialog(
            viewModel = viewModel,
            mints = mints,
            onDismiss = {
                receiveOpen = false
                viewModel.resetMintState()
            },
        )
    }
    if (sendLnOpen) {
        SendLnDialog(
            viewModel = viewModel,
            // displayMints (not just configured) so an unconfigured mint the
            // user is evacuating is a valid source.
            mints = displayMints,
            initialMint = sendInitialMint,
            onDismiss = {
                sendLnOpen = false
                sendInitialMint = null
                viewModel.resetMeltState()
            },
        )
    }
    if (sendTokenOpen) {
        SendTokenDialog(
            viewModel = viewModel,
            mints = displayMints,
            initialMint = sendInitialMint,
            onDismiss = {
                sendTokenOpen = false
                sendInitialMint = null
                viewModel.resetSendTokenState()
            },
        )
    }
    if (redeemOpen) {
        RedeemDialog(
            viewModel = viewModel,
            onDismiss = {
                redeemOpen = false
                viewModel.resetRedeemState()
            },
        )
    }

    evacuateMint?.let { source ->
        EvacuateMintDialog(
            viewModel = viewModel,
            sourceMint = source,
            sourceBalance = mintBalances[source] ?: 0L,
            // Trusted destinations for a rebalance: configured mints other
            // than the one we're emptying.
            trustedTargets = mints.filter { it != source },
            onWithdrawLightning = {
                sendInitialMint = source
                evacuateMint = null
                sendLnOpen = true
            },
            onExportToken = {
                sendInitialMint = source
                evacuateMint = null
                sendTokenOpen = true
            },
            onDismiss = {
                evacuateMint = null
                viewModel.resetRebalanceState()
            },
        )
    }
}

@Composable
private fun DiscoveringCashuWallet(modifier: Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringRes(Res.string.cashu_discovering),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringRes(Res.string.cashu_discovering_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyCashuWallet(
    modifier: Modifier,
    onCreate: () -> Unit,
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
            text = stringRes(Res.string.cashu_no_wallet),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringRes(Res.string.cashu_no_wallet_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onCreate) {
            Text(stringRes(Res.string.wallet_add_cashu_title))
        }
    }
}

@Composable
private fun CashuWalletContent(
    modifier: Modifier,
    balanceSats: Long,
    mints: List<String>,
    mintBalances: Map<String, Long>,
    unconfiguredMints: Set<String>,
    history: List<CashuSpendingHistoryEvent>,
    pendingQuoteCount: Int,
    accountViewModel: AccountViewModel,
    nav: INav,
    onReceive: () -> Unit,
    onSendLn: () -> Unit,
    onSendToken: () -> Unit,
    onRedeem: () -> Unit,
    onTopUpMint: (String) -> Unit,
    onMoveCoins: (String) -> Unit,
    onResumePendingQuote: () -> Unit,
) {
    val listState = rememberLazyListState()

    // The kind:7376 rows below are only ever as complete as the relays were asked to be. The live wallet
    // subscription asks for six kinds in one uncapped REQ, and history is the most numerous of them, so
    // what lands there is a recent-N suffix chosen by each relay's cap. This pager walks older pages on
    // demand — see CashuWalletHistoryEoseManager.
    val history7376 = remember(accountViewModel) { accountViewModel.dataSources().account.cashuWalletHistory }
    val loadingOlder by history7376.loadingMore.collectAsStateWithLifecycle()
    val pagingStatus by history7376.status.collectAsStateWithLifecycle()
    CashuHistoryPaging(historyCount = { history.size }, listState = listState, history = history7376)

    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            BalanceCard(balanceSats)
        }

        if (pendingQuoteCount > 0) {
            item { PendingQuoteBanner(count = pendingQuoteCount, onResume = onResumePendingQuote) }
        }

        if (unconfiguredMints.isNotEmpty()) {
            item {
                UntrustedMintBanner(
                    count = unconfiguredMints.size,
                    onClick = { unconfiguredMints.firstOrNull()?.let(onMoveCoins) },
                )
            }
        }

        item {
            ActionRow(
                onReceive = onReceive,
                onSendLn = onSendLn,
                onSendToken = onSendToken,
                onRedeem = onRedeem,
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringRes(Res.string.cashu_mints),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(mints, key = { it }) { mint ->
            val isUntrusted = mint in unconfiguredMints
            MintRow(
                mint = mint,
                balanceSats = mintBalances[mint] ?: 0L,
                untrusted = isUntrusted,
                onTopUp = { onTopUpMint(mint) },
                onMoveCoins = if (isUntrusted) ({ onMoveCoins(mint) }) else null,
            )
        }

        if (history.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringRes(Res.string.cashu_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(history, key = { it.id }) { entry ->
                HistoryRow(entry, accountViewModel, nav)
            }
            item {
                CashuHistoryFooter(
                    loadingOlder = loadingOlder,
                    exhausted = pagingStatus.exhausted,
                    stalledCount = pagingStatus.stalledCount,
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

/** How many history rows to pull in before the user has scrolled at all. */
private const val CASHU_HISTORY_TARGET = 30

/** How close to the end of the list a page request fires. */
private const val CASHU_HISTORY_PREFETCH_AHEAD = 5

/**
 * Drives the spending-history backward pager: pull a page on open so the list isn't whatever suffix the
 * relay caps returned, then page older rows as the list nears its end. Same two-driver shape the NIP-29
 * thread list and the notifications feed use — a bootstrap that fills the first screen, and a look-ahead
 * that keeps going only while the user is actually scrolling toward the bottom.
 */
@Composable
private fun CashuHistoryPaging(
    historyCount: () -> Int,
    listState: LazyListState,
    history: CashuWalletHistoryEoseManager,
) {
    LaunchedEffect(history) {
        combine(snapshotFlow { historyCount() }, history.loadingMore, history.status) { count, loading, s ->
            count < CASHU_HISTORY_TARGET && !loading && !s.exhausted
        }.distinctUntilChanged()
            .filter { it }
            .collect { history.advanceAll() }
    }
    LaunchedEffect(history, listState) {
        snapshotFlow {
            val last =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val total = historyCount()
            total > 0 && last >= total - CASHU_HISTORY_PREFETCH_AHEAD
        }.distinctUntilChanged()
            .filter { it }
            .collect {
                if (!history.status.value.exhausted && !history.loadingMore.value) history.advanceAll()
            }
    }
}

/**
 * A quiet footer under the transaction list: what the pager is doing, or nothing when idle.
 *
 * Splits on [stalledCount] because `exhausted` means "nothing more reachable right now", not "caught
 * up" — a relay that answered an auth CLOSE, is unreachable, or went silent is stalled rather than done,
 * and claiming the history is complete while some of it was never served would be a lie about the user's
 * own money.
 */
@Composable
private fun CashuHistoryFooter(
    loadingOlder: Boolean,
    exhausted: Boolean,
    stalledCount: Int,
) {
    val text =
        when {
            loadingOlder -> stringRes(Res.string.cashu_history_loading_older)
            exhausted && stalledCount > 0 -> stringRes(Res.string.cashu_history_some_relays_unreachable)
            exhausted -> stringRes(Res.string.cashu_history_all_loaded)
            else -> return
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

/**
 * Banner that surfaces unfinished mint quotes — tappable to resume the
 * receive flow with the stored invoice. Driven by
 * [com.vitorpamplona.amethyst.commons.model.nip60Cashu.CashuWalletState.pendingQuotes].
 *
 * Replaces the earlier auto-popup behaviour which re-surfaced the Receive
 * dialog on every entry to the screen.
 */
@Composable
private fun PendingQuoteBanner(
    count: Int,
    onResume: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onResume),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.Bolt,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        pluralStringRes(
                            Res.plurals.cashu_pending_quotes_title,
                            count,
                            count,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringRes(Res.string.cashu_pending_quotes_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            Text(
                text = stringRes(Res.string.cashu_pending_quotes_resume),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * Surfaced above the mint list when we hold a balance at one or more mints
 * the user never configured — almost always coins auto-redeemed from a
 * NIP-61 nutzap sent on a mint outside our kind:10019. Tapping it opens
 * [EvacuateMintDialog] for the first such mint so the user can move the
 * funds somewhere they trust.
 */
@Composable
private fun UntrustedMintBanner(
    count: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        pluralStringRes(
                            Res.plurals.cashu_untrusted_mint_title,
                            count,
                            count,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringRes(Res.string.cashu_untrusted_mint_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                )
            }
            Text(
                text = stringRes(Res.string.cashu_untrusted_mint_move),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * Helps the user get coins OFF a mint they never configured (almost always
 * a nutzap redeemed on an untrusted mint), offering the three exits whose
 * backends already exist:
 *   - rebalance to a mint the user trusts (no new Lightning sats),
 *   - withdraw via Lightning (hands off to the Send-LN dialog),
 *   - export as a Cashu token (hands off to the Send-token dialog).
 * Only the rebalance runs inline; the Lightning / token paths reuse the
 * existing, tested Send dialogs pre-pointed at this mint.
 */
@Composable
private fun EvacuateMintDialog(
    viewModel: CashuWalletViewModel,
    sourceMint: String,
    sourceBalance: Long,
    trustedTargets: List<String>,
    onWithdrawLightning: () -> Unit,
    onExportToken: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.rebalanceState.collectAsState()
    var target by remember { mutableStateOf(trustedTargets.firstOrNull() ?: "") }
    // Defaults to the whole balance, but rebalance deducts a Lightning fee
    // from the source, so the user may have to shave a little off — the fee
    // is only known once the mint returns a melt quote.
    var amount by remember { mutableStateOf(sourceBalance.toString()) }
    val busy = state is CashuRebalanceFlowState.Working
    val done = state is CashuRebalanceFlowState.Completed

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringRes(Res.string.cashu_move_coins_title)) },
        text = {
            Column {
                Text(
                    text = stringRes(Res.string.cashu_move_coins_body, sourceMint, sourceBalance.toString()),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(12.dp))

                when (val s = state) {
                    is CashuRebalanceFlowState.Working -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes(Res.string.cashu_move_coins_working))
                        }
                    }

                    is CashuRebalanceFlowState.Completed -> {
                        Text(
                            text =
                                stringRes(
                                    Res.string.cashu_move_coins_done,
                                    s.movedSats.toString(),
                                    s.targetMintUrl,
                                ),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    is CashuRebalanceFlowState.Error -> {
                        Text(
                            text = s.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    CashuRebalanceFlowState.Idle -> {}
                }

                if (!busy && !done) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (trustedTargets.isNotEmpty()) {
                        Text(
                            text = stringRes(Res.string.cashu_move_coins_to_mint),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { v -> amount = v.filter { it.isDigit() } },
                            label = { Text(stringRes(Res.string.cashu_amount_sats)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringRes(Res.string.cashu_move_coins_fee_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        MintPicker(trustedTargets, target, { target = it })
                        TextButton(
                            onClick = {
                                amount.toLongOrNull()?.let { viewModel.rebalanceOut(sourceMint, target, it) }
                            },
                            enabled = target.isNotBlank() && (amount.toLongOrNull() ?: 0L) > 0L,
                        ) { Text(stringRes(Res.string.cashu_move_coins_move)) }
                    } else {
                        Text(
                            text = stringRes(Res.string.cashu_move_coins_no_trusted),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = onWithdrawLightning) {
                        Text(stringRes(Res.string.cashu_move_coins_withdraw_ln))
                    }
                    TextButton(onClick = onExportToken) {
                        Text(stringRes(Res.string.cashu_move_coins_export_token))
                    }
                }
            }
        },
        confirmButton = {
            if (done) {
                TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cashu_done)) }
            }
        },
        dismissButton = {
            if (!busy && !done) {
                TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
            }
        },
    )
}

@Composable
private fun BalanceCard(balanceSats: Long) {
    val formatted =
        remember(balanceSats) {
            formatGrouped(balanceSats)
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringRes(Res.string.cashu_balance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatted,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringRes(Res.string.wallet_sats),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ActionRow(
    onReceive: () -> Unit,
    onSendLn: () -> Unit,
    onSendToken: () -> Unit,
    onRedeem: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionTile(MaterialSymbols.Bolt, stringRes(Res.string.cashu_action_receive), onReceive, Modifier.weight(1f))
        ActionTile(MaterialSymbols.AutoMirrored.Send, stringRes(Res.string.cashu_action_send_ln), onSendLn, Modifier.weight(1f))
        ActionTile(MaterialSymbols.ContentPaste, stringRes(Res.string.cashu_action_send_token), onSendToken, Modifier.weight(1f))
        ActionTile(MaterialSymbols.Add, stringRes(Res.string.cashu_action_redeem), onRedeem, Modifier.weight(1f))
    }
}

/**
 * Square-ish tile with the icon stacked over a 1-2 line label.
 *
 * Replaces an earlier OutlinedButton-per-tile layout: with 4 tiles in a row
 * and OutlinedButton's default 24dp horizontal padding plus a 20dp icon,
 * the "Send Token" label clipped on standard 360dp-wide phones. A Surface
 * with explicit padding gives us full control over the content area.
 */
@Composable
private fun ActionTile(
    icon: MaterialSymbol,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier =
                Modifier
                    .clickable(onClick = onClick)
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                symbol = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MintRow(
    mint: String,
    balanceSats: Long,
    untrusted: Boolean,
    onTopUp: () -> Unit,
    onMoveCoins: (() -> Unit)?,
) {
    val formattedBalance =
        remember(balanceSats) {
            formatGrouped(balanceSats)
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (untrusted) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Material3Icon(
                imageVector = CustomHashTagIcons.Cashu,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = mint, style = MaterialTheme.typography.bodyMedium)
                if (untrusted) {
                    Text(
                        text = stringRes(Res.string.cashu_untrusted_mint_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$formattedBalance ${stringRes(Res.string.wallet_sats)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (onMoveCoins != null) {
                // Untrusted mint: lead with "move coins out", not "top up".
                IconButton(onClick = onMoveCoins) {
                    Icon(
                        symbol = MaterialSymbols.AutoMirrored.Send,
                        contentDescription = stringRes(Res.string.cashu_untrusted_mint_move),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                IconButton(onClick = onTopUp) {
                    Icon(
                        symbol = MaterialSymbols.AddCircle,
                        contentDescription = stringRes(Res.string.topup_mint_action),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Renders one kind:7376 history event in the same visual language as the
 * LN + on-chain transaction lists: counterparty avatar on the left,
 * description on top, timestamp underneath, signed amount on the right.
 *
 * For inbound nutzap redemptions (kind:9321 referenced with a
 * "redeemed" marker on the kind:7376) we can resolve the sender's
 * Nostr pubkey from LocalCache and show their profile picture + name.
 * For everything else (LN mint/melt, send-as-token, redeem cashuB)
 * there is no Nostr counterparty, so we fall back to a directional
 * arrow icon matching the LN wallet's empty-counterparty pattern.
 */
@Composable
private fun HistoryRow(
    entry: CashuSpendingHistoryEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val signer = accountViewModel.account.signer

    // Decrypt the encrypted half of the spending history once per row.
    // Cheap (NIP-44 on a few hundred bytes) and Compose memoizes the
    // produceState by entry.id so it doesn't repeat on recomposition.
    val decoded by produceState<DecodedHistoryRow?>(initialValue = null, key1 = entry.id) {
        value = decodeHistoryRow(entry, signer)
    }

    val nutzapSenderHex =
        remember(decoded) {
            // Sender lookup: the redeemed-marker `e` tag points at the kind:9321
            // event id; if it's already in LocalCache (we get it via our cashu
            // wallet filter assembler) its event.pubKey is the sender.
            entry.redeemedReferences().firstNotNullOfOrNull { ref ->
                LocalCache
                    .getOrCreateNote(ref.eventId)
                    .event
                    ?.takeIf { it is NutzapEvent }
                    ?.pubKey
            }
        }

    val isIncoming = decoded?.direction == SpendingDirection.IN

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left avatar / icon.
            if (nutzapSenderHex != null) {
                UserPicture(
                    userHex = nutzapSenderHex,
                    size = 40.dp,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            } else {
                Icon(
                    symbol = if (isIncoming) MaterialSymbols.ArrowDownward else MaterialSymbols.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint =
                        if (isIncoming) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            Spacer(modifier = Modifier.width(12.dp))

            // Middle: title + timestamp.
            Column(modifier = Modifier.weight(1f)) {
                if (nutzapSenderHex != null) {
                    HistoryUserName(nutzapSenderHex, accountViewModel)
                } else {
                    Text(
                        text =
                            stringRes(
                                if (isIncoming) {
                                    Res.string.cashu_history_incoming
                                } else {
                                    Res.string.cashu_history_outgoing
                                },
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text =
                        formatDateTime(entry.createdAt * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Right: signed amount.
            val amount = decoded?.amount
            if (amount != null) {
                Text(
                    text = (if (isIncoming) "+" else "−") + formatGrouped(amount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (isIncoming) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

private data class DecodedHistoryRow(
    val direction: SpendingDirection?,
    val amount: Long?,
)

private suspend fun decodeHistoryRow(
    entry: CashuSpendingHistoryEvent,
    signer: NostrSigner,
): DecodedHistoryRow =
    runCatching {
        DecodedHistoryRow(
            direction = entry.direction(signer),
            amount = entry.amount(signer),
        )
    }.getOrNull() ?: DecodedHistoryRow(direction = null, amount = null)

@Composable
private fun HistoryUserName(
    pubkeyHex: String,
    accountViewModel: AccountViewModel,
) {
    LoadUser(baseUserHex = pubkeyHex) { user ->
        if (user != null) {
            UsernameDisplay(
                baseUser = user,
                fontWeight = FontWeight.Medium,
                accountViewModel = accountViewModel,
            )
        } else {
            Text(
                text = pubkeyHex.take(8) + "…",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ============================================================================
// Receive (mint from LN)
// ============================================================================

@Composable
private fun ReceiveDialog(
    viewModel: CashuWalletViewModel,
    mints: List<String>,
    onDismiss: () -> Unit,
) {
    val state by viewModel.mintState.collectAsState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var amount by remember { mutableStateOf("") }
    var pickedMint by remember { mutableStateOf(mints.firstOrNull() ?: "") }

    // Poll every 3s while waiting for payment.
    LaunchedEffect(state) {
        if (state is CashuMintFlowState.AwaitingPayment) {
            while (true) {
                delay(3000)
                viewModel.checkAndCompleteMint()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.cashu_action_receive)) },
        text = {
            Column {
                when (val s = state) {
                    is CashuMintFlowState.AwaitingPayment -> {
                        Text(
                            stringRes(Res.string.cashu_receive_invoice_explainer, s.amountSats.toString()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = s.flow.invoice,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringRes(Res.string.cashu_invoice_bolt11)) },
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            scope.launch { clipboard.setText(s.flow.invoice) }
                        }) {
                            Icon(
                                symbol = MaterialSymbols.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringRes(Res.string.cashu_copy_invoice))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // The mint is polled every 3s in the background. We
                        // keep the invoice on screen and only toggle this
                        // status line between "waiting" and "checking" so the
                        // dialog never swaps its whole body mid-poll.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringRes(
                                    if (s.checking) {
                                        Res.string.cashu_checking_mint
                                    } else {
                                        Res.string.cashu_waiting_for_payment
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    is CashuMintFlowState.Completing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes(Res.string.cashu_completing_mint))
                        }
                    }

                    is CashuMintFlowState.Completed -> {
                        Text(stringRes(Res.string.cashu_received_amount, s.amountSats.toString()))
                    }

                    is CashuMintFlowState.Error -> {
                        Text(
                            text = s.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FormReceive(
                            mints = mints,
                            amount = amount,
                            onAmountChange = { amount = it },
                            pickedMint = pickedMint,
                            onMintChange = { pickedMint = it },
                        )
                    }

                    CashuMintFlowState.Requesting -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes(Res.string.cashu_requesting_invoice))
                        }
                    }

                    CashuMintFlowState.Idle -> {
                        FormReceive(
                            mints = mints,
                            amount = amount,
                            onAmountChange = { amount = it },
                            pickedMint = pickedMint,
                            onMintChange = { pickedMint = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is CashuMintFlowState.Idle, is CashuMintFlowState.Error -> {
                    TextButton(
                        onClick = {
                            val n = amount.toLongOrNull() ?: 0L
                            viewModel.startMintFromLightning(pickedMint, n)
                        },
                        enabled = amount.isNotBlank() && pickedMint.isNotBlank(),
                    ) { Text(stringRes(Res.string.cashu_request_invoice)) }
                }

                is CashuMintFlowState.AwaitingPayment -> {
                    // While the user is waiting for an invoice they may
                    // decide they don't want it after all (typo'd amount,
                    // wrong mint, lost interest). Discard NIP-09-deletes
                    // the kind:7374 so the pending-invoice banner won't
                    // re-surface it on next entry.
                    TextButton(onClick = { viewModel.discardMintQuote() }) {
                        Text(stringRes(Res.string.cashu_discard_invoice))
                    }
                }

                is CashuMintFlowState.Completed -> {
                    TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cashu_done)) }
                }

                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}

@Composable
private fun FormReceive(
    mints: List<String>,
    amount: String,
    onAmountChange: (String) -> Unit,
    pickedMint: String,
    onMintChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = amount,
        onValueChange = { v -> onAmountChange(v.filter { it.isDigit() }) },
        label = { Text(stringRes(Res.string.cashu_amount_sats)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    MintPicker(mints, pickedMint, onMintChange)
}

@Composable
private fun MintPicker(
    mints: List<String>,
    picked: String,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = picked.ifEmpty { stringRes(Res.string.cashu_pick_mint) },
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Icon(MaterialSymbols.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        mints.forEach { m ->
            MintPickerItem(m) {
                onPick(m)
                expanded = false
            }
        }
    }
}

@Composable
private fun MintPickerItem(
    mint: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(mint) },
        onClick = onClick,
    )
}

// ============================================================================
// Send LN (melt)
// ============================================================================

@Composable
private fun SendLnDialog(
    viewModel: CashuWalletViewModel,
    mints: List<String>,
    initialMint: String?,
    onDismiss: () -> Unit,
) {
    val state by viewModel.meltState.collectAsState()
    var invoice by remember { mutableStateOf("") }
    var pickedMint by remember { mutableStateOf(initialMint ?: mints.firstOrNull() ?: "") }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.cashu_action_send_ln)) },
        text = {
            Column {
                when (val s = state) {
                    is CashuMeltFlowState.Quoting -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes(Res.string.cashu_getting_quote))
                        }
                    }

                    is CashuMeltFlowState.Quoted -> {
                        Text(
                            stringRes(
                                Res.string.cashu_quote_confirm,
                                s.quote.amount.toString(),
                                s.quote.feeReserve.toString(),
                            ),
                        )
                    }

                    is CashuMeltFlowState.Paying -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes(Res.string.cashu_paying_invoice))
                        }
                    }

                    is CashuMeltFlowState.Completed -> {
                        Text(stringRes(Res.string.cashu_paid_amount, s.paidAmount.toString(), s.fees.toString()))
                    }

                    is CashuMeltFlowState.Error -> {
                        Text(
                            text = s.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        InvoiceForm(
                            invoice = invoice,
                            onInvoiceChange = { invoice = it },
                            onPaste = { scope.launch { clipboard.getText()?.let { invoice = it } } },
                            mints = mints,
                            pickedMint = pickedMint,
                            onMintChange = { pickedMint = it },
                        )
                    }

                    CashuMeltFlowState.Idle -> {
                        InvoiceForm(
                            invoice = invoice,
                            onInvoiceChange = { invoice = it },
                            onPaste = { scope.launch { clipboard.getText()?.let { invoice = it } } },
                            mints = mints,
                            pickedMint = pickedMint,
                            onMintChange = { pickedMint = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is CashuMeltFlowState.Idle, is CashuMeltFlowState.Error -> {
                    TextButton(
                        onClick = { viewModel.startMelt(pickedMint, invoice) },
                        enabled = invoice.isNotBlank() && pickedMint.isNotBlank(),
                    ) { Text(stringRes(Res.string.cashu_get_quote)) }
                }

                is CashuMeltFlowState.Quoted -> {
                    TextButton(onClick = { viewModel.confirmMelt() }) {
                        Text(stringRes(Res.string.cashu_pay_invoice))
                    }
                }

                is CashuMeltFlowState.Completed -> {
                    TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cashu_done)) }
                }

                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}

@Composable
private fun InvoiceForm(
    invoice: String,
    onInvoiceChange: (String) -> Unit,
    onPaste: () -> Unit,
    mints: List<String>,
    pickedMint: String,
    onMintChange: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = invoice,
            onValueChange = onInvoiceChange,
            label = { Text(stringRes(Res.string.cashu_invoice_bolt11)) },
            placeholder = { Text("lnbc…") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPaste) {
            Icon(MaterialSymbols.ContentPaste, contentDescription = stringRes(Res.string.paste_from_clipboard))
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    MintPicker(mints, pickedMint, onMintChange)
}

// ============================================================================
// Send as token
// ============================================================================

@Composable
private fun SendTokenDialog(
    viewModel: CashuWalletViewModel,
    mints: List<String>,
    initialMint: String?,
    onDismiss: () -> Unit,
) {
    val state by viewModel.sendTokenState.collectAsState()
    var amount by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var pickedMint by remember { mutableStateOf(initialMint ?: mints.firstOrNull() ?: "") }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.cashu_action_send_token)) },
        text = {
            Column {
                when (val s = state) {
                    is CashuSendTokenFlowState.Building -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes(Res.string.cashu_building_token))
                        }
                    }

                    is CashuSendTokenFlowState.Ready -> {
                        Text(stringRes(Res.string.cashu_token_ready, s.amount.toString()))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = s.token,
                            onValueChange = {},
                            readOnly = true,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = { scope.launch { clipboard.setText(s.token) } }) {
                            Icon(
                                symbol = MaterialSymbols.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringRes(Res.string.cashu_copy_token))
                        }
                    }

                    is CashuSendTokenFlowState.Error -> {
                        Text(
                            text = s.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SendTokenForm(amount, { amount = it }, memo, { memo = it }, mints, pickedMint, { pickedMint = it })
                    }

                    CashuSendTokenFlowState.Idle -> {
                        SendTokenForm(amount, { amount = it }, memo, { memo = it }, mints, pickedMint, { pickedMint = it })
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is CashuSendTokenFlowState.Idle, is CashuSendTokenFlowState.Error -> {
                    TextButton(
                        onClick = {
                            val n = amount.toLongOrNull() ?: 0L
                            viewModel.sendAsToken(pickedMint, n, memo.ifBlank { null })
                        },
                        enabled = amount.isNotBlank() && pickedMint.isNotBlank(),
                    ) { Text(stringRes(Res.string.cashu_create_token)) }
                }

                is CashuSendTokenFlowState.Ready -> {
                    TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cashu_done)) }
                }

                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}

@Composable
private fun SendTokenForm(
    amount: String,
    onAmount: (String) -> Unit,
    memo: String,
    onMemo: (String) -> Unit,
    mints: List<String>,
    pickedMint: String,
    onMintChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = amount,
        onValueChange = { v -> onAmount(v.filter { it.isDigit() }) },
        label = { Text(stringRes(Res.string.cashu_amount_sats)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    SpellCheckedOutlinedTextField(
        value = memo,
        onValueChange = onMemo,
        label = { Text(stringRes(Res.string.cashu_memo_optional)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    MintPicker(mints, pickedMint, onMintChange)
}

// ============================================================================
// Redeem
// ============================================================================

@Composable
private fun RedeemDialog(
    viewModel: CashuWalletViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.redeemState.collectAsState()
    var token by remember { mutableStateOf("") }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.cashu_action_redeem)) },
        text = {
            Column {
                when (val s = state) {
                    is CashuRedeemFlowState.Redeeming -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes(Res.string.cashu_redeeming))
                        }
                    }

                    is CashuRedeemFlowState.Completed -> {
                        Text(stringRes(Res.string.cashu_redeemed_amount, s.amount.toString()))
                    }

                    is CashuRedeemFlowState.Error -> {
                        Text(
                            text = s.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TokenInput(token, { token = it }) {
                            scope.launch { clipboard.getText()?.let { token = it } }
                        }
                    }

                    CashuRedeemFlowState.Idle -> {
                        TokenInput(token, { token = it }) {
                            scope.launch { clipboard.getText()?.let { token = it } }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is CashuRedeemFlowState.Idle, is CashuRedeemFlowState.Error -> {
                    TextButton(
                        onClick = { viewModel.redeemToken(token) },
                        enabled = token.isNotBlank(),
                    ) { Text(stringRes(Res.string.cashu_redeem_button)) }
                }

                is CashuRedeemFlowState.Completed -> {
                    TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cashu_done)) }
                }

                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}

@Composable
private fun TokenInput(
    token: String,
    onTokenChange: (String) -> Unit,
    onPaste: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            label = { Text(stringRes(Res.string.cashu_token_label)) },
            placeholder = { Text("cashuB… / cashuA…") },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPaste) {
            Icon(MaterialSymbols.ContentPaste, contentDescription = stringRes(Res.string.paste_from_clipboard))
        }
    }
}
