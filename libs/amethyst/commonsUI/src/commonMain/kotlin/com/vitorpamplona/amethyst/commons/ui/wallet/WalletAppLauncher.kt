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
package com.vitorpamplona.amethyst.commons.ui.wallet

import androidx.compose.runtime.Composable

/** Hands a payment to a wallet app installed on the device. */
fun interface WalletAppLauncher {
    /**
     * Opens the payment [uri] (`lightning:` or `bitcoin:`) in the wallet app. [onPaid] runs once
     * the wallet took it, which is not a payment confirmation; [onError] gets [noWalletFound] when
     * no wallet can open it. Desktop never calls [onError]: with no wallet app it shows the payment
     * to pay from another wallet instead ([PayElsewhereDialog]).
     */
    fun openPaymentUri(
        uri: String,
        noWalletFound: String,
        onPaid: () -> Unit,
        onError: (String) -> Unit,
    )
}

/** Opens a BOLT-11 [invoice] in the wallet app; see [WalletAppLauncher.openPaymentUri]. */
fun WalletAppLauncher.payInvoice(
    invoice: String,
    noWalletFound: String,
    onPaid: () -> Unit,
    onError: (String) -> Unit,
) = openPaymentUri("lightning:$invoice", noWalletFound, onPaid, onError)

/** Opens a BOLT-12 [offer] in the wallet app; see [WalletAppLauncher.openPaymentUri]. */
fun WalletAppLauncher.payBolt12Offer(
    offer: String,
    noWalletFound: String,
    onPaid: () -> Unit,
    onError: (String) -> Unit,
) = openPaymentUri("bitcoin:?lno=$offer", noWalletFound, onPaid, onError)

@Composable
expect fun rememberWalletAppLauncher(): WalletAppLauncher
