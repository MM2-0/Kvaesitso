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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.paymentTargets.PaymentTargetsViewModel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.delete_payment_target
import com.vitorpamplona.amethyst.commons.resources.lightning_address
import com.vitorpamplona.amethyst.commons.resources.no_payment_targets_message
import com.vitorpamplona.amethyst.commons.resources.payment_targets
import com.vitorpamplona.amethyst.commons.resources.payment_targets_section_explainer
import com.vitorpamplona.amethyst.commons.resources.wallet_connect
import com.vitorpamplona.amethyst.commons.ui.actions.paymentTargets.PaymentTargetAddField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.commons.ui.note.UpdateZapAmountContent
import com.vitorpamplona.amethyst.commons.ui.note.UpdateZapAmountViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.SettingsCategory
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.WalletViewModel
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsCategory
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SettingsCategorySpacingModifier
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTarget

@Composable
fun NIP47SetupScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    nip47: String?,
) {
    val postViewModel: UpdateZapAmountViewModel = rememberViewModel { UpdateZapAmountViewModel() }
    postViewModel.init(accountViewModel)

    val walletViewModel: WalletViewModel = rememberViewModel { WalletViewModel() }
    walletViewModel.init(accountViewModel)

    val paymentTargetsViewModel: PaymentTargetsViewModel = rememberViewModel { PaymentTargetsViewModel() }
    paymentTargetsViewModel.init(accountViewModel)

    LaunchedEffect(accountViewModel, postViewModel) {
        postViewModel.load()
        walletViewModel.loadLnAddress()
        paymentTargetsViewModel.load()
    }

    NIP47SetupScreen(postViewModel, walletViewModel, paymentTargetsViewModel, accountViewModel, nav, nip47)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NIP47SetupScreen(
    postViewModel: UpdateZapAmountViewModel,
    walletViewModel: WalletViewModel,
    paymentTargetsViewModel: PaymentTargetsViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    nip47: String?,
) {
    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = Res.string.wallet_connect,
                isActive = postViewModel::hasChanged,
                onCancel = {
                    postViewModel.cancel()
                    paymentTargetsViewModel.refresh()
                    nav.popBack()
                },
                onPost = {
                    accountViewModel.launchSigner {
                        postViewModel.sendPostSuspend()
                        walletViewModel.saveLnAddressSuspend()
                        paymentTargetsViewModel.savePaymentTargetsSuspend()
                    }
                    nav.popBack()
                },
            )
        },
    ) {
        Column(Modifier.padding(it)) {
            UpdateZapAmountContent(
                postViewModel,
                onClose = {
                    postViewModel.cancel()
                    paymentTargetsViewModel.refresh()
                    nav.popBack()
                },
                accountViewModel,
            ) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                LightningAddressSetupSection(walletViewModel)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                PaymentTargetsInlineSection(paymentTargetsViewModel)
            }
        }
    }
}

@Composable
private fun LightningAddressSetupSection(walletViewModel: WalletViewModel) {
    val lnAddress by walletViewModel.lnAddress.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringRes(Res.string.lightning_address),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            modifier = SettingsCategorySpacingModifier,
        )
        OutlinedTextField(
            value = lnAddress,
            onValueChange = { walletViewModel.updateLnAddress(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "me@mylightningnode.com",
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            singleLine = true,
        )
    }
}

@Composable
private fun PaymentTargetsInlineSection(viewModel: PaymentTargetsViewModel) {
    val targets by viewModel.paymentTargets.collectAsStateWithLifecycle()

    SettingsCategory(
        Res.string.payment_targets,
        Res.string.payment_targets_section_explainer,
        SettingsCategorySpacingModifier,
    )

    if (targets.isEmpty()) {
        Text(
            text = stringRes(id = Res.string.no_payment_targets_message),
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.grayText,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        Column {
            targets.forEach { target ->
                PaymentTargetInlineEntry(target = target, onDelete = { viewModel.removeTarget(target) })
            }
        }
    }

    PaymentTargetAddField { type, authority ->
        viewModel.addTarget(type, authority)
    }
}

@Composable
private fun PaymentTargetInlineEntry(
    target: PaymentTarget,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.type.replaceFirstChar(Char::titlecase),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = target.authority,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                symbol = MaterialSymbols.Delete,
                contentDescription = stringRes(id = Res.string.delete_payment_target),
            )
        }
    }
}
