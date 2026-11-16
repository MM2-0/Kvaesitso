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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.publicChannels.concord.datasource.ConcordChannelHistoryFilterAssembler
import com.vitorpamplona.amethyst.commons.chats.publicChannels.concord.datasource.ConcordChannelHistoryQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * Mount on the open Concord Channel screen to keep its backward-history pager bound and armed. The
 * channel's Chat Plane pubkey (the REQ author) is only known once the Control Plane folds, so — like
 * [ConcordChannelSubscription] — we re-derive the history filter whenever
 * [com.vitorpamplona.amethyst.commons.model.concord.ConcordSessionManager.revision] advances.
 */
@Composable
fun ConcordChannelHistorySubscription(
    communityId: String,
    channelId: String,
    dataSource: ConcordChannelHistoryFilterAssembler,
    accountViewModel: AccountViewModel,
) {
    val account = accountViewModel.account
    val state = remember(account, communityId, channelId) { ConcordChannelHistoryQueryState(account, communityId, channelId) }

    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) {
        // A fold can reveal this channel's plane pubkey (the REQ author) for the first time.
        dataSource.invalidateFilters()
    }

    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}
