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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.error_dialog_zap_error
import com.vitorpamplona.amethyst.commons.resources.no_wallet_found
import com.vitorpamplona.amethyst.commons.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ZapAmountChoiceGrid
import com.vitorpamplona.amethyst.commons.ui.note.observeZapRailCapability
import com.vitorpamplona.amethyst.commons.ui.note.payViaIntentOrManualSplit
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.navigateToReloadMint
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.wallet.rememberWalletAppLauncher
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.uuid.ExperimentalUuidApi

/**
 * The user's zap presets unpacked as rail-aware amount chips (same grid the zap
 * amount popup shows), firing directly from the sheet. Lightning/cashu zaps
 * dismiss immediately — errors surface as toasts and the receipt lands on the
 * bubble's sats chip; on-chain amounts hand off to the dialog hosted by the sheet.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun QuickZapAmountRow(
    note: Note,
    onDismiss: () -> Unit,
    onOnchainRequest: (Long?) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noWalletFoundStr = stringRes(Res.string.no_wallet_found)
    val zapAmountChoices by
        accountViewModel.account.settings.syncedSettings.zaps.zapAmountChoices
            .collectAsStateWithLifecycle()

    val amountChoices = remember(zapAmountChoices) { zapAmountChoices.distinct().toImmutableList() }
    if (amountChoices.isEmpty()) return

    val railCapability =
        observeZapRailCapability(
            baseNote = note,
            accountViewModel = accountViewModel,
            // Onchain zap events are public and would e-tag the rumor id.
            onchainSupported = !note.isPrivateRumor(),
        )

    val walletLauncher = rememberWalletAppLauncher()

    val onError = { _: String, message: String, user: User? ->
        // Payment failed — drop the optimistic "zapping" indicator on the bubble.
        accountViewModel.endZapInFlight(note.idHex)
        accountViewModel.toastManager.toast(Res.string.error_dialog_zap_error, message, user)
    }

    val onPayViaIntent = { payables: ImmutableList<ZapPaymentHandler.Payable> ->
        // Handoff to an external wallet: we can't observe whether it completes, so clear
        // the optimistic indicator rather than leave it spinning forever.
        accountViewModel.endZapInFlight(note.idHex)
        payViaIntentOrManualSplit(payables, walletLauncher, noWalletFoundStr, accountViewModel, nav)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        ZapAmountChoiceGrid(
            amountChoices = amountChoices,
            railCapability = railCapability,
            onLightningZap = { amountInSats ->
                // Show a pending zap chip on the bubble immediately; it settles into the
                // real sats chip once the receipt lands (or clears on error/timeout).
                accountViewModel.markZapInFlight(note.idHex)
                accountViewModel.zap(
                    note,
                    amountInSats * 1000,
                    null,
                    "",
                    true,
                    onError,
                    { },
                    onPayViaIntent,
                )
                onDismiss()
            },
            onNutzap = { amountInSats ->
                accountViewModel.markZapInFlight(note.idHex)
                accountViewModel.sendNutzap(
                    baseNote = note,
                    amountSats = amountInSats,
                    message = "",
                    onError = onError,
                    onProgress = { },
                )
                onDismiss()
            },
            onOnchainAmount = onOnchainRequest,
            onReloadNutzap = { amount ->
                navigateToReloadMint(accountViewModel, nav, note, amount)
                onDismiss()
            },
            onChangeAmount = {
                nav.nav(Route.UpdateZapAmount())
                onDismiss()
            },
            // Hands off to another app; the sheet must not stay stacked behind it.
            onHandedOff = onDismiss,
        )
    }
}
