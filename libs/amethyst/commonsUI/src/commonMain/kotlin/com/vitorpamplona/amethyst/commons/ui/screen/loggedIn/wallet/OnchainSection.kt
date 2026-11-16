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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nipBCOnchainZaps.OnchainBalanceStatus
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.copy
import com.vitorpamplona.amethyst.commons.resources.quick_action_dont_show_again_button
import com.vitorpamplona.amethyst.commons.resources.send
import com.vitorpamplona.amethyst.commons.resources.wallet_onchain_address_unavailable
import com.vitorpamplona.amethyst.commons.resources.wallet_onchain_balance_unavailable
import com.vitorpamplona.amethyst.commons.resources.wallet_onchain_copy_dialog_confirm
import com.vitorpamplona.amethyst.commons.resources.wallet_onchain_no_backend_short
import com.vitorpamplona.amethyst.commons.resources.wallet_onchain_public_chip
import com.vitorpamplona.amethyst.commons.resources.wallet_onchain_public_dialog_body
import com.vitorpamplona.amethyst.commons.resources.wallet_onchain_public_dialog_confirm
import com.vitorpamplona.amethyst.commons.resources.wallet_onchain_public_dialog_title
import com.vitorpamplona.amethyst.commons.resources.wallet_onchain_subtitle
import com.vitorpamplona.amethyst.commons.resources.wallet_onchain_title
import com.vitorpamplona.amethyst.commons.resources.wallet_sats
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.sats
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch

/**
 * "Bitcoin" card on the wallet screen, shown above the lightning NWC wallet
 * list. Visually mirrors [WalletCard] so the two payment rails read as the
 * same kind of object; bitcoin-orange accent in place of NWC's primary
 * (purple) keeps the rails distinct at a glance.
 *
 * Every account has exactly one Taproot address (derived from its Nostr
 * pubkey via NIP-BC / BIP-341), so this is always a single card — not a list.
 */
@Composable
fun OnchainSection(
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    // Shared with the zap picker's on-chain gating. The screen hosting this card
    // forces the fresh fetch once per visit (see WalletScreen); here, inside a
    // lazy list, an effect would refetch every time the card scrolled back in.
    val onchainWallet = accountViewModel.account.onchainWalletState
    val address = onchainWallet.address

    val funds by onchainWallet.funds.collectAsStateWithLifecycle()
    val balanceState by onchainWallet.status.collectAsStateWithLifecycle()
    val balanceSats = funds?.totalSats

    val orange = MaterialTheme.colorScheme.bitcoinColor
    val hideBalance by accountViewModel.settings.uiSettingsFlow.hideWalletBalances
        .collectAsStateWithLifecycle()

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { nav.nav(Route.OnchainTransactions) },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, orange),
        colors =
            CardDefaults.cardColors(
                containerColor = orange.copy(alpha = 0.12f),
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            HeaderRow(
                orange = orange,
                balanceState = balanceState,
                balanceSats = balanceSats,
                hideBalance = hideBalance,
            )

            if (address == null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringRes(Res.string.wallet_onchain_address_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                ActionRow(address = address, accountViewModel = accountViewModel, orange = orange)
            }
        }
    }
}

@Composable
private fun HeaderRow(
    orange: Color,
    balanceState: OnchainBalanceStatus,
    balanceSats: Long?,
    hideBalance: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BitcoinChip(orange)
        Spacer(modifier = Modifier.width(12.dp))
        HeaderRowTitle(modifier = Modifier.weight(1f), orange = orange)
        BalanceBlock(state = balanceState, sats = balanceSats, orange = orange, hideBalance = hideBalance)
    }
}

