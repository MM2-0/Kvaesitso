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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accessibility_scan_qr_code
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.ic_qrcode
import com.vitorpamplona.amethyst.commons.resources.paste_from_clipboard
import com.vitorpamplona.amethyst.commons.resources.wallet_add_clink_invalid
import com.vitorpamplona.amethyst.commons.resources.wallet_add_clink_title
import com.vitorpamplona.amethyst.commons.resources.wallet_name
import com.vitorpamplona.amethyst.commons.resources.wallet_name_hint
import com.vitorpamplona.amethyst.commons.resources.wallet_paste_ndebit
import com.vitorpamplona.amethyst.commons.resources.wallet_save
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.util.getText
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch

/**
 * Adds a CLINK Debits pointer (`ndebit1…`) as a spend-only payment source. Unlike NWC
 * there is no secret to paste — authorization is the account's own identity, pre-approved
 * on the wallet service — so this screen only collects a name and the pointer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClinkDebitWalletScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    ndebit: String? = null,
) {
    val walletViewModel: WalletViewModel = rememberViewModel { WalletViewModel() }
    walletViewModel.init(accountViewModel)

    var walletName by remember { mutableStateOf("") }
    var ndebitUri by remember { mutableStateOf(ndebit.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var qrScanning by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.wallet_add_clink_title)) },
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
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePaddingSafe()
                    .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = walletName,
                onValueChange = { walletName = it },
                label = { Text(stringRes(Res.string.wallet_name)) },
                placeholder = { Text(stringRes(Res.string.wallet_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Paste from clipboard
                IconButton(
                    onClick = {
                        scope.launch {
                            val clipText = clipboardManager.getText()
                            if (clipText != null) {
                                ndebitUri = clipText
                                error = null
                            }
                        }
                    },
                ) {
                    Icon(
                        symbol = MaterialSymbols.ContentPaste,
                        contentDescription = stringRes(id = Res.string.paste_from_clipboard),
                        modifier = Size24Modifier,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                // QR code scanner
                IconButton(onClick = { qrScanning = true }) {
                    Icon(
                        painter = painterRes(Res.drawable.ic_qrcode, 3),
                        contentDescription = stringRes(id = Res.string.accessibility_scan_qr_code),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (qrScanning) {
                SimpleQrCodeScanner {
                    qrScanning = false
                    if (!it.isNullOrEmpty()) {
                        ndebitUri = it
                        error = null
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = ndebitUri,
                onValueChange = {
                    ndebitUri = it
                    error = null
                },
                label = { Text(stringRes(Res.string.wallet_paste_ndebit)) },
                placeholder = { Text("ndebit1...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val invalidMessage = stringRes(Res.string.wallet_add_clink_invalid)
            Button(
                onClick = {
                    if (walletViewModel.addClinkDebitWallet(walletName.trim(), ndebitUri.trim())) {
                        nav.popBack()
                    } else {
                        error = invalidMessage
                    }
                },
                enabled = ndebitUri.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringRes(Res.string.wallet_save))
            }
        }
    }
}
