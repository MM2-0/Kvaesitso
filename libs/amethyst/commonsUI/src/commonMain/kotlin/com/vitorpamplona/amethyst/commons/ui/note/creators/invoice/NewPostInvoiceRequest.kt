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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.lightning_create_and_add_invoice
import com.vitorpamplona.amethyst.commons.resources.lightning_invoice
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun NewPostInvoiceRequest(
    onSuccess: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    val user = accountViewModel.account.userProfile()
    val info by observeUserInfo(user, accountViewModel)
    val lnAddress = info?.info?.lnAddress()

    if (lnAddress != null) {
        InvoiceRequest(
            lud16 = lnAddress,
            user = accountViewModel.account.userProfile(),
            accountViewModel = accountViewModel,
            titleText = stringRes(id = Res.string.lightning_invoice),
            buttonText = stringRes(id = Res.string.lightning_create_and_add_invoice),
            onNewInvoice = onSuccess,
            onError = accountViewModel.toastManager::toast,
        )
    }
}
