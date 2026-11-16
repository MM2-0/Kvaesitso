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
package com.vitorpamplona.amethyst.commons.relayClient.notifyCommand.compose

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.relayClient.notify.NotifyRequestsCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.payment_required_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

@Composable
fun DisplayNotifyMessages(
    accountViewModel: AccountViewModel,
    nav: INav,
) = DisplayNotifyMessages(accountViewModel.account.relayNotifications, accountViewModel, nav)

@Composable
fun DisplayNotifyMessages(
    requests: NotifyRequestsCache,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // [requests] is THIS account's own cache. NotifyCoordinator only files a NOTIFY under the account
    // whose AUTH the relay rejected, so there is no cross-account leak to filter out here — a prompt
    // in this cache genuinely belongs to this account.
    val openDialogMsg = requests.transientPaymentRequests.collectAsStateWithLifecycle()

    openDialogMsg.value.firstOrNull()?.let { request ->
        NotifyRequestDialog(
            title =
                stringRes(
                    id = Res.string.payment_required_title,
                    request.relayUrl.displayUrl(),
                ),
            textContent = request.description,
            accountViewModel = accountViewModel,
            nav = nav,
            onDismiss = { requests.dismissPaymentRequest(request) },
            onBlockRelay =
                if (accountViewModel.isWriteable()) {
                    {
                        accountViewModel.launchSigner {
                            accountViewModel.account.blockRelay(request.relayUrl)

                            // Reached only once the block is signed and published, because
                            // reportSignerErrors swallows a refused or timed-out signature without
                            // a toast: dismissing up front would close the dialog on a relay that
                            // is still unblocked and leave the user no sign anything failed. The
                            // prompt staying up is the feedback.
                            //
                            // Every queued prompt from the relay goes at once, not just the one on
                            // screen — a paid relay files one NOTIFY per rejected AUTH.
                            requests.dismissAllFrom(request.relayUrl)
                        }
                    }
                } else {
                    null
                },
        )
    }
}
