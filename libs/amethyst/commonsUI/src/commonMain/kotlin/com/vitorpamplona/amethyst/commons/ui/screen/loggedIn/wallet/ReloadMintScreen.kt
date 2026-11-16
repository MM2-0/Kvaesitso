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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.reload_mint_available
import com.vitorpamplona.amethyst.commons.resources.reload_mint_awaiting_payment
import com.vitorpamplona.amethyst.commons.resources.reload_mint_confirm
import com.vitorpamplona.amethyst.commons.resources.reload_mint_copy_invoice
import com.vitorpamplona.amethyst.commons.resources.reload_mint_funded
import com.vitorpamplona.amethyst.commons.resources.reload_mint_lightning_desc
import com.vitorpamplona.amethyst.commons.resources.reload_mint_needs_more
import com.vitorpamplona.amethyst.commons.resources.reload_mint_not_enough
import com.vitorpamplona.amethyst.commons.resources.reload_mint_pay_lightning
import com.vitorpamplona.amethyst.commons.resources.reload_mint_recipient_fallback
import com.vitorpamplona.amethyst.commons.resources.reload_mint_retry
import com.vitorpamplona.amethyst.commons.resources.reload_mint_sats_amount
import com.vitorpamplona.amethyst.commons.resources.reload_mint_section_from
import com.vitorpamplona.amethyst.commons.resources.reload_mint_section_to
import com.vitorpamplona.amethyst.commons.resources.reload_mint_send_confirm
import com.vitorpamplona.amethyst.commons.resources.reload_mint_summary
import com.vitorpamplona.amethyst.commons.resources.reload_mint_summary_funded
import com.vitorpamplona.amethyst.commons.resources.reload_mint_title
import com.vitorpamplona.amethyst.commons.resources.reload_mint_topup_label
import com.vitorpamplona.amethyst.commons.resources.sats
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.wallet.ReloadMintRequest
import com.vitorpamplona.amethyst.commons.wallet.ReloadMintViewModel
import com.vitorpamplona.amethyst.commons.wallet.ReloadSource
import com.vitorpamplona.amethyst.commons.wallet.ReloadStatus
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import androidx.compose.material3.Icon as Material3Icon

/**
 * Stash the pending nutzap and open the Reload Mint screen. Called from the zap
 * picker when the user taps an underfunded (NEEDS_RELOAD) cashu logo.
 */
@OptIn(ExperimentalUuidApi::class)
fun navigateToReloadMint(
    accountViewModel: AccountViewModel,
    nav: INav,
    baseNote: Note,
    amountSats: Long,
) {
    val uid = Uuid.random().toString()
    accountViewModel.tempReloadRequestCache.put(uid, ReloadMintRequest(baseNote, amountSats))
    nav.nav(Route.ReloadMint(uid))
}

internal fun shortMint(url: String): String = url.removePrefix("https://").removePrefix("http://").removeSuffix("/")