@Composable
private fun HeaderRowTitle(
    modifier: Modifier = Modifier,
    orange: Color,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringRes(Res.string.wallet_onchain_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            PublicChip()
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringRes(Res.string.wallet_onchain_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PublicChip() {
    var showDialog by remember { mutableStateOf(false) }
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(onSurfaceVariant.copy(alpha = 0.12f))
                .clickable { showDialog = true }
                .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = MaterialSymbols.Info,
            contentDescription = null,
            tint = onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringRes(Res.string.wallet_onchain_public_chip),
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = {
                Icon(
                    symbol = MaterialSymbols.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            title = { Text(stringRes(Res.string.wallet_onchain_public_dialog_title)) },
            text = { Text(stringRes(Res.string.wallet_onchain_public_dialog_body)) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringRes(Res.string.wallet_onchain_public_dialog_confirm))
                }
            },
        )
    }
}

@Composable
private fun BitcoinChip(orange: Color) {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color = orange),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.CurrencyBitcoin,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun BalanceBlock(
    state: OnchainBalanceStatus,
    sats: Long?,
    orange: Color,
    hideBalance: Boolean,
) {
    if (state == OnchainBalanceStatus.LOADING && sats == null) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = orange,
        )
        return
    }

    Column(horizontalAlignment = Alignment.End) {
        when (state) {
            OnchainBalanceStatus.READY -> {
                if (hideBalance) {
                    BalanceDots(pulsing = false)
                } else {
                    val formatted =
                        remember(sats) {
                            formatGrouped(sats ?: 0L)
                        }
                    Text(
                        text = formatted,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = orange,
                    )
                }
                Text(
                    text = stringRes(Res.string.wallet_sats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OnchainBalanceStatus.ERROR -> {
                Text(
                    text = "—",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringRes(Res.string.wallet_onchain_balance_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OnchainBalanceStatus.UNAVAILABLE -> {
                Text(
                    text = "—",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringRes(Res.string.wallet_onchain_no_backend_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OnchainBalanceStatus.LOADING -> Unit
        }
    }
}

@Composable
private fun ActionRow(
    address: String,
    accountViewModel: AccountViewModel,
    orange: Color,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = accountViewModel.settings.uiSettingsFlow
    val dontShowCopyWarning by sharedPrefs.dontShowOnchainPublicWarning.collectAsStateWithLifecycle()
    var showSendDialog by remember { mutableStateOf(false) }
    var showCopyWarning by remember { mutableStateOf(false) }

    val copyAddress = {
        scope.launch { clipboard.setText(address) }
        Unit
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = {
                if (dontShowCopyWarning) {
                    copyAddress()
                } else {
                    showCopyWarning = true
                }
            },
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringRes(Res.string.copy), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { showSendDialog = true },
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = orange,
                    contentColor = Color.White,
                ),
        ) {
            Icon(
                symbol = MaterialSymbols.ArrowUpward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringRes(Res.string.send), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showSendDialog) {
        OnchainZapSendDialog(
            accountViewModel = accountViewModel,
            onDismiss = { showSendDialog = false },
        )
    }

    if (showCopyWarning) {
        CopyPublicAddressDialog(
            onConfirm = {
                showCopyWarning = false
                copyAddress()
            },
            onDontShowAgain = {
                sharedPrefs.dontShowOnchainPublicWarning()
                showCopyWarning = false
                copyAddress()
            },
            onDismiss = { showCopyWarning = false },
        )
    }
}

@Composable
private fun CopyPublicAddressDialog(
    onConfirm: () -> Unit,
    onDontShowAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                symbol = MaterialSymbols.Info,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        title = { Text(stringRes(Res.string.wallet_onchain_public_dialog_title)) },
        text = { Text(stringRes(Res.string.wallet_onchain_public_dialog_body)) },
        confirmButton = {
            Row(
                modifier = Modifier.padding(all = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDontShowAgain) {
                    Text(stringRes(Res.string.quick_action_dont_show_again_button))
                }
                Button(
                    onClick = onConfirm,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringRes(Res.string.wallet_onchain_copy_dialog_confirm))
                }
            }
        },
    )
}
