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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.ChannelFinderFilterAssemblyGroup
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.ChannelFinderQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun ChannelFinderFilterAssemblerSubscription(
    channel: Channel,
    accountViewModel: AccountViewModel,
) = ChannelFinderFilterAssemblerSubscription(channel, accountViewModel.account, accountViewModel.dataSources().channelFinder)

@Composable
fun ChannelFinderFilterAssemblerSubscription(
    channel: Channel,
    account: Account,
    dataSource: ChannelFinderFilterAssemblyGroup,
) {
    // different screens get different states
    // even if they are tracking the same tag.
    val state =
        remember(channel, account) {
            ChannelFinderQueryState(channel, account)
        }

    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}
