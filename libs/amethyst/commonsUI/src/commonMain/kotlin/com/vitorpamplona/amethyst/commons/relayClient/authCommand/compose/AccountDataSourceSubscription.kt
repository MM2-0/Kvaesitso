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
package com.vitorpamplona.amethyst.commons.relayClient.authCommand.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthCoordinator
import com.vitorpamplona.amethyst.commons.relayClient.auth.ScreenAuthAccount
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun RelayAuthSubscription(accountViewModel: AccountViewModel) = RelayAuthSubscription(accountViewModel, LocalAppServices.current.authCoordinator)

@Composable
fun RelayAuthSubscription(
    accountViewModel: AccountViewModel,
    dataSource: AuthCoordinator,
) {
    val account = accountViewModel.account

    // The per-account NIP-42 policy ledger now lives on Account (account.relayAuthLedger), so this
    // only has to register the account itself. The coordinator decides + signs per account.
    val state =
        remember(accountViewModel) {
            ScreenAuthAccount(account)
        }

    DisposableEffect(state) {
        dataSource.subscribe(state)
        onDispose {
            dataSource.unsubscribe(state)
        }
    }
}
