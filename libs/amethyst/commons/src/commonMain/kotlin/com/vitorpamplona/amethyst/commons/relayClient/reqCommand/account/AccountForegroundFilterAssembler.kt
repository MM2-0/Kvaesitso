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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.follows.AccountFollowsLoaderSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip01Notifications.AccountNotificationsEoseFromRandomRelaysManager
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.IAuthStatus
import kotlinx.coroutines.CoroutineScope

/**
 * Account-scoped loaders that only need to run while the app has a screen
 * in the foreground. Mounted by a lifecycle-aware subscription so they pause
 * on ON_STOP and resume on ON_START.
 *
 * Lightweight always-on account work (metadata, gift wraps, drafts, inbox
 * notifications, marmot groups) stays in [AccountFilterAssembler].
 */
@Stable
class AccountForegroundFilterAssembler(
    client: INostrClient,
    cache: LocalCache,
    authenticator: IAuthStatus,
    failureTracker: RelayOfflineTracker,
    scope: CoroutineScope,
) : ComposeSubscriptionManager<AccountUiQueryState>() {
    val group =
        listOf(
            AccountFollowsLoaderSubAssembler(client, cache, scope, authenticator, failureTracker, ::allKeys),
            AccountNotificationsEoseFromRandomRelaysManager(client, ::allKeys),
        )

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}