fun sats(value: Long): String = showAmount(BigDecimal(value))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReloadMintScreen(
    requestId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val request = remember(requestId) { accountViewModel.tempReloadRequestCache.get(requestId) }

    // The cache entry can be gone after process death — nothing to fund, pop.
    if (request == null) {
        LaunchedEffect(Unit) { nav.popBack() }
        return
    }

    val viewModel: ReloadMintViewModel = rememberViewModel { ReloadMintViewModel() }
    LaunchedEffect(requestId) { viewModel.init(accountViewModel, request) }

    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // Local source-of-truth for the editable top-up field. The send amount is
    // fixed; the top-up defaults to the shortfall the VM computes once the wallet
    // has loaded, and every edit is pushed back so the VM recomputes sources/fees.
    var topUpText by remember(requestId) { mutableStateOf("") }
    var topUpSeeded by remember(requestId) { mutableStateOf(false) }
    LaunchedEffect(ui.topUpSats) {
        if (!topUpSeeded && ui.topUpSats > 0L) {
            topUpText = ui.topUpSats.toString()
            topUpSeeded = true
        }
    }

    if (ui.status is ReloadStatus.Done) {
        LaunchedEffect(Unit) { nav.popBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.reload_mint_title)) },
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
            // ── Header: you → cashu zap → recipient ────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserPicture(
                    userHex = accountViewModel.account.userProfile().pubkeyHex,
                    size = 54.dp,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
                Spacer(Modifier.width(14.dp))
                Material3Icon(
                    imageVector = CustomHashTagIcons.Cashu,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                // The amount we're about to zap, shown along the flow.
                Text(
                    text = stringRes(Res.string.reload_mint_sats_amount, sats(ui.sendSats)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    symbol = MaterialSymbols.AutoMirrored.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(14.dp))
                UserPicture(
                    userHex = ui.recipient,
                    size = 54.dp,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            // ── Top-up amount (editable; only when the target is short) ────
            if (ui.shortfallSats > 0L) {
                SectionHeader(stringRes(Res.string.reload_mint_topup_label))
                OutlinedTextField(
                    value = topUpText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(12)
                        topUpText = digits
                        viewModel.setTopUp(digits.toLongOrNull() ?: 0L)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text(stringRes(Res.string.sats)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Destination ───────────────────────────────────────────────
            SectionHeader(stringRes(Res.string.reload_mint_section_to))
            if (ui.targetOptions.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.targetOptions.forEach { mint ->
                        FilterChip(
                            selected = mint == ui.selectedTarget,
                            onClick = { viewModel.selectTarget(mint) },
                            label = { Text(shortMint(mint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            val targetBalance = ui.balances.firstOrNull { it.mintUrl == ui.selectedTarget }?.balanceSats ?: 0L
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = shortMint(ui.selectedTarget),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringRes(Res.string.reload_mint_available, sats(targetBalance)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    }
                    Text(
                        text =
                            if (ui.shortfallSats > 0) {
                                stringRes(Res.string.reload_mint_needs_more, sats(ui.shortfallSats))
                            } else {
                                stringRes(Res.string.reload_mint_funded)
                            },
                        color =
                            if (ui.shortfallSats > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.placeholderText
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // ── Funds from (only when a top-up is actually needed) ─────────
            if (ui.shortfallSats > 0) {
                SectionHeader(stringRes(Res.string.reload_mint_section_from))
                ui.sources.forEach { source ->
                    SourceRow(
                        source = source,
                        selected = source == ui.selectedSource,
                        onSelect = { viewModel.selectSource(source) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Summary: what we top up and who we zap ─────────────────────
            val recipientName = ui.recipientName.ifBlank { stringRes(Res.string.reload_mint_recipient_fallback) }
            Text(
                text =
                    if (ui.shortfallSats > 0) {
                        stringRes(
                            Res.string.reload_mint_summary,
                            sats(maxOf(ui.topUpSats, ui.shortfallSats)),
                            shortMint(ui.selectedTarget),
                            recipientName,
                            sats(ui.sendSats),
                            sats(ui.estFeeSats),
                        )
                    } else {
                        stringRes(Res.string.reload_mint_summary_funded, recipientName, sats(ui.sendSats), shortMint(ui.selectedTarget))
                    },
                style = MaterialTheme.typography.bodyMedium,
            )

            when (val status = ui.status) {
                is ReloadStatus.Working -> {
                    LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                    Text(status.step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.placeholderText)
                }

                is ReloadStatus.AwaitingInvoice -> {
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

                is ReloadStatus.Failed -> {
                    Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { viewModel.confirm() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringRes(Res.string.reload_mint_retry))
                    }
                }

                else -> {
                    val source = ui.selectedSource
                    val enabled =
                        ui.sendSats > 0 &&
                            (
                                ui.shortfallSats == 0L ||
                                    source is ReloadSource.LightningWallet ||
                                    source is ReloadSource.LightningExternal ||
                                    (source is ReloadSource.Mint && source.canCover)
                            )
                    Button(
                        onClick = { viewModel.confirm() },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringRes(if (ui.shortfallSats > 0) Res.string.reload_mint_confirm else Res.string.reload_mint_send_confirm))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
internal fun SourceRow(
    source: ReloadSource,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val enabled =
        when (source) {
            is ReloadSource.Mint -> source.canCover
            is ReloadSource.LightningWallet -> true
            ReloadSource.LightningExternal -> true
        }
    val title =
        when (source) {
            is ReloadSource.Mint -> shortMint(source.mintUrl)
            is ReloadSource.LightningWallet -> source.name
            ReloadSource.LightningExternal -> stringRes(Res.string.reload_mint_pay_lightning)
        }
    // Description / balance lives on its own row under the title so longer text
    // (e.g. the Lightning explanation) wraps instead of clipping.
    val subtitle =
        when (source) {
            is ReloadSource.Mint ->
                if (source.canCover) {
                    stringRes(Res.string.reload_mint_available, sats(source.balanceSats))
                } else {
                    stringRes(Res.string.reload_mint_not_enough)
                }
            is ReloadSource.LightningWallet -> stringRes(Res.string.reload_mint_lightning_desc)
            ReloadSource.LightningExternal -> stringRes(Res.string.reload_mint_lightning_desc)
        }

    Card(
        // Muted selection: the radio carries the signal, so the fill/border stay
        // subtle (a faint primary wash + a thin, low-opacity outline).
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, enabled = enabled, onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            }
        }
    }
}
