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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.marmotGroups.marmotEmojiMediaPortOf
import com.vitorpamplona.amethyst.commons.nests.room.activity.NestBridge
import com.vitorpamplona.amethyst.commons.notices.ui.ResUserNoticeResolver
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.commons.service.call.CallSessionBridge
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.state.UiSettingsState
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.ui.components.toasts.ToastManager
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client

/**
 * The [BaseAccountViewModel] the GUI apps render: adds the [toastManager] its notices land in,
 * worded by [ResUserNoticeResolver], and the platform bridges that hand this ViewModel to
 * activities outside the main navigation.
 */
@Stable
class AccountViewModel(
    account: Account,
    settings: UiSettingsState,
    torSettings: TorSettingsFlow,
    dataSources: RelaySubscriptionsCoordinator,
    httpClientBuilder: IRoleBasedHttpClientBuilder,
    nip05ClientBuilder: () -> INip05Client,
    host: AccountViewModelHost,
    val toastManager: ToastManager = ToastManager(),
) : BaseAccountViewModel(
        account = account,
        settings = settings,
        torSettings = torSettings,
        dataSources = dataSources,
        httpClientBuilder = httpClientBuilder,
        nip05ClientBuilder = nip05ClientBuilder,
        host = host,
        notices = toastManager,
        noticeResolver = ResUserNoticeResolver,
    ) {
    init {
        // Populate CallSessionBridge so CallActivity and background
        // receivers can reach callManager + account + accountViewModel.
        CallSessionBridge
            .set(callManager, account, this)

        // Lets a custom emoji in a Marmot group travel as an encrypted picture,
        // through this account's own HTTP routes (proxy, Tor).
        if (account.marmot.emojiMediaPort == null) {
            account.marmot.emojiMediaPort = marmotEmojiMediaPortOf(account, httpClientBuilder)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Only the ViewModel references are dropped; see BaseAccountViewModel.onCleared.
        CallSessionBridge
            .clearViewModel(this)
        NestBridge
            .clear(this)
    }
}
