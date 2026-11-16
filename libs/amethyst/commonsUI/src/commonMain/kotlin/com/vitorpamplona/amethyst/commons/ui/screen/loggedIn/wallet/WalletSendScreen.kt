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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.action_paste
import com.vitorpamplona.amethyst.commons.resources.action_scan
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.ic_qrcode
import com.vitorpamplona.amethyst.commons.resources.wallet_amount_sats
import com.vitorpamplona.amethyst.commons.resources.wallet_paste_invoice
import com.vitorpamplona.amethyst.commons.resources.wallet_pay
import com.vitorpamplona.amethyst.commons.resources.wallet_payment_sending
import com.vitorpamplona.amethyst.commons.resources.wallet_payment_success
import com.vitorpamplona.amethyst.commons.resources.wallet_send
import com.vitorpamplona.amethyst.commons.service.lnurl.lightningAddressOrNull
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.util.getText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.SendState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.WalletViewModel
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.wallet.paymentFromUri
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon as Material3Icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSendScreen(
    walletId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val walletViewModel: WalletViewModel = rememberViewModel { WalletViewModel() }

    LaunchedEffect(accountViewModel, walletId) {
        walletViewModel.init(accountViewModel)
        walletViewModel.selectWallet(walletId)
    }

    DisposableEffect(Unit) {
        onDispose { walletViewModel.resetSendState() }
    }

    val sendState by walletViewModel.sendState.collectAsState()
    var invoiceText by remember { mutableStateOf("") }
    var qrScanning by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.wallet_send)) },
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
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val state = sendState) {
                is SendState.Idle -> {
                    // A Lightning address has no amount of its own, so the screen asks for one.
                    val lnAddress = lightningAddressOrNull(invoiceText)
                    val amountSats = amountText.toLongOrNull()
                    // One decision for both the button's action and whether it is enabled.
                    val pay: (() -> Unit)? =
                        when {
                            lnAddress != null -> amountSats?.takeIf { it in 1..WalletViewModel.MAX_SEND_SATS }?.let { sats -> { walletViewModel.sendToLightningAddress(lnAddress, sats) } }
                            invoiceText.isNotBlank() -> ({ walletViewModel.sendPayment(invoiceText.trim()) })
                            else -> null
                        }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = invoiceText,
                        onValueChange = { invoiceText = it },
                        label = { Text(stringRes(Res.string.wallet_paste_invoice)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 5,
                    )

                    // Paste and scan sit under the field, not in its trailing
                    // slot: two icon buttons there took ~100dp of the width and
                    // left a pasted address too little room to read.
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        InputActionButton(
                            label = stringRes(Res.string.action_paste),
                            onClick = { clipboardScope.launch { clipboard.getText()?.let { invoiceText = it } } },
                        ) {
                            Icon(
                                symbol = MaterialSymbols.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        InputActionButton(
                            label = stringRes(Res.string.action_scan),
                            onClick = { qrScanning = true },
                        ) {
                            Material3Icon(
                                painter = painterRes(Res.drawable.ic_qrcode, 3),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    if (qrScanning) {
                        SimpleQrCodeScanner {
                            qrScanning = false
                            if (!it.isNullOrEmpty()) {
                                // Lightning QRs usually carry the URI prefix (in either
                                // case); the parser wants the bare invoice.
                                invoiceText = paymentFromUri(it)
                            }
                        }
                    }

                    if (lnAddress != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter(Char::isDigit) },
                            label = { Text(stringRes(Res.string.wallet_amount_sats)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { pay?.invoke() },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = pay != null,
                    ) {
                        Text(
                            stringRes(Res.string.wallet_pay),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                is SendState.Sending -> {
                    Spacer(modifier = Modifier.weight(1f))
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringRes(Res.string.wallet_payment_sending),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }

                is SendState.Success -> {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        symbol = MaterialSymbols.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringRes(Res.string.wallet_payment_success),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { nav.popBack() },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(stringRes(Res.string.back))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                is SendState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { walletViewModel.resetSendState() }) {
                            Text(stringRes(Res.string.back))
                        }
                    }
                }
            }
        }
    }
}

/** Paste / Scan under the input field: an icon and a label sharing the row's width. */
@Composable
private fun RowScope.InputActionButton(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(48.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}
