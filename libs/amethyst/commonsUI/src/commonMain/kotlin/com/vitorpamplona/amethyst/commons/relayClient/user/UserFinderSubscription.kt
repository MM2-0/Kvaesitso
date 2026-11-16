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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription

/**
 * The shared per-user metadata data source for the current front end. A front
 * end provides this once near its composition root (Android via AppModules,
 * Desktop via its subscriptions coordinator). Reading it without a provider is
 * a programming error — the `observeUser*` composables must never be reachable
 * from a composition that has no relay client (e.g. the Android `:napplet`
 * sandbox process).
 */
val LocalUserFinder =
    staticCompositionLocalOf<UserFinderFilterAssembler> {
        error("LocalUserFinder not provided")
    }

/**
 * The current logged-in account, in the narrow [UserFinderAccount] view the
 * finder needs to route REQs. Provided alongside [LocalUserFinder].
 */
val LocalUserFinderAccount =
    staticCompositionLocalOf<UserFinderAccount> {
        error("LocalUserFinderAccount not provided")
    }

/**
 * Subscribes to relay updates for [user]'s metadata (and relay list / reports /
 * contact cards) for as long as this composable is in composition, coalesced
 * with every other on-screen user into batched REQs by [dataSource].
 *
 * Because a `LazyColumn` composes only the visible window (+ a small prefetch
 * buffer), this naturally means "load metadata only for users currently on
 * screen" — the [LifecycleAwareKeyDataSourceSubscription] unsubscribes as soon
 * as the row leaves composition, and ~30s after the app is backgrounded.
 */
@Composable
fun UserFinderFilterAssemblerSubscription(
    user: User,
    account: UserFinderAccount,
    dataSource: UserFinderFilterAssembler,
) {
    // Different screens get their own query-state instance even when tracking
    // the same user; the assembler dedups to one REQ per pubkey.
    val state = remember(user, account) { UserFinderQueryState(user, account) }

    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}

/**
 * Convenience overload that reads the front end's [LocalUserFinder] and
 * [LocalUserFinderAccount] from the composition.
 */
@Composable
fun UserFinderFilterAssemblerSubscription(user: User) {
    UserFinderFilterAssemblerSubscription(
        user = user,
        account = LocalUserFinderAccount.current,
        dataSource = LocalUserFinder.current,
    )
}
