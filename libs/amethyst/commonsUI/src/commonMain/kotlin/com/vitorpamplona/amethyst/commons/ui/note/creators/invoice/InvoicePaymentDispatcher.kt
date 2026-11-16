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
package com.vitorpamplona.amethyst.commons.ui.note.creators.invoice

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.vitorpamplona.amethyst.commons.model.payments.PaymentSource
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.clink_confirm_pay_amount_via_source
import com.vitorpamplona.amethyst.commons.resources.clink_confirm_pay_via_source
import com.vitorpamplona.amethyst.commons.resources.clink_confirm_payment_title
import com.vitorpamplona.amethyst.commons.resources.clink_debit_no_response
import com.vitorpamplona.amethyst.commons.resources.no_wallet_found
import com.vitorpamplona.amethyst.commons.resources.pay
import com.vitorpamplona.amethyst.commons.resources.sats
import com.vitorpamplona.amethyst.commons.service.nwc.nwcFailureDetail
import com.vitorpamplona.amethyst.commons.service.nwc.nwcTimeoutMessage
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.wallet.payInvoice
import com.vitorpamplona.amethyst.commons.ui.wallet.rememberWalletAppLauncher
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.utils.toLongValue
import kotlinx.coroutines.launch

/**
 * Pays a single BOLT-11 from an in-post card (offer card, invoice card) through the
 * user's selected default payment source.
 *
 * Unlike the zap button — a deliberate small-amount tap that fires immediately — a
 * card "Pay" can be a larger or variable amount, so an **in-app** payment (NWC or CLINK
 * debit) is gated behind a confirmation dialog. The external-wallet path needs no extra
 * confirmation: the wallet app presents its own.
 *
 * Drive it from a nullable `bolt11` state: set it to trigger, [onClear] resets it.
 */
@Composable
fun InvoicePaymentDispatcher(
    bolt11: String?,
    accountViewModel: AccountViewModel,
    onClear: () -> Unit,
    onError: (String) -> Unit,
    onSuccess: () -> Unit = {},
) {
    val noWalletFoundStr = stringRes(Res.string.no_wallet_found)
    val clinkDebitNoResponseStr = stringRes(Res.string.clink_debit_no_response)
    if (bolt11 == null) return
    val walletLauncher = rememberWalletAppLauncher()
    // The NIP-47 callbacks below are invoked by the relay dispatcher, not by
    // composition, so their bodies run in this scope to read their messages.
    val scope = rememberCoroutineScope()

    val source = remember(bolt11) { accountViewModel.account.settings.defaultPaymentSource() }

    if (source == null) {
        // No in-app wallet configured -> hand off to an external wallet app (it confirms).
        LaunchedEffect(bolt11) {
            walletLauncher.payInvoice(bolt11, noWalletFoundStr, onSuccess, onError)
            onClear()
        }
        return
    }

    val amountSats =
        remember(bolt11) {
            try {
                LnInvoiceUtil.getAmountInSats(bolt11).toLongValue().takeIf { it > 0 }
            } catch (_: Exception) {
                null
            }
        }

    ConfirmPaymentDialog(
        amountSats = amountSats,
        sourceName = source.name,
        onConfirm = {
            when (source) {
                is PaymentSource.Nwc ->
                    accountViewModel.sendZapPaymentRequestFor(
                        bolt11 = bolt11,
                        zappedNote = null,
                        onTimeout = { scope.launch { onError(nwcTimeoutMessage()) } },
                        onResponse = { response ->
                            scope.launch {
                                val failure = response.nwcFailureDetail()
                                if (failure == null) onSuccess() else onError(failure)
                            }
                        },
                    )

                is PaymentSource.ClinkDebit ->
                    accountViewModel.payInvoiceViaClinkDebit(source.wallet.pointer, bolt11) { response ->
                        if (response?.isOk() == true) {
                            onSuccess()
                        } else {
                            onError(
                                response?.failureDetail()
                                    ?: clinkDebitNoResponseStr,
                            )
                        }
                    }
            }
            onClear()
        },
        onDismiss = onClear,
    )
}

@Composable
private fun ConfirmPaymentDialog(
    amountSats: Long?,
    sourceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message =
        if (amountSats != null) {
            val amountText = "$amountSats ${stringRes(Res.string.sats)}"
            stringRes(Res.string.clink_confirm_pay_amount_via_source, amountText, sourceName)
        } else {
            stringRes(Res.string.clink_confirm_pay_via_source, sourceName)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.clink_confirm_payment_title)) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringRes(Res.string.pay)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}
