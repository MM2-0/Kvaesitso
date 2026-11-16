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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.polls.results.datasources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.relayClient.polls.results.PollResponsesFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.polls.results.PollResponsesQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/** Subscribes to a poll's votes for as long as its results screen is composed. */
@Composable
fun PollResponsesFilterAssemblerSubscription(
    pollId: HexKey,
    accountViewModel: AccountViewModel,
) = PollResponsesFilterAssemblerSubscription(
    pollId,
    accountViewModel.account,
    accountViewModel.dataSources().pollResponses,
)

@Composable
fun PollResponsesFilterAssemblerSubscription(
    pollId: HexKey,
    account: Account,
    filterAssembler: PollResponsesFilterAssembler,
) {
    // different screens get different states, even when tracking the same poll.
    val state =
        remember(pollId, account) {
            PollResponsesQueryState(pollId, account)
        }

    LifecycleAwareKeyDataSourceSubscription(state, filterAssembler)
}
