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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcBackupState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accessibility_scan_qr_code
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.couldnt_find_nwc_wallets
import com.vitorpamplona.amethyst.commons.resources.couldnt_find_nwc_wallets_description
import com.vitorpamplona.amethyst.commons.resources.ic_qrcode
import com.vitorpamplona.amethyst.commons.resources.paste_from_clipboard
import com.vitorpamplona.amethyst.commons.resources.wallet_add_nwc_title
import com.vitorpamplona.amethyst.commons.resources.wallet_already_connected
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_all_already_added
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_none_found
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_restore
import com.vitorpamplona.amethyst.commons.resources.wallet_backup_restored_toast
import com.vitorpamplona.amethyst.commons.resources.wallet_connect_connect_app
import com.vitorpamplona.amethyst.commons.resources.wallet_name
import com.vitorpamplona.amethyst.commons.resources.wallet_name_hint
import com.vitorpamplona.amethyst.commons.resources.wallet_paste_uri
import com.vitorpamplona.amethyst.commons.resources.wallet_save
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.util.getText
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.loadPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47DeepLink
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNwcWalletScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    nip47Uri: String? = null,
) {
    val walletViewModel: WalletViewModel = rememberViewModel { WalletViewModel() }
    walletViewModel.init(accountViewModel)

    var walletName by remember { mutableStateOf("") }
    // Prefilled when arriving from the `nostr+walletconnect` connect deep link.
    var nwcUri by remember { mutableStateOf(nip47Uri.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val shortNotice = rememberShortNotice()
    val restoreScope = rememberCoroutineScope()
    val noneFoundMsg = stringRes(Res.string.wallet_backup_none_found)
    val alreadyConnectedMsg = stringRes(Res.string.wallet_already_connected)
    var qrScanning by remember { mutableStateOf(false) }
    var showBackupPrompt by remember { mutableStateOf(false) }
    val backupEnabled by walletViewModel.backupEnabled.collectAsState()
    val backupCardDismissed by walletViewModel.backupCardDismissed.collectAsState()

    val uri = LocalUriHandler.current
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    if (showBackupPrompt) {
        BackupEnableDialog(
            onConfirm = {
                showBackupPrompt = false
                walletViewModel.setBackupEnabled(true)
                nav.popBack()
            },
            onDismiss = {
                showBackupPrompt = false
                walletViewModel.dismissBackupCard()
                nav.popBack()
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.wallet_add_nwc_title)) },
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

            // Connect action buttons: Connect Wallet app, Paste, QR scan
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        try {
                            uri.openUri(
                                Nip47DeepLink.buildConnectUri(
                                    callback = "amethyst+walletconnect://dlnwc",
                                    appName = "Amethyst",
                                    appIcon = "https://raw.githubusercontent.com/vitorpamplona/amethyst/refs/heads/main/icon.png",
                                ),
                            )
                        } catch (_: IllegalArgumentException) {
                            accountViewModel.toastManager.toast(
                                Res.string.couldnt_find_nwc_wallets,
                                Res.string.couldnt_find_nwc_wallets_description,
                            )
                        }
                    },
                ) {
                    Icon(
                        symbol = MaterialSymbols.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringRes(Res.string.wallet_connect_connect_app))
                }

                Spacer(DoubleHorzSpacer)

                // Paste from clipboard
                IconButton(
                    onClick = {
                        scope.launch {
                            val clipText = clipboardManager.getText()
                            if (clipText != null) {
                                nwcUri = clipText
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
                        nwcUri = it
                        error = null
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = nwcUri,
                onValueChange = {
                    nwcUri = it
                    error = null
                },
                label = { Text(stringRes(Res.string.wallet_paste_uri)) },
                placeholder = { Text("nostr+walletconnect://...") },
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

            Button(
                onClick = {
                    try {
                        val parsed = Nip47WalletConnect.parse(nwcUri.trim())
                        if (walletViewModel.addWallet(walletName.trim(), parsed)) {
                            // One chance to opt in while the wallet is fresh; "Not now" also hides the card.
                            if (!backupEnabled && !backupCardDismissed) {
                                showBackupPrompt = true
                            } else {
                                nav.popBack()
                            }
                        } else {
                            error = alreadyConnectedMsg
                            shortNotice.show(alreadyConnectedMsg)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        error = e.message ?: "Invalid NWC connection URI"
                    }
                },
                enabled = nwcUri.isNotBlank(),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringRes(Res.string.wallet_save))
            }

            Spacer(modifier = Modifier.height(24.dp))

            val backupBusy by walletViewModel.backupBusy.collectAsState()
            var restoreCandidate by remember { mutableStateOf<NwcBackupState.FoundNwcBackup?>(null) }

            restoreCandidate?.let { backup ->
                RestoreFoundDialog(
                    backup = backup,
                    onConfirm = { chosen ->
                        restoreCandidate = null
                        restoreScope.launch {
                            val added = walletViewModel.applyRestoredBackup(backup, chosen)
                            shortNotice.show(
                                if (added > 0) {
                                    loadPluralStringRes(Res.plurals.wallet_backup_restored_toast, added, added)
                                } else {
                                    loadStringRes(Res.string.wallet_backup_all_already_added)
                                },
                            )
                            nav.popBack()
                        }
                    },
                    onDismiss = { restoreCandidate = null },
                )
            }

            RestoreBackupButton(
                busy = backupBusy,
                idleLabel = Res.string.wallet_backup_restore,
                prominent = true,
                onClick = {
                    walletViewModel.fetchBackupForRestore { backup ->
                        if (backup == null) {
                            shortNotice.show(noneFoundMsg)
                        } else {
                            restoreCandidate = backup
                        }
                    }
                },
            )
        }
    }
}
