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
package com.vitorpamplona.amethyst.commons.nipsOnNostr.ui.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.relayClient.nips.NipsQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/** Keeps every NIP on Nostr arriving while the NIPs screen (or its bottom-bar tab) is on screen. */
@Composable
fun AllNipsSubscription(accountViewModel: AccountViewModel) {
    val state = remember(accountViewModel.account) { NipsQueryState.All(accountViewModel.account) }
    LifecycleAwareKeyDataSourceSubscription(state, accountViewModel.dataSources().nips)
}

/** Keeps the NIPs that define [kind] arriving while something showing them is on screen. */
@Composable
fun NipsDefiningKindSubscription(
    kind: Int,
    accountViewModel: AccountViewModel,
) {
    val state = remember(kind, accountViewModel.account) { NipsQueryState.DefiningKind(kind, accountViewModel.account) }
    LifecycleAwareKeyDataSourceSubscription(state, accountViewModel.dataSources().nips)
}

/** Keeps recent events of [kind] arriving while the kind page is on screen. */
@Composable
fun KindEventsSubscription(
    kind: Int,
    accountViewModel: AccountViewModel,
) {
    val state = remember(kind, accountViewModel.account) { NipsQueryState.KindEvents(kind, accountViewModel.account) }
    LifecycleAwareKeyDataSourceSubscription(state, accountViewModel.dataSources().nips)
}

/** Watches the NIP [note] for new versions while it is being read. */
@Composable
fun WatchNipSubscription(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
) {
    val state = remember(note, accountViewModel.account) { NipsQueryState.Watch(note, accountViewModel.account) }
    LifecycleAwareKeyDataSourceSubscription(state, accountViewModel.dataSources().nips)
}
