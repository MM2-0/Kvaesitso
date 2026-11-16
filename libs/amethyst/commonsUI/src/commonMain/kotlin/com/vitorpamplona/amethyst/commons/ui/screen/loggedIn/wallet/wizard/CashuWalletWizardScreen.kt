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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.wizard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.cashu_wallet_title
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_adopting
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_analyzing
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_create
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_invalid
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_main_label
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_mints_label
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_multiple_description
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_multiple_title
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_no_funds
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_none_description
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_none_title
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_old_label
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_recover_funds
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_recoverable
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_recovered
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_retry
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_searching
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_searching_description
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_searching_progress
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_set_main
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_set_main_first
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_single_description
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_single_title
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_title
import com.vitorpamplona.amethyst.commons.resources.cashu_wizard_use_wallet
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.wallet.wizard.AdoptState
import com.vitorpamplona.amethyst.commons.wallet.wizard.CashuWalletWizardViewModel
import com.vitorpamplona.amethyst.commons.wallet.wizard.FoundWallet
import com.vitorpamplona.amethyst.commons.wallet.wizard.RecoveryState
import com.vitorpamplona.amethyst.commons.wallet.wizard.WizardState

/**
 * Find-or-create wizard for the Cashu wallet. Reached from the wallet screen's
 * empty state (and only there) when no wallet is loaded. Crawls every relay for
 * an existing NIP-60 wallet and routes the user into one of three outcomes:
 * create new, adopt the single found wallet, or pick the newest among several
 * and recover funds from the older ones.
 *
 * Phases [AnimatedContent]-transition into each other; the long relay crawl
 * shows a determinate progress bar with a count that ticks up; and the
 * "recovered N sats" moments count up + fire a haptic, so the find-my-funds
 * flow has a payoff rather than a static jump.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashuWalletWizardScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: CashuWalletWizardViewModel = rememberViewModel { CashuWalletWizardViewModel() }
    viewModel.init(accountViewModel)

    val wizardState by viewModel.wizardState.collectAsState()
    val adoptState by viewModel.adoptState.collectAsState()
    val recoveryStates by viewModel.recoveryStates.collectAsState()
    val mainWalletEvent by viewModel.mainWalletEvent.collectAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        if (viewModel.wizardState.value is WizardState.Idle) viewModel.startDiscovery()
    }

    // The "create new" path leaves the wizard for the mint manager, which
    // publishes a kind:17375 and pops back here. Once that wallet lands, the
    // wizard's job is done — return to the wallet screen instead of stranding
    // the user on the now-stale "No wallet found" view.
    LaunchedEffect(mainWalletEvent != null, wizardState) {
        if (mainWalletEvent != null && wizardState is WizardState.NoWallet) nav.popBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.cashu_wizard_title)) },
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
        AnimatedContent(
            targetState = wizardState,
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            // Key the transition on the phase TYPE so frequent in-phase updates
            // (the crawl's relays-completed counter) don't re-trigger the
            // enter/exit animation — only genuine phase changes cross-fade.
            contentKey = { it::class },
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 12 })
                    .togetherWith(fadeOut(tween(160)))
            },
            label = "cashuWizardPhase",
        ) { state ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state) {
                    is WizardState.Idle ->
                        BusyState(stringRes(Res.string.cashu_wizard_searching), null)

                    is WizardState.Crawling ->
                        CrawlingState(state)

                    is WizardState.Analyzing ->
                        BusyState(stringRes(Res.string.cashu_wizard_analyzing), null)

                    is WizardState.NoWallet ->
                        NoWalletContent(onCreate = { nav.nav(Route.CashuWalletCreated) })

                    is WizardState.Single ->
                        SingleContent(
                            wallet = state.wallet,
                            adoptState = adoptState,
                            haptic = haptic,
                            onUse = { viewModel.adoptAsMain(state.wallet, recoverFunds = true) },
                            onDone = { nav.popBack() },
                        )

                    is WizardState.Multiple ->
                        MultipleContent(
                            main = state.main,
                            others = state.others,
                            adoptState = adoptState,
                            recoveryStates = recoveryStates,
                            haptic = haptic,
                            onSetMain = { viewModel.adoptAsMain(state.main, recoverFunds = true) },
                            onRecoverOld = { viewModel.recoverOldWallet(it) },
                            onDone = { nav.popBack() },
                        )

                    is WizardState.Error ->
                        ErrorContent(
                            message = state.message,
                            onRetry = { viewModel.startDiscovery() },
                        )
                }
            }
        }
    }
}

private fun formatSats(sats: Long): String = formatGrouped(sats)

/**
 * Animate a sats figure counting up from zero the first time it's shown, then
 * format it. The reveal is the payoff of the find/recover flow, so the headline
 * numbers tick up instead of snapping in. Safe to call with 0 (returns "0").
 */
