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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_delete_wallet
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_delete_wallet_confirm_body
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_delete_wallet_confirm_title
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_delete_wallet_subtitle
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_edit_wallet
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_edit_wallet_subtitle
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_import_key
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_import_key_action
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_import_key_confirm_body
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_import_key_confirm_title
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_import_key_field
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_import_key_subtitle
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_my_recommendations
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_recommendations_subtitle
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_recreate_key
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_recreate_key_action
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_recreate_key_confirm_body
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_recreate_key_confirm_title
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_recreate_key_subtitle
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_restore_nothing_found
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_restore_result
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_restore_running
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_restore_subtitle
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_restore_title
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_stop_nutzaps
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_stop_nutzaps_confirm_body
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_stop_nutzaps_confirm_title
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_stop_nutzaps_subtitle
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_title
import com.vitorpamplona.amethyst.commons.resources.danger_zone
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.wallet.CashuWalletViewModel

/**
 * Settings hub for the Cashu wallet. Lives behind the gear icon on the
 * main wallet screen. It is a thin redirector: each row either navigates to a
 * dedicated screen or triggers a single action. Hosts:
 *
 *  - "My mints" → the [CashuMintsScreen] mint manager (edit mode).
 *  - "Recover from seed" → NUT-09 recovery action (inline, with status).
 *  - "Mint recommendations" → the [CashuMintRecommendationsScreen].
 *  - Danger Zone → stop nutzaps, recreate/import the nutzap key, delete wallet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashuWalletSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: CashuWalletViewModel = rememberViewModel { CashuWalletViewModel() }
    viewModel.init(accountViewModel)

    val walletEvent by viewModel.walletEvent.collectAsState()
    var showStopNutzapsConfirm by remember { mutableStateOf(false) }
    var showRecreateKeyConfirm by remember { mutableStateOf(false) }
    var showImportKeyDialog by remember { mutableStateOf(false) }
    var showDeleteWalletConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.cashu_settings_title)) },
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
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePaddingSafe()
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                SettingsRow(
                    icon = MaterialSymbols.Edit,
                    title = stringRes(Res.string.cashu_settings_edit_wallet),
                    subtitle = stringRes(Res.string.cashu_settings_edit_wallet_subtitle),
                    onClick = { nav.nav(Route.CashuWalletMints) },
                )
            }

            item {
                SettingsRow(
                    icon = MaterialSymbols.ThumbUp,
                    title = stringRes(Res.string.cashu_settings_my_recommendations),
                    subtitle = stringRes(Res.string.cashu_settings_recommendations_subtitle),
                    onClick = { nav.nav(Route.CashuMintRecommendations) },
                )
            }

            // NUT-09 recovery: ask every mint in our list which blind
            // signatures it has previously issued for our seed and
            // republish them as fresh kind:7375 events. Useful after a
            // device loss or rogue-relay NIP-09 of our token events. Kept
            // last before the Danger Zone as an occasional maintenance action.
            item {
                val restoreState by viewModel.restoreState.collectAsState()
                val restoreSubtitle =
                    when (val s = restoreState) {
                        is CashuWalletViewModel.RestoreFlowState.Idle ->
                            stringRes(Res.string.cashu_settings_restore_subtitle)
                        is CashuWalletViewModel.RestoreFlowState.Running ->
                            stringRes(Res.string.cashu_settings_restore_running)
                        is CashuWalletViewModel.RestoreFlowState.Completed ->
                            if (s.totalSatsRecovered == 0L) {
                                stringRes(Res.string.cashu_settings_restore_nothing_found)
                            } else {
                                stringRes(
                                    Res.string.cashu_settings_restore_result,
                                    s.totalSatsRecovered.toString(),
                                    s.proofsRecovered.toString(),
                                )
                            }
                        is CashuWalletViewModel.RestoreFlowState.Error -> s.message
                    }
                SettingsRow(
                    icon = MaterialSymbols.CloudDownload,
                    title = stringRes(Res.string.cashu_settings_restore_title),
                    subtitle = restoreSubtitle,
                    onClick = {
                        if (restoreState !is CashuWalletViewModel.RestoreFlowState.Running) {
                            viewModel.restoreFromAllMints()
                        }
                    },
                )
            }

            // Destructive actions — only meaningful when a wallet exists.
            if (walletEvent != null) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringRes(Res.string.danger_zone),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                item {
                    SettingsRow(
                        icon = MaterialSymbols.Block,
                        title = stringRes(Res.string.cashu_settings_stop_nutzaps),
                        subtitle = stringRes(Res.string.cashu_settings_stop_nutzaps_subtitle),
                        isDanger = true,
                        onClick = { showStopNutzapsConfirm = true },
                    )
                }
                item {
                    SettingsRow(
                        icon = MaterialSymbols.Refresh,
                        title = stringRes(Res.string.cashu_settings_recreate_key),
                        subtitle = stringRes(Res.string.cashu_settings_recreate_key_subtitle),
                        isDanger = true,
                        onClick = { showRecreateKeyConfirm = true },
                    )
                }
                item {
                    SettingsRow(
                        icon = MaterialSymbols.ContentPaste,
                        title = stringRes(Res.string.cashu_settings_import_key),
                        subtitle = stringRes(Res.string.cashu_settings_import_key_subtitle),
                        isDanger = true,
                        onClick = { showImportKeyDialog = true },
                    )
                }
                item {
                    SettingsRow(
                        icon = MaterialSymbols.DeleteForever,
                        title = stringRes(Res.string.cashu_settings_delete_wallet),
                        subtitle = stringRes(Res.string.cashu_settings_delete_wallet_subtitle),
                        isDanger = true,
                        onClick = { showDeleteWalletConfirm = true },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showStopNutzapsConfirm) {
        AlertDialog(
            onDismissRequest = { showStopNutzapsConfirm = false },
            title = { Text(stringRes(Res.string.cashu_settings_stop_nutzaps_confirm_title)) },
            text = { Text(stringRes(Res.string.cashu_settings_stop_nutzaps_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.stopNutzaps()
                        showStopNutzapsConfirm = false
                    },
                ) { Text(stringRes(Res.string.cashu_settings_stop_nutzaps)) }
            },
            dismissButton = {
                TextButton(onClick = { showStopNutzapsConfirm = false }) {
                    Text(stringRes(Res.string.cancel))
                }
            },
        )
    }

    if (showRecreateKeyConfirm) {
        AlertDialog(
            onDismissRequest = { showRecreateKeyConfirm = false },
            title = { Text(stringRes(Res.string.cashu_settings_recreate_key_confirm_title)) },
            text = { Text(stringRes(Res.string.cashu_settings_recreate_key_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.recreateNutzapKey()
                        showRecreateKeyConfirm = false
                    },
                ) { Text(stringRes(Res.string.cashu_settings_recreate_key_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showRecreateKeyConfirm = false }) {
                    Text(stringRes(Res.string.cancel))
                }
            },
        )
    }

    if (showImportKeyDialog) {
        var keyInput by remember { mutableStateOf("") }
        var working by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!working) showImportKeyDialog = false },
            title = { Text(stringRes(Res.string.cashu_settings_import_key_confirm_title)) },
            text = {
                Column {
                    Text(stringRes(Res.string.cashu_settings_import_key_confirm_body))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = {
                            keyInput = it
                            error = null
                        },
                        label = { Text(stringRes(Res.string.cashu_settings_import_key_field)) },
                        placeholder = { Text("hex…") },
                        singleLine = true,
                        isError = error != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = keyInput.isNotBlank() && !working,
                    onClick = {
                        working = true
                        error = null
                        viewModel.recreateNutzapKey(manualPrivkey = keyInput.trim()) { err ->
                            working = false
                            if (err == null) showImportKeyDialog = false else error = err
                        }
                    },
                ) {
                    if (working) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringRes(Res.string.cashu_settings_import_key_action))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportKeyDialog = false }, enabled = !working) {
                    Text(stringRes(Res.string.cancel))
                }
            },
        )
    }

    if (showDeleteWalletConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteWalletConfirm = false },
            title = { Text(stringRes(Res.string.cashu_settings_delete_wallet_confirm_title)) },
            text = { Text(stringRes(Res.string.cashu_settings_delete_wallet_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteWalletConfirm = false
                        // Tear down the wallet, then go to the top-level Wallet
                        // hub — NOT back to CashuWalletScreen, which on an empty
                        // wallet auto-launches the find-or-create wizard and
                        // would funnel the user straight back into creating the
                        // wallet they just deleted. newStack pops the Cashu
                        // screens off the back stack so the wizard never composes.
                        viewModel.deleteWallet(onDone = {})
                        nav.newStack(Route.Wallet)
                    },
                ) { Text(stringRes(Res.string.cashu_settings_delete_wallet)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteWalletConfirm = false }) {
                    Text(stringRes(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsRow(
    icon: MaterialSymbol,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    isDanger: Boolean = false,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isDanger) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ============================================================
// @Preview composables — Android Studio rendering only
// ============================================================
// Each preview is rendered in both dark and light themes via
// ThemeComparisonColumn so we can eyeball both at once. Plain-data
// composables only — anything that takes AccountViewModel / INav /
// CashuWalletViewModel can't be cheaply mocked, so those are skipped.

@Preview
@Composable
fun SettingsRowEditWalletPreview() {
    ThemeComparisonColumn {
        SettingsRow(
            icon = MaterialSymbols.Edit,
            title = "My mints",
            subtitle = "Add or remove the mints your wallet uses.",
            onClick = {},
        )
    }
}

@Preview
@Composable
fun SettingsRowNoSubtitlePreview() {
    ThemeComparisonColumn {
        SettingsRow(
            icon = MaterialSymbols.Settings,
            title = "Some setting",
            subtitle = null,
            onClick = {},
        )
    }
}
