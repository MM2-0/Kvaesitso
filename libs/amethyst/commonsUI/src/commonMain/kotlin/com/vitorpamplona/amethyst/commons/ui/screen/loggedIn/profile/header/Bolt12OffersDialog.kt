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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.bolt12_offers
import com.vitorpamplona.amethyst.commons.resources.bolt12_pay_with_wallet
import com.vitorpamplona.amethyst.commons.resources.bolt12_payment_amount_sats
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copy_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.no_wallet_found
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.wallet.payBolt12Offer
import com.vitorpamplona.amethyst.commons.ui.wallet.rememberWalletAppLauncher
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch

@Composable
fun Bolt12OffersDialog(
    offers: List<String>,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val noWalletFoundStr = stringRes(Res.string.no_wallet_found)
    val walletApp = rememberWalletAppLauncher()
    val shortNotice = rememberShortNotice()
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // Whether we can settle in-app over the user's NIP-47 wallet (nwc#2 `pay`), or
    // must hand off to an external wallet via a bitcoin: intent.
    val canPayInApp = remember { accountViewModel.canPayBolt12ViaNwc() }

    // The offer the user chose to pay over NWC; drives the amount-entry dialog.
    var nwcPayOffer by remember { mutableStateOf<String?>(null) }

    M3ActionDialog(
        title = stringRes(Res.string.bolt12_offers),
        onDismiss = onDismiss,
    ) {
        M3ActionSection {
            offers.forEach { offer ->
                Bolt12OfferRow(
                    offer = offer,
                    canPayInApp = canPayInApp,
                    onCopy = {
                        scope.launch {
                            clipboardManager.setText(offer)
                            shortNotice.show(loadStringRes(Res.string.copied_to_clipboard))
                        }
                    },
                    onPayViaIntent = {
                        walletApp.payBolt12Offer(
                            offer = offer,
                            noWalletFound = noWalletFoundStr,
                            onPaid = { onDismiss() },
                            onError = { msg ->
                                shortNotice.show(msg)
                            },
                        )
                    },
                    onPayInApp = { nwcPayOffer = offer },
                )
            }
        }
    }

    nwcPayOffer?.let { offer ->
        Bolt12NwcAmountDialog(
            onDismiss = { nwcPayOffer = null },
            onPay = { amountMillisats ->
                accountViewModel.payBolt12OfferViaNwc(offer, amountMillisats)
                nwcPayOffer = null
                onDismiss()
            },
        )
    }
}

@Composable
private fun Bolt12OfferRow(
    offer: String,
    canPayInApp: Boolean,
    onCopy: () -> Unit,
    onPayViaIntent: () -> Unit,
    onPayInApp: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = abbreviateBolt12Offer(offer),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onCopy) {
            Icon(
                symbol = MaterialSymbols.ContentCopy,
                contentDescription = stringRes(Res.string.copy_to_clipboard),
                modifier = Size20Modifier,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canPayInApp) {
            IconButton(onClick = onPayInApp) {
                Icon(
                    symbol = MaterialSymbols.AccountBalanceWallet,
                    contentDescription = stringRes(Res.string.bolt12_pay_with_wallet),
                    modifier = Size20Modifier,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onPayViaIntent) {
            Icon(
                symbol = MaterialSymbols.Bolt,
                contentDescription = stringRes(Res.string.bolt12_offers),
                modifier = Size20Modifier,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Collects an amount (in sats) and returns it to [onPay] in millisats. */
@Composable
private fun Bolt12NwcAmountDialog(
    onDismiss: () -> Unit,
    onPay: (amountMillisats: Long) -> Unit,
) {
    var sats by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringRes(Res.string.bolt12_pay_with_wallet),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = sats,
                    onValueChange = { new -> sats = new.filter { it.isDigit() } },
                    label = { Text(text = stringRes(Res.string.bolt12_payment_amount_sats)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val amountSats = sats.toLongOrNull()
                            if (amountSats != null && amountSats > 0) {
                                onPay(amountSats * 1000)
                            }
                        },
                        shape = ButtonBorder,
                        enabled = (sats.toLongOrNull() ?: 0) > 0,
                    ) {
                        Text(text = stringRes(Res.string.bolt12_pay_with_wallet))
                    }
                }
            }
        }
    }
}

/**
 * The short form every BOLT12 surface shows for an `lno1…` offer: enough of the
 * head to recognise the prefix, the tail to tell two offers apart. Shared by the
 * profile chip, this dialog and the offers settings screen so they agree.
 */
fun abbreviateBolt12Offer(offer: String): String = "${offer.take(14)}\u2026${offer.takeLast(6)}"
