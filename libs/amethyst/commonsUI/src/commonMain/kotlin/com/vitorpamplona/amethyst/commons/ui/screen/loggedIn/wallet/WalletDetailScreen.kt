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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.wallet
import com.vitorpamplona.amethyst.commons.resources.wallet_hide_balances
import com.vitorpamplona.amethyst.commons.resources.wallet_no_transactions
import com.vitorpamplona.amethyst.commons.resources.wallet_receive
import com.vitorpamplona.amethyst.commons.resources.wallet_sats
import com.vitorpamplona.amethyst.commons.resources.wallet_send
import com.vitorpamplona.amethyst.commons.resources.wallet_transactions
import com.vitorpamplona.amethyst.commons.resources.wallet_transactions_view_all
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

private const val TRANSACTION_PREVIEW_COUNT = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDetailScreen(
    walletId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val walletViewModel: WalletViewModel = rememberViewModel { WalletViewModel() }
    walletViewModel.init(accountViewModel)
    walletViewModel.selectWallet(walletId)

    val balance by walletViewModel.balanceSats.collectAsState()
    val walletAlias by walletViewModel.walletAlias.collectAsState()
    val error by walletViewModel.error.collectAsState()
    val allTransactions by walletViewModel.filteredTransactions.collectAsState()
    val wallets by walletViewModel.wallets.collectAsState()
    val hideBalance by accountViewModel.settings.uiSettingsFlow.hideWalletBalances
        .collectAsState()
    val walletName = wallets.firstOrNull { it.id == walletId }?.name ?: stringRes(Res.string.wallet)

    LaunchedEffect(walletId) {
        walletViewModel.fetchBalance()
        walletViewModel.fetchInfo()
        walletViewModel.fetchTransactions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(walletAlias ?: walletName) },
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
                            .tryEmit(!hideBalance)
                    }) {
                        Icon(
                            symbol =
                                if (hideBalance) {
                                    MaterialSymbols.Visibility
                                } else {
                                    MaterialSymbols.VisibilityOff
                                },
                            contentDescription = stringRes(Res.string.wallet_hide_balances),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (hideBalance) {
                Text(
                    text = BALANCE_DOTS,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            } else if (balance == null) {
                BalanceDots(pulsing = true)
            } else {
                val formattedBalance =
                    remember(balance) {
                        formatGrouped(balance ?: 0L)
                    }
                Text(
                    text = formattedBalance,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringRes(Res.string.wallet_sats),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Error
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Button(
                    onClick = { nav.nav(Route.WalletReceive(walletId)) },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                ) {
                    Icon(
                        symbol = MaterialSymbols.ArrowDownward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringRes(Res.string.wallet_receive), fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { nav.nav(Route.WalletSend(walletId)) },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringRes(Res.string.wallet_send), fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(Res.string.wallet_transactions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }

            if (allTransactions.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringRes(Res.string.wallet_no_transactions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                allTransactions.take(TRANSACTION_PREVIEW_COUNT).forEach { tx ->
                    TransactionItem(
                        tx = tx,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onRowTap = { nav.nav(Route.WalletTransactions(walletId)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Transactions button
            OutlinedButton(
                onClick = { nav.nav(Route.WalletTransactions(walletId)) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoMirrored.List,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringRes(Res.string.wallet_transactions_view_all))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