@Composable
private fun countUpSats(targetSats: Long): String {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(targetSats) { armed = true }
    val shown by animateIntAsState(
        // Single-wallet balances comfortably fit in Int (< 21 BTC in sats);
        // coerce defensively so an absurd value can't overflow the animation.
        targetValue = if (armed) targetSats.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt() else 0,
        animationSpec = tween(durationMillis = 700),
        label = "satsCountUp",
    )
    return formatSats(shown.toLong())
}

@Composable
private fun BusyState(
    title: String,
    subtitle: String?,
) {
    Spacer(Modifier.height(48.dp))
    CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
    Spacer(Modifier.height(20.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    if (subtitle != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The relay crawl can sweep hundreds of relays, so it gets a determinate bar
 * that fills as relays complete and a count that ticks up — otherwise a 30s
 * sweep behind a bare spinner reads as "hung".
 */
@Composable
private fun CrawlingState(state: WizardState.Crawling) {
    val fraction = if (state.totalRelays > 0) state.relaysCompleted.toFloat() / state.totalRelays else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 400),
        label = "scanProgress",
    )
    val animatedCount by animateIntAsState(
        targetValue = state.relaysCompleted,
        animationSpec = tween(durationMillis = 400),
        label = "scanCount",
    )

    Spacer(Modifier.height(48.dp))
    Icon(
        symbol = MaterialSymbols.Search,
        contentDescription = null,
        modifier = Modifier.size(40.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(20.dp))
    Text(
        text = stringRes(Res.string.cashu_wizard_searching),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringRes(Res.string.cashu_wizard_searching_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    LinearProgressIndicator(
        progress = { animatedFraction },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringRes(Res.string.cashu_wizard_searching_progress, animatedCount, state.totalRelays),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NoWalletContent(onCreate: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Icon(
        symbol = MaterialSymbols.AccountBalanceWallet,
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringRes(Res.string.cashu_wizard_none_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringRes(Res.string.cashu_wizard_none_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
        Text(stringRes(Res.string.cashu_wizard_create))
    }
}

@Composable
private fun SingleContent(
    wallet: FoundWallet,
    adoptState: AdoptState,
    haptic: HapticFeedback,
    onUse: () -> Unit,
    onDone: () -> Unit,
) {
    LaunchedEffect(adoptState is AdoptState.Done) {
        if (adoptState is AdoptState.Done) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Text(
        text = stringRes(Res.string.cashu_wizard_single_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringRes(Res.string.cashu_wizard_single_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    WalletCard(wallet)
    Spacer(Modifier.height(24.dp))

    when (adoptState) {
        is AdoptState.Done -> {
            val recoveredCount = countUpSats(adoptState.recoveredSats)
            SuccessLine(
                if (adoptState.recoveredSats > 0) {
                    stringRes(Res.string.cashu_wizard_recovered, recoveredCount)
                } else {
                    stringRes(Res.string.cashu_wizard_single_title)
                },
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringRes(Res.string.cashu_wallet_title))
            }
        }

        is AdoptState.Working ->
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringRes(Res.string.cashu_wizard_adopting))
            }

        is AdoptState.Error -> {
            ErrorLine(adoptState.message)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) {
                Text(stringRes(Res.string.cashu_wizard_use_wallet))
            }
        }

        is AdoptState.Idle ->
            Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) {
                Text(stringRes(Res.string.cashu_wizard_use_wallet))
            }
    }
}

@Composable
private fun MultipleContent(
    main: FoundWallet,
    others: List<FoundWallet>,
    adoptState: AdoptState,
    recoveryStates: Map<String, RecoveryState>,
    haptic: HapticFeedback,
    onSetMain: () -> Unit,
    onRecoverOld: (FoundWallet) -> Unit,
    onDone: () -> Unit,
) {
    val mainAdopted = adoptState is AdoptState.Done

    LaunchedEffect(mainAdopted) {
        if (mainAdopted) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Text(
        text = stringRes(Res.string.cashu_wizard_multiple_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringRes(Res.string.cashu_wizard_multiple_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))

    // Main (newest) wallet
    Text(
        text = stringRes(Res.string.cashu_wizard_main_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    WalletCard(main)
    Spacer(Modifier.height(12.dp))
    when (adoptState) {
        is AdoptState.Working ->
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringRes(Res.string.cashu_wizard_adopting))
            }

        is AdoptState.Done -> {
            val recoveredCount = countUpSats(adoptState.recoveredSats)
            SuccessLine(
                if (adoptState.recoveredSats > 0) {
                    stringRes(Res.string.cashu_wizard_recovered, recoveredCount)
                } else {
                    stringRes(Res.string.cashu_wizard_main_label)
                },
            )
        }

        is AdoptState.Error -> {
            ErrorLine(adoptState.message)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSetMain, modifier = Modifier.fillMaxWidth()) {
                Text(stringRes(Res.string.cashu_wizard_set_main))
            }
        }

        is AdoptState.Idle ->
            Button(onClick = onSetMain, modifier = Modifier.fillMaxWidth()) {
                Text(stringRes(Res.string.cashu_wizard_set_main))
            }
    }

    if (others.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringRes(Res.string.cashu_wizard_old_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!mainAdopted) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringRes(Res.string.cashu_wizard_set_main_first),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        others.forEach { old ->
            Spacer(Modifier.height(8.dp))
            OldWalletCard(
                wallet = old,
                recoveryState = recoveryStates[old.event.id],
                enabled = mainAdopted,
                haptic = haptic,
                onRecover = { onRecoverOld(old) },
            )
        }
    }

    Spacer(Modifier.height(24.dp))
    Button(onClick = onDone, enabled = mainAdopted, modifier = Modifier.fillMaxWidth()) {
        Text(stringRes(Res.string.cashu_wallet_title))
    }
}

@Composable
private fun WalletCard(wallet: FoundWallet) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (!wallet.valid) {
                Text(
                    text = stringRes(Res.string.cashu_wizard_invalid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }
            Text(
                text = stringRes(Res.string.cashu_wizard_mints_label, wallet.mints.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            // Always call countUpSats (unconditional call site keeps the
            // composable structurally stable); the "0" is unused when there
            // are no funds.
            val countedSats = countUpSats(wallet.totalRecoverableSats)
            val hasFunds = wallet.totalRecoverableSats > 0
            Text(
                text =
                    if (hasFunds) {
                        stringRes(Res.string.cashu_wizard_recoverable, countedSats)
                    } else {
                        stringRes(Res.string.cashu_wizard_no_funds)
                    },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (hasFunds) FontWeight.Bold else FontWeight.Normal,
                color =
                    if (hasFunds) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

@Composable
private fun OldWalletCard(
    wallet: FoundWallet,
    recoveryState: RecoveryState?,
    enabled: Boolean,
    haptic: HapticFeedback,
    onRecover: () -> Unit,
) {
    LaunchedEffect(recoveryState is RecoveryState.Done) {
        if (recoveryState is RecoveryState.Done) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringRes(Res.string.cashu_wizard_mints_label, wallet.mints.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            when (recoveryState) {
                is RecoveryState.Working ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringRes(Res.string.cashu_wizard_recover_funds),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                is RecoveryState.Done ->
                    SuccessLine(stringRes(Res.string.cashu_wizard_recovered, countUpSats(recoveryState.recoveredSats)))

                is RecoveryState.Error ->
                    ErrorLine(recoveryState.message)

                null -> {
                    val hasFunds = wallet.totalRecoverableSats > 0
                    Text(
                        text =
                            if (hasFunds) {
                                stringRes(Res.string.cashu_wizard_recoverable, formatSats(wallet.totalRecoverableSats))
                            } else {
                                stringRes(Res.string.cashu_wizard_no_funds)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (hasFunds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (hasFunds) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRecover,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringRes(Res.string.cashu_wizard_recover_funds))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            symbol = MaterialSymbols.Check,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorLine(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Spacer(Modifier.height(24.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text(stringRes(Res.string.cashu_wizard_retry))
    }
}
