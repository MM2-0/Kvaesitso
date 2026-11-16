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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.TransactionRowLabels
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.reload_mint_sats_amount
import com.vitorpamplona.amethyst.commons.resources.wallet_copied
import com.vitorpamplona.amethyst.commons.resources.wallet_filter_all
import com.vitorpamplona.amethyst.commons.resources.wallet_filter_non_zaps
import com.vitorpamplona.amethyst.commons.resources.wallet_filter_zaps
import com.vitorpamplona.amethyst.commons.resources.wallet_incoming
import com.vitorpamplona.amethyst.commons.resources.wallet_loading
import com.vitorpamplona.amethyst.commons.resources.wallet_no_transactions
import com.vitorpamplona.amethyst.commons.resources.wallet_open_zapped_note
import com.vitorpamplona.amethyst.commons.resources.wallet_outgoing
import com.vitorpamplona.amethyst.commons.resources.wallet_refresh
import com.vitorpamplona.amethyst.commons.resources.wallet_transactions
import com.vitorpamplona.amethyst.commons.resources.wallet_transactions_load_failed
import com.vitorpamplona.amethyst.commons.resources.wallet_tx_amount
import com.vitorpamplona.amethyst.commons.resources.wallet_tx_collapse
import com.vitorpamplona.amethyst.commons.resources.wallet_tx_expand
import com.vitorpamplona.amethyst.commons.resources.wallet_tx_fee
import com.vitorpamplona.amethyst.commons.resources.wallet_tx_from
import com.vitorpamplona.amethyst.commons.resources.wallet_tx_invoice
import com.vitorpamplona.amethyst.commons.resources.wallet_tx_note
import com.vitorpamplona.amethyst.commons.resources.wallet_tx_payment_hash
import com.vitorpamplona.amethyst.commons.resources.wallet_tx_preimage
import com.vitorpamplona.amethyst.commons.resources.wallet_tx_to
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.note.rememberMonthDayTimeFormatter
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.TransactionFilter
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.WalletViewModel
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransaction
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransactionType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletTransactionsScreen(
    walletId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val walletViewModel: WalletViewModel = rememberViewModel { WalletViewModel() }

    LaunchedEffect(accountViewModel, walletId) {
        walletViewModel.init(accountViewModel)
        walletViewModel.selectWallet(walletId)
        walletViewModel.fetchTransactions()
    }

    val transactions by walletViewModel.filteredTransactions.collectAsState()
    val isLoading by walletViewModel.isLoading.collectAsState()
    val isLoadingMore by walletViewModel.isLoadingMore.collectAsState()
    val hasMore by walletViewModel.hasMoreTransactions.collectAsState()
    val currentFilter by walletViewModel.transactionFilter.collectAsState()
    val error by walletViewModel.error.collectAsState()

    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleIndex >= totalItems - 5 && !isLoadingMore && hasMore && transactions.isNotEmpty()
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            walletViewModel.loadMoreTransactions()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.wallet_transactions)) },
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
                    IconButton(onClick = { walletViewModel.fetchTransactions() }) {
                        Icon(
                            symbol = MaterialSymbols.Refresh,
                            contentDescription = stringRes(Res.string.wallet_refresh),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val currentError = error
        if (isLoading && transactions.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringRes(Res.string.wallet_loading),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else if (currentError != null && transactions.isEmpty()) {
            // A wallet refusal (e.g. RESTRICTED) leaves the list empty. Without this
            // branch the screen would claim "no transactions yet" and hide the reason.
            EmptyState(
                title = stringRes(Res.string.wallet_transactions_load_failed),
                modifier = Modifier.padding(padding).padding(vertical = 24.dp),
                description = currentError,
                onRefresh = { walletViewModel.fetchTransactions() },
                refreshLabel = stringRes(Res.string.wallet_refresh),
            )
        } else if (transactions.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringRes(Res.string.wallet_no_transactions),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                state = listState,
            ) {
                if (currentError != null) {
                    // Already-loaded transactions stay visible; the banner explains why
                    // the latest refresh or page load did not land.
                    item {
                        Text(
                            text = currentError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                item {
                    TransactionFilterRow(currentFilter) { walletViewModel.setTransactionFilter(it) }
                }
                items(transactions) { tx ->
                    TransactionItem(
                        tx = tx,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                    HorizontalDivider()
                }
                if (isLoadingMore) {
                    item {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionFilterRow(
    currentFilter: TransactionFilter,
    onFilterSelected: (TransactionFilter) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = currentFilter == TransactionFilter.ALL,
            onClick = { onFilterSelected(TransactionFilter.ALL) },
            label = { Text(stringRes(Res.string.wallet_filter_all)) },
        )
        FilterChip(
            selected = currentFilter == TransactionFilter.ZAPS,
            onClick = { onFilterSelected(TransactionFilter.ZAPS) },
            label = { Text(stringRes(Res.string.wallet_filter_zaps)) },
        )
        FilterChip(
            selected = currentFilter == TransactionFilter.NON_ZAPS,
            onClick = { onFilterSelected(TransactionFilter.NON_ZAPS) },
            label = { Text(stringRes(Res.string.wallet_filter_non_zaps)) },
        )
    }
}

// Internal, not private: the wallet detail screen previews the first few
// transactions with the same rows this full list renders. Without [onRowTap]
// the row expands its payment details; the preview passes one that opens the
// full list instead — a details drawer costs more space than the preview has.
@Composable
internal fun TransactionItem(
    tx: NwcTransaction,
    accountViewModel: AccountViewModel,
    nav: INav,
    onRowTap: (() -> Unit)? = null,
) {
    val isIncoming = tx.type == NwcTransactionType.INCOMING
    val amountSats = (tx.amount ?: 0L) / 1000L
    val formattedAmount =
        remember(amountSats) {
            (if (isIncoming) "+" else "-") + formatGrouped(amountSats)
        }

    val monthDayTime = rememberMonthDayTimeFormatter()
    val dateText =
        remember(tx.created_at, monthDayTime) {
            tx.created_at?.let { monthDayTime(it) } ?: ""
        }

    val directionLabel =
        if (isIncoming) stringRes(Res.string.wallet_incoming) else stringRes(Res.string.wallet_outgoing)

    val labels =
        remember(tx.metadata, tx.description, tx.type, directionLabel) {
            TransactionRowLabels.resolve(tx, directionLabel)
        }
    val counterpartyPubkeyHex = (labels.title as? TransactionRowLabels.Title.User)?.pubkeyHex
    val zappedNoteId = labels.zappedNoteId

    var expanded by remember(tx) { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    // Full list: the row expands its details. Preview: the row
                    // opens the full list. The profile and the zapped-note
                    // chevron keep their own taps.
                    if (onRowTap == null) {
                        Modifier.clickable(
                            onClickLabel = stringRes(if (expanded) Res.string.wallet_tx_collapse else Res.string.wallet_tx_expand),
                        ) {
                            expanded = !expanded
                        }
                    } else {
                        Modifier.clickable(onClickLabel = stringRes(Res.string.wallet_transactions), onClick = onRowTap)
                    },
                ).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (counterpartyPubkeyHex != null) {
            UserPicture(
                userHex = counterpartyPubkeyHex,
                size = 40.dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            // No contact to show: a subtle directional badge (bitcoin.design's
            // activity rows) — green for received, neutral for sent. Nothing is
            // lost when a face takes this slot instead.
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isIncoming) {
                                ReceivedGreen.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = if (isIncoming) MaterialSymbols.ArrowDownward else MaterialSymbols.ArrowUpward,
                    contentDescription = directionLabel,
                    modifier = Modifier.size(20.dp),
                    tint =
                        if (isIncoming) {
                            ReceivedGreen
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            when (val title = labels.title) {
                is TransactionRowLabels.Title.User ->
                    TransactionUserName(title.pubkeyHex, title.name, accountViewModel)

                is TransactionRowLabels.Title.Literal ->
                    Text(
                        text = title.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
            }

            labels.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Keeps a truncated note from running into the amount.
        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = stringRes(Res.string.reload_mint_sats_amount, formattedAmount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color =
                if (isIncoming) {
                    ReceivedGreen
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
        )

        // Only a row with a zapped note carries the chevron; rows without
        // one let the amount run to the row's own right padding.
        if (zappedNoteId != null) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = { nav.nav(Route.Note(zappedNoteId)) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.ChevronRight,
                    contentDescription = stringRes(Res.string.wallet_open_zapped_note),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (onRowTap == null) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            TransactionItemDetails(
                tx = tx,
                labels = labels,
                amountSats = amountSats,
                accountViewModel = accountViewModel,
            )
        }
    }
}

@Composable
private fun TransactionUserName(
    pubkeyHex: String,
    fallbackName: String?,
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
                text = fallbackName ?: (pubkeyHex.take(8) + "..."),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The expanded block under a transaction row, Sidecar's payment-details set.
 * The top rows are human-readable and carry no copy button; only the machine
 * strings (hash, preimage, invoice) do, since their full values never fit on
 * screen and are useless to retype.
 */
@Composable
private fun TransactionItemDetails(
    tx: NwcTransaction,
    labels: TransactionRowLabels,
    amountSats: Long,
    accountViewModel: AccountViewModel,
) {
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val shortNotice = rememberShortNotice()
    val copiedMsg = stringRes(Res.string.wallet_copied)
    val onCopy: (String) -> Unit = { value ->
        clipboardScope.launch {
            clipboard.setText(value)
            shortNotice.show(copiedMsg)
        }
    }

    val isIncoming = tx.type == NwcTransactionType.INCOMING
    val counterparty =
        when (val title = labels.title) {
            // The profile name, as the row above shows it; the zap request's name is only a fallback.
            is TransactionRowLabels.Title.User ->
                observeUserNameByHex(title.pubkeyHex, accountViewModel)
                    .takeUnless { it == title.pubkeyHex.take(8) && title.name != null }
                    ?: title.name.orEmpty()
            is TransactionRowLabels.Title.Literal -> title.text
        }
    val note = remember(tx) { tx.displayDescription() ?: tx.parsedMetadata()?.displayComment() }
    val feeSats = (tx.fees_paid ?: 0L) / 1000L
    val whenSecs = tx.settled_at ?: tx.created_at
    val dateText =
        remember(whenSecs) {
            whenSecs?.let { formatDateTime(it * 1000L, DateTimeStyle.MEDIUM, DateTimeStyle.MEDIUM) }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    ) {
        WalletDetailRow(
            label = stringRes(if (isIncoming) Res.string.wallet_tx_from else Res.string.wallet_tx_to),
            value = counterparty,
            monospace = false,
            trailing =
                dateText?.let {
                    {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                },
        )
        note?.let {
            WalletDetailRow(
                label = stringRes(Res.string.wallet_tx_note),
                value = it,
                monospace = false,
                maxLines = 3,
            )
        }
        WalletDetailRow(
            label = stringRes(Res.string.wallet_tx_amount),
            value = stringRes(Res.string.reload_mint_sats_amount, formatGrouped(amountSats)),
            monospace = false,
        )
        if (feeSats > 0) {
            WalletDetailRow(
                label = stringRes(Res.string.wallet_tx_fee),
                value = stringRes(Res.string.reload_mint_sats_amount, formatGrouped(feeSats)),
                monospace = false,
            )
        }

        listOf(
            Res.string.wallet_tx_payment_hash to tx.payment_hash,
            Res.string.wallet_tx_preimage to tx.preimage,
            Res.string.wallet_tx_invoice to tx.invoice,
        ).forEach { (label, value) ->
            // Some wallets report an empty preimage for a received payment: nothing to show or copy.
            if (!value.isNullOrBlank()) {
                WalletDetailRow(
                    label = stringRes(label),
                    // One line; the copy button carries the full value.
                    value = shortenMiddle(value, head = 16, tail = 8),
                    copyValue = value,
                    onCopy = onCopy,
                )
            }
        }
    }
}

/** bitcoin.design's activity green: received amounts and badges; red stays reserved for errors. */
private val ReceivedGreen = Color(0xFF16A34A)
