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
package com.vitorpamplona.amethyst.commons.relayClient.event

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.relayClient.user.LocalUserFinderAccount
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderAccount

/**
 * The shared per-note event data source (reactions / zaps / reposts / replies /
 * OTS / references) for the current front end. Provided once near the composition
 * root (Android via AppModules, Desktop via its subscriptions coordinator).
 * Reading it without a provider is a programming error — the per-note observers
 * must never be reachable from a composition that has no relay client (e.g. the
 * Android `:napplet` sandbox process).
 *
 * The *account* half is reused from the user-finder: [LocalUserFinderAccount]
 * already carries the narrow relay-hint seam the event loaders need.
 */
val LocalEventFinder =
    staticCompositionLocalOf<EventFinderFilterAssembler> {
        error("LocalEventFinder not provided")
    }

/**
 * Subscribes to relay updates for [note]'s interactions (reactions, zaps,
 * reposts, replies, …) for as long as this composable is in composition,
 * coalesced with every other on-screen note into batched REQs by [dataSource].
 *
 * Like the user-finder, because a `LazyColumn` composes only the visible window
 * (+ a small prefetch buffer) this means "load interactions only for notes
 * currently on screen" — [LifecycleAwareKeyDataSourceSubscription] unsubscribes
 * ~30s after the row leaves composition or the app is backgrounded.
 */
@Composable
fun EventFinderFilterAssemblerSubscription(
    note: Note,
    account: UserFinderAccount,
    dataSource: EventFinderFilterAssembler,
) {
    // Different screens get their own query-state instance even when tracking
    // the same note; the assembler dedups to one REQ per note.
    val state =
        remember(note, account) {
            EventFinderQueryState(note, account)
        }

    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}

/**
 * Convenience overload that reads the front end's [LocalEventFinder] and
 * [LocalUserFinderAccount] from the composition.
 */
@Composable
fun EventFinderFilterAssemblerSubscription(note: Note) {
    EventFinderFilterAssemblerSubscription(
        note = note,
        account = LocalUserFinderAccount.current,
        dataSource = LocalEventFinder.current,
    )
}
