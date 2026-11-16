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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource.RELAY_GROUP_WARMUP_LIMIT
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource.RelayGroupCardWarmupFilterAssembler
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource.RelayGroupCardWarmupQueryState
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * Mount on a group card to warm its recent content ahead of a tap. [contentOnly] drops the
 * metadata/roster filter (39000-39003) — pass true on the discovery feed, where the directory
 * subscription already streams that for the whole relay, so each visible row asks for content only.
 */
@Composable
fun RelayGroupCardWarmupSubscription(
    channel: RelayGroupChannel,
    dataSource: RelayGroupCardWarmupFilterAssembler,
    accountViewModel: AccountViewModel,
    contentOnly: Boolean = false,
    contentLimit: Int = RELAY_GROUP_WARMUP_LIMIT,
) {
    val account = accountViewModel.account
    val state =
        remember(account, channel.groupId, contentOnly, contentLimit) {
            RelayGroupCardWarmupQueryState(account, channel, contentOnly, contentLimit)
        }

    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}
