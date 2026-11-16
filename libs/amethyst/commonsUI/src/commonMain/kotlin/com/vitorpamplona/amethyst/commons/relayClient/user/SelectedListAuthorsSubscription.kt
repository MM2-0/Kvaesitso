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
package com.vitorpamplona.amethyst.commons.relayClient.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Watches the members of every list the user picked in a top bar ([Account.selectedListAuthors])
 * through the regular [UserFinderFilterAssembler], as feed authors ([UserFinderQueryState.isFeedAuthor]):
 * their relay lists and live metadata load even when nobody follows them, so those feeds can ask
 * their relays. Called once, at the app root.
 *
 * Changing a selection swaps the set, which unsubscribes the old members and subscribes the new
 * ones. Like the other app-wide loaders it stops 30s after the app leaves the foreground.
 */
@Composable
fun SelectedListAuthorsSubscription(accountViewModel: AccountViewModel) {
    SelectedListAuthorsSubscription(accountViewModel.account, accountViewModel.dataSources().userFinder)
}

@Composable
fun SelectedListAuthorsSubscription(
    account: Account,
    dataSource: UserFinderFilterAssembler,
) {
    val authors by account.selectedListAuthors.collectAsStateWithLifecycle()

    // A list can hold thousands of members, so their users are looked up (and created) off the
    // main thread. The set only changes when a selection or a selected list changes.
    val states by produceState(emptyList<UserFinderQueryState>(), authors, account) {
        value =
            withContext(Dispatchers.Default) {
                authors.mapNotNull { pubkey ->
                    account.cache.checkGetOrCreateUser(pubkey)?.let { UserFinderQueryState(it, account, isFeedAuthor = true) }
                }
            }
    }

    LifecycleAwareKeyDataSourceSubscription(states, dataSource)
}
