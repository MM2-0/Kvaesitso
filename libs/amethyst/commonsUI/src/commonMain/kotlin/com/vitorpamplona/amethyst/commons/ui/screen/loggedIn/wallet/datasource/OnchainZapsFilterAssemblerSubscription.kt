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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.relayClient.wallet.OnchainZapsQueryState
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * Lifecycle-aware relay subscription for kind-8333 zaps involving [user],
 * bounded by [windowSinceSeconds] (the oldest visible chain transaction's
 * blockTime) so the relay only ships zaps that could plausibly attribute to
 * what we're showing.
 *
 * Re-subscribes whenever either input changes — scrolling back into older
 * transactions widens the window and re-queries; switching accounts swaps
 * the key entirely.
 */
@Composable
fun OnchainZapsFilterAssemblerSubscription(
    user: User,
    windowSinceSeconds: Long?,
    accountViewModel: AccountViewModel,
) {
    val state =
        remember(user, windowSinceSeconds) {
            OnchainZapsQueryState(user, windowSinceSeconds)
        }
    LifecycleAwareKeyDataSourceSubscription(state, accountViewModel.dataSources().onchainZaps)
}
