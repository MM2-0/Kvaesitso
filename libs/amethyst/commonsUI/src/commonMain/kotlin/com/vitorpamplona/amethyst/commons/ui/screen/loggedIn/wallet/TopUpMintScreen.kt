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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.reload_mint_available
import com.vitorpamplona.amethyst.commons.resources.reload_mint_awaiting_payment
import com.vitorpamplona.amethyst.commons.resources.reload_mint_copy_invoice
import com.vitorpamplona.amethyst.commons.resources.reload_mint_retry
import com.vitorpamplona.amethyst.commons.resources.reload_mint_section_from
import com.vitorpamplona.amethyst.commons.resources.sats
import com.vitorpamplona.amethyst.commons.resources.topup_mint_amount_label
import com.vitorpamplona.amethyst.commons.resources.topup_mint_confirm
import com.vitorpamplona.amethyst.commons.resources.topup_mint_title
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.wallet.ReloadSource
import com.vitorpamplona.amethyst.commons.wallet.TopUpMintViewModel
import com.vitorpamplona.amethyst.commons.wallet.TopUpStatus
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon as Material3Icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopUpMintScreen(
    mintUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: TopUpMintViewModel = rememberViewModel { TopUpMintViewModel() }
    LaunchedEffect(mintUrl) { viewModel.init(accountViewModel, mintUrl) }

    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var amountText by remember(mintUrl) { mutableStateOf("") }

    if (ui.status is TopUpStatus.Done) {
        LaunchedEffect(Unit) { nav.popBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.topup_mint_title)) },
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── Header: the mint we're topping up + its current balance ────
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Material3Icon(
                        imageVector = CustomHashTagIcons.Cashu,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = shortMint(ui.targetMint),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringRes(Res.string.reload_mint_available, sats(ui.targetBalanceSats)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    }
                }
            }

            // ── Amount to add ─────────────────────────────────────────────
            SectionHeader(stringRes(Res.string.topup_mint_amount_label))
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(12)
                    amountText = digits
                    viewModel.setAmount(digits.toLongOrNull() ?: 0L)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text(stringRes(Res.string.sats)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Funds from ────────────────────────────────────────────────
            SectionHeader(stringRes(Res.string.reload_mint_section_from))
            ui.sources.forEach { source ->
                SourceRow(
                    source = source,
                    selected = source == ui.selectedSource,
                    onSelect = { viewModel.selectSource(source) },
                )
            }

            Spacer(Modifier.height(4.dp))

            when (val status = ui.status) {
                is TopUpStatus.Working -> {
                    LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                    Text(status.step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.placeholderText)
                }

                is TopUpStatus.AwaitingInvoice -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        Text(stringRes(Res.string.reload_mint_awaiting_payment), style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(
                        onClick = { scope.launch { clipboard.setText(status.invoice) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringRes(Res.string.reload_mint_copy_invoice))
                    }
                }

                is TopUpStatus.Failed -> {
                    Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { viewModel.confirm() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringRes(Res.string.reload_mint_retry))
                    }
                }

                else -> {
                    val source = ui.selectedSource
                    val enabled =
                        ui.amountSats > 0 &&
                            (
                                source is ReloadSource.LightningWallet ||
                                    source is ReloadSource.LightningExternal ||
                                    (source is ReloadSource.Mint && source.canCover)
                            )
                    Button(
                        onClick = { viewModel.confirm() },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringRes(Res.string.topup_mint_confirm))
                    }
                }
            }
        }
    }
}
